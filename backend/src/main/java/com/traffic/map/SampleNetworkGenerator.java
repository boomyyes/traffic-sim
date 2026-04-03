package com.traffic.map;

import com.traffic.engine.SimulationConfig;
import com.traffic.model.RoadNetwork;
import com.traffic.model.RoadSegment;
import com.traffic.model.RoadType;
import com.traffic.model.SignalPhaseController;
import com.traffic.model.TrafficSignal;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates a realistic Nerul (Navi Mumbai) road network with 4 intersections
 * along Palm Beach Road (PBR).
 *
 * Layout (world coordinates, not to scale):
 *
 *   PBR runs north–south (vertical), with 4 cross-roads.
 *
 *              NB(x=100)  SB(x=115)
 *                 ↑          ↓
 *   ←─ Cross4 ───┤──────────┤─── Cross4 →    y=750
 *                 |          |
 *   ←─ Cross3 ───┤──────────┤─── Cross3 →    y=550
 *                 |          |
 *   ←─ Cross2 ───┤──────────┤─── Cross2 →    y=350
 *                 |          |
 *   ←─ Cross1 ───┤──────────┤─── Cross1 →    y=150
 *                 |          |
 *                 ↑          ↓
 *
 *  PBR: 900m total (y=0 to y=900)
 *  Cross roads: 250m each
 *  Junctions at y=150, 350, 550, 750
 */
@Service
public class SampleNetworkGenerator {

    private final SimulationConfig config;

    public SampleNetworkGenerator(SimulationConfig config) {
        this.config = config;
    }

    public RoadNetwork generateSampleNetwork() {
        return generateNerulIntersection();
    }

    public RoadNetwork generateNerulIntersection() {
        RoadNetwork network = new RoadNetwork();
        double sw = config.getStripWidth();

        double expresswaySpeed = 22.22; // 80 km/h
        double crossRoadSpeed = 13.89;  // 50 km/h

        // =============================================
        // MAIN ROADS: PBR Northbound & Southbound
        // =============================================

        // Road 1: PBR Northbound (south → north, y=0 to y=900)
        RoadSegment nbRoad = new RoadSegment(
                1, 100, 0, 100, 900,
                10.0, expresswaySpeed, sw, true, RoadType.EXPRESSWAY);
        network.addSegment(nbRoad);

        // Road 2: PBR Southbound (north → south, y=900 to y=0)
        RoadSegment sbRoad = new RoadSegment(
                2, 115, 900, 115, 0,
                10.0, expresswaySpeed, sw, true, RoadType.EXPRESSWAY);
        network.addSegment(sbRoad);

        // =============================================
        // CROSS ROADS (4 intersections)
        // =============================================

        int roadId = 3;
        int signalId = 1;
        double[] junctionYs = {150, 350, 550, 750};

        for (int jIdx = 0; jIdx < junctionYs.length; jIdx++) {
            double jY = junctionYs[jIdx];
            int junctionId = jIdx + 1;

            // Eastbound cross road
            RoadSegment eb = new RoadSegment(
                    roadId++, 0, jY, 250, jY,
                    7.0, crossRoadSpeed, sw, true, RoadType.URBAN);
            network.addSegment(eb);

            // Westbound cross road
            RoadSegment wb = new RoadSegment(
                    roadId++, 250, jY + 14, 0, jY + 14,
                    7.0, crossRoadSpeed, sw, true, RoadType.URBAN);
            network.addSegment(wb);

            // Junction zone (world coords)
            double jMinX = 93, jMaxX = 122;
            double jMinY = jY - 7, jMaxY = jY + 21;
            network.addJunctionZone(junctionId, jMinX, jMinY, jMaxX, jMaxY);

            // Traffic signals — one per approach road at this junction
            // NB signal: junction at world y = jY-7 → road-local y = jY-7 (since NB starts at y=0)
            TrafficSignal sigNB = new TrafficSignal(signalId++, 1, jY - 12,
                    20.0, 5.0, 55.0, 0.0);
            network.addSignal(sigNB);

            // SB signal: starts at y=900, ends at y=0. World y=jY+21 → local = 900-(jY+21)
            TrafficSignal sigSB = new TrafficSignal(signalId++, 2, 900 - (jY + 26),
                    20.0, 5.0, 55.0, 0.0);
            network.addSignal(sigSB);

            // EB signal: junction at world x=93 → road-local x = 93, signal at 88
            TrafficSignal sigEB = new TrafficSignal(signalId++, eb.getId(), 83.0,
                    20.0, 5.0, 55.0, 25.0);
            network.addSignal(sigEB);

            // WB signal: starts x=250, ends x=0. World x=122 → local = 250-122=128, signal at 123
            TrafficSignal sigWB = new TrafficSignal(signalId++, wb.getId(), 118.0,
                    20.0, 5.0, 55.0, 25.0);
            network.addSignal(sigWB);

            // Create independent signal controller for this junction
            SignalPhaseController controller = new SignalPhaseController(
                    List.of(sigNB, sigSB, sigEB, sigWB), 20.0, 5.0);
            network.addSignalController(controller);
        }

        return network;
    }
}
