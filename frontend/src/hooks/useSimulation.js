'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { fetchState } from '../lib/api';

/**
 * Custom hook that polls /api/state using requestAnimationFrame.
 * Returns the latest simulation snapshot.
 */
export function useSimulation() {
  const [state, setState] = useState(null);
  const rafRef = useRef(null);
  const lastFetchTime = useRef(0);
  const fetchingRef = useRef(false);

  // Target ~20 polls per second (50ms interval) to avoid overloading
  const POLL_INTERVAL_MS = 50;

  const poll = useCallback(() => {
    const now = performance.now();
    if (now - lastFetchTime.current >= POLL_INTERVAL_MS && !fetchingRef.current) {
      lastFetchTime.current = now;
      fetchingRef.current = true;
      fetchState()
        .then((data) => {
          setState(data);
          fetchingRef.current = false;
        })
        .catch(() => {
          fetchingRef.current = false;
        });
    }
    rafRef.current = requestAnimationFrame(poll);
  }, []);

  useEffect(() => {
    rafRef.current = requestAnimationFrame(poll);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [poll]);

  return state;
}
