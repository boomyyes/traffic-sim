# Project Knowledge Item & Master Implementation Plan

This is the persistent knowledge item for Claude Code to serve as the ground-truth context and master plan for continuous development and deployment of the Indian Traffic Simulation Engine.

## 1. Project Architecture & Stack
- **Backend**: Spring Boot 3.2.5, Java 17 (Maven), JTS 1.19.0. Located in `backend/`.
- **Frontend**: Next.js 16, React 19 (React Compiler enabled), anime.js. Located in `frontend/`.
- **Core Pattern**: Double-buffered vehicle state (`prepareNextState`/`applyNextState`), strip-based lane-free model, IDM + seepage logic.
- **API URL**: Frontend points to `NEXT_PUBLIC_API_URL` (defaults to `http://localhost:8080/api`).

## 2. Immediate Implementation Plan (from implementation_plan.md)
Claude Code should refer to the following pending bug fixes and redesign tasks:

### A. Bug Fixes
1. **Junction braking**: In `PhysicsEngine.java:86`, fix the `Math.min` bug by using additive braking (`acceleration += junctionBraking`) before clamping.
2. **Intersection disappearances**: In `SimulationEngine.java`, handle OSM roads without proper junction zones so vehicles don't get deleted upon reaching a road end (reassign targeted transferred vehicles at `localY = 0`).
3. **Collision Resolution**: Improve `resolveWorldCollisions` in `SimulationEngine.java` by using multi-pass resolution (2-3 iterations) to resolve chain reactions, increasing the gap to `1.0m`, and prioritizing the SpatialGrid.
4. **SpatialGrid indexing**: Update `SpatialGrid.java:45` to index by `getWorldX()` and `getWorldY()` instead of road-local coordinates.
5. **Speed Smoothing**: Apply exponential moving average (EMA) in `SimulationEngine.java` for average speed over time (`speed > 0.5 m/s` only).
6. **Vehicle cleanup**: Optimize `vehicles.removeIf` by setting `vehicle.setActive(false)` and batch purging.
7. **Vehicle AI state locks**: Ensure vehicles exit `COMMITTED` state after a timeout or distance if the road lacks a stop line.
8. **Junction turn targets**: Only pick mathematically "left/right" roads that actually share points with the current road.
9. **API payload bloat**: Split `SimulationStateDTO` into `/api/map` (static roads, signals) and `/api/state` (fast-changing vehicle coordinates).
10. **Polling performance**: Suspend `/api/state` polling when Document is hidden, and build an O(1) road lookup map in `SimulationCanvas.js`.

### B. UI Redesign (altis.to-style)
- **Aesthetic**: Deep dark background (`#05070a`), glassmorphism panels (`backdrop-filter: blur()`), minimal floating HUD instead of a fixed sidebar.
- **Controls**: Soft glows on components, floating bottom control bar for playback and tools.
- **Features**: Implement a red-to-green gradient arc indicator showing live average simulation speed.

## 3. Deployment Strategy
The deployment configs are already scaffolded. Claude Code must follow these steps to execute deployment:

### Backend Deployment (Render)
- Config is defined in `render.yaml` and `backend/Dockerfile` (using Java 17).
- Application targets Render Web Service (`https://github.com/boomyyes/traffic-sim`).
- **Environment Variable Requirement**: When creating the Web Service on Render, ensure the `CORS_ALLOWED_ORIGINS` environment variable is set to the Vercel frontend URL (e.g., `https://traffic-sim-nine.vercel.app/`), which allows `WebConfig.java` to whitelist the frontend domain.

### Frontend Deployment (Vercel)
- Navigate to `frontend/` directory. Next.js settings are contained in `next.config.mjs` and `package.json`.
- Project is ready for Vercel out-of-the-box (`npm run build`).
- **Environment Variable Requirement**: In your Vercel project settings, set `NEXT_PUBLIC_API_URL` to point to the backend's Render HTTPS URL (e.g., `https://indian-traffic-engine.onrender.com/api`).

## Execution Protocol for Claude Code
1. Read this item and the `implementation_plan.md` to establish context.
2. Knock out bugs systematically, compiling (`mvn compile`) after backend changes.
3. Apply UI changes to Next.js (`npm run build`), verifying with `npm run dev`.
4. Run `run.bat` (handles both) to test interactions locally.
5. Merge changes for CI/CD deployments.
