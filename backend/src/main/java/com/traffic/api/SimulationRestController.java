package com.traffic.api;

import com.traffic.engine.SimulationConfig;
import com.traffic.engine.SimulationEngine;
import com.traffic.map.MapParser;
import com.traffic.map.OsmFetcher;
import com.traffic.map.SampleNetworkGenerator;
import com.traffic.model.RoadNetwork;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * REST controller exposing the simulation engine to the Next.js frontend.
 * Mirrors all controls from the old DashboardController.
 */
@RestController
@RequestMapping("/api")
public class SimulationRestController {

    private final SimulationEngine engine;
    private final SimulationConfig config;
    private final SampleNetworkGenerator networkGen;
    private final MapParser mapParser;
    private final OsmFetcher osmFetcher;

    private Timer simulationTimer;

    public SimulationRestController(
            SimulationEngine engine,
            SimulationConfig config,
            SampleNetworkGenerator networkGen,
            MapParser mapParser,
            OsmFetcher osmFetcher) {
        this.engine = engine;
        this.config = config;
        this.networkGen = networkGen;
        this.mapParser = mapParser;
        this.osmFetcher = osmFetcher;
    }

    /**
     * GET /api/state - full simulation snapshot for rendering.
     */
    @GetMapping("/state")
    public SimulationStateDTO getState() {
        return SimulationStateDTO.from(engine);
    }

    /**
     * POST /api/start - start the simulation tick loop.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        engine.start();
        startSimulationTimer();
        return ResponseEntity.ok(Map.of("status", "running"));
    }

    /**
     * POST /api/stop - pause the simulation.
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        engine.stop();
        stopSimulationTimer();
        return ResponseEntity.ok(Map.of("status", "paused"));
    }

    /**
     * POST /api/reset - reset to sample network.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        engine.reset();
        stopSimulationTimer();
        RoadNetwork network = networkGen.generateSampleNetwork();
        engine.initialize(network);
        return ResponseEntity.ok(Map.of(
                "status", "reset",
                "roads", network.getSegmentCount()));
    }

    /**
     * GET /api/config - simulation configuration.
     */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return Map.of(
                "tickRateMs", config.getTickRateMs(),
                "renderFps", config.getRenderFps(),
                "stripWidth", config.getStripWidth(),
                "defaultRoadWidth", config.getDefaultRoadWidth(),
                "defaultSpeedLimit", config.getDefaultSpeedLimit(),
                "maxVehicles", config.getMaxVehicles(),
                "spawnRatePerSecond", config.getSpawnRatePerSecond());
    }

    /**
     * POST /api/upload-osm - upload an OSM XML file.
     */
    @PostMapping("/upload-osm")
    public ResponseEntity<Map<String, Object>> uploadOsm(
            @RequestParam("file") MultipartFile file) {
        try {
            // Save uploaded file to temp location
            File tempFile = File.createTempFile("osm_upload_", ".osm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }

            engine.reset();
            stopSimulationTimer();

            RoadNetwork network = mapParser.parseOsmFile(tempFile.getAbsolutePath());
            tempFile.delete();

            if (network.getSegmentCount() == 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "No road segments found in the OSM file."));
            }

            engine.initialize(network);
            String mapName = file.getOriginalFilename()
                    .replace(".osm", "").replace(".xml", "");

            return ResponseEntity.ok(Map.of(
                    "status", "loaded",
                    "mapName", mapName,
                    "roads", network.getSegmentCount()));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to parse OSM file: " + e.getMessage()));
        }
    }

    /**
     * POST /api/load-nerul - load the Nerul demo intersection.
     */
    @PostMapping("/load-nerul")
    public ResponseEntity<Map<String, Object>> loadNerul() {
        engine.reset();
        stopSimulationTimer();
        RoadNetwork network = networkGen.generateNerulIntersection();
        engine.initialize(network);
        return ResponseEntity.ok(Map.of(
                "status", "loaded",
                "mapName", "Nerul Junction, Navi Mumbai",
                "roads", network.getSegmentCount()));
    }

    // ---- Timer management (same as DashboardController) ----

    private synchronized void startSimulationTimer() {
        if (simulationTimer != null) return;
        simulationTimer = new Timer("SimTick", true);
        simulationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                engine.tick();
            }
        }, 0, config.getTickRateMs());
    }

    private synchronized void stopSimulationTimer() {
        if (simulationTimer != null) {
            simulationTimer.cancel();
            simulationTimer = null;
        }
    }
}
