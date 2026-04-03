package com.traffic;

import com.traffic.engine.SimulationEngine;
import com.traffic.map.MapParser;
import com.traffic.map.OsmFetcher;
import com.traffic.model.RoadNetwork;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot application entry point.
 * Starts as a web server exposing REST APIs for the simulation.
 */
@SpringBootApplication
public class MainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

    /**
     * Auto-initialize the simulation with the default OSM map on startup.
     */
    @Bean
    public CommandLineRunner initSimulation(
            SimulationEngine engine,
            OsmFetcher osmFetcher,
            MapParser mapParser,
            com.traffic.map.SampleNetworkGenerator networkGen) {
        return args -> {
            RoadNetwork network;
            try {
                String mapFile = osmFetcher.fetchDefaultGuwahatiMap("guwahati_map.osm");
                network = mapParser.parseOsmFile(mapFile);
                System.out.println("Loaded OSM map with " + network.getSegmentCount() + " roads.");
            } catch (Exception e) {
                System.err.println("OSM load failed, using sample network.");
                network = networkGen.generateSampleNetwork();
            }
            engine.initialize(network);
            System.out.println("Simulation backend ready at http://localhost:8080");
        };
    }
}
