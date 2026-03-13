package com.traffic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the road network, traffic signals, and junction zone definitions.
 * Supports multiple junctions, each with its own signal controller.
 */
public class RoadNetwork {

    private final List<RoadSegment> segments;
    private final Map<Integer, RoadSegment> segmentById;

    private final List<TrafficSignal> signals;
    private final Map<Integer, List<TrafficSignal>> signalsByRoad;

    // Multiple junction zones
    private final List<JunctionZone> junctionZones = new ArrayList<>();

    // Multiple signal controllers (one per junction)
    private final List<SignalPhaseController> signalControllers = new ArrayList<>();

    // Legacy single junction support
    private double junctionMinX, junctionMinY, junctionMaxX, junctionMaxY;
    private boolean hasJunction = false;

    public RoadNetwork() {
        this.segments = new ArrayList<>();
        this.segmentById = new HashMap<>();
        this.signals = new ArrayList<>();
        this.signalsByRoad = new HashMap<>();
    }

    // ---- Road Segments ----

    public void addSegment(RoadSegment segment) {
        segments.add(segment);
        segmentById.put(segment.getId(), segment);
    }

    public RoadSegment getSegment(int id) {
        return segmentById.get(id);
    }

    public List<RoadSegment> getAllSegments() {
        return Collections.unmodifiableList(segments);
    }

    public int getSegmentCount() {
        return segments.size();
    }

    // ---- Traffic Signals ----

    public void addSignal(TrafficSignal signal) {
        signals.add(signal);
        signalsByRoad.computeIfAbsent(signal.getRoadSegmentId(), k -> new ArrayList<>())
                .add(signal);
    }

    public List<TrafficSignal> getAllSignals() {
        return Collections.unmodifiableList(signals);
    }

    public List<TrafficSignal> getSignalsForRoad(int roadId) {
        return signalsByRoad.getOrDefault(roadId, Collections.emptyList());
    }

    // ---- Signal Phase Controllers ----

    /**
     * Add a signal controller for a specific junction.
     */
    public void addSignalController(SignalPhaseController controller) {
        signalControllers.add(controller);
    }

    /**
     * Legacy: create single controller for all signals.
     */
    public void initSignalController(double greenDuration, double yellowDuration) {
        if (!signals.isEmpty()) {
            signalControllers.clear();
            signalControllers.add(new SignalPhaseController(signals, greenDuration, yellowDuration));
        }
    }

    public SignalPhaseController getSignalController() {
        return signalControllers.isEmpty() ? null : signalControllers.get(0);
    }

    public List<SignalPhaseController> getSignalControllers() {
        return Collections.unmodifiableList(signalControllers);
    }

    public void tickSignals(double dt) {
        if (!signalControllers.isEmpty()) {
            for (SignalPhaseController ctrl : signalControllers) {
                ctrl.tick(dt);
            }
        } else {
            for (TrafficSignal signal : signals) {
                signal.tick(dt);
            }
        }
    }

    // ---- Junction Zones (Multi-junction support) ----

    /**
     * A rectangular junction zone in world coordinates.
     */
    public record JunctionZone(int id, double minX, double minY, double maxX, double maxY) {
        public boolean contains(double wx, double wy) {
            return wx >= minX && wx <= maxX && wy >= minY && wy <= maxY;
        }
    }

    /**
     * Add a junction zone.
     */
    public void addJunctionZone(int id, double minX, double minY, double maxX, double maxY) {
        junctionZones.add(new JunctionZone(id, minX, minY, maxX, maxY));
        // Also set legacy single junction to the union of all zones
        if (!hasJunction) {
            junctionMinX = minX; junctionMinY = minY;
            junctionMaxX = maxX; junctionMaxY = maxY;
            hasJunction = true;
        } else {
            junctionMinX = Math.min(junctionMinX, minX);
            junctionMinY = Math.min(junctionMinY, minY);
            junctionMaxX = Math.max(junctionMaxX, maxX);
            junctionMaxY = Math.max(junctionMaxY, maxY);
        }
    }

    /** Legacy: single junction setter */
    public void setJunctionZone(double minX, double minY, double maxX, double maxY) {
        addJunctionZone(1, minX, minY, maxX, maxY);
    }

    public List<JunctionZone> getJunctionZones() {
        return Collections.unmodifiableList(junctionZones);
    }

    public boolean hasJunction() {
        return hasJunction;
    }

    public boolean isInJunction(double worldX, double worldY) {
        for (JunctionZone jz : junctionZones) {
            if (jz.contains(worldX, worldY)) return true;
        }
        return false;
    }

    // Legacy getters
    public double getJunctionMinX() { return junctionMinX; }
    public double getJunctionMinY() { return junctionMinY; }
    public double getJunctionMaxX() { return junctionMaxX; }
    public double getJunctionMaxY() { return junctionMaxY; }

    @Override
    public String toString() {
        return String.format("RoadNetwork[%d segments, %d signals, %d junctions]",
                segments.size(), signals.size(), junctionZones.size());
    }
}
