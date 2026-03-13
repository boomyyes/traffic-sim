package com.traffic.physics;

import com.traffic.model.RoadSegment;
import com.traffic.model.RoadType;
import com.traffic.model.Vehicle;
import com.traffic.model.VehicleType;

import java.util.List;

/**
 * Lateral movement logic for Indian lane-free traffic.
 * 
 * Three modes of lateral movement:
 * 
 * 1. CENTER-ROAD DRIFT (undivided roads):
 * Vehicles prefer driving in the center of the road.
 * They only move aside when a faster vehicle approaches from behind
 * or when the road ahead is crowded.
 * 
 * 2. SEEPAGE (low speed / congestion):
 * Small vehicles filter through gaps between larger vehicles.
 * 
 * 3. DISCRETIONARY LANE CHANGE (any speed):
 * Vehicles proactively seek better corridors.
 */
public class SeepageLogic {

    private final double stripWidth;
    private final double safetyBuffer;
    private final double speedThreshold;

    // Center-driving parameters
    private static final double CENTER_DRIFT_STRENGTH = 0.3; // strength of center pull (strips/tick)
    private static final double YIELD_LOOK_BEHIND = 15.0; // how far behind to check for faster vehicles (m)

    // Lane change parameters
    private static final double LANE_CHANGE_GAP_RATIO = 1.5;

    public SeepageLogic(double stripWidth, double safetyBuffer, double speedThreshold) {
        this.stripWidth = stripWidth;
        this.safetyBuffer = safetyBuffer;
        this.speedThreshold = speedThreshold;
    }

    /**
     * Calculate the lateral movement for a vehicle.
     * RULE: No lateral movement when the vehicle is nearly stationary.
     * Vehicles must be moving forward before they can change lanes.
     */
    public double calculateLateralMove(Vehicle vehicle, List<Vehicle> neighbors,
            RoadSegment road, double forwardGap) {

        // *** SPEED GUARD: No lateral movement when nearly stopped ***
        // Bikes can start moving laterally at lower speeds than cars
        double minLateralSpeed = switch (vehicle.getType()) {
            case BIKE -> 1.0; // 1 m/s (~3.6 km/h)
            case AUTO_RICKSHAW -> 1.5;
            case CAR -> 2.0; // 2 m/s (~7 km/h)
            case BUS -> 2.5;
            case TRUCK -> 2.5;
        };
        if (vehicle.getSpeed() < minLateralSpeed) {
            return 0.0; // Stationary or near-stationary — no lateral move
        }

        // --- MODE 1: CENTER-ROAD DRIFT (RURAL and RESIDENTIAL roads only) ---
        RoadType rType = road.getRoadType();
        if (rType == RoadType.RURAL || rType == RoadType.RESIDENTIAL) {
            double centerMove = tryCenterDrive(vehicle, neighbors, road, forwardGap);
            if (centerMove != 0.0)
                return centerMove;
        }

        // --- MODE 2: SEEPAGE (low speed or congested gap ahead) ---
        if (vehicle.getSpeed() < speedThreshold || forwardGap < vehicle.getLength() * 2.5) {
            double seepageMove = trySeepage(vehicle, neighbors, road, forwardGap);
            if (seepageMove != 0.0)
                return seepageMove;
        }

        // --- MODE 3: DISCRETIONARY LANE CHANGE (any speed when gap is short) ---
        if (forwardGap < desiredLookahead(vehicle)) {
            double laneChangeMove = tryLaneChange(vehicle, neighbors, road, forwardGap);
            if (laneChangeMove != 0.0)
                return laneChangeMove;
        }

        return 0.0;
    }

