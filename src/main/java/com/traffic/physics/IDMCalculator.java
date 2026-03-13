package com.traffic.physics;

/**
 * Implements the Intelligent Driver Model (IDM) for longitudinal acceleration.
 * 
 * The IDM calculates acceleration based on:
 * - The vehicle's current speed vs desired speed
 * - The gap to the leading vehicle
 * - The speed difference with the leader
 * 
 * Reference: Treiber, Hennecke, Helbing (2000).
 * Modified for Indian traffic (tighter gaps, more aggressive acceleration).
 */
public class IDMCalculator {

    private final double desiredSpeed; // v0 (m/s) — target free-flow speed
    private final double maxAcceleration; // a (m/s²) — max acceleration
    private final double comfortDecel; // b (m/s²) — comfortable deceleration
    private final double minGap; // s0 (m) — minimum standstill distance
    private final double timeHeadway; // T (s) — desired time headway
    private final int accelExponent; // δ — acceleration exponent (usually 4)

    public IDMCalculator(double desiredSpeed, double maxAcceleration,
            double comfortDecel, double minGap,
            double timeHeadway, int accelExponent) {
        this.desiredSpeed = desiredSpeed;
        this.maxAcceleration = maxAcceleration;
        this.comfortDecel = comfortDecel;
        this.minGap = minGap;
        this.timeHeadway = timeHeadway;
        this.accelExponent = accelExponent;
    }

    /**
     * Calculate acceleration using the IDM formula.
     * 
     * @param currentSpeed Current speed of the vehicle (m/s)
     * @param gap          Net gap to the leading vehicle (m), Double.MAX_VALUE if
     *                     no leader
     * @param deltaV       Speed difference (mySpeed - leaderSpeed), 0 if no leader
     * @return Acceleration (m/s²), can be negative (braking)
     */
    public double calculateAcceleration(double currentSpeed, double gap, double deltaV) {
        // Free-road acceleration term: a * [1 - (v/v0)^δ]
        double speedRatio = currentSpeed / desiredSpeed;
        double freeAccel = maxAcceleration * (1.0 - Math.pow(speedRatio, accelExponent));

        if (gap >= Double.MAX_VALUE / 2) {
            // No leader — pure free-road acceleration
            return freeAccel;
        }

        // EMERGENCY BRAKING: if gap is negative or nearly zero, slam the brakes
        if (gap <= 0.5) {
            return -maxAcceleration * 4.0; // Hard emergency brake
        }

        // Desired dynamic gap: s* = s0 + v*T + (v*Δv) / (2*sqrt(a*b))
        double interactionTerm = (currentSpeed * deltaV) /
                (2.0 * Math.sqrt(maxAcceleration * comfortDecel));
        double desiredGap = minGap
                + currentSpeed * timeHeadway
                + Math.max(0, interactionTerm);

        // Interaction (braking) term: -a * (s*/s)^2
        double gapRatio = desiredGap / Math.max(gap, 0.1); // Avoid division by zero
        double interactionAccel = -maxAcceleration * gapRatio * gapRatio;

        return freeAccel + interactionAccel;
    }

    /**
     * Overloaded: calculate acceleration with a specific desired speed override.
     * Useful for road segments with different speed limits.
     */
    public double calculateAcceleration(double currentSpeed, double gap,
            double deltaV, double effectiveDesiredSpeed) {
        double speedRatio = currentSpeed / effectiveDesiredSpeed;
        double freeAccel = maxAcceleration * (1.0 - Math.pow(speedRatio, accelExponent));

        if (gap >= Double.MAX_VALUE / 2) {
            return freeAccel;
        }

        // EMERGENCY BRAKING
        if (gap <= 0.5) {
            return -maxAcceleration * 4.0;
        }

        double interactionTerm = (currentSpeed * deltaV) /
                (2.0 * Math.sqrt(maxAcceleration * comfortDecel));
        double desiredGap = minGap
                + currentSpeed * timeHeadway
                + Math.max(0, interactionTerm);

        double gapRatio = desiredGap / Math.max(gap, 0.1);
        double interactionAccel = -maxAcceleration * gapRatio * gapRatio;

        return freeAccel + interactionAccel;
    }
}
