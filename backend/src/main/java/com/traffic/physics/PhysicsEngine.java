package com.traffic.physics;

import com.traffic.ai.VehicleAI;
import com.traffic.model.RoadNetwork;
import com.traffic.model.RoadSegment;
import com.traffic.model.TrafficSignal;
import com.traffic.model.Vehicle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates longitudinal (IDM) and lateral (Seepage) physics.
 * Now delegates to VehicleAI for per-vehicle state-based decisions.
 */
@Service
public class PhysicsEngine {

    private final IDMCalculator idm;
    private final SeepageLogic seepage;

    public PhysicsEngine(
            @Value("${sim.idm.desired-speed:13.89}") double desiredSpeed,
            @Value("${sim.idm.max-acceleration:2.0}") double maxAccel,
            @Value("${sim.idm.comfortable-deceleration:3.0}") double comfortDecel,
            @Value("${sim.idm.min-gap:2.0}") double minGap,
            @Value("${sim.idm.time-headway:1.5}") double timeHeadway,
            @Value("${sim.idm.accel-exponent:4}") int accelExponent,
            @Value("${sim.strip-width:0.5}") double stripWidth,
            @Value("${sim.seepage.safety-buffer:0.3}") double safetyBuffer,
            @Value("${sim.seepage.min-speed-threshold:2.0}") double speedThreshold) {
        this.idm = new IDMCalculator(desiredSpeed, maxAccel, comfortDecel,
                minGap, timeHeadway, accelExponent);
        this.seepage = new SeepageLogic(stripWidth, safetyBuffer, speedThreshold);
    }

    /**
     * Compute the next state for a vehicle using its AI.
     */
    public void updateVehicle(Vehicle vehicle, List<Vehicle> allVehicles,
                               RoadSegment road, double dt,
                               RoadNetwork network) {
        VehicleAI ai = vehicle.getAI();

        // 1. Same-road neighbors
        List<Vehicle> sameRoadVehicles = allVehicles.stream()
                .filter(v -> v.getRoadSegmentId() == vehicle.getRoadSegmentId()
                        && v.getId() != vehicle.getId() && v.isActive())
                .toList();

        // 2. Find same-road leader
        LeaderInfo leader = findLeaderInfo(vehicle, sameRoadVehicles);

        // 3. Update AI state machine
        VehicleAI.AIOutput aiOutput = ai.update(vehicle, road, network, leader.gap, dt);

        // 4. Signal awareness — only if NOT committed
        if (!aiOutput.committed()) {
            LeaderInfo signalLeader = findSignalLeader(vehicle, road, network, ai);
            if (signalLeader.gap < leader.gap) {
                leader = signalLeader;
            }
        }

        // 5. Junction braking — applied to all vehicles to avoid cross-road crashes
        double junctionBraking = computeJunctionBraking(vehicle, allVehicles, network);

        // 6. IDM acceleration with personality-adjusted gap
        double effectiveDesiredSpeed = Math.min(
                vehicle.getType().getMaxSpeed(), road.getSpeedLimit());
        double adjustedGap = Math.max(leader.gap, ai.getMinFollowingGap());
        double acceleration = idm.calculateAcceleration(
                vehicle.getSpeed(), adjustedGap, leader.deltaV,
                effectiveDesiredSpeed);

        // 7. Apply AI acceleration modifier (personality-based)
        acceleration += aiOutput.accelModifier();

        // 8. Enforce minimum speed for committed vehicles (push through junction)
        if (aiOutput.committed() && vehicle.getSpeed() < aiOutput.minSpeed()) {
            acceleration = Math.max(acceleration, 2.0);
        }

        // 9. Apply junction braking LAST, so it overrides the minimum speed push
        acceleration = Math.min(acceleration, acceleration + junctionBraking);

        // 10. Clamp
        acceleration = Math.max(-vehicle.getType().getMaxAcceleration() * 2,
                Math.min(vehicle.getType().getMaxAcceleration(), acceleration));

        // 11. Compute turn-side lateral bias
        // When approaching a junction to turn, the vehicle should drift toward
        // the edge of the road appropriate for its turn direction, seeping past
        // straight-going vehicles instead of joining their queue.
        double lateralMove = seepage.calculateLateralMove(
                vehicle, sameRoadVehicles, road, leader.gap);
        lateralMove *= aiOutput.lateralMultiplier();

        if (aiOutput.state() == VehicleAI.State.APPROACHING_JUNCTION
                && vehicle.getTargetRoadId() >= 0 && network != null) {
            double bias = computeTurnLateralBias(vehicle, road, network);
            if (bias != 0) {
                // The bias overrides normal seepage when turning:
                // pull vehicle strongly toward its turn side
                lateralMove = lateralMove * 0.2 + bias * 0.8;
            }
        }

        // 12. Prepare next state
        vehicle.prepareNextState(acceleration, lateralMove, dt);
    }

    public void updateVehicle(Vehicle vehicle, List<Vehicle> neighbors,
                               RoadSegment road, double dt) {
        updateVehicle(vehicle, neighbors, road, dt, null);
    }

