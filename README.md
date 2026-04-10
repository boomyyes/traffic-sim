# Indian Traffic Simulation Engine

A microscopic simulation engine using Strip-Based lane-free logic for heterogeneous Indian traffic environments. Now with a modern **Next.js web frontend** powered by **anime.js** animations.

## Architecture

```
mini-project/
├── backend/        Java/Spring Boot simulation engine + REST API
├── frontend/       Next.js web dashboard + anime.js animations
├── run.bat         Launches both backend and frontend
└── README.md
```

### Backend (`backend/`)
- **Language**: Java 21
- **Framework**: Spring Boot 3.2.5
- **API**: REST endpoints on `http://localhost:8080/api`
- **Spatial Math**: JTS 1.19.0
- **Build**: Maven

### Frontend (`frontend/`)
- **Framework**: Next.js (App Router)
- **Rendering**: HTML5 Canvas (parametric road/vehicle rendering)
- **Animations**: anime.js v4
- **Styling**: Vanilla CSS (dark theme)

## Getting Started

### Prerequisites
- Java Development Kit (JDK) 21+
- Apache Maven
- Node.js 18+ and npm

### Quick Start (both processes)
```cmd
run.bat
```

### Manual Start

**Backend:**
```bash
cd backend
mvn spring-boot:run
```

**Frontend** (in a second terminal):
```bash
cd frontend
npm install    # first time only
npm run dev
```

Then open **http://localhost:3000** in your browser.


## Project Structure

### Backend
- `src/main/java/com/traffic/model/` — Core entities (Vehicle, Strip, RoadNetwork)
- `src/main/java/com/traffic/physics/` — IDM Calculator, Seepage Logic
- `src/main/java/com/traffic/engine/` — Simulation tick loop
- `src/main/java/com/traffic/map/` — OSM parser, Overpass API fetcher
- `src/main/java/com/traffic/api/` — REST controller, DTOs, CORS config

### Frontend
- `src/components/SimulationCanvas.js` — HTML5 Canvas renderer
- `src/components/StatsPanel.js` — Live statistics sidebar
- `src/components/ControlBar.js` — Playback controls + zoom
- `src/hooks/useSimulation.js` — Real-time state polling
- `src/lib/api.js` — Backend API client
