package com.traffic.model;

/**
 * Represents a single 0.5m-wide lateral strip on a road segment.
 * This is the core of the "Strip-Based" lane-free approach:
 * instead of lanes, the road is divided into narrow strips that
 * vehicles can occupy based on their actual width.
 */
public class Strip {

    private final int index; // Strip index (0 = leftmost)
    private final double centerX; // Lateral center position (meters from road left edge)
    private final double width; // Strip width (default 0.5m)

    public Strip(int index, double stripWidth) {
        this.index = index;
        this.width = stripWidth;
        this.centerX = (index * stripWidth) + (stripWidth / 2.0);
    }

    public int getIndex() {
        return index;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getWidth() {
        return width;
    }

    /** Left edge of this strip in meters */
    public double getLeftEdge() {
        return centerX - width / 2.0;
    }

    /** Right edge of this strip in meters */
    public double getRightEdge() {
        return centerX + width / 2.0;
    }

    @Override
    public String toString() {
        return String.format("Strip[%d center=%.2fm]", index, centerX);
    }
}
