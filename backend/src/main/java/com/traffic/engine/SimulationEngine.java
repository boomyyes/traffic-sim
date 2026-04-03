package com.traffic.engine;

import com.traffic.model.RoadNetwork;
import com.traffic.model.RoadSegment;
import com.traffic.model.Vehicle;
import com.traffic.model.VehicleType;
import com.traffic.physics.PhysicsEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core simulation engine.
 * Manages vehicle lifecycle: spawn → perceive → decide → update → junction
 * transfer → remove.
 *
 * Vehicles spawn at the START of each road and travel toward the END.
 * At the junction zone, vehicles with a targetRoadId transfer to their target
 * road (turning).
 */
@Service
public class SimulationEngine {

    private final PhysicsEngine physicsEngine;
    private final SimulationConfig config;

    private RoadNetwork roadNetwork;
    private final List<Vehicle> vehicles = new CopyOnWriteArrayList<>();
    private SpatialGrid spatialGrid;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Random random = new Random();

    private long tickCount = 0;
    private int spawnAccumulator = 0;

    // Indian traffic mix: ~50% bikes, 15% autos, 20% cars, 10% buses, 5% trucks
    private static final VehicleType[] SPAWN_DISTRIBUTION = {
            VehicleType.BIKE, VehicleType.BIKE, VehicleType.BIKE,
            VehicleType.BIKE, VehicleType.BIKE, VehicleType.BIKE,
            VehicleType.BIKE, VehicleType.BIKE, VehicleType.BIKE, VehicleType.BIKE,
            VehicleType.AUTO_RICKSHAW, VehicleType.AUTO_RICKSHAW, VehicleType.AUTO_RICKSHAW,
            VehicleType.CAR, VehicleType.CAR, VehicleType.CAR, VehicleType.CAR,
            VehicleType.BUS, VehicleType.BUS,
            VehicleType.TRUCK
    };

    public SimulationEngine(PhysicsEngine physicsEngine, SimulationConfig config) {
        this.physicsEngine = physicsEngine;
        this.config = config;
    }

    public void initialize(RoadNetwork network) {
        this.roadNetwork = network;

        double maxX = 300, maxY = 700;
        for (RoadSegment seg : network.getAllSegments()) {
            maxX = Math.max(maxX, Math.max(seg.getStartX(), seg.getEndX()) + seg.getWidth());
            maxY = Math.max(maxY, Math.max(seg.getStartY(), seg.getEndY()) + seg.getWidth());
        }
        this.spatialGrid = new SpatialGrid(maxX + 50, maxY + 50, 20.0);
        vehicles.clear();
        tickCount = 0;

        // Inject queue counter for adaptive signal control
        if (network.getSignalController() != null) {
            network.getSignalController().setQueueCounter(this::countVehiclesOnRoad);
        }
    }

    /**
     * Count how many active vehicles are on a given road.
     * Used by the signal controller to prioritize congested approaches.
     */
    private int countVehiclesOnRoad(int roadId) {
        int count = 0;
        for (Vehicle v : vehicles) {
            if (v.isActive() && v.getRoadSegmentId() == roadId)
                count++;
        }
        return count;
    }

    /**
     * Execute one simulation tick.
     */
    public void tick() {
        if (!running.get() || roadNetwork == null)
            return;

        double dt = config.getDt();
        tickCount++;

        // 1. Tick signals
        roadNetwork.tickSignals(dt);

        // 2. Spawn
        spawnVehicles();

        // 3. Cache world coordinates for ALL active vehicles (needed for cross-road
        // detection)
        for (Vehicle v : vehicles) {
            if (!v.isActive())
                continue;
            RoadSegment road = roadNetwork.getSegment(v.getRoadSegmentId());
            if (road != null) {
                v.setWorldCoords(getWorldX(v, road), getWorldY(v, road));
            }
        }

        // 4. Rebuild spatial index (uses world coords for cross-road proximity)
        spatialGrid.rebuild(new ArrayList<>(vehicles));

        // 5. Compute next state — pass ALL vehicles for cross-road awareness
        List<Vehicle> allVehicles = new ArrayList<>(vehicles);
        vehicles.parallelStream()
                .filter(Vehicle::isActive)
                .forEach(vehicle -> {
                    RoadSegment road = roadNetwork.getSegment(vehicle.getRoadSegmentId());
                    if (road != null) {
                        physicsEngine.updateVehicle(vehicle, allVehicles, road, dt, roadNetwork);
                    }
                });

        // 6. Commit state
        vehicles.parallelStream()
                .filter(Vehicle::isActive)
                .forEach(Vehicle::commitState);

        // 7. World-space collision resolution (works across ALL roads)
        resolveWorldCollisions();

        // 8. Junction transfers
        processJunctionTransfers();

        // 9. Remove exited vehicles
        removeExitedVehicles();
    }

