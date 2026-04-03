# Bug Fix & UI Redesign Plan

## Audit Summary

Read every source file (14 files, ~3500 lines). Found **12 bugs** and studied [altis.to](https://altis.to/) for the UI redesign.

---

## Part A: Bug Fixes

### BUG 1 — Junction braking is a no-op ⚠️ CRITICAL
**File:** [PhysicsEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/physics/PhysicsEngine.java#L86)
**Line 86:** `acceleration = Math.min(acceleration, acceleration + junctionBraking);`

When `junctionBraking` is negative (braking), `acceleration + junctionBraking < acceleration`, so `Math.min` returns the braked value — BUT this is only correct when `acceleration` is also negative. When `acceleration` is positive, `Math.min(+2.0, +2.0 + (-4.0))` = `Math.min(+2.0, -2.0)` = `-2.0`. This works by accident for some cases but is semantically wrong and confusing.

**The real issue:** When `junctionBraking == 0` (no threat), `Math.min(acceleration, acceleration + 0) == acceleration` — this is fine. But the intended semantics are unclear and the code is fragile.

**Fix:** Replace with `acceleration += junctionBraking;` (additive braking), then clamp afterwards.

---

### BUG 2 — Vehicles disappear at intersections ⚠️ CRITICAL
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L268-L316)

**Root cause:** The junction transfer checks `roadNetwork.isInJunction(worldX, worldY)` — but on OSM maps with 3856 roads, many roads DON'T have junction zones defined (junctionZones is empty or doesn't cover the actual intersection areas). Vehicles with a `targetRoadId` never get transferred, reach the end of their road, and are removed by `removeExitedVehicles()`.

Additionally, `processJunctionTransfers()` sets `targetLocalY = Math.min(targetLocalY, targetRoad.getLength() - v.getLength())`. If the computed `junctionExitY` is past the target road length, the vehicle gets placed at the end and immediately removed next tick.

**Fix:**
1. When a vehicle with `targetRoadId >= 0` exits its road without being transferred (reaches end), gracefully reassign it to the target road at position 0 rather than deleting it.
2. Clamp transferred vehicles to a safe range `[vehicleLength, roadLength - vehicleLength]`.

---

### BUG 3 — Vehicle overlapping (collision resolution too weak) ⚠️ CRITICAL
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L441-L493)

**Root causes:**
1. `resolveWorldCollisions()` only handles **same-road** overlaps. Cross-road overlaps at junctions are completely ignored.
2. The resolution pushes the follower back by `0.5m`, but doesn't account for **chain reactions** — pushing vehicle B back may cause it to overlap with vehicle C behind it. A single pass is insufficient.
3. The `O(N²)` nested loop over ALL active vehicles is slow and the spatial grid is never used for collision resolution.

**Fix:**
1. Run collision resolution in **multiple passes** (2-3 iterations) to resolve chain reactions.
2. Increase the separation gap from `0.5m` to `1.0m`.
3. Use the spatial grid for neighbor lookups instead of O(N²).

---

### BUG 4 — SpatialGrid indexes by road-local coordinates, not world coordinates
**File:** [SpatialGrid.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SpatialGrid.java#L45)
**Line 45:** `long key = cellKey(v.getX(), v.getY());`

`v.getX()` and `v.getY()` are **road-local** coordinates (lateral position and longitudinal position along a road). The spatial grid is supposed to accelerate **world-space** neighbor lookups, but it indexes by road-local coords which are meaningless across different roads.

**Fix:** Use `v.getWorldX()` and `v.getWorldY()` instead. The world coords are already computed before `rebuild()` is called (SimulationEngine line 112).

---

### BUG 5 — Average speed calculation is naive
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L542-L547)

Current: Simple arithmetic mean of all vehicle speeds. This is misleading because:
- Stopped vehicles (at signals) drag the average way down
- A single bike going 80 km/h skews it up
- No smoothing — the value jitters wildly each tick

**Fix:**
1. Use an **exponential moving average (EMA)** with α=0.05 for temporal smoothing.
2. Exclude stopped vehicles (`speed < 0.5 m/s`) from the calculation — they're queued, not "traffic flow".
3. Also expose `minSpeed` and `maxSpeed` for the frontend's gradient indicator.

---

### BUG 6 — `removeExitedVehicles` doesn't deactivate, it removes
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L495-L501)

`vehicles.removeIf(...)` on a `CopyOnWriteArrayList` is extremely expensive — it copies the entire backing array for each removal. On every tick with 500 vehicles, this causes GC pressure.

**Fix:** Set `v.setActive(false)` instead of removing. Periodically batch-purge inactive vehicles (e.g., every 100 ticks).

---