    /**
     * Junction braking for non-committed vehicles.
     */
    private double computeJunctionBraking(Vehicle vehicle, List<Vehicle> allVehicles,
                                           RoadNetwork network) {
        if (network == null || !network.hasJunction()) return 0;

        double myWX = vehicle.getWorldX();
        double myWY = vehicle.getWorldY();

        // Check if near ANY junction
        boolean nearJunction = false;
        for (RoadNetwork.JunctionZone jz : network.getJunctionZones()) {
            double margin = 15;
            if (myWX >= jz.minX() - margin && myWX <= jz.maxX() + margin
             && myWY >= jz.minY() - margin && myWY <= jz.maxY() + margin) {
                nearJunction = true;
                break;
            }
        }
        if (!nearJunction) return 0;

        double maxBraking = 0;

        for (Vehicle other : allVehicles) {
            if (other.getId() == vehicle.getId()) continue;
            if (!other.isActive()) continue;
            if (other.getRoadSegmentId() == vehicle.getRoadSegmentId()) continue;
            if (other.getSpeed() < 0.5) continue;

            double dx = other.getWorldX() - myWX;
            double dy = other.getWorldY() - myWY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            double combinedHalfLen = (vehicle.getLength() + other.getLength()) / 2.0;
            // Dynamic threat radius based on vehicle's speed (~2 seconds lookahead)
            double threatRadius = combinedHalfLen + 3.0 + (vehicle.getSpeed() * 2.0);

            if (dist < threatRadius && dist > 0.1) {
                double severity = 1.0 - (dist / threatRadius);
                double braking = -severity * 6.0; // Brake harder (up to -6 m/s^2)
                maxBraking = Math.min(maxBraking, braking);
            }
        }

        return maxBraking;
    }

    /**
     * Signal leader — strictly enforced, no running yellow/red.
     */
    private LeaderInfo findSignalLeader(Vehicle vehicle, RoadSegment road,
                                         RoadNetwork network, VehicleAI ai) {
        if (network == null)
            return new LeaderInfo(Double.MAX_VALUE, 0);

        List<TrafficSignal> signals = network.getSignalsForRoad(road.getId());
        double myFront = vehicle.getY() + vehicle.getLength() / 2.0;
        double minGap = Double.MAX_VALUE;

        for (TrafficSignal signal : signals) {
            if (!signal.shouldStop()) continue;
            double stopLine = signal.getStopLineY();
            
            double gap = stopLine - myFront;
            
            // If the vehicle's nose is slightly past the stop line (e.g. up to half its length),
            // it still needs to treat the signal as a solid wall to avoid creeping into the intersection.
            if (gap < -vehicle.getLength() / 2.0) {
                // Center has crossed the line -> fully committed to intersection, ignore this signal.
                continue;
            }
            
            // If gap is negative but we haven't committed (just slightly over the line), clamp to 0 
            // so IDM forces a hard stop.
            if (gap < 0) gap = 0.0;
            
            if (gap < minGap) minGap = gap;
        }

        return new LeaderInfo(minGap, vehicle.getSpeed());
    }

    private LeaderInfo findLeaderInfo(Vehicle vehicle, List<Vehicle> sameRoadVehicles) {
        double minGap = Double.MAX_VALUE;
        double deltaV = 0;

        double myX = vehicle.getX();
        double myY = vehicle.getY();
        double myHalfWidth = vehicle.getWidth() / 2.0;
        double myFront = myY + vehicle.getLength() / 2.0;

        for (Vehicle other : sameRoadVehicles) {
            double otherY = other.getY();
            if (otherY <= myY) continue;

            double otherLeft = other.getX() - other.getWidth() / 2.0;
            double otherRight = other.getX() + other.getWidth() / 2.0;

            boolean lateralOverlap = (myX - myHalfWidth) < otherRight
                                  && (myX + myHalfWidth) > otherLeft;

            if (lateralOverlap) {
                double otherRear = otherY - other.getLength() / 2.0;
                double gap = otherRear - myFront;
                if (gap < minGap) {
                    minGap = gap;
                    deltaV = vehicle.getSpeed() - other.getSpeed();
                }
            }
        }

        return new LeaderInfo(minGap, deltaV);
    }

    /**
     * Compute a lateral movement bias for a vehicle that wants to turn.
     * A left-turner drifts toward left edge (low X), right-turner toward right edge.
     * This lets turning vehicles seep past straight-going queues.
     *
     * Returns a signed displacement (meters): negative = left, positive = right.
     * Returns 0 if no target road or turn direction can't be determined.
     */
    private double computeTurnLateralBias(Vehicle vehicle, RoadSegment road,
                                           RoadNetwork network) {
        RoadSegment targetRoad = network.getSegment(vehicle.getTargetRoadId());
        if (targetRoad == null) return 0;

        // Compute heading difference to determine left or right turn
        double myHeading = road.getHeading();
        double targetHeading = targetRoad.getHeading();
        double angleDiff = targetHeading - myHeading;
        // Normalize to [-π, π]
        while (angleDiff >  Math.PI) angleDiff -= 2 * Math.PI;
        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;

        if (Math.abs(angleDiff) < 0.3) return 0; // Going straight — no bias

        double myX = vehicle.getX();
        double roadWidth = road.getWidth();
        double halfWidth = vehicle.getWidth() / 2.0;

        if (angleDiff < 0) {
            // LEFT turn — drift toward left edge (low X)
            double targetX = halfWidth + 0.3;
            double needed = targetX - myX; // negative
            return Math.max(needed, -0.5); // move at most 0.5m per tick toward left
        } else {
            // RIGHT turn — drift toward right edge (high X)
            double targetX = roadWidth - halfWidth - 0.3;
            double needed = targetX - myX; // positive
            return Math.min(needed, 0.5); // move at most 0.5m per tick toward right
        }
    }

    private record LeaderInfo(double gap, double deltaV) {}
}