    /**
     * Spawn vehicles only on roads with spawnEnabled=true, at y=0 (road start).
     * Assigns a random target road for turning at the junction.
     */
    private void spawnVehicles() {
        if (vehicles.size() >= config.getMaxVehicles())
            return;

        spawnAccumulator += config.getSpawnRatePerSecond();
        int ticksPerSecond = 1000 / config.getTickRateMs();
        int toSpawn = spawnAccumulator / ticksPerSecond;
        spawnAccumulator -= toSpawn * ticksPerSecond;

        List<RoadSegment> spawnableRoads = new ArrayList<>();
        for (RoadSegment seg : roadNetwork.getAllSegments()) {
            if (seg.isSpawnEnabled())
                spawnableRoads.add(seg);
        }
        if (spawnableRoads.isEmpty())
            return;

        for (int i = 0; i < toSpawn; i++) {
            if (vehicles.size() >= config.getMaxVehicles())
                break;

            RoadSegment road = spawnableRoads.get(random.nextInt(spawnableRoads.size()));
            VehicleType type = SPAWN_DISTRIBUTION[random.nextInt(SPAWN_DISTRIBUTION.length)];

            double halfWidth = type.getWidth() / 2.0;
            double lateralPos = halfWidth + random.nextDouble() * (road.getWidth() - type.getWidth());
            double spawnY = type.getLength() / 2.0 + 1.0;

            if (!isSpawnZoneClear(road.getId(), lateralPos, spawnY, type.getWidth(), type.getLength())) {
                continue;
            }

            double initialSpeed = type.getMaxSpeed() * (0.3 + random.nextDouble() * 0.5);
            Vehicle v = new Vehicle(type, lateralPos, spawnY, initialSpeed, road.getId());

            // Assign a turning target: 60% straight, 20% left, 20% right
            assignTurnTarget(v, road);

            vehicles.add(v);
        }
    }

    /**
     * Assign a target road based on the vehicle's LATERAL POSITION.
     * Indian traffic: loose lane discipline means:
     * - Left side of road → likely to turn left or go straight
     * - Center of road → mostly straight
     * - Right side of road → likely to turn right or go straight
     *
     * Turn direction is determined by the heading difference between roads.
     */
    private void assignTurnTarget(Vehicle vehicle, RoadSegment currentRoad) {
        List<RoadSegment> allRoads = roadNetwork.getAllSegments();
        if (allRoads.size() <= 1) {
            vehicle.setTargetRoadId(-1);
            return;
        }

        // Determine vehicle's lateral position fraction (0 = left edge, 1 = right edge)
        double lateralFrac = vehicle.getX() / currentRoad.getWidth();

        // Find left-turn and right-turn target roads based on heading
        int leftTurnId = -1, rightTurnId = -1;
        double myHeading = currentRoad.getHeading();

        for (RoadSegment r : allRoads) {
            if (r.getId() == currentRoad.getId())
                continue;

            double angleDiff = normalizeAngle(r.getHeading() - myHeading);

            // angleDiff ≈ +π/2 → right turn, ≈ -π/2 → left turn, ≈ π → U-turn
            if (angleDiff > 0.3 && angleDiff < 2.8) {
                // Right turn candidate (clockwise turn)
                rightTurnId = r.getId();
            } else if (angleDiff < -0.3 && angleDiff > -2.8) {
                // Left turn candidate (counter-clockwise turn)
                leftTurnId = r.getId();
            }
            // ≈ ±π is a U-turn — skip
        }

        // Probabilities based on lateral position
        // Nearly impossible to make a wrong-lane turn (3%)
        double pLeft, pStraight, pRight;
        if (lateralFrac < 0.30) {
            // Left side of road → strongly favors left turn
            pLeft = 0.60; pStraight = 0.37; pRight = 0.03;
        } else if (lateralFrac > 0.70) {
            // Right side of road → strongly favors right turn
            pLeft = 0.03; pStraight = 0.37; pRight = 0.60;
        } else {
            // Center → mostly straight
            pLeft = 0.15; pStraight = 0.60; pRight = 0.25;
        }

        double roll = random.nextDouble();
        if (roll < pLeft && leftTurnId >= 0) {
            vehicle.setTargetRoadId(leftTurnId);
        } else if (roll < pLeft + pRight && rightTurnId >= 0) {
            vehicle.setTargetRoadId(rightTurnId);
        } else {
            vehicle.setTargetRoadId(-1); // Go straight
        }
    }

    /** Normalize angle to [-π, π] */
    private double normalizeAngle(double angle) {
        while (angle > Math.PI)
            angle -= 2 * Math.PI;
        while (angle < -Math.PI)
            angle += 2 * Math.PI;
        return angle;
    }

