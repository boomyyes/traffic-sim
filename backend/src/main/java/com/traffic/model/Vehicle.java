package com.traffic.model;

import com.traffic.ai.VehicleAI;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a single vehicle agent in the simulation.
 * Uses double-buffered state: current (x, y, speed) vs next (nextX, nextY,
 * nextSpeed)
 * to allow safe parallel updates without race conditions.
 */
public class Vehicle {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    // Identity
    private final int id;
    private final VehicleType type;

    // Physical dimensions (from VehicleType)
    private final double width;
    private final double length;

    // Current state
    private double x; // Lateral position (meters from road left edge)
    private double y; // Longitudinal position (meters along road)
    private double speed; // Current speed (m/s)
    private double acceleration;// Current acceleration (m/s²)

    // Smooth lateral movement
    private double lateralVelocity = 0;
    private static final double LATERAL_SMOOTHING = 4.0; // Smoother ramp-up
    private static final double MAX_LATERAL_SPEED = 2.5; // Less snappy sideways movement

    // Acceleration smoothing
    private double smoothedAccel = 0;
    private static final double ACCEL_SMOOTHING = 3.0; // Prevents jerky speed changes

    // Per-vehicle AI
    private final VehicleAI ai;

    // Next state (computed in parallel, committed atomically)
    private double nextX;
    private double nextY;
    private double nextSpeed;

    // Cached world coordinates (set by SimulationEngine each tick)
    private double worldX, worldY;

    // Road assignment
    private int roadSegmentId;
    private int targetRoadId = -1;

    // Flags
    private boolean active = true;

    public Vehicle(VehicleType type, double x, double y, double initialSpeed, int roadSegmentId) {
        this.id = ID_COUNTER.incrementAndGet();
        this.type = type;
        this.width = type.getWidth();
        this.length = type.getLength();
        this.x = x;
        this.y = y;
        this.speed = initialSpeed;
        this.acceleration = 0;
        this.nextX = x;
        this.nextY = y;
        this.nextSpeed = initialSpeed;
        this.roadSegmentId = roadSegmentId;
        this.ai = new VehicleAI();
    }

    // ---- Double-buffer update pattern ----

    /**
     * Prepare the next state based on physics calculations.
     * Lateral movement is smoothly interpolated instead of instant.
     */
    public void prepareNextState(double newAcceleration, double lateralMove, double dt) {
        // Smooth acceleration to prevent jerk
        double accelAlpha = Math.min(1.0, ACCEL_SMOOTHING * dt);
        smoothedAccel = smoothedAccel + accelAlpha * (newAcceleration - smoothedAccel);
        this.acceleration = smoothedAccel;

        // Longitudinal update with smoothed acceleration
        this.nextSpeed = Math.max(0, speed + smoothedAccel * dt);
        this.nextY = y + speed * dt + 0.5 * smoothedAccel * dt * dt;

        // Smooth lateral update:
        // lateralMove is the DESIRED lateral displacement (from SeepageLogic)
        // Convert to a target lateral velocity, then smooth toward it
        double targetLateralVel = 0;
        if (Math.abs(lateralMove) > 0.001) {
            // Convert desired move into a velocity (move / dt gives desired vel)
            targetLateralVel = lateralMove / Math.max(dt, 0.05);
            // Clamp to max lateral speed
            targetLateralVel = Math.max(-MAX_LATERAL_SPEED,
                    Math.min(MAX_LATERAL_SPEED, targetLateralVel));
        }

        // Smoothly interpolate lateral velocity toward target
        double alpha = Math.min(1.0, LATERAL_SMOOTHING * dt);
        lateralVelocity = lateralVelocity + alpha * (targetLateralVel - lateralVelocity);

        // Apply smooth lateral velocity
        this.nextX = x + lateralVelocity * dt;
    }

    public void commitState() {
        this.x = nextX;
        this.y = nextY;
        this.speed = nextSpeed;
    }

    // ---- Getters ----

    public int getId() {
        return id;
    }

    public VehicleType getType() {
        return type;
    }

    public VehicleAI getAI() {
        return ai;
    }

    public double getWidth() {
        return width;
    }

    public double getLength() {
        return length;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getSpeed() {
        return speed;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public int getRoadSegmentId() {
        return roadSegmentId;
    }

    public boolean isActive() {
        return active;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setRoadSegmentId(int roadSegmentId) {
        this.roadSegmentId = roadSegmentId;
    }

    public double getWorldX() {
        return worldX;
    }

    public double getWorldY() {
        return worldY;
    }

    public void setWorldCoords(double wx, double wy) {
        this.worldX = wx;
        this.worldY = wy;
    }

    public int getTargetRoadId() {
        return targetRoadId;
    }

    public void setTargetRoadId(int targetRoadId) {
        this.targetRoadId = targetRoadId;
    }

    /**
     * Returns the lateral strip index this vehicle's CENTER occupies.
     */
    public int getCenterStripIndex(double stripWidth) {
        return (int) (x / stripWidth);
    }

    /**
     * Returns the range of strips this vehicle occupies [min, max] inclusive.
     */
    public int[] getOccupiedStripRange(double stripWidth) {
        int leftStrip = (int) ((x - width / 2.0) / stripWidth);
        int rightStrip = (int) ((x + width / 2.0) / stripWidth);
        return new int[] { Math.max(0, leftStrip), rightStrip };
    }

    @Override
    public String toString() {
        return String.format("Vehicle[%d %s pos=(%.1f,%.1f) spd=%.1f]",
                id, type, x, y, speed);
    }
}
