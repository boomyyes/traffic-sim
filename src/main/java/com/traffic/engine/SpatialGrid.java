package com.traffic.engine;

import com.traffic.model.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grid-based spatial index for fast neighbor lookups.
 * 
 * Divides the simulation space into cells of fixed size.
 * To find neighbors, a vehicle only checks its own cell and the 8 surrounding
 * cells.
 * This reduces neighbor queries from O(N²) to approximately O(N).
 */
public class SpatialGrid {

    private final double cellSize;
    private final int gridWidth;
    private final int gridHeight;
    private final ConcurrentHashMap<Long, List<Vehicle>> cells;

    /**
     * @param worldWidth  Total world width in meters
     * @param worldHeight Total world height in meters
     * @param cellSize    Size of each cell in meters (e.g., 20m)
     */
    public SpatialGrid(double worldWidth, double worldHeight, double cellSize) {
        this.cellSize = cellSize;
        this.gridWidth = (int) Math.ceil(worldWidth / cellSize) + 1;
        this.gridHeight = (int) Math.ceil(worldHeight / cellSize) + 1;
        this.cells = new ConcurrentHashMap<>();
    }

    /**
     * Clear all cells and re-insert all vehicles.
     * Called once per tick after all vehicles have committed their new positions.
     */
    public void rebuild(List<Vehicle> vehicles) {
        cells.clear();
        for (Vehicle v : vehicles) {
            if (!v.isActive())
                continue;
            long key = cellKey(v.getX(), v.getY());
            cells.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }
    }

    /**
     * Get all vehicles within the vicinity of coordinates (x, y).
     * Searches the cell containing (x,y) plus all 8 neighboring cells.
     */
    public List<Vehicle> getNeighbors(double x, double y) {
        List<Vehicle> neighbors = new ArrayList<>();
        int cx = (int) (x / cellSize);
        int cy = (int) (y / cellSize);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                long key = packKey(cx + dx, cy + dy);
                List<Vehicle> cell = cells.get(key);
                if (cell != null) {
                    neighbors.addAll(cell);
                }
            }
        }
        return neighbors;
    }

    /**
     * Get all vehicles within a specific radius.
     */
    public List<Vehicle> getNeighborsInRadius(double x, double y, double radius) {
        List<Vehicle> all = getNeighbors(x, y);
        List<Vehicle> filtered = new ArrayList<>();
        double r2 = radius * radius;
        for (Vehicle v : all) {
            double dx = v.getX() - x;
            double dy = v.getY() - y;
            if (dx * dx + dy * dy <= r2) {
                filtered.add(v);
            }
        }
        return filtered;
    }

    private long cellKey(double x, double y) {
        int cx = (int) (x / cellSize);
        int cy = (int) (y / cellSize);
        return packKey(cx, cy);
    }

    private long packKey(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }
}
