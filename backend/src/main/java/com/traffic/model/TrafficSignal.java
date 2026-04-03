package com.traffic.model;

/**
 * Represents a traffic signal at a specific point on a road segment.
 * Cycles through RED → GREEN → YELLOW → RED states.
 */
public class TrafficSignal {

    public enum State {
        RED, GREEN, YELLOW
    }

    private final int id;
    private final int roadSegmentId;
    private final double stopLineY; // Position along the road where vehicles must stop

    // Timing (seconds)
    private final double greenDuration;
    private final double yellowDuration;
    private final double redDuration;

    // Phase offset: allows coordinating multiple signals
    private final double phaseOffset;

    // Internal timer
    private double timer;
    private State state;

    public TrafficSignal(int id, int roadSegmentId, double stopLineY,
            double greenDuration, double yellowDuration,
            double redDuration, double phaseOffset) {
        this.id = id;
        this.roadSegmentId = roadSegmentId;
        this.stopLineY = stopLineY;
        this.greenDuration = greenDuration;
        this.yellowDuration = yellowDuration;
        this.redDuration = redDuration;
        this.phaseOffset = phaseOffset;

        // Start at the offset point in the cycle
        this.timer = phaseOffset;
        this.state = computeState();
    }

    /**
     * Advance the signal timer by dt seconds and update state.
     */
    public void tick(double dt) {
        timer += dt;
        // Wrap timer to total cycle length
        double cycle = greenDuration + yellowDuration + redDuration;
        if (timer >= cycle) {
            timer -= cycle;
        }
        state = computeState();
    }

    private State computeState() {
        double cycle = greenDuration + yellowDuration + redDuration;
        double t = timer % cycle;
        if (t < greenDuration)
            return State.GREEN;
        if (t < greenDuration + yellowDuration)
            return State.YELLOW;
        return State.RED;
    }

    /**
     * Whether vehicles should stop at this signal.
     */
    public boolean shouldStop() {
        return state == State.RED || state == State.YELLOW;
    }

    // ---- Getters ----

    public int getId() {
        return id;
    }

    public int getRoadSegmentId() {
        return roadSegmentId;
    }

    public double getStopLineY() {
        return stopLineY;
    }

    public State getState() {
        return state;
    }

    /**
     * Force the signal to a specific state (used by SignalPhaseController).
     * Bypasses the internal timer.
     */
    public void forceState(State newState) {
        this.state = newState;
    }

    public double getGreenDuration() {
        return greenDuration;
    }

    public double getYellowDuration() {
        return yellowDuration;
    }

    public double getRedDuration() {
        return redDuration;
    }

    @Override
    public String toString() {
        return String.format("Signal[%d road=%d pos=%.1f state=%s]",
                id, roadSegmentId, stopLineY, state);
    }
}
