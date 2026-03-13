package com.traffic.ai;

import com.traffic.model.RoadNetwork;
import com.traffic.model.RoadSegment;
import com.traffic.model.TrafficSignal;
import com.traffic.model.Vehicle;

import java.util.List;
import java.util.Random;

/**
 * Per-vehicle AI with FSM states and personality traits.
 * Each vehicle has independent decision-making with randomized personality.
 */
public class VehicleAI {

    // ---- States ----
    public enum State {
        CRUISING,              // Normal driving
        APPROACHING_JUNCTION,  // Near junction, preparing for turn
        QUEUED,                // Stopped at red signal
        COMMITTED,             // Past stop line, clearing junction
        LANE_CHANGING          // Actively executing a lane change
    }

    // ---- Personality Traits ----
    private final double aggressiveness; // 0.0 (cautious) to 1.0 (aggressive)
    private final double patience;       // 0.0 (impatient) to 1.0 (patient)
    private final double laneRespect;    // 0.8 to 1.0 (how well driver respects lane discipline)

    // ---- State ----
    private State state = State.CRUISING;
    private double stateTimer = 0;       // Time spent in current state
    private double laneChangeTarget = -1; // Target lateral position during lane change
    private double laneChangeProgress = 0;

    private static final Random rng = new Random();

    public VehicleAI() {
        this.aggressiveness = rng.nextDouble();             // 0.0 - 1.0
        this.patience = rng.nextDouble();                   // 0.0 - 1.0
        this.laneRespect = 0.8 + rng.nextDouble() * 0.2;   // 0.8 - 1.0
    }

    /**
     * Update the AI state based on current context.
     * Returns the desired acceleration modifier and lateral move.
     */
    public AIOutput update(Vehicle vehicle, RoadSegment road, RoadNetwork network,
                           double leaderGap, double dt) {
        stateTimer += dt;

        // Detect context
        boolean nearJunction = isNearJunction(vehicle, road, network);
        boolean pastStopLine = isPastStopLine(vehicle, road, network);
        boolean atRedSignal = isAtRedSignal(vehicle, road, network);

        // ---- State Transitions ----
        switch (state) {
            case CRUISING:
                if (nearJunction && !pastStopLine) {
                    transitionTo(State.APPROACHING_JUNCTION);
                }
                break;

            case APPROACHING_JUNCTION:
                if (pastStopLine) {
                    transitionTo(State.COMMITTED);
                } else if (atRedSignal && vehicle.getSpeed() < 0.5) {
                    transitionTo(State.QUEUED);
                }
                break;

            case QUEUED:
                if (!atRedSignal) {
                    transitionTo(State.COMMITTED);
                }
                break;

            case COMMITTED:
                if (!nearJunction && pastStopLine) {
                    transitionTo(State.CRUISING);
                }
                break;

            case LANE_CHANGING:
                laneChangeProgress += dt * 2.0; // Complete in ~0.5s
                if (laneChangeProgress >= 1.0) {
                    transitionTo(State.CRUISING);
                }
                break;
        }

        // ---- Per-State Behavior ----
        return computeOutput(vehicle, road, leaderGap, dt);
    }

    private AIOutput computeOutput(Vehicle vehicle, RoadSegment road,
                                    double leaderGap, double dt) {
        double accelMod = 0;   // Modifier applied to IDM acceleration
        double lateralMod = 1.0; // Multiplier for seepage lateral move
        double minSpeed = 0;   // Minimum speed floor

        switch (state) {
            case CRUISING:
                // Aggressive drivers follow closer and accelerate harder
                accelMod = aggressiveness * 0.5; // Up to +0.5 m/s² bonus
                lateralMod = 1.0;
                break;

            case APPROACHING_JUNCTION:
                // Slow down somewhat when approaching, cautious drivers more so
                accelMod = -(1.0 - aggressiveness) * 0.5; // Cautious: -0.5, Aggressive: 0
                lateralMod = 0.3; // Reduce lateral movement near junction
                break;

            case QUEUED:
                // Stopped at signal — no lateral movement
                lateralMod = 0.0;
                break;

            case COMMITTED:
                // Must clear junction — maintain speed, no lateral jittering
                minSpeed = 3.0 + aggressiveness * 4.0; // 3-7 m/s depending on personality
                accelMod = 1.0; // Push through
                lateralMod = 0.0; // No lane changes in junction
                break;

            case LANE_CHANGING:
                lateralMod = 0.5; // Controlled lateral movement
                break;
        }

        return new AIOutput(accelMod, lateralMod, minSpeed, state == State.COMMITTED, state);
    }

    private void transitionTo(State newState) {
        this.state = newState;
        this.stateTimer = 0;
        if (newState == State.LANE_CHANGING) {
            this.laneChangeProgress = 0;
        }
    }

    // ---- Context Detection ----

    private boolean isNearJunction(Vehicle vehicle, RoadSegment road, RoadNetwork network) {
        if (network == null) return false;
        // Check all junctions
        for (RoadNetwork.JunctionZone jz : network.getJunctionZones()) {
            double wx = vehicle.getWorldX();
            double wy = vehicle.getWorldY();
            double margin = 30;
            if (wx >= jz.minX() - margin && wx <= jz.maxX() + margin
             && wy >= jz.minY() - margin && wy <= jz.maxY() + margin) {
                return true;
            }
        }
        return false;
    }

    private boolean isPastStopLine(Vehicle vehicle, RoadSegment road, RoadNetwork network) {
        if (network == null) return false;
        List<TrafficSignal> signals = network.getSignalsForRoad(road.getId());
        double myFront = vehicle.getY() + vehicle.getLength() / 2.0;
        for (TrafficSignal s : signals) {
            if (myFront > s.getStopLineY()) return true;
        }
        return false;
    }

    private boolean isAtRedSignal(Vehicle vehicle, RoadSegment road, RoadNetwork network) {
        if (network == null) return false;
        List<TrafficSignal> signals = network.getSignalsForRoad(road.getId());
        double myFront = vehicle.getY() + vehicle.getLength() / 2.0;
        for (TrafficSignal s : signals) {
            if (s.shouldStop() && s.getStopLineY() > myFront && s.getStopLineY() - myFront < 50) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the aggressiveness-adjusted minimum following gap.
     * Aggressive: 1.0m, Cautious: 3.0m
     */
    public double getMinFollowingGap() {
        return 3.0 - aggressiveness * 2.0;
    }

    /**
     * Should this driver run a yellow light?
     * Aggressive drivers are more likely to.
     */
    public boolean shouldRunYellow(double gapToSignal) {
        // Less likely to run yellow now — only if extremely close (within 5m) and very aggressive
        return gapToSignal < 5 && rng.nextDouble() < aggressiveness * 0.15;
    }

    // ---- Getters ----
    public State getState() { return state; }
    public double getAggressiveness() { return aggressiveness; }
    public double getPatience() { return patience; }
    public double getLaneRespect() { return laneRespect; }

    /**
     * Output from the AI decision.
     */
    public record AIOutput(
        double accelModifier,      // Added to IDM acceleration
        double lateralMultiplier,  // Multiplied with seepage output
        double minSpeed,           // Speed floor (for committed vehicles)
        boolean committed,         // Whether vehicle is committed to crossing
        State state                // Current FSM state (for external callers)
    ) {}
}
