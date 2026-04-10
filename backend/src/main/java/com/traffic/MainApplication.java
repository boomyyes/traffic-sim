package com.traffic;

import com.traffic.engine.SimulationEngine;
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
     * Auto-initialize the simulation with the sample network on startup.
     */
    @Bean
    public CommandLineRunner initSimulation(
            SimulationEngine engine,
            com.traffic.map.SampleNetworkGenerator networkGen) {
        return args -> {
            RoadNetwork network = networkGen.generateSampleNetwork();
            engine.initialize(network);
            System.out.println("Simulation backend ready at http://localhost:8080");
        };
    }
}
