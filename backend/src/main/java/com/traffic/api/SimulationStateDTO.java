package com.traffic.api;

import com.traffic.model.RoadNetwork;
import com.traffic.model.RoadSegment;
import com.traffic.model.TrafficSignal;
import com.traffic.model.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for the complete simulation state.
 * Serialized to JSON for the frontend.
 */
public class SimulationStateDTO {

    // ---- Top-level fields ----
    public boolean running;
    public long tickCount;
    public int vehicleCount;
    public double avgSpeed;
    public int roadCount;
    public int bikeCount;
    public int carAutoCount;
    public int busTruckCount;

    public List<VehicleDTO> vehicles;
    public List<RoadDTO> roads;
    public List<SignalDTO> signals;
    public List<double[]> junctionZones;

    // ---- Nested DTOs ----

    public static class VehicleDTO {
        public int id;
        public String type;
        public double worldX;
        public double worldY;
        public double speed;
        public double width;
        public double length;
        public int roadSegmentId;
        public double x; // lateral position
        public double y; // longitudinal position
    }

    public static class RoadDTO {
        public int id;
        public double startX, startY, endX, endY;
        public double width;
        public double roadLength;
        public int stripCount;
        public String roadType;
        public double heading;
        public double speedLimit;
    }

    public static class SignalDTO {
        public int id;
        public int roadSegmentId;
        public double stopLineY;
        public String state;
    }

    // ---- Factory method ----

    public static SimulationStateDTO from(
            com.traffic.engine.SimulationEngine engine) {

        SimulationStateDTO dto = new SimulationStateDTO();
        dto.running = engine.isRunning();
        dto.tickCount = engine.getTickCount();
        dto.vehicleCount = engine.getActiveVehicleCount();
        dto.avgSpeed = engine.getAverageSpeed();

        RoadNetwork network = engine.getRoadNetwork();

        // Roads
        dto.roads = new ArrayList<>();
        if (network != null) {
            dto.roadCount = network.getSegmentCount();
            for (RoadSegment seg : network.getAllSegments()) {
                RoadDTO r = new RoadDTO();
                r.id = seg.getId();
                r.startX = seg.getStartX();
                r.startY = seg.getStartY();
                r.endX = seg.getEndX();
                r.endY = seg.getEndY();
                r.width = seg.getWidth();
                r.roadLength = seg.getLength();
                r.stripCount = seg.getStripCount();
                r.roadType = seg.getRoadType().name();
                r.heading = seg.getHeading();
                r.speedLimit = seg.getSpeedLimit();
                dto.roads.add(r);
            }

            // Signals
            dto.signals = new ArrayList<>();
            for (TrafficSignal sig : network.getAllSignals()) {
                SignalDTO s = new SignalDTO();
                s.id = sig.getId();
                s.roadSegmentId = sig.getRoadSegmentId();
                s.stopLineY = sig.getStopLineY();
                s.state = sig.getState().name();
                dto.signals.add(s);
            }

            // Junction zones
            dto.junctionZones = new ArrayList<>();
            for (RoadNetwork.JunctionZone jz : network.getJunctionZones()) {
                dto.junctionZones.add(new double[]{jz.minX(), jz.minY(), jz.maxX(), jz.maxY()});
            }
        } else {
            dto.roadCount = 0;
            dto.signals = new ArrayList<>();
            dto.junctionZones = new ArrayList<>();
        }

        // Vehicles
        dto.vehicles = new ArrayList<>();
        int bikes = 0, carsAutos = 0, busesTrucks = 0;
        for (Vehicle v : engine.getVehicles()) {
            if (!v.isActive()) continue;
            VehicleDTO vd = new VehicleDTO();
            vd.id = v.getId();
            vd.type = v.getType().name();
            vd.worldX = v.getWorldX();
            vd.worldY = v.getWorldY();
            vd.speed = v.getSpeed();
            vd.width = v.getWidth();
            vd.length = v.getLength();
            vd.roadSegmentId = v.getRoadSegmentId();
            vd.x = v.getX();
            vd.y = v.getY();
            dto.vehicles.add(vd);

            switch (v.getType()) {
                case BIKE -> bikes++;
                case CAR, AUTO_RICKSHAW -> carsAutos++;
                case BUS, TRUCK -> busesTrucks++;
            }
        }
        dto.bikeCount = bikes;
        dto.carAutoCount = carsAutos;
        dto.busTruckCount = busesTrucks;

        return dto;
    }
}
