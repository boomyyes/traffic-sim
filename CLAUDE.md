# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Indian Traffic Simulation Engine — a microscopic traffic simulator using strip-based lane-free logic for heterogeneous Indian traffic. Monorepo with a Java/Spring Boot backend (simulation engine + REST API) and a Next.js frontend (HTML5 Canvas visualization + anime.js animations).

## Build & Run Commands

### Backend (from `backend/`)
```bash
mvn spring-boot:run          # Run on http://localhost:8080
mvn compile                  # Compile only
mvn test                     # Run tests
```

### Frontend (from `frontend/`)
```bash
npm install                  # Install deps (first time)
npm run dev                  # Dev server on http://localhost:3000
npm run build                # Production build
npm run lint                 # ESLint
```

### Both at once (from root, Windows)
```cmd
run.bat
```

## Architecture

### Backend (`backend/src/main/java/com/traffic/`)
- **Spring Boot 3.2.5**, Java 21 (compiled with source/target 21), JTS 1.19.0 for spatial math
- `api/` — REST controller (`SimulationRestController`), DTOs, CORS config. Endpoints under `/api/*`
- `engine/` — `SimulationEngine` (tick loop, vehicle spawning, junction transfers, collision resolution), `SpatialGrid` (spatial indexing), `SimulationConfig` (Spring `@ConfigurationProperties` bound to `sim.*` in `application.properties`)
- `physics/` — `PhysicsEngine` (orchestrates per-vehicle updates), `IDMCalculator` (Intelligent Driver Model), `SeepageLogic` (two-wheeler filtering through gaps)
- `model/` — Core domain: `Vehicle` (double-buffered state with `prepareNextState`/`applyNextState`), `RoadSegment`, `Strip`, `RoadNetwork`, `TrafficSignal`, `SignalPhaseController`, `VehicleType`, `RoadType`
- `ai/` — `VehicleAI` (state machine: CRUISING → APPROACHING → COMMITTED → CROSSING for junction behavior)
- `map/` — `MapParser` (OSM XML parsing), `OsmFetcher` (Overpass API), `SampleNetworkGenerator` (demo network)

### Frontend (`frontend/src/`)
- **Next.js 16** (App Router) with React 19 and React Compiler enabled
- `components/SimulationCanvas.js` — Full-viewport HTML5 Canvas renderer (roads, vehicles, signals)
- `components/StatsPanel.js` — Live statistics overlay
- `components/ControlBar.js` — Playback controls, zoom, map loading
- `hooks/useSimulation.js` — Polls `/api/state` via requestAnimationFrame
- `lib/api.js` — Backend API client (base URL `http://localhost:8080/api`)

### Key Design Patterns
- **Double-buffered vehicle state**: Vehicles compute next state (`nextX`, `nextY`, `nextSpeed`) then apply atomically, allowing safe parallel computation
- **Strip-based lane-free model**: Roads are divided into lateral strips rather than discrete lanes; vehicles can occupy fractional lateral positions (realistic for Indian traffic)
- **IDM + seepage**: Intelligent Driver Model for car-following; separate seepage logic lets two-wheelers filter through gaps between larger vehicles

## Important Notes

- The frontend uses **Next.js 16** which has breaking changes from earlier versions. Always read `node_modules/next/dist/docs/` before modifying Next.js-specific code (see `frontend/AGENTS.md`).
- Backend simulation config is in `backend/src/main/resources/application.properties` — all `sim.*` properties are bound via `SimulationConfig`.
- The backend API serves on port 8080, frontend dev server on port 3000. CORS is configured in `WebConfig.java`.
- `implementation_plan.md` documents 12 known bugs and a UI redesign plan — consult it before working on simulation correctness or frontend layout.
