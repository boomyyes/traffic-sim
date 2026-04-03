package com.traffic.ui;

import com.traffic.model.RoadSegment;
import com.traffic.model.RoadType;
import com.traffic.model.TrafficSignal;
import com.traffic.model.Vehicle;
import com.traffic.model.VehicleType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Renders the traffic simulation.
 * Uses parametric interpolation for all roads — works for any start/end
 * direction.
 */
public class TrafficCanvas extends Canvas {

    private double scale = 0.85; // Smaller scale to fit 900m road
    private double offsetX = 10;
    private double offsetY = 10;

    // Vehicle colors
    private static final Color BIKE_COLOR = Color.rgb(255, 200, 50);
    private static final Color AUTO_COLOR = Color.rgb(255, 140, 50);
    private static final Color CAR_COLOR = Color.rgb(70, 130, 230);
    private static final Color BUS_COLOR = Color.rgb(50, 180, 80);
    private static final Color TRUCK_COLOR = Color.rgb(220, 60, 60);

    // Road colors
    private static final Color ROAD_COLOR = Color.rgb(55, 55, 60);
    private static final Color STRIP_LINE_COLOR = Color.rgb(80, 80, 85);
    private static final Color ROAD_EDGE_COLOR = Color.rgb(180, 180, 180);
    private static final Color BACKGROUND_COLOR = Color.rgb(35, 38, 42);
    private static final Color JUNCTION_COLOR = Color.rgb(65, 65, 70);
    private static final Color CENTER_LINE_COLOR = Color.rgb(230, 200, 50);
    private static final Color RURAL_ROAD_COLOR = Color.rgb(70, 65, 55);

    // Signal colors
    private static final Color SIGNAL_RED = Color.rgb(255, 50, 50);
    private static final Color SIGNAL_YELLOW = Color.rgb(255, 220, 50);
    private static final Color SIGNAL_GREEN = Color.rgb(50, 220, 70);

    private String locationLabel = "📍 Palm Beach Road, Nerul – 4 Junctions";

    // Junction zones (set from network)
    private double[][] junctionZones = {
        {93, 143, 122, 171},
        {93, 343, 122, 371},
        {93, 543, 122, 571},
        {93, 743, 122, 771}
    };

    public TrafficCanvas(double width, double height) {
        super(width, height);
    }