    /**
     * Indian center-road driving behavior for undivided roads.
     * 
     * Default: drift toward road center.
     * Yield: if a faster vehicle is close behind, move to the left edge.
     */
    private double tryCenterDrive(Vehicle vehicle, List<Vehicle> neighbors,
            RoadSegment road, double forwardGap) {
        double roadCenter = road.getWidth() / 2.0;
        double myX = vehicle.getX();
        double distFromCenter = myX - roadCenter;

        // Check if a faster vehicle is approaching from behind
        boolean shouldYield = false;
        for (Vehicle other : neighbors) {
            if (other.getId() == vehicle.getId())
                continue;
            if (other.getRoadSegmentId() != vehicle.getRoadSegmentId())
                continue;

            double dy = vehicle.getY() - other.getY(); // positive = other is behind us
            if (dy > 0 && dy < YIELD_LOOK_BEHIND) {
                // There's someone behind us
                if (other.getSpeed() > vehicle.getSpeed() * 1.2) {
                    // They're faster — check lateral overlap (same corridor)
                    double overlap = checkLateralOverlap(vehicle, other);
                    if (overlap > 0) {
                        shouldYield = true;
                        break;
                    }
                }
            }
        }

        if (shouldYield) {
            // Move LEFT (toward road edge) to let faster vehicle pass
            double targetX = vehicle.getWidth() / 2.0 + safetyBuffer; // near left edge
            if (myX > targetX + stripWidth) {
                double move = -stripWidth;
                double newX = myX + move;
                if (canMoveLaterally(vehicle, newX, neighbors, road))
                    return move;
            }
        } else {
            // No one behind — drift toward center
            if (Math.abs(distFromCenter) > stripWidth) {
                double move = distFromCenter > 0 ? -stripWidth * CENTER_DRIFT_STRENGTH
                        : stripWidth * CENTER_DRIFT_STRENGTH;
                double newX = myX + move;
                if (canMoveLaterally(vehicle, newX, neighbors, road))
                    return move;
            }
        }

        return 0.0;
    }

    private double checkLateralOverlap(Vehicle a, Vehicle b) {
        double aLeft = a.getX() - a.getWidth() / 2.0;
        double aRight = a.getX() + a.getWidth() / 2.0;
        double bLeft = b.getX() - b.getWidth() / 2.0;
        double bRight = b.getX() + b.getWidth() / 2.0;
        return Math.min(aRight, bRight) - Math.max(aLeft, bLeft);
    }

    /**
     * Seepage: move 1 strip at a time to filter through congestion.
     */
    private double trySeepage(Vehicle vehicle, List<Vehicle> neighbors,
            RoadSegment road, double forwardGap) {
        double myX = vehicle.getX();
        double myY = vehicle.getY();
        double myWidth = vehicle.getWidth();
        double myLength = vehicle.getLength();

        double leftTarget = myX - stripWidth;
        double rightTarget = myX + stripWidth;

        boolean canLeft = canMoveLaterally(vehicle, leftTarget, neighbors, road);
        boolean canRight = canMoveLaterally(vehicle, rightTarget, neighbors, road);

        if (canLeft && canRight) {
            double leftGap = getForwardGapAt(leftTarget, myY, myWidth, myLength, neighbors, vehicle);
            double rightGap = getForwardGapAt(rightTarget, myY, myWidth, myLength, neighbors, vehicle);
            if (leftGap > rightGap && leftGap > forwardGap)
                return -stripWidth;
            else if (rightGap > forwardGap)
                return stripWidth;
        } else if (canLeft) {
            double leftGap = getForwardGapAt(leftTarget, myY, myWidth, myLength, neighbors, vehicle);
            if (leftGap > forwardGap)
                return -stripWidth;
        } else if (canRight) {
            double rightGap = getForwardGapAt(rightTarget, myY, myWidth, myLength, neighbors, vehicle);
            if (rightGap > forwardGap)
                return stripWidth;
        }

        return 0.0;
    }

    /**
     * Discretionary lane change: explore 1–3 strips for a better corridor.
     */
    private double tryLaneChange(Vehicle vehicle, List<Vehicle> neighbors,
            RoadSegment road, double forwardGap) {
        double myX = vehicle.getX();
        double myY = vehicle.getY();
        double myWidth = vehicle.getWidth();
        double myLength = vehicle.getLength();

        int maxShift = getMaxLateralShift(vehicle.getType());
        double bestMove = 0.0;
        double bestGap = forwardGap;
        double minRequiredGap = forwardGap * LANE_CHANGE_GAP_RATIO;

        // Check LEFT
        for (int shift = 1; shift <= maxShift; shift++) {
            double targetX = myX - shift * stripWidth;
            if (!canMoveLaterally(vehicle, targetX, neighbors, road))
                break;
            double gap = getForwardGapAt(targetX, myY, myWidth, myLength, neighbors, vehicle);
            double rearGap = getRearGapAt(targetX, myY, myWidth, myLength, neighbors, vehicle);
            if (gap > minRequiredGap && gap > bestGap && rearGap > vehicle.getLength()) {
                bestGap = gap;
                bestMove = -shift * stripWidth;
            }
        }

        // Check RIGHT
        for (int shift = 1; shift <= maxShift; shift++) {
            double targetX = myX + shift * stripWidth;
            if (!canMoveLaterally(vehicle, targetX, neighbors, road))
                break;
            double gap = getForwardGapAt(targetX, myY, myWidth, myLength, neighbors, vehicle);
            double rearGap = getRearGapAt(targetX, myY, myWidth, myLength, neighbors, vehicle);
            if (gap > minRequiredGap && gap > bestGap && rearGap > vehicle.getLength()) {
                bestGap = gap;
                bestMove = shift * stripWidth;
            }
        }

        return bestMove;
    }

