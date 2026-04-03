package com.traffic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single road segment between two points.
 * The road is subdivided into lateral Strips for lane-free movement.
 */
public class RoadSegment {

    private final int id;
    private final double startX, startY; // Start point (world coordinates)
    private final double endX, endY; // End point (world coordinates)
    private final double width; // Total road width in meters
    private final double speedLimit; // Speed limit in m/s
    private final double length; // Computed length
    private final double heading; // Angle of the road in radians
    private final boolean divided; // true = one-way carriageway
    private final RoadType roadType; // Classification for behavior rules
    private boolean spawnEnabled = true; // Whether vehicles can spawn on this road

    private final List<Strip> strips; // Lateral strips

    /** Full constructor with road type */
    public RoadSegment(int id, double startX, double startY,
            double endX, double endY,
            double width, double speedLimit, double stripWidth,
            boolean divided, RoadType roadType) {
        this.id = id;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.width = width;
        this.speedLimit = speedLimit;
        this.divided = divided;
        this.roadType = roadType;

        double dx = endX - startX;
        double dy = endY - startY;
        this.length = Math.sqrt(dx * dx + dy * dy);
        this.heading = Math.atan2(dy, dx);

        int numStrips = (int) Math.floor(width / stripWidth);
        List<Strip> stripList = new ArrayList<>();
        for (int i = 0; i < numStrips; i++) {
            stripList.add(new Strip(i, stripWidth));
        }
        this.strips = Collections.unmodifiableList(stripList);
    }

    /** Constructor with divided flag (defaults to URBAN type) */
    public RoadSegment(int id, double startX, double startY,
            double endX, double endY,
            double width, double speedLimit, double stripWidth,
            boolean divided) {
        this(id, startX, startY, endX, endY, width, speedLimit, stripWidth,
                divided, divided ? RoadType.ARTERIAL : RoadType.URBAN);
    }

    /** Simple constructor (defaults to divided=true, ARTERIAL type) */
    public RoadSegment(int id, double startX, double startY,
            double endX, double endY,
            double width, double speedLimit, double stripWidth) {
        this(id, startX, startY, endX, endY, width, speedLimit, stripWidth, true, RoadType.ARTERIAL);
    }

    // ---- Getters ----

    public int getId() {
        return id;
    }

    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public double getEndX() {
        return endX;
    }

    public double getEndY() {
        return endY;
    }

    public double getWidth() {
        return width;
    }

    public double getSpeedLimit() {
        return speedLimit;
    }

    public double getLength() {
        return length;
    }

    public double getHeading() {
        return heading;
    }

    public List<Strip> getStrips() {
        return strips;
    }

    public int getStripCount() {
        return strips.size();
    }

    public boolean isDivided() {
        return divided;
    }

    public RoadType getRoadType() {
        return roadType;
    }

    public boolean isSpawnEnabled() {
        return spawnEnabled;
    }

    public void setSpawnEnabled(boolean spawnEnabled) {
        this.spawnEnabled = spawnEnabled;
    }

    /**
     * Check if a given longitudinal position is within this road segment.
     */
    public boolean containsPosition(double longitudinalPos) {
        return longitudinalPos >= 0 && longitudinalPos <= length;
    }

    /**
     * Check if a lateral position is within the road boundaries.
     */
    public boolean isWithinWidth(double lateralPos, double vehicleHalfWidth) {
        return (lateralPos - vehicleHalfWidth) >= 0
                && (lateralPos + vehicleHalfWidth) <= width;
    }

    @Override
    public String toString() {
        return String.format("Road[%d len=%.1fm w=%.1fm strips=%d]",
                id, length, width, strips.size());
    }
}
