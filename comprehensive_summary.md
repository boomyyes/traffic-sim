# Indian Traffic Simulation Engine — Comprehensive Project Summary

> **Project**: Indian Traffic Simulation Engine  
> **Repository**: [github.com/boomyyes/traffic-sim](https://github.com/boomyyes/traffic-sim)  
> **Directory**: `c:\mini-project`  
> **Date Range**: February 27, 2026 → April 4, 2026  
> **Total Conversations**: 8 (including this one)

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Conversation Timeline](#conversation-timeline)
   - [Conversation 1: Project Exploration](#conversation-1-project-exploration-feb-27-2026)
   - [Conversation 2: Building the Entire Engine](#conversation-2-building-the-entire-engine-mar-9-11-2026)
   - [Conversation 3: Program Start Simplification](#conversation-3-program-start-simplification-mar-11-2026)
   - [Conversation 4: README Creation](#conversation-4-readme-creation-mar-13-2026)
   - [Conversation 5: Git Repository Setup](#conversation-5-git-repository-setup)
   - [Conversation 6: JavaFX → Next.js Migration + Bug Audit](#conversation-6-javafx--nextjs-migration--bug-audit)
3. [Current Architecture](#current-architecture)
4. [Full Implementation Plan (Bug Fixes & UI Redesign)](#full-implementation-plan-bug-fixes--ui-redesign)
5. [What Was Built — File Inventory](#what-was-built--file-inventory)
6. [How to Run](#how-to-run)

---

## Project Overview

The **Indian Traffic Simulation Engine** is a microscopic traffic simulator using **strip-based lane-free logic** to model heterogeneous Indian traffic. Unlike Western simulators that use fixed lane-based models, this engine divides roads into 0.5m lateral **strips**, allowing vehicles of different sizes (bikes, auto-rickshaws, cars, buses, trucks) to share road space realistically — including the characteristic "seepage" behavior where two-wheelers filter through gaps between larger vehicles.

### Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Backend Language | Java | 21 |
| Backend Framework | Spring Boot | 3.2.5 |
| Spatial Math | JTS | 1.19.0 |
| Frontend Framework | Next.js (App Router) | 16 |
| Frontend Rendering | HTML5 Canvas | — |
| Animations | anime.js | v4 |
| Styling | Vanilla CSS (dark theme) | — |
| Build (Backend) | Maven | — |
| Build (Frontend) | npm | — |

---

## Conversation Timeline

### Conversation 1: Project Exploration (Feb 27, 2026)

> **ID**: `161394f7-251f-4542-a331-d9ebf0b9fed2`  
> **User Request**: "Explore the project structure and give me a general understanding."

**What happened**: The user asked to understand the current state of the project. At this point, the `c:\mini-project` directory was essentially empty — there was no code, no configuration, nothing. The conversation explored the empty directory and established that the project needed to be built from scratch.

**Outcome**: Understanding that the project was a blank slate ready for scaffolding.

---

### Conversation 2: Building the Entire Engine (Mar 9–11, 2026)

> **ID**: `dfd8d74b-db14-4472-a8f3-5a3909dc916e`  
> **User Requests** (multiple across sessions):
> 1. Build the full simulation engine from scratch
> 2. Add VehicleAI with finite state machine and personality traits
> 3. Create a multi-intersection Nerul demo map
> 4. Implement in-house OSM map fetching via the Overpass API
> 5. Fix vehicle rendering rotation on angled OSM roads
> 6. Fix the "creeping red light" bug

This was the **longest and most productive conversation**, spanning multiple days. Here's everything that was built:

#### Phase 1: Initial Engine Creation

**Implementation Plan** created from scratch for a **Java 21 + Spring Boot 3.2 + JavaFX 21** application:

- **Project scaffolding**: `pom.xml` with Spring Boot 3.2.5, JavaFX 21, JTS 1.19 dependencies; `application.properties` with configurable simulation parameters (`sim.tick-rate-ms=100`, `sim.strip-width=0.5`, etc.)
- **Model layer** (`com.traffic.model`):
  - `Vehicle.java` — Mutable agent with double-buffered state (`x/y/speed` + `nextX/nextY/nextSpeed`), `prepareNextState()`/`commitState()` methods
  - `VehicleType.java` — Enum: BIKE, AUTO, CAR, BUS, TRUCK with dimension presets
  - `RoadSegment.java` — Road with auto-generated strips from width
  - `Strip.java` — 0.5m lateral road division with occupancy tracking
  - `RoadNetwork.java` — Network container with junction zone management
- **Physics layer** (`com.traffic.physics`):
  - `IDMCalculator.java` — Intelligent Driver Model acceleration formula
  - `SeepageLogic.java` — Lateral gap-seeking for two-wheelers filtering through traffic
  - `PhysicsEngine.java` — Orchestrates IDM + Seepage per vehicle update
- **Engine layer** (`com.traffic.engine`):
  - `SimulationEngine.java` — Tick loop, vehicle spawning (50% bikes, 15% autos, 20% cars, 10% buses, 5% trucks), junction transfers, collision resolution, `parallelStream()` for concurrent updates
  - `SpatialGrid.java` — Grid-based O(1) neighbor lookup
  - `SimulationConfig.java` — Spring `@ConfigurationProperties` binding
- **Map layer** (`com.traffic.map`):
  - `MapParser.java` — OSM XML parser reading `<node>` and `<way>` tags
  - `SampleNetworkGenerator.java` — Hardcoded demo road network
- **UI layer** (`com.traffic.ui`):
  - `TrafficApp.java` — JavaFX ↔ Spring Boot bridge
  - `TrafficCanvas.java` — 2D Canvas renderer (roads, vehicles, signals)
  - `DashboardController.java` — Stats panel + controls

**Result**: 17 source files, ~3500 lines, fully compiling and running.

#### Phase 2: VehicleAI + Multi-Intersection Map

**User**: Wanted smarter vehicle behavior and a more complex map.

**What was built**:
- **VehicleAI** (`com.traffic.ai`):
  - Finite state machine: `CRUISING` → `APPROACHING_JUNCTION` → `QUEUED` → `COMMITTED` → `CROSSING`
  - Individualized personality traits: `aggressiveness`, `patience`, `laneRespect`
  - Turn queue bypass: Turning vehicles naturally pull toward the road edge
  - Junction braking: Speed-dependent collision avoidance for cross-traffic
- **4-intersection Nerul map** in `SampleNetworkGenerator`
- Tightened turn discipline (reduced wrong-lane turns to 3%)
- Smoothed vehicle movement (lateral + acceleration)
- Updated `TrafficCanvas` for larger map viewport

#### Phase 3: In-House OSM Map Fetching

**User**: "I want to fetch real-world map data from OpenStreetMap directly in the application."

**Problem**: Parsing raw Geofabrik country-level OSM files (5GB+) causes `OutOfMemoryError`.

**Solution proposed and implemented** — the **Overpass API approach**:
- Created [OsmFetcher.java](file:///c:/mini-project/backend/src/main/java/com/traffic/map/OsmFetcher.java) using Java's native `HttpClient`
- Queries Overpass API with a precise bounding box (e.g., GS Road, Guwahati)
- Returns lightweight XML that the existing `MapParser` can process directly
- Added dynamic canvas scaling: `computeScaleAndOffset(RoadNetwork)` automatically fits any downloaded map to the window

**User conversation excerpt**:
> **User**: "Can we fetch the map data in-house instead of downloading gigabyte files?"  
> **AI**: Proposed the Overpass API approach — 100% Java-native, no external tools needed.  
> **User**: Approved the plan.  
> **AI**: Implemented `OsmFetcher`, wired it up, and made the UI zoom dynamic.

#### Phase 4: Bug Fixes (within same conversation)

1. **Vehicle rotation on slanted roads**: Previously vehicles appeared to "strafe" sideways on diagonal roads. Fixed using JavaFX `gc.translate()` and `gc.rotate()` to match the road's exact mathematical heading.

2. **"Creeping" red light bug**: Vehicles would inch forward at red lights and then suddenly shoot across. Fixed by treating the stop line as an absolute physical barrier until the vehicle's *center* crosses the threshold.

#### Tasks Completed (all checked off ✅):
- [x] Create VehicleAI FSM with personality traits
- [x] Add VehicleAI to Vehicle class
- [x] Update RoadNetwork for multiple junctions
- [x] Create 4-intersection Nerul map in SampleNetworkGenerator
- [x] Integrate VehicleAI into PhysicsEngine
- [x] Tighten turn discipline (3% wrong-lane)
- [x] Smooth vehicle movement (lateral + acceleration)
- [x] Update TrafficCanvas for larger map
- [x] Compile and test
- [x] Reduce red light runners
- [x] Implement junction braking for cross-road vehicles
- [x] Implement in-house OSM map fetching (Overpass API)
- [x] Add dynamic canvas scaling for custom OSM bounds
- [x] Fix vehicle rendering rotation to match angled OSM roads
- [x] Fix creeping bug where vehicles shoot past stop line at red lights

---

### Conversation 3: Program Start Simplification (Mar 11, 2026)

> **ID**: `65d8f515-4bb3-4af4-8510-b7a25904bf4d`  
> **User Request**: "Simplify the program's startup process."

**What happened**: The user wanted a streamlined way to launch the application. The conversation examined the current project structure and build configuration, and implemented a simpler startup flow. This was a brief, targeted session focused on developer experience.

---

### Conversation 4: README Creation (Mar 13, 2026)

> **ID**: `7498f51d-be4a-43e4-a3f2-47554510402d`  
> **User Request**: "Create a README file for the Git repository."

**What happened**: Created [README.md](file:///c:/mini-project/README.md) with:
- Project description and architecture diagram
- Tech stack breakdown (Backend: Java 21, Spring Boot 3.2.5, JTS; Frontend: Next.js, Canvas, anime.js)
- Getting started guide with prerequisites (JDK 21+, Maven, Node.js 18+)
- Quick start (`run.bat`) and manual start instructions
- Complete API endpoint table (7 endpoints)
- Controls reference table
- Project structure breakdown

---

### Conversation 5: Git Repository Setup

> **ID**: `a84cfb27-e7c3-4f97-b17e-2639c65db2fa`  
> **User Request**: "Set up the project for GitHub."

**What happened**:
- [x] Initialized Git repository
- [x] Created [.gitignore](file:///c:/mini-project/.gitignore) — excludes build artifacts, `node_modules`, `.next/`, OSM cache files, IDE files
- [x] Added all source files to Git
- [x] Created initial commit
- [x] Added remote origin: `https://github.com/boomyyes/traffic-sim.git`
- [ ] Push to GitHub (left for user to do with credentials)

---

### Conversation 6: JavaFX → Next.js Migration + Bug Audit

> **ID**: `6f2d603e-3444-4bf9-bf81-1ce046e8662d`  
> **User Request**: Migrate the frontend from JavaFX to a modern web stack (Next.js + anime.js), and audit the codebase for bugs.

This was a **major architectural change** — converting the monolithic JavaFX desktop app into a two-process web architecture.

#### Part 1: Next.js Migration

**Implementation Plan** created and approved:

**Backend changes (4 new files)**:
- [SimulationRestController.java](file:///c:/mini-project/backend/src/main/java/com/traffic/api/SimulationRestController.java) — 7 REST endpoints (`/api/state`, `/api/start`, `/api/stop`, `/api/reset`, `/api/config`, `/api/upload-osm`, `/api/load-nerul`)
- [SimulationStateDTO.java](file:///c:/mini-project/backend/src/main/java/com/traffic/api/SimulationStateDTO.java) — Full state DTO aggregating vehicles, roads, signals, stats, junction zones
- [WebConfig.java](file:///c:/mini-project/backend/src/main/java/com/traffic/api/WebConfig.java) — CORS configuration allowing `http://localhost:3000`
- Modified [MainApplication.java](file:///c:/mini-project/backend/src/main/java/com/traffic/MainApplication.java) — Web server entry point with `CommandLineRunner` auto-init

**Frontend (8 new files in `frontend/`)**:
- [SimulationCanvas.js](file:///c:/mini-project/frontend/src/components/SimulationCanvas.js) — HTML5 Canvas renderer (direct port of TrafficCanvas.java)
- [StatsPanel.js](file:///c:/mini-project/frontend/src/components/StatsPanel.js) — Live statistics sidebar
- [ControlBar.js](file:///c:/mini-project/frontend/src/components/ControlBar.js) — Playback + zoom controls with anime.js button animations
- [page.js](file:///c:/mini-project/frontend/src/app/page.js) — Dashboard layout
- [useSimulation.js](file:///c:/mini-project/frontend/src/hooks/useSimulation.js) — `requestAnimationFrame`-based state polling hook
- [api.js](file:///c:/mini-project/frontend/src/lib/api.js) — Backend API client
- [globals.css](file:///c:/mini-project/frontend/src/app/globals.css) — Dark theme design system
- [layout.js](file:///c:/mini-project/frontend/src/app/layout.js) — Root layout

**Infrastructure**:
- [run.bat](file:///c:/mini-project/run.bat) — Launches both backend and frontend in separate windows
- Updated [.gitignore](file:///c:/mini-project/.gitignore) for `frontend/node_modules/`, `frontend/.next/`, etc.
- Updated [README.md](file:///c:/mini-project/README.md)

**Verification**:
- API test: `curl http://localhost:8080/api/state` → returned `running=False, roads=3856, vehicles=0, signals=18`
- Browser verification: Initial load with 3856 roads rendered, simulation running with live stats, pause/resume working

#### Part 2: Deep Code Audit — 12 Bugs Found

Read every source file (14 files, ~3500 lines) and also studied [altis.to](https://altis.to/) for UI redesign inspiration. Found **12 bugs**:

| # | Bug | Severity | Status |
|---|-----|----------|--------|
| 1 | Junction braking is a no-op | ⚠️ CRITICAL | Planned |
| 2 | Vehicles disappear at intersections | ⚠️ CRITICAL | Planned |
| 3 | Vehicle overlapping (collision resolution too weak) | ⚠️ CRITICAL | Planned |
| 4 | SpatialGrid indexes by road-local coords, not world coords | Medium | Planned |
| 5 | Average speed calculation is naive | Medium | Planned |
| 6 | `removeExitedVehicles` causes GC pressure | Medium | Planned |
| 7 | VehicleAI committed state never exits on some roads | Medium | Planned |
| 8 | Parallel stream mutation of Vehicle state | Low | No fix needed (documented) |
| 9 | `assignTurnTarget` picks random roads, not connected ones | ⚠️ CRITICAL | Planned |
| 10 | DTO serializes roads on every poll (200KB/response) | Performance | Planned |
| 11 | Frontend polling continues when tab is hidden | Medium | Planned |
| 12 | Frontend O(N) road lookup per vehicle per frame | Performance | Planned |

---

## Current Architecture

```
mini-project/
├── backend/                    Java/Spring Boot simulation engine + REST API
│   └── src/main/java/com/traffic/
│       ├── MainApplication.java           Spring Boot entry point
│       ├── ai/
│       │   └── VehicleAI.java             FSM: CRUISING→APPROACHING→COMMITTED→CROSSING
│       ├── api/
│       │   ├── SimulationRestController.java   7 REST endpoints
│       │   ├── SimulationStateDTO.java         Full state DTO
│       │   └── WebConfig.java                  CORS config
│       ├── engine/
│       │   ├── SimulationEngine.java      Tick loop, spawning, collision resolution
│       │   ├── SpatialGrid.java           Grid-based neighbor lookup
│       │   └── SimulationConfig.java      @ConfigurationProperties
│       ├── map/
│       │   ├── MapParser.java             OSM XML parser
│       │   ├── OsmFetcher.java            Overpass API client
│       │   └── SampleNetworkGenerator.java Demo network
│       ├── model/
│       │   ├── Vehicle.java               Double-buffered agent state
│       │   ├── VehicleType.java           BIKE, AUTO, CAR, BUS, TRUCK
│       │   ├── RoadSegment.java           Road with strips
│       │   ├── Strip.java                 0.5m lateral division
│       │   ├── RoadNetwork.java           Network container
│       │   ├── TrafficSignal.java         Signal with phases
│       │   ├── SignalPhaseController.java Phase timing
│       │   └── RoadType.java             Road classification
│       ├── physics/
│       │   ├── PhysicsEngine.java         Orchestrates IDM + Seepage
│       │   ├── IDMCalculator.java         Intelligent Driver Model
│       │   └── SeepageLogic.java          Lateral gap-seeking
│       └── ui/                            (Legacy JavaFX — retained for reference)
│           ├── TrafficApp.java
│           ├── TrafficCanvas.java
│           └── DashboardController.java
├── frontend/                   Next.js web dashboard
│   └── src/
│       ├── app/
│       │   ├── page.js                    Dashboard layout
│       │   ├── layout.js                  Root layout
│       │   └── globals.css                Dark theme CSS
│       ├── components/
│       │   ├── SimulationCanvas.js         HTML5 Canvas renderer
│       │   ├── StatsPanel.js              Live statistics
│       │   └── ControlBar.js              Playback controls
│       ├── hooks/
│       │   └── useSimulation.js           RAF-based state polling
│       └── lib/
│           └── api.js                     Backend API client
├── run.bat                     Launches both processes
├── README.md                   Project documentation
├── CLAUDE.md                   AI assistant context file
├── .gitignore                  Git ignore rules
└── implementation_plan.md      Bug fix & UI redesign plan
```

### Key Design Patterns

1. **Double-buffered vehicle state**: Vehicles compute `nextX/nextY/nextSpeed` then apply atomically — prevents race conditions in parallel computation
2. **Strip-based lane-free model**: Roads divided into 0.5m strips rather than lanes; vehicles occupy fractional lateral positions (realistic for Indian traffic)
3. **IDM + Seepage**: Intelligent Driver Model for car-following; separate seepage logic lets two-wheelers filter through gaps
4. **Two-process web architecture**: Spring Boot REST API (port 8080) + Next.js frontend (port 3000)

---

## Full Implementation Plan (Bug Fixes & UI Redesign)

> [!NOTE]
> The full implementation plan is also stored at [implementation_plan.md](file:///c:/mini-project/implementation_plan.md) in the project root.

### Part A: Bug Fixes (12 Bugs)

#### BUG 1 — Junction braking is a no-op ⚠️ CRITICAL
**File:** [PhysicsEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/physics/PhysicsEngine.java#L86)

```java
// Current (broken):
acceleration = Math.min(acceleration, acceleration + junctionBraking);
// When junctionBraking is negative, Math.min returns the braked value —
// but the semantics are confusing and fragile.

// Fix:
acceleration += junctionBraking;
// Then clamp afterwards.
```

---

#### BUG 2 — Vehicles disappear at intersections ⚠️ CRITICAL
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L268-L316)

**Root cause**: On OSM maps with 3856 roads, many roads don't have junction zones defined. Vehicles with a `targetRoadId` never get transferred, reach the end of their road, and are deleted by `removeExitedVehicles()`.

**Fix**: When a vehicle with `targetRoadId >= 0` exits its road without being transferred, gracefully reassign it to the target road at position 0. Clamp to `[vehicleLength, roadLength - vehicleLength]`.

---

#### BUG 3 — Vehicle overlapping (collision resolution too weak) ⚠️ CRITICAL
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L441-L493)

**Root causes**: Only same-road overlaps handled; single-pass resolution; O(N²) loop ignoring spatial grid.

**Fix**: Multiple collision resolution passes (2-3), increase gap from 0.5m to 1.0m, use spatial grid.

---

#### BUG 4 — SpatialGrid indexes by road-local coordinates
**File:** [SpatialGrid.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SpatialGrid.java#L45)

**Fix**: Use `v.getWorldX()` / `v.getWorldY()` instead of `v.getX()` / `v.getY()`.

---

#### BUG 5 — Average speed calculation is naive
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L542-L547)

**Fix**: Exponential moving average (α=0.05), exclude stopped vehicles (`speed < 0.5 m/s`), expose `minSpeed`/`maxSpeed`.

---

#### BUG 6 — `removeExitedVehicles` causes GC pressure
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L495-L501)

**Fix**: Set `v.setActive(false)` instead of removing; batch-purge every 100 ticks.

---

#### BUG 7 — VehicleAI committed state never exits on some roads
**File:** [VehicleAI.java](file:///c:/mini-project/backend/src/main/java/com/traffic/ai/VehicleAI.java#L80-L84)

**Fix**: Exit COMMITTED after 5-second timeout or when far from junction zone.

---

#### BUG 8 — Parallel stream mutation of Vehicle state
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L121-L128)

**Status**: ✅ No fix needed — documented for clarity. The per-vehicle AI mutation is safe because each `forEach` lambda only touches its own vehicle.

---

#### BUG 9 — `assignTurnTarget` picks random roads, not connected roads ⚠️ CRITICAL
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L200-L253)

**Fix**: Only consider roads whose start/end points are within 50m of the current road's end point.

---

#### BUG 10 — DTO serializes roads on every poll
**File:** [SimulationStateDTO.java](file:///c:/mini-project/backend/src/main/java/com/traffic/api/SimulationStateDTO.java#L67-L148)

**Fix**: Split into `GET /api/map` (called once) and `GET /api/state` (called 20x/sec, vehicles + stats only).

---

#### BUG 11 — Frontend polling continues when tab is hidden
**File:** [useSimulation.js](file:///c:/mini-project/frontend/src/hooks/useSimulation.js)

**Fix**: Add `document.hidden` check.

---

#### BUG 12 — Frontend O(N) road lookup per vehicle per frame
**File:** [SimulationCanvas.js](file:///c:/mini-project/frontend/src/components/SimulationCanvas.js#L342)

**Fix**: Build `Map<id, road>` once, use O(1) lookup (currently ~1.9M comparisons per frame at 500 vehicles × 3856 roads).

---

### Part B: UI Redesign (altis.to-inspired)

#### Design Inspiration
Studied [altis.to](https://altis.to/) for modern simulation UI patterns:
- Deep dark background (`#05070a`)
- HUD-style floating panels (corner-anchored, not sidebar)
- Glassmorphism (`backdrop-filter: blur()`)
- Bloom/glow effects on active elements
- Inter font, ultra-light weights for titles, mono for data

#### Proposed Layout
```
┌──────────────────────────────────────────┐
│ ◉ Traffic Sim     [stats]  [speed grad]  │  ← Top HUD
│                                          │
│          ┌──────────────────┐            │
│          │                  │            │
│          │   FULL-SCREEN    │            │
│          │     CANVAS       │            │
│          │                  │            │
│          └──────────────────┘            │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  ▶  ⏸  🔄  |  −  +  🔍  |  📂  📍 │  │  ← Bottom glass bar
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

#### Key UI Changes

| Current | Redesigned |
|---------|-----------|
| Fixed sidebar for stats | Top-left HUD overlay with glassmorphism |
| Static bottom bar | Floating bottom glass bar with blur |
| Plain dark gray canvas bg | Deep space-dark bg (`#05070a`) |
| No speed indicator | **Red-to-green gradient arc** showing avg speed |
| Simple colored rectangles for vehicles | Rounded rects with glow/trail effects |
| No entrance animation | Fade-in stagger on load |
| Canvas fixed 1000×700 | **Full-viewport responsive** canvas |
| 210px sidebar always visible | Auto-hiding stat overlays |

#### Speed Gradient Indicator
- Circular arc gauge in the top-right corner
- Gradient: **red** (0 km/h) → **yellow** (30 km/h) → **green** (60+ km/h)
- Smooth animation with anime.js
- Numeric avg speed displayed inside

---

## What Was Built — File Inventory

### Backend (17+ files)

| Package | File | Purpose |
|---------|------|---------|
| root | `MainApplication.java` | Spring Boot entry + auto-init |
| `ai` | `VehicleAI.java` | FSM with personality traits |
| `api` | `SimulationRestController.java` | 7 REST endpoints |
| `api` | `SimulationStateDTO.java` | Full state serialization |
| `api` | `WebConfig.java` | CORS config |
| `engine` | `SimulationEngine.java` | Tick loop, spawning, collisions |
| `engine` | `SpatialGrid.java` | Grid-based neighbor lookup |
| `engine` | `SimulationConfig.java` | Spring config binding |
| `map` | `MapParser.java` | OSM XML parsing |
| `map` | `OsmFetcher.java` | Overpass API client |
| `map` | `SampleNetworkGenerator.java` | Demo network |
| `model` | `Vehicle.java` | Double-buffered agent |
| `model` | `VehicleType.java` | Vehicle type enum |
| `model` | `RoadSegment.java` | Road with strips |
| `model` | `Strip.java` | 0.5m lateral strip |
| `model` | `RoadNetwork.java` | Network container |
| `model` | `TrafficSignal.java` | Signal with phases |
| `model` | `SignalPhaseController.java` | Phase timing |
| `model` | `RoadType.java` | Road classification |
| `physics` | `PhysicsEngine.java` | IDM + Seepage orchestrator |
| `physics` | `IDMCalculator.java` | Intelligent Driver Model |
| `physics` | `SeepageLogic.java` | Lateral gap-seeking |
| `ui` | `TrafficApp.java` | Legacy JavaFX bridge |
| `ui` | `TrafficCanvas.java` | Legacy 2D renderer |
| `ui` | `DashboardController.java` | Legacy stats + controls |

### Frontend (8 files)

| Directory | File | Purpose |
|-----------|------|---------|
| `app` | `page.js` | Dashboard layout |
| `app` | `layout.js` | Root layout |
| `app` | `globals.css` | Dark theme CSS |
| `components` | `SimulationCanvas.js` | HTML5 Canvas renderer |
| `components` | `StatsPanel.js` | Live statistics |
| `components` | `ControlBar.js` | Playback controls |
| `hooks` | `useSimulation.js` | RAF state polling |
| `lib` | `api.js` | Backend API client |

### Root Files

| File | Purpose |
|------|---------|
| `run.bat` | Launches both backend + frontend |
| `README.md` | Project documentation |
| `CLAUDE.md` | AI assistant context |
| `.gitignore` | Git ignore rules |
| `implementation_plan.md` | Bug fix & UI redesign plan |

---

## How to Run

### Quick Start (Windows)
```cmd
run.bat
```

### Manual Start

**Backend** (Terminal 1):
```bash
cd backend
mvn spring-boot:run          # http://localhost:8080
```

**Frontend** (Terminal 2):
```bash
cd frontend
npm install                  # first time only
npm run dev                  # http://localhost:3000
```

Then open **http://localhost:3000** in your browser.

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/state` | GET | Full simulation snapshot |
| `/api/start` | POST | Start simulation |
| `/api/stop` | POST | Pause simulation |
| `/api/reset` | POST | Reset to sample network |
| `/api/config` | GET | Simulation config |
| `/api/upload-osm` | POST | Upload OSM map file |
| `/api/load-nerul` | POST | Load Nerul demo |

### Controls

| Action | Control |
|--------|---------|
| Start simulation | ▶ Start button |
| Pause | ⏸ Pause button |
| Reset | 🔄 Reset button |
| Zoom | Scroll wheel or +/- buttons |
| Pan | Click and drag |
| Fit view | 🔍 Fit button |
| Load map | 📂 Load OSM button |
