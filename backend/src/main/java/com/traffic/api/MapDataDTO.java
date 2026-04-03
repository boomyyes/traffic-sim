package com.traffic.api;

import com.traffic.model.RoadNetwork;
import com.traffic.model.RoadSegment;
import com.traffic.model.TrafficSignal;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for static map data (roads, signals, junction zones).
 * Fetched once per map load, not on every state poll.
 */
public class MapDataDTO {

    public List<SimulationStateDTO.RoadDTO> roads;
    public List<SimulationStateDTO.SignalDTO> signals;
    public List<double[]> junctionZones;
    public int roadCount;

    public static MapDataDTO from(com.traffic.engine.SimulationEngine engine) {
        MapDataDTO dto = new MapDataDTO();
        RoadNetwork network = engine.getRoadNetwork();

        dto.roads = new ArrayList<>();
        dto.signals = new ArrayList<>();
        dto.junctionZones = new ArrayList<>();

        if (network != null) {
            dto.roadCount = network.getSegmentCount();
            for (RoadSegment seg : network.getAllSegments()) {
                SimulationStateDTO.RoadDTO r = new SimulationStateDTO.RoadDTO();
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

            for (TrafficSignal sig : network.getAllSignals()) {
                SimulationStateDTO.SignalDTO s = new SimulationStateDTO.SignalDTO();
                s.id = sig.getId();
                s.roadSegmentId = sig.getRoadSegmentId();
                s.stopLineY = sig.getStopLineY();
                s.state = sig.getState().name();
                dto.signals.add(s);
            }

            for (RoadNetwork.JunctionZone jz : network.getJunctionZones()) {
                dto.junctionZones.add(new double[]{jz.minX(), jz.minY(), jz.maxX(), jz.maxY()});
            }
        }

        return dto;
    }
}