### BUG 7 — VehicleAI committed state never exits on some roads
**File:** [VehicleAI.java](file:///c:/mini-project/backend/src/main/java/com/traffic/ai/VehicleAI.java#L80-L84)

```java
case COMMITTED:
    if (!nearJunction && pastStopLine) {
        transitionTo(State.CRUISING);
    }
```

On roads with **no signals** (most OSM roads), `isPastStopLine()` returns `false` (no signals to be past). A vehicle that enters COMMITTED state on such a road will **never** exit it, maintaining the forced minimum speed and zero lateral movement forever.

**Fix:** Also exit COMMITTED after a timeout (e.g., 5 seconds) or when the vehicle is far enough from the junction zone.

---

### BUG 8 — Parallel stream mutation of Vehicle state
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L121-L128)

```java
vehicles.parallelStream()
    .filter(Vehicle::isActive)
    .forEach(vehicle -> {
        physicsEngine.updateVehicle(vehicle, allVehicles, road, dt, roadNetwork);
    });
```

`updateVehicle` calls `vehicle.prepareNextState()` which writes to `nextX`, `nextY`, `nextSpeed` — this is safe due to double-buffering. **BUT** `VehicleAI.update()` mutates `state`, `stateTimer`, and `laneChangeProgress` directly without synchronization. Two threads could process the same vehicle's AI if the stream scheduler overlaps work.

**Fix:** The `allVehicles` list is read-only (used for neighbor lookup). The per-vehicle AI mutation is safe because each vehicle's `forEach` lambda only touches its own AI. However, the `allVehicles` list iteration inside `findLeaderInfo` and `computeJunctionBraking` reads other vehicles' `worldX/worldY` which may not yet be set if the world-coord caching step (step 3) runs concurrently. This is fine because step 3 is sequential. **No fix needed** — documenting for clarity.

---

### BUG 9 — `assignTurnTarget` picks random roads, not connected roads
**File:** [SimulationEngine.java](file:///c:/mini-project/backend/src/main/java/com/traffic/engine/SimulationEngine.java#L200-L253)

The function iterates ALL roads in the network to find left/right turn candidates by heading difference. On an OSM map with 3856 roads, this picks roads that are geometrically "to the left" or "right" but may be **physically hundreds of meters away**. This causes vehicles to get assigned target roads they can never reach, leading to them disappearing (Bug 2).

**Fix:** Only consider roads whose start/end points are within a threshold distance of the current road's end point (e.g., 50m). These are actually connected at the junction.

---

### BUG 10 — DTO serializes roads on every poll (3856 roads × 20 polls/sec)
**File:** [SimulationStateDTO.java](file:///c:/mini-project/backend/src/main/java/com/traffic/api/SimulationStateDTO.java#L67-L148)

Every `/api/state` call serializes all 3856 roads, 18 signals, and all junction zones — even though roads/signals/junctions are static data that never change. This wastes ~200KB per response.

**Fix:** Split into two endpoints:
- `GET /api/map` — returns roads, signals, junctions (called once on load)
- `GET /api/state` — returns only vehicles + stats (called 20x/sec)

---

### BUG 11 — Frontend polling continues even when tab is hidden
**File:** [useSimulation.js](file:///c:/mini-project/frontend/src/hooks/useSimulation.js)

`requestAnimationFrame` naturally pauses when the tab is hidden, but the fetch inside it uses a time-based guard that will fire a burst of fetches when the tab becomes visible again (all accumulated time).

**Fix:** Add `document.hidden` check and use `requestAnimationFrame` properly — the RAF itself handles throttling when hidden.

---

### BUG 12 — Frontend O(N) road lookup per vehicle per frame
**File:** [SimulationCanvas.js](file:///c:/mini-project/frontend/src/components/SimulationCanvas.js#L342)

```js
const road = roads.find((r) => r.id === v.roadSegmentId);
```

For 500 vehicles × 3856 roads, this is **~1.9M comparisons per frame**. At 60fps, this is 115M comparisons/second.

**Fix:** Build a `Map<id, road>` once when roads are loaded, use `O(1)` lookup.

---

## Part B: UI Redesign (altis.to-inspired)

### Design Inspiration from altis.to
- **Deep dark background** (`#05070a`) — makes simulation elements pop
- **HUD-style layout** — corner-anchored floating panels, not sidebar
- **Glassmorphism** — semi-transparent panels with `backdrop-filter: blur()`
- **Bloom/glow effects** — soft glows on active elements
- **Minimalist controls** — sliders and buttons are clean, borderless
- **Typography** — Inter font, ultra-light weights for titles, mono for data

### Proposed Layout
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

### Key UI Changes

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

### Speed Gradient Indicator
- A circular arc gauge in the top-right corner
- Gradient from **red** (0 km/h) → **yellow** (30 km/h) → **green** (60+ km/h)
- Needle or fill that animates smoothly with anime.js
- Shows numeric avg speed inside

---

## Verification Plan

### Automated
1. `mvn compile` — backend compiles after all fixes
2. `npx next build` — frontend builds after redesign
3. Start backend, `curl /api/state` — verify response structure

### Manual (Browser)
1. Start simulation → verify no vehicles disappear at intersections
2. Run for 2 minutes → verify minimal vehicle overlapping
3. Check speed gradient responds smoothly to simulation state
4. Verify HUD panels have glassmorphism effect
5. Verify canvas is full-viewport and responsive
