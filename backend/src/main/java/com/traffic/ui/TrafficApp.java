package com.traffic.ui;

import com.traffic.engine.SimulationConfig;
import com.traffic.engine.SimulationEngine;
import com.traffic.map.MapParser;
import com.traffic.map.SampleNetworkGenerator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX Application entry point.
 * Bridges JavaFX lifecycle with Spring Boot context.
 * 
 * JavaFX's init() → start Spring Boot
 * JavaFX's start() → build UI using Spring beans
 * JavaFX's stop() → close Spring context
 */
public class TrafficApp extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(
                com.traffic.MainApplication.class)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage primaryStage) {
        SimulationEngine engine = springContext.getBean(SimulationEngine.class);
        SimulationConfig config = springContext.getBean(SimulationConfig.class);
        SampleNetworkGenerator networkGen = springContext.getBean(SampleNetworkGenerator.class);
        MapParser mapParser = springContext.getBean(MapParser.class);
        com.traffic.map.OsmFetcher osmFetcher = springContext.getBean(com.traffic.map.OsmFetcher.class);

        DashboardController dashboard = new DashboardController(engine, config, networkGen, mapParser, osmFetcher);
        dashboard.setupStage(primaryStage);
    }

    @Override
    public void stop() {
        // Graceful shutdown
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }

    /**
     * Launch the JavaFX application.
     * This is the actual main() the user should run.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
