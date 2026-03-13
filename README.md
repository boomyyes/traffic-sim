# Indian Traffic Simulation Engine

A Java-based microscopic simulation engine using Strip-Based lane-free logic tailored for heterogeneous Indian traffic environments.

## Features

- **Double-Buffered Updates**: Prevents race conditions during simulation ticks by separating state computation and commitment.
- **Strip-Based Lane-Free Logic**: Roads are divided into 0.5m lateral strips instead of lanes to simulate the characteristic "filtering" behavior of Indian traffic.
- **Intelligent Driver Model (IDM)**: Modified IDM for finding leaders in the same *lateral corridor*, accounting for mixed-width vehicles.
- **Indian Vehicle Mix**: Accurate spawning distribution reflecting real Indian roads (Bikes, Auto-rickshaws, Cars, Buses, Trucks).
- **OpenStreetMap Integration**: Capable of parsing real-world OSM data (like the included Guwahati map) directly into the application.

## Tech Stack

- **Language**: Java 17 / 21
- **Framework**: Spring Boot 3.2.5
- **UI**: JavaFX 21.0.2
- **Spatial Math**: JTS 1.19.0
- **Build Tool**: Maven

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 21 or higher
- Apache Maven

### How to Run

You can start the application using the provided batch script (on Windows) or via Maven.

**Using the batch script:**
```cmd
run.bat
```

**Using Maven directly:**
```bash
mvn javafx:run
```
*(Alternatively, you can try `mvn spring-boot:run`)*

## Controls

| Action | Control |
|--------|---------|
| Start simulation | ▶ Start button |
| Pause | ⏸ Pause button |
| Reset | 🔄 Reset button |
| Zoom | Scroll wheel or slider |
| Pan | Click and drag |

## Project Structure

- `src/main/java/com/traffic/model/` - Core entities (Vehicle types, Strips, RoadNetworks)
- `src/main/java/com/traffic/physics/` - Movement logic, IDM Calculator, and Lateral Seepage Logic
- `src/main/java/com/traffic/engine/` - Simulation tick loop, Simulation Engine configurable params
- `src/main/java/com/traffic/map/` - Network generation and OSM XML parser tools
- `src/main/java/com/traffic/ui/` - JavaFX application entry, Dashboard Controllers, and renderers (TrafficCanvas)