    /**
     * Process vehicles that have entered the junction zone and need to turn.
     * Transfers them from their current road to their target road.
     */
    private void processJunctionTransfers() {
        if (!roadNetwork.hasJunction())
            return;

        for (Vehicle v : vehicles) {
            if (!v.isActive())
                continue;
            if (v.getTargetRoadId() <= 0)
                continue; // Going straight

            RoadSegment currentRoad = roadNetwork.getSegment(v.getRoadSegmentId());
            if (currentRoad == null)
                continue;

            // Convert vehicle position to world coordinates
            double worldX = getWorldX(v, currentRoad);
            double worldY = getWorldY(v, currentRoad);

            // Check if vehicle is in the junction zone
            if (roadNetwork.isInJunction(worldX, worldY)) {
                RoadSegment targetRoad = roadNetwork.getSegment(v.getTargetRoadId());
                if (targetRoad == null) {
                    v.setTargetRoadId(-1);
                    continue;
                }

                // Transfer: remap position to target road's coordinate system
                double targetLocalY = worldToRoadLocalY(worldX, worldY, targetRoad);
                double targetLocalX = worldToRoadLocalX(worldX, worldY, targetRoad);

                // Clamp lateral position to target road width
                targetLocalX = Math.max(v.getWidth() / 2.0,
                        Math.min(targetRoad.getWidth() - v.getWidth() / 2.0, targetLocalX));

                // KEY FIX: ensure vehicle is placed PAST the junction zone on the target road.
                // This prevents newly-transferred vehicles from sitting at the junction mouth
                // and forming a queue that blocks subsequent turners.
                double junctionExitY = computeJunctionExitY(worldX, worldY, targetRoad);
                targetLocalY = Math.max(targetLocalY, junctionExitY + v.getLength());
                // Also clamp to road length
                targetLocalY = Math.min(targetLocalY, targetRoad.getLength() - v.getLength());

                v.setRoadSegmentId(targetRoad.getId());
                v.setY(targetLocalY);
                v.setX(targetLocalX);
                v.setTargetRoadId(-1); // Transfer complete, now go straight
            }
        }
    }

    /**
     * Convert a vehicle's road-local Y to the longitudinal position along a target
     * road.
     */
    private double worldToRoadLocalY(double worldX, double worldY, RoadSegment road) {
        double dx = road.getEndX() - road.getStartX();
        double dy = road.getEndY() - road.getStartY();
        double len = road.getLength();

        // Project world point onto road direction
        double relX = worldX - road.getStartX();
        double relY = worldY - road.getStartY();

        // Dot product with road direction gives longitudinal position
        return (relX * dx + relY * dy) / len;
    }

    /**
     * Convert world coordinates to road-local lateral position.
     */
    private double worldToRoadLocalX(double worldX, double worldY, RoadSegment road) {
        double dx = road.getEndX() - road.getStartX();
        double dy = road.getEndY() - road.getStartY();
        double len = road.getLength();

        // Perpendicular direction (road's "right")
        double perpX = -dy / len;
        double perpY = dx / len;

        double relX = worldX - road.getStartX();
        double relY = worldY - road.getStartY();

        // Cross product gives lateral offset from road center-line
        double lateralOffset = relX * perpX + relY * perpY;

        return road.getWidth() / 2.0 + lateralOffset;
    }

    /**
     * Compute how far along a target road the junction zone extends,
     * in the road's local longitudinal coordinate system.
     * Vehicles transferred to this road should start at least this far
     * so they're clear of the junction mouth and don't block turners.
     */
    private double computeJunctionExitY(double worldX, double worldY, RoadSegment road) {
        double maxLocalY = 0;
        for (RoadNetwork.JunctionZone jz : roadNetwork.getJunctionZones()) {
            // Sample the four corners of the junction zone
            double[] wxs = {jz.minX(), jz.maxX(), jz.minX(), jz.maxX()};
            double[] wys = {jz.minY(), jz.minY(), jz.maxY(), jz.maxY()};
            for (int k = 0; k < 4; k++) {
                double localY = worldToRoadLocalY(wxs[k], wys[k], road);
                if (localY >= 0 && localY <= road.getLength()) {
                    maxLocalY = Math.max(maxLocalY, localY);
                }
            }
        }
        return maxLocalY;
    }

    /**
     * Get world X coordinate of a vehicle on its road.
     */
    private double getWorldX(Vehicle v, RoadSegment road) {
        double t = v.getY() / road.getLength();
        double roadCenterX = road.getStartX() + t * (road.getEndX() - road.getStartX());

        // Add lateral offset perpendicular to road direction
        double dx = road.getEndX() - road.getStartX();
        double dy = road.getEndY() - road.getStartY();
        double len = road.getLength();
        double perpX = -dy / len; // perpendicular direction

        double lateralOffset = v.getX() - road.getWidth() / 2.0;
        return roadCenterX + lateralOffset * perpX;
    }

