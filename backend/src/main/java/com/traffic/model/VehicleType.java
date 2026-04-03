package com.traffic.model;

/**
 * Enum defining the heterogeneous vehicle types found in Indian traffic.
 * Each type has preset physical dimensions and performance characteristics.
 */
public enum VehicleType {

    // width(m), length(m), maxSpeed(m/s), maxAccel(m/s²)
    BIKE(0.6, 1.8, 22.22, 3.0), // ~80 km/h
    AUTO_RICKSHAW(1.4, 2.6, 16.67, 1.8), // ~60 km/h
    CAR(1.8, 4.0, 27.78, 2.5), // ~100 km/h
    BUS(2.5, 12.0, 22.22, 1.2), // ~80 km/h
    TRUCK(2.5, 10.0, 19.44, 0.8); // ~70 km/h

    private final double width;
    private final double length;
    private final double maxSpeed;
    private final double maxAcceleration;

    VehicleType(double width, double length, double maxSpeed, double maxAcceleration) {
        this.width = width;
        this.length = length;
        this.maxSpeed = maxSpeed;
        this.maxAcceleration = maxAcceleration;
    }

    public double getWidth() {
        return width;
    }

    public double getLength() {
        return length;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getMaxAcceleration() {
        return maxAcceleration;
    }

    /**
     * How many 0.5m strips this vehicle occupies laterally.
     */
    public int getStripCount(double stripWidth) {
        return (int) Math.ceil(width / stripWidth);
    }
}
