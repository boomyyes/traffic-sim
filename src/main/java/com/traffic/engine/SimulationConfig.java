package com.traffic.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Binds simulation configuration from application.properties to Java fields.
 */
@Component
public class SimulationConfig {

    @Value("${sim.tick-rate-ms:100}")
    private int tickRateMs;

    @Value("${sim.render-fps:60}")
    private int renderFps;

    @Value("${sim.strip-width:0.5}")
    private double stripWidth;

    @Value("${sim.default-road-width:7.0}")
    private double defaultRoadWidth;

    @Value("${sim.default-speed-limit:40.0}")
    private double defaultSpeedLimit;

    @Value("${sim.max-vehicles:500}")
    private int maxVehicles;

    @Value("${sim.spawn-rate-per-second:5}")
    private int spawnRatePerSecond;

    // Getters
    public int getTickRateMs() {
        return tickRateMs;
    }

    public int getRenderFps() {
        return renderFps;
    }

    public double getStripWidth() {
        return stripWidth;
    }

    public double getDefaultRoadWidth() {
        return defaultRoadWidth;
    }

    public double getDefaultSpeedLimit() {
        return defaultSpeedLimit;
    }

    public int getMaxVehicles() {
        return maxVehicles;
    }

    public int getSpawnRatePerSecond() {
        return spawnRatePerSecond;
    }

    /** Time step in seconds (derived from tick rate) */
    public double getDt() {
        return tickRateMs / 1000.0;
    }
}
