package com.traffic.ui;

import com.traffic.engine.SimulationConfig;
import com.traffic.engine.SimulationEngine;
import com.traffic.map.MapParser;
import com.traffic.map.SampleNetworkGenerator;
import com.traffic.model.RoadNetwork;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main dashboard controller that assembles the JavaFX UI:
 * - Center: TrafficCanvas (simulation view)
 * - Right: Stats panel
 * - Bottom: Control bar with Start/Stop/Reset, zoom, and Load OSM
 */
public class DashboardController {

    private final SimulationEngine engine;
    private final SimulationConfig config;
    private final SampleNetworkGenerator networkGenerator;
    private final MapParser mapParser;

    private TrafficCanvas canvas;
    private Stage primaryStage;
    private Timer simulationTimer;

    // Stats labels
    private Label vehicleCountLabel;
    private Label avgSpeedLabel;
    private Label tickCountLabel;
    private Label statusLabel;
    private Label bikeCountLabel;
    private Label carCountLabel;
    private Label busCountLabel;
    private Label roadCountLabel;
    private Label mapNameLabel;

    private final com.traffic.map.OsmFetcher osmFetcher;

    public DashboardController(SimulationEngine engine, SimulationConfig config,
            SampleNetworkGenerator networkGenerator,
            com.traffic.map.MapParser mapParser,
            com.traffic.map.OsmFetcher osmFetcher) {
        this.engine = engine;
        this.config = config;
        this.networkGenerator = networkGenerator;
        this.mapParser = mapParser;
        this.osmFetcher = osmFetcher;
    }

    public void setupStage(Stage stage) {
        this.primaryStage = stage;

        RoadNetwork network = null;
        try {
            // Fetch live map data from Overpass API (default: GS Road, Guwahati)
            String mapFile = osmFetcher.fetchDefaultGuwahatiMap("guwahati_map.osm");
            network = mapParser.parseOsmFile(mapFile);
            System.out.println("✅ Successfully loaded OSM map with " + network.getSegmentCount() + " roads.");
        } catch (Exception e) {
            System.err.println("❌ Failed to load OSM map, falling back to sample Network.");
            e.printStackTrace();
            network = networkGenerator.generateSampleNetwork();
        }

        engine.initialize(network);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a22;");

        // Center: Traffic canvas
        canvas = new TrafficCanvas(1000, 750);
        canvas.computeScaleAndCenter(network); // Dynamically scale to fit the map
        setupCanvasInteraction();
        root.setCenter(canvas);

        // Right: Stats panel
        VBox statsPanel = createStatsPanel();
        root.setRight(statsPanel);

        // Bottom: Control bar
        HBox controlBar = createControlBar(stage);
        root.setBottom(controlBar);

        // Top: Title bar
        HBox titleBar = createTitleBar();
        root.setTop(titleBar);

        Scene scene = new Scene(root, 1280, 820);
        scene.setFill(Color.rgb(26, 26, 34));

        stage.setScene(scene);
        stage.setTitle("Indian Traffic Simulation Engine — Nerul Junction");
        stage.setMinWidth(1050);
        stage.setMinHeight(650);

        startRenderLoop();

        stage.setOnCloseRequest(e -> stopSimulationTimer());
        stage.show();
    }

    private HBox createTitleBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setStyle("-fx-background-color: #111118;");

        Label title = new Label("🚦 Indian Traffic Simulation Engine");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("  —  Strip-Based Lane-Free Model");
        subtitle.setFont(Font.font("System", 13));
        subtitle.setTextFill(Color.gray(0.5));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        mapNameLabel = new Label("📍 Nerul Junction, Navi Mumbai");
        mapNameLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        mapNameLabel.setTextFill(Color.rgb(120, 180, 255));