    public void render(List<RoadSegment> roads, List<Vehicle> vehicles,
            List<TrafficSignal> signals) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, getWidth(), getHeight());
        if (roads == null)
            return;

        drawJunctionArea(gc);
        for (RoadSegment road : roads)
            drawRoad(gc, road);
        if (signals != null) {
            for (TrafficSignal signal : signals)
                drawSignal(gc, signal, roads);
        }
        if (vehicles != null) {
            for (Vehicle v : vehicles)
                if (v.isActive())
                    drawVehicle(gc, v, roads);
        }
        drawLegend(gc);
        drawLocationLabel(gc);
    }

    public void render(List<RoadSegment> roads, List<Vehicle> vehicles) {
        render(roads, vehicles, null);
    }

    // ==== Junction ====

    private void drawJunctionArea(GraphicsContext gc) {
        gc.setFill(JUNCTION_COLOR);
        for (double[] jz : junctionZones) {
            double x1 = toScreenX(jz[0]);
            double y1 = toScreenY(jz[1]);
            double w = (jz[2] - jz[0]) * scale;
            double h = (jz[3] - jz[1]) * scale;
            gc.fillRect(x1, y1, w, h);
        }
    }

    // ==== Road Drawing ====

    private void drawRoad(GraphicsContext gc, RoadSegment road) {
        double sx = road.getStartX(), sy = road.getStartY();
        double ex = road.getEndX(), ey = road.getEndY();
        double dx = ex - sx, dy = ey - sy;
        double len = road.getLength();

        // Road direction unit vector
        double ux = dx / len, uy = dy / len;
        // Perpendicular (points "right" of travel direction)
        double px = -uy, py = ux;

        double halfW = road.getWidth() / 2.0;

        boolean isRural = road.getRoadType() == RoadType.RURAL
                || road.getRoadType() == RoadType.RESIDENTIAL;

        // Four corners of the road rectangle
        double[] xPoints = {
                toScreenX(sx + px * halfW), toScreenX(sx - px * halfW),
                toScreenX(ex - px * halfW), toScreenX(ex + px * halfW)
        };
        double[] yPoints = {
                toScreenY(sy + py * halfW), toScreenY(sy - py * halfW),
                toScreenY(ey - py * halfW), toScreenY(ey + py * halfW)
        };

        gc.setFill(isRural ? RURAL_ROAD_COLOR : ROAD_COLOR);
        gc.fillPolygon(xPoints, yPoints, 4);

        // Edge lines
        gc.setStroke(ROAD_EDGE_COLOR);
        gc.setLineWidth(1.5);
        gc.strokeLine(xPoints[0], yPoints[0], xPoints[3], yPoints[3]); // right edge
        gc.strokeLine(xPoints[1], yPoints[1], xPoints[2], yPoints[2]); // left edge

        // Center line (dashed) or strip lines
        if (isRural) {
            gc.setStroke(CENTER_LINE_COLOR);
            gc.setLineWidth(1.5);
            int dashes = (int) (len / 8);
            for (int i = 0; i < dashes; i++) {
                double t1 = (i * 8.0) / len;
                double t2 = (i * 8.0 + 4.0) / len;
                if (t2 > 1.0)
                    t2 = 1.0;
                gc.strokeLine(
                        toScreenX(sx + dx * t1), toScreenY(sy + dy * t1),
                        toScreenX(sx + dx * t2), toScreenY(sy + dy * t2));
            }
        } else {
            drawStripLines(gc, road, sx, sy, dx, dy, len, px, py, halfW);
        }

        // Road label
        gc.setFill(Color.gray(0.4));
        gc.setFont(Font.font("System", 8));
        gc.fillText("R" + road.getId(), toScreenX(sx), toScreenY(sy) - 2);
    }

    private void drawStripLines(GraphicsContext gc, RoadSegment road,
            double sx, double sy, double dx, double dy,
            double len, double px, double py, double halfW) {
        gc.setStroke(STRIP_LINE_COLOR);
        gc.setLineWidth(0.5);
        int stripCount = road.getStripCount();
        if (stripCount <= 1)
            return;

        for (int i = 1; i < stripCount; i++) {
            // Lateral position from right edge
            double lateralFrac = (double) i / stripCount;
            double lateralOffset = halfW - lateralFrac * road.getWidth();
            double offsetX_l = px * lateralOffset;
            double offsetY_l = py * lateralOffset;

            int dashes = (int) (len / 10);
            for (int d = 0; d < dashes; d++) {
                double t1 = (d * 10.0) / len;
                double t2 = (d * 10.0 + 5.0) / len;
                if (t2 > 1.0)
                    t2 = 1.0;
                gc.strokeLine(
                        toScreenX(sx + dx * t1 + offsetX_l), toScreenY(sy + dy * t1 + offsetY_l),
                        toScreenX(sx + dx * t2 + offsetX_l), toScreenY(sy + dy * t2 + offsetY_l));
            }
        }
    }

    // ==== Signal Drawing ====

    private void drawSignal(GraphicsContext gc, TrafficSignal signal, List<RoadSegment> roads) {
        RoadSegment road = null;
        for (RoadSegment r : roads) {
            if (r.getId() == signal.getRoadSegmentId()) {
                road = r;
                break;
            }
        }
        if (road == null)
            return;

        double len = road.getLength();
        double t = signal.getStopLineY() / len; // parametric position
        double dx = road.getEndX() - road.getStartX();
        double dy = road.getEndY() - road.getStartY();
        double ux = dx / len, uy = dy / len;
        double px = -uy, py = ux; // perpendicular
        double halfW = road.getWidth() / 2.0;

        // Stop line position (on the road)
        double stopWorldX = road.getStartX() + dx * t;
        double stopWorldY = road.getStartY() + dy * t;

        // Signal housing position (offset to the right of the road)
        double sigX = toScreenX(stopWorldX + px * (halfW + 4));
        double sigY = toScreenY(stopWorldY + py * (halfW + 4));

        // Draw housing
        double boxW = 8 * scale / 1.5;
        double boxH = 20 * scale / 1.5;
        gc.setFill(Color.rgb(30, 30, 30));
        gc.fillRect(sigX - boxW / 2, sigY - boxH / 2, boxW, boxH);
        gc.setStroke(Color.gray(0.3));
        gc.setLineWidth(0.5);
        gc.strokeRect(sigX - boxW / 2, sigY - boxH / 2, boxW, boxH);

        // Three lights
        double r = boxW * 0.35;
        double lightY1 = sigY - boxH / 2 + boxH * 0.2;
        gc.setFill(signal.getState() == TrafficSignal.State.RED ? SIGNAL_RED : Color.rgb(60, 20, 20));
        gc.fillOval(sigX - r, lightY1 - r, r * 2, r * 2);
        double lightY2 = sigY;
        gc.setFill(signal.getState() == TrafficSignal.State.YELLOW ? SIGNAL_YELLOW : Color.rgb(60, 55, 20));
        gc.fillOval(sigX - r, lightY2 - r, r * 2, r * 2);
        double lightY3 = sigY + boxH / 2 - boxH * 0.2;
        gc.setFill(signal.getState() == TrafficSignal.State.GREEN ? SIGNAL_GREEN : Color.rgb(20, 55, 25));
        gc.fillOval(sigX - r, lightY3 - r, r * 2, r * 2);

        // Stop line across the road
        Color lineColor = switch (signal.getState()) {
            case RED -> SIGNAL_RED;
            case YELLOW -> SIGNAL_YELLOW;
            case GREEN -> SIGNAL_GREEN;
        };
        gc.setStroke(lineColor.deriveColor(0, 1, 1, 0.7));
        gc.setLineWidth(2);
        gc.strokeLine(
                toScreenX(stopWorldX + px * halfW), toScreenY(stopWorldY + py * halfW),
                toScreenX(stopWorldX - px * halfW), toScreenY(stopWorldY - py * halfW));
    }

    // ==== Vehicle Drawing ====

    private void drawVehicle(GraphicsContext gc, Vehicle v, List<RoadSegment> roads) {
        RoadSegment road = null;
        for (RoadSegment r : roads) {
            if (r.getId() == v.getRoadSegmentId()) {
                road = r;
                break;
            }
        }
        if (road == null)
            return;

        // Parametric position along road
        double t = v.getY() / road.getLength();
        double dx = road.getEndX() - road.getStartX();
        double dy = road.getEndY() - road.getStartY();
        double len = road.getLength();
        double ux = dx / len, uy = dy / len;
        double px = -uy, py = ux;

        // World center of vehicle
        double worldCX = road.getStartX() + dx * t;
        double worldCY = road.getStartY() + dy * t;

        // Lateral offset from road center
        double lateralOffset = v.getX() - road.getWidth() / 2.0;
        worldCX += px * lateralOffset;
        worldCY += py * lateralOffset;

        double screenCX = toScreenX(worldCX);
        double screenCY = toScreenY(worldCY);

        double screenW = v.getLength() * scale;
        double screenH = v.getWidth() * scale;
        
        double angle = Math.toDegrees(Math.atan2(dy, dx));

        gc.save();
        gc.translate(screenCX, screenCY);
        gc.rotate(angle);
        
        gc.setFill(getVehicleColor(v.getType()));
        gc.fillRect(-screenW / 2, -screenH / 2, screenW, screenH);
        gc.setStroke(Color.rgb(20, 20, 20, 0.6));
        gc.setLineWidth(0.5);
        gc.strokeRect(-screenW / 2, -screenH / 2, screenW, screenH);
        
        gc.restore();
    }

    private Color getVehicleColor(VehicleType type) {
        return switch (type) {
            case BIKE -> BIKE_COLOR;
            case AUTO_RICKSHAW -> AUTO_COLOR;
            case CAR -> CAR_COLOR;
            case BUS -> BUS_COLOR;
            case TRUCK -> TRUCK_COLOR;
        };
    }

    // ==== UI Overlays ====

    private void drawLegend(GraphicsContext gc) {
        double lx = getWidth() - 120;
        double ly = 10;
        gc.setFill(Color.rgb(20, 20, 25, 0.88));
        gc.fillRect(lx - 8, ly - 5, 118, 120);

        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.setFill(Color.gray(0.7));
        gc.fillText("VEHICLE TYPES", lx, ly + 8);

        gc.setFont(Font.font("System", 10));
        Object[][] legend = {
                { BIKE_COLOR, "Bike" }, { AUTO_COLOR, "Auto Rickshaw" },
                { CAR_COLOR, "Car" }, { BUS_COLOR, "Bus" }, { TRUCK_COLOR, "Truck" }
        };
        for (int i = 0; i < legend.length; i++) {
            gc.setFill((Color) legend[i][0]);
            gc.fillRect(lx, ly + 16 + i * 18, 12, 12);
            gc.setFill(Color.rgb(210, 210, 210));
            gc.fillText((String) legend[i][1], lx + 18, ly + 16 + i * 18 + 10);
        }
    }

    private void drawLocationLabel(GraphicsContext gc) {
        gc.setFill(Color.rgb(20, 20, 25, 0.85));
        gc.fillRect(8, 8, 260, 30);
        gc.setFont(Font.font("System", FontWeight.BOLD, 12));
        gc.setFill(Color.rgb(120, 180, 255));
        gc.fillText(locationLabel, 14, 28);
    }

    // ==== Coordinates ====

    private double toScreenX(double worldX) {
        return (worldX + offsetX) * scale;
    }

    private double toScreenY(double worldY) {
        return (worldY + offsetY) * scale;
    }

    // ==== View Controls ====

    public void setScale(double s) {
        scale = Math.max(0.3, Math.min(15.0, s));
    }

    public void adjustScale(double delta) {
        setScale(scale + delta);
    }

    public double getScale() {
        return scale;
    }

    public void pan(double dx, double dy) {
        offsetX += dx / scale;
        offsetY += dy / scale;
    }

    public void resetView() {
        scale = 1.5;
        offsetX = 10;
        offsetY = 10;
    }

    public void setLocationLabel(String label) {
        this.locationLabel = label;
    }

    public void setJunctionZone(double minX, double minY, double maxX, double maxY) {
        // Legacy single-junction setter — add as first zone
        if (junctionZones.length > 0) {
            junctionZones[0] = new double[]{minX, minY, maxX, maxY};
        }
    }

    public void setJunctionZones(double[][] zones) {
        this.junctionZones = zones;
    }

    /**
     * Compute scale and center offset to perfectly frame the given road network.
     */
    public void computeScaleAndCenter(com.traffic.model.RoadNetwork network) {
        if (network == null || network.getAllSegments().isEmpty()) return;

        double minWorldX = Double.MAX_VALUE;
        double maxWorldX = -Double.MAX_VALUE;
        double minWorldY = Double.MAX_VALUE;
        double maxWorldY = -Double.MAX_VALUE;

        for (RoadSegment road : network.getAllSegments()) {
            minWorldX = Math.min(minWorldX, Math.min(road.getStartX(), road.getEndX()));
            maxWorldX = Math.max(maxWorldX, Math.max(road.getStartX(), road.getEndX()));
            minWorldY = Math.min(minWorldY, Math.min(road.getStartY(), road.getEndY()));
            maxWorldY = Math.max(maxWorldY, Math.max(road.getStartY(), road.getEndY()));
        }

        double worldW = maxWorldX - minWorldX;
        double worldH = maxWorldY - minWorldY;

        // Add 10% padding
        worldW = Math.max(1, worldW) * 1.1;
        worldH = Math.max(1, worldH) * 1.1;

        double scaleX = getWidth() / worldW;
        double scaleY = getHeight() / worldH;

        this.scale = Math.min(scaleX, scaleY);
        
        // Center the map
        this.offsetX = (getWidth() / this.scale - (maxWorldX + minWorldX)) / 2.0;
        // SVG/Canvas usually has Y down, but our world is Y down too.
        // We just need to shift the bounding box center to the canvas center.
        this.offsetY = (getHeight() / this.scale - (maxWorldY + minWorldY)) / 2.0;

        // Dynamically pull junction zones
        List<com.traffic.model.RoadNetwork.JunctionZone> jzs = network.getJunctionZones();
        if (jzs != null && !jzs.isEmpty()) {
            this.junctionZones = new double[jzs.size()][4];
            for (int i = 0; i < jzs.size(); i++) {
                com.traffic.model.RoadNetwork.JunctionZone jz = jzs.get(i);
                this.junctionZones[i] = new double[]{jz.minX(), jz.minY(), jz.maxX(), jz.maxY()};
            }
        } else {
            this.junctionZones = new double[0][0]; // Clear old zones
        }
    }
}