    private int getMaxLateralShift(VehicleType type) {
        return switch (type) {
            case BIKE -> 3;
            case AUTO_RICKSHAW -> 2;
            case CAR -> 2;
            case BUS -> 1;
            case TRUCK -> 1;
        };
    }

    private double desiredLookahead(Vehicle vehicle) {
        // At 80 km/h: ~73m lookahead; at 0: 20m minimum
        return Math.max(20.0, Math.min(80.0, vehicle.getSpeed() * 3.5));
    }

    private boolean canMoveLaterally(Vehicle vehicle, double targetX,
            List<Vehicle> neighbors, RoadSegment road) {
        double halfWidth = vehicle.getWidth() / 2.0;

        if (targetX - halfWidth < 0 || targetX + halfWidth > road.getWidth()) {
            return false;
        }

        for (Vehicle other : neighbors) {
            if (other.getId() == vehicle.getId())
                continue;
            if (other.getRoadSegmentId() != vehicle.getRoadSegmentId())
                continue;

            double otherLeft = other.getX() - other.getWidth() / 2.0;
            double otherRight = other.getX() + other.getWidth() / 2.0;
            double myLeft = targetX - halfWidth - safetyBuffer;
            double myRight = targetX + halfWidth + safetyBuffer;

            boolean lateralOverlap = myLeft < otherRight && myRight > otherLeft;

            // Fixed small longitudinal buffer — NOT speed-based.
            // Speed-based buffer (old: speed * 0.5) made seepage impossible at highway speed.
            double longBuffer = 0.5;
            double myFront = vehicle.getY() + vehicle.getLength() / 2.0 + longBuffer;
            double myRear  = vehicle.getY() - vehicle.getLength() / 2.0 - longBuffer;
            double otherFront = other.getY() + other.getLength() / 2.0;
            double otherRear  = other.getY() - other.getLength() / 2.0;

            boolean longOverlap = myRear < otherFront && myFront > otherRear;

            if (lateralOverlap && longOverlap)
                return false;
        }

        return true;
    }

    private double getForwardGapAt(double targetX, double currentY,
            double vehicleWidth, double vehicleLength,
            List<Vehicle> neighbors, Vehicle self) {
        double minGap = Double.MAX_VALUE;
        double halfWidth = vehicleWidth / 2.0;

        for (Vehicle other : neighbors) {
            if (other.getId() == self.getId())
                continue;
            if (other.getY() <= currentY)
                continue;

            double otherLeft = other.getX() - other.getWidth() / 2.0;
            double otherRight = other.getX() + other.getWidth() / 2.0;

            if (targetX - halfWidth < otherRight && targetX + halfWidth > otherLeft) {
                double gap = (other.getY() - other.getLength() / 2.0)
                        - (currentY + vehicleLength / 2.0);
                minGap = Math.min(minGap, gap);
            }
        }

        return minGap;
    }

    private double getRearGapAt(double targetX, double currentY,
            double vehicleWidth, double vehicleLength,
            List<Vehicle> neighbors, Vehicle self) {
        double minGap = Double.MAX_VALUE;
        double halfWidth = vehicleWidth / 2.0;

        for (Vehicle other : neighbors) {
            if (other.getId() == self.getId())
                continue;
            if (other.getY() >= currentY)
                continue;

            double otherLeft = other.getX() - other.getWidth() / 2.0;
            double otherRight = other.getX() + other.getWidth() / 2.0;

            if (targetX - halfWidth < otherRight && targetX + halfWidth > otherLeft) {
                double gap = (currentY - vehicleLength / 2.0)
                        - (other.getY() + other.getLength() / 2.0);
                minGap = Math.min(minGap, gap);
            }
        }

        return minGap;
    }
}