        bar.getChildren().addAll(title, subtitle, spacer, mapNameLabel);
        return bar;
    }

    private VBox createStatsPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(15));
        panel.setPrefWidth(210);
        panel.setStyle("-fx-background-color: #1e1e28; -fx-border-color: #2a2a38; -fx-border-width: 0 0 0 1;");

        Label header = new Label("📊 Live Statistics");
        header.setFont(Font.font("System", FontWeight.BOLD, 14));
        header.setTextFill(Color.rgb(180, 180, 220));

        vehicleCountLabel = statLabel("Vehicles: 0");
        avgSpeedLabel = statLabel("Avg Speed: 0.0 km/h");
        tickCountLabel = statLabel("Tick: 0");
        statusLabel = statLabel("⏹ Stopped");
        statusLabel.setTextFill(Color.rgb(200, 100, 100));

        Label networkHeader = new Label("🗺️ Road Network");
        networkHeader.setFont(Font.font("System", FontWeight.BOLD, 12));
        networkHeader.setTextFill(Color.gray(0.65));

        roadCountLabel = statLabel("Roads: 0");

        Label compHeader = new Label("🚗 Vehicle Mix");
        compHeader.setFont(Font.font("System", FontWeight.BOLD, 12));
        compHeader.setTextFill(Color.gray(0.65));

        bikeCountLabel = statLabel("🏍️ Bikes: 0");
        carCountLabel = statLabel("🚙 Cars/Autos: 0");
        busCountLabel = statLabel("🚌 Buses/Trucks: 0");

        panel.getChildren().addAll(
                header, sep(),
                statusLabel, vehicleCountLabel, avgSpeedLabel, tickCountLabel,
                sep(), networkHeader, roadCountLabel,
                sep(), compHeader, bikeCountLabel, carCountLabel, busCountLabel);

        return panel;
    }

    private Label statLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Consolas", 11));
        label.setTextFill(Color.rgb(200, 200, 200));
        return label;
    }

    private Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #333;");
        return s;
    }

    private HBox createControlBar(Stage stage) {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setStyle("-fx-background-color: #111118;");

        // ---- Simulation Controls ----
        Button startBtn = styledBtn("▶ Start", "#2d8a4e");
        startBtn.setOnAction(e -> {
            engine.start();
            startSimulationTimer();
            statusLabel.setText("▶ Running");
            statusLabel.setTextFill(Color.rgb(80, 200, 100));
        });

        Button stopBtn = styledBtn("⏸ Pause", "#c49a2a");
        stopBtn.setOnAction(e -> {
            engine.stop();
            stopSimulationTimer();
            statusLabel.setText("⏸ Paused");
            statusLabel.setTextFill(Color.rgb(200, 180, 60));
        });

        Button resetBtn = styledBtn("🔄 Reset", "#a83232");
        resetBtn.setOnAction(e -> {
            engine.reset();
            stopSimulationTimer();
            RoadNetwork network = networkGenerator.generateSampleNetwork();
            engine.initialize(network);
            statusLabel.setText("⏹ Reset");
            statusLabel.setTextFill(Color.rgb(200, 100, 100));
            mapNameLabel.setText("📍 Nerul Junction, Navi Mumbai");
        });

        // ---- Zoom ----
        Label zoomLabel = new Label("Zoom:");
        zoomLabel.setTextFill(Color.WHITE);
        zoomLabel.setFont(Font.font("System", 11));

        Slider zoomSlider = new Slider(0.3, 8.0, 1.5);
        zoomSlider.setPrefWidth(130);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> canvas.setScale(newVal.doubleValue()));

        Button resetViewBtn = styledBtn("🔍 Fit", "#444455");
        resetViewBtn.setOnAction(e -> {
            canvas.resetView();
            zoomSlider.setValue(1.5);
        });

        // ---- OSM File Loading ----
        Button loadOsmBtn = styledBtn("📂 Load OSM Map", "#2a6a9e");
        loadOsmBtn.setOnAction(e -> loadOsmFile(stage, zoomSlider));

        // ---- Nerul Demo ----
        Button nerulBtn = styledBtn("📍 Nerul Demo", "#5a3d8a");
        nerulBtn.setOnAction(e -> {
            engine.reset();
            stopSimulationTimer();
            RoadNetwork network = networkGenerator.generateNerulIntersection();
            engine.initialize(network);
            canvas.resetView();
            zoomSlider.setValue(1.5);
            statusLabel.setText("⏹ Ready");
            statusLabel.setTextFill(Color.rgb(200, 100, 100));
            mapNameLabel.setText("📍 Nerul Junction, Navi Mumbai");
        });

        bar.getChildren().addAll(
                startBtn, stopBtn, resetBtn,
                sep(), zoomLabel, zoomSlider, resetViewBtn,
                sep(), loadOsmBtn, nerulBtn);
        return bar;
    }

    /**
     * Open a file dialog and load an OSM XML file.
     */
    private void loadOsmFile(Stage stage, Slider zoomSlider) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select OpenStreetMap File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("OSM Files", "*.osm", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showOpenDialog(stage);
        if (file == null)
            return;

        try {
            engine.reset();
            stopSimulationTimer();

            RoadNetwork network = mapParser.parseOsmFile(file.getAbsolutePath());

            if (network.getSegmentCount() == 0) {
                showAlert("No Roads Found",
                        "The OSM file was parsed but no road segments were found.\n" +
                                "Make sure the file contains <way> tags with highway=* attributes.");
                return;
            }

            engine.initialize(network);
            canvas.resetView();
            zoomSlider.setValue(1.0); // OSM maps are often larger, zoom out

            String fileName = file.getName().replace(".osm", "").replace(".xml", "");
            mapNameLabel.setText("📂 " + fileName);
            statusLabel.setText("⏹ Ready (" + network.getSegmentCount() + " roads)");
            statusLabel.setTextFill(Color.rgb(100, 180, 255));

        } catch (Exception ex) {
            showAlert("OSM Parse Error",
                    "Failed to parse the OSM file:\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Button styledBtn(String text, String bgColor) {
        Button btn = new Button(text);
        btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; " +
                        "-fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 7 14; " +
                        "-fx-cursor: hand; -fx-background-radius: 5;",
                bgColor));
        return btn;
    }

    private void setupCanvasInteraction() {
        canvas.setOnScroll(e -> {
            double delta = e.getDeltaY() > 0 ? 0.15 : -0.15;
            canvas.adjustScale(delta);
        });

        final double[] dragStart = new double[2];
        canvas.setOnMousePressed(e -> {
            dragStart[0] = e.getX();
            dragStart[1] = e.getY();
        });
        canvas.setOnMouseDragged(e -> {
            double dx = e.getX() - dragStart[0];
            double dy = e.getY() - dragStart[1];
            canvas.pan(dx, dy);
            dragStart[0] = e.getX();
            dragStart[1] = e.getY();
        });
    }

    private void startRenderLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                var network = engine.getRoadNetwork();
                canvas.render(
                        network != null ? network.getAllSegments() : null,
                        engine.getVehicles(),
                        network != null ? network.getAllSignals() : null);
                updateStats();
            }
        }.start();
    }

    private void startSimulationTimer() {
        if (simulationTimer != null)
            return;
        simulationTimer = new Timer("SimTick", true);
        simulationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                engine.tick();
            }
        }, 0, config.getTickRateMs());
    }

    private void stopSimulationTimer() {
        if (simulationTimer != null) {
            simulationTimer.cancel();
            simulationTimer = null;
        }
    }

    private void updateStats() {
        var vehicles = engine.getVehicles();
        vehicleCountLabel.setText("Vehicles: " + engine.getActiveVehicleCount());
        avgSpeedLabel.setText(String.format("Avg Speed: %.1f km/h", engine.getAverageSpeed() * 3.6));
        tickCountLabel.setText("Tick: " + engine.getTickCount());

        if (engine.getRoadNetwork() != null) {
            roadCountLabel.setText("Roads: " + engine.getRoadNetwork().getSegmentCount());
        }

        long bikes = vehicles.stream().filter(v -> v.getType() == com.traffic.model.VehicleType.BIKE).count();
        long carsAutos = vehicles.stream().filter(v -> v.getType() == com.traffic.model.VehicleType.CAR ||
                v.getType() == com.traffic.model.VehicleType.AUTO_RICKSHAW).count();
        long busesT = vehicles.stream().filter(v -> v.getType() == com.traffic.model.VehicleType.BUS ||
                v.getType() == com.traffic.model.VehicleType.TRUCK).count();

        bikeCountLabel.setText("🏍️ Bikes: " + bikes);
        carCountLabel.setText("🚙 Cars/Autos: " + carsAutos);
        busCountLabel.setText("🚌 Buses/Trucks: " + busesT);
    }
}
