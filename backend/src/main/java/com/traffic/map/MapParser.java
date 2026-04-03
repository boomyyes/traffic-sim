package com.traffic.map;

import com.traffic.engine.SimulationConfig;
import com.traffic.model.RoadNetwork;
import com.traffic.model.RoadSegment;
import com.traffic.model.RoadType;
import com.traffic.model.TrafficSignal;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenStreetMap XML parser.
 * Reads .osm files and extracts road segments with width + type info,
 * and traffic signals from node tags.
 */
@Service
public class MapParser {

    private final SimulationConfig config;

    public MapParser(SimulationConfig config) {
        this.config = config;
    }

    /**
     * Parse an OSM XML file and create a RoadNetwork with roads and signals.
     */
    public RoadNetwork parseOsmFile(String osmFilePath) throws Exception {
        RoadNetwork network = new RoadNetwork();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(osmFilePath));
        doc.getDocumentElement().normalize();

        // Step 1: Parse all nodes (lat/lon → coordinates)
        Map<Long, double[]> nodeMap = new HashMap<>();
        List<long[]> signalNodeIds = new ArrayList<>(); // nodes tagged as traffic signals
        NodeList nodes = doc.getElementsByTagName("node");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            long id = Long.parseLong(node.getAttribute("id"));
            double lat = Double.parseDouble(node.getAttribute("lat"));
            double lon = Double.parseDouble(node.getAttribute("lon"));

            // Simple Mercator projection (good for small areas)
            double x = lon * 111320 * Math.cos(Math.toRadians(lat));
            double y = lat * 110540;
            nodeMap.put(id, new double[] { x, y });

            // Check if this node is a traffic signal
            NodeList tags = node.getElementsByTagName("tag");
            for (int j = 0; j < tags.getLength(); j++) {
                Element tag = (Element) tags.item(j);
                if ("highway".equals(tag.getAttribute("k"))
                        && "traffic_signals".equals(tag.getAttribute("v"))) {
                    signalNodeIds.add(new long[] { id });
                    break;
                }
            }
        }

        // Step 2: Parse ways (road segments)
        int segmentId = 1;
        // Track which segment each node belongs to (for signal assignment)
        Map<Long, List<int[]>> nodeToSegments = new HashMap<>(); // nodeId → [(segId, index)]

        NodeList ways = doc.getElementsByTagName("way");
        for (int i = 0; i < ways.getLength(); i++) {
            Element way = (Element) ways.item(i);

            boolean isRoad = false;
            double width = config.getDefaultRoadWidth();
            String highway = null;

            NodeList tags = way.getElementsByTagName("tag");
            for (int j = 0; j < tags.getLength(); j++) {
                Element tag = (Element) tags.item(j);
                String key = tag.getAttribute("k");
                String value = tag.getAttribute("v");

                if ("highway".equals(key)) {
                    isRoad = true;
                    highway = value;
                }
                if ("width".equals(key)) {
                    try {
                        width = Double.parseDouble(value.replaceAll("[^0-9.]", ""));
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }
            }

            if (!isRoad)
                continue;

            if (width == config.getDefaultRoadWidth() && highway != null) {
                width = inferWidthFromType(highway);
            }

            RoadType roadType = inferRoadType(highway);
            boolean divided = (roadType == RoadType.EXPRESSWAY || roadType == RoadType.ARTERIAL);

            NodeList ndRefs = way.getElementsByTagName("nd");
            for (int j = 0; j < ndRefs.getLength() - 1; j++) {
                long refA = Long.parseLong(((Element) ndRefs.item(j)).getAttribute("ref"));
                long refB = Long.parseLong(((Element) ndRefs.item(j + 1)).getAttribute("ref"));

                double[] a = nodeMap.get(refA);
                double[] b = nodeMap.get(refB);
                if (a == null || b == null)
                    continue;

                int sid = segmentId++;
                RoadSegment segment = new RoadSegment(
                        sid, a[0], a[1], b[0], b[1],
                        width,
                        config.getDefaultSpeedLimit() / 3.6,
                        config.getStripWidth(),
                        divided, roadType);
                network.addSegment(segment);

                // Track node-to-segment mapping for signal assignment
                nodeToSegments.computeIfAbsent(refA, k -> new ArrayList<>())
                        .add(new int[] { sid, 0 });
                nodeToSegments.computeIfAbsent(refB, k -> new ArrayList<>())
                        .add(new int[] { sid, 1 });
            }
        }

        // Step 3: Create traffic signals from tagged nodes
        int signalId = 1;
        for (long[] sigNode : signalNodeIds) {
            long nodeId = sigNode[0];
            double[] coords = nodeMap.get(nodeId);
            if (coords == null)
                continue;

            // Find the nearest road segment to this signal node
            List<int[]> segRefs = nodeToSegments.get(nodeId);
            if (segRefs != null) {
                for (int[] ref : segRefs) {
                    int roadId = ref[0];
                    RoadSegment road = network.getSegment(roadId);
                    if (road == null)
                        continue;

                    // Calculate where along the road segment this signal falls
                    double dx = coords[0] - road.getStartX();
                    double dy = coords[1] - road.getStartY();
                    double projection = Math.sqrt(dx * dx + dy * dy);
                    double stopLineY = Math.min(projection, road.getLength());

                    network.addSignal(new TrafficSignal(
                            signalId++, roadId, stopLineY,
                            30.0, 5.0, 25.0, 0.0));
                }
            }
        }

        return network;
    }

    private double inferWidthFromType(String highwayType) {
        return switch (highwayType) {
            case "motorway", "trunk" -> 10.5;
            case "primary" -> 7.0;
            case "secondary" -> 6.0;
            case "tertiary" -> 5.0;
            case "residential", "service" -> 4.0;
            case "living_street" -> 3.5;
            case "unclassified", "track" -> 3.5;
            default -> 7.0;
        };
    }

    /**
     * Infer RoadType from OSM highway tag.
     */
    private RoadType inferRoadType(String highwayType) {
        if (highwayType == null)
            return RoadType.URBAN;
        return switch (highwayType) {
            case "motorway", "motorway_link", "trunk", "trunk_link" -> RoadType.EXPRESSWAY;
            case "primary", "primary_link" -> RoadType.ARTERIAL;
            case "secondary", "secondary_link", "tertiary",
                    "tertiary_link" ->
                RoadType.URBAN;
            case "unclassified", "track" -> RoadType.RURAL;
            case "residential", "living_street", "service" -> RoadType.RESIDENTIAL;
            default -> RoadType.URBAN;
        };
    }
}