    /**
     * Get world Y coordinate of a vehicle on its road.
     */
    private double getWorldY(Vehicle v, RoadSegment road) {
        double t = v.getY() / road.getLength();
        double roadCenterY = road.getStartY() + t * (road.getEndY() - road.getStartY());

        double dx = road.getEndX() - road.getStartX();
        double dy = road.getEndY() - road.getStartY();
        double len = road.getLength();
        double perpY = dx / len;

        double lateralOffset = v.getX() - road.getWidth() / 2.0;
        return roadCenterY + lateralOffset * perpY;
    }

    private boolean isSpawnZoneClear(int roadId, double x, double y,
            double vehWidth, double vehLength) {
        double safetyMargin = 3.0;
        for (Vehicle existing : vehicles) {
            if (!existing.isActive() || existing.getRoadSegmentId() != roadId)
                continue;

            double eFront = existing.getY() + existing.getLength() / 2.0;
            double eRear = existing.getY() - existing.getLength() / 2.0;
            double sFront = y + vehLength / 2.0 + safetyMargin;
            double sRear = y - vehLength / 2.0 - safetyMargin;

            if (sRear < eFront && sFront > eRear) {
                double eLeft = existing.getX() - existing.getWidth() / 2.0;
                double eRight = existing.getX() + existing.getWidth() / 2.0;
                double sLeft = x - vehWidth / 2.0;
                double sRight = x + vehWidth / 2.0;
                if (sLeft < eRight && sRight > eLeft)
                    return false;
            }
        }
        return true;
    }

    /**
     * World-space collision resolution.
     * Updates world coords then resolves same-road overlaps.
     * Cross-road conflicts are handled by junction braking in PhysicsEngine,
     * NOT by speed penalties here (which caused oscillation).
     */
    private void resolveWorldCollisions() {
        // Update world coords after commit
        for (Vehicle v : vehicles) {
            if (!v.isActive())
                continue;
            RoadSegment road = roadNetwork.getSegment(v.getRoadSegmentId());
            if (road != null) {
                v.setWorldCoords(getWorldX(v, road), getWorldY(v, road));
            }
        }

        // Only resolve same-road overlaps (push follower back)
        List<Vehicle> active = new ArrayList<>();
        for (Vehicle v : vehicles)
            if (v.isActive())
                active.add(v);

        for (int i = 0; i < active.size(); i++) {
            Vehicle a = active.get(i);
            for (int j = i + 1; j < active.size(); j++) {
                Vehicle b = active.get(j);

                // Only resolve same-road overlaps
                if (a.getRoadSegmentId() != b.getRoadSegmentId())
                    continue;

                // Check lateral overlap
                double aLeft = a.getX() - a.getWidth() / 2.0;
                double aRight = a.getX() + a.getWidth() / 2.0;
                double bLeft = b.getX() - b.getWidth() / 2.0;
                double bRight = b.getX() + b.getWidth() / 2.0;
                if (!(aLeft < bRight && aRight > bLeft))
                    continue;

                // Check longitudinal overlap
                double aFront = a.getY() + a.getLength() / 2.0;
                double aRear = a.getY() - a.getLength() / 2.0;
                double bFront = b.getY() + b.getLength() / 2.0;
                double bRear = b.getY() - b.getLength() / 2.0;
                if (!(aRear < bFront && aFront > bRear))
                    continue;

                // Push follower back, match speeds
                Vehicle leader = a.getY() >= b.getY() ? a : b;
                Vehicle follower = a.getY() >= b.getY() ? b : a;
                double leaderRear = leader.getY() - leader.getLength() / 2.0;
                follower.setY(leaderRear - follower.getLength() / 2.0 - 0.5);
                if (follower.getSpeed() > leader.getSpeed()) {
                    follower.setSpeed(leader.getSpeed());
                }
            }
        }
    }

    private void removeExitedVehicles() {
        vehicles.removeIf(v -> {
            RoadSegment road = roadNetwork.getSegment(v.getRoadSegmentId());
            if (road == null)
                return true;
            return v.getY() > road.getLength() + 10;
        });
    }

    // ---- Control ----

    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
    }

    public void reset() {
        running.set(false);
        vehicles.clear();
        tickCount = 0;
    }

    public boolean isRunning() {
        return running.get();
    }

    // ---- Accessors ----

    public List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public RoadNetwork getRoadNetwork() {
        return roadNetwork;
    }

    public long getTickCount() {
        return tickCount;
    }

    public int getActiveVehicleCount() {
        return (int) vehicles.stream().filter(Vehicle::isActive).count();
    }

    public double getAverageSpeed() {
        return vehicles.stream()
                .filter(Vehicle::isActive)
                .mapToDouble(Vehicle::getSpeed)
                .average().orElse(0.0);
    }
}
