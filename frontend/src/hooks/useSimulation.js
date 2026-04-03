'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { fetchState, fetchMapData } from '../lib/api';

/**
 * Custom hook that polls /api/state using requestAnimationFrame.
 * Fetches map data once on mount and when roadCount changes.
 * Returns { state, mapData }.
 */
export function useSimulation() {
  const [state, setState] = useState(null);
  const [mapData, setMapData] = useState(null);
  const rafRef = useRef(null);
  const lastFetchTime = useRef(0);
  const fetchingRef = useRef(false);
  const lastRoadCount = useRef(-1);

  // Target ~20 polls per second (50ms interval) to avoid overloading
  const POLL_INTERVAL_MS = 50;

  // Fetch static map data
  const loadMapData = useCallback(() => {
    fetchMapData()
      .then((data) => setMapData(data))
      .catch(() => {});
  }, []);

  // Load map data on mount
  useEffect(() => {
    loadMapData();
  }, [loadMapData]);

  const poll = useCallback(() => {
    if (document.hidden) {
      rafRef.current = requestAnimationFrame(poll);
      return;
    }
    const now = performance.now();
    if (now - lastFetchTime.current >= POLL_INTERVAL_MS && !fetchingRef.current) {
      lastFetchTime.current = now;
      fetchingRef.current = true;
      fetchState()
        .then((data) => {
          setState(data);
          // Reload map data if road count changed (new map loaded)
          if (data.roadCount !== lastRoadCount.current) {
            lastRoadCount.current = data.roadCount;
            loadMapData();
          }
          fetchingRef.current = false;
        })
        .catch(() => {
          fetchingRef.current = false;
        });
    }
    rafRef.current = requestAnimationFrame(poll);
  }, [loadMapData]);

  useEffect(() => {
    rafRef.current = requestAnimationFrame(poll);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [poll]);

  return { state, mapData };
}
