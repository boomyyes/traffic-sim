package com.traffic;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point.
 * 
 * NOTE: Do NOT run this class directly.
 * Instead, run com.traffic.ui.TrafficApp which initializes
 * both JavaFX and Spring Boot together.
 */
@SpringBootApplication
public class MainApplication {
    // Spring Boot uses this for component scanning.
    // The actual main() is in TrafficApp.
}
