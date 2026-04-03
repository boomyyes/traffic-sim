'use client';

import { useEffect, useRef } from 'react';
import { animate } from 'animejs';
import styles from './StatsPanel.module.css';

/**
 * Live statistics sidebar with anime.js counter animations.
 */
export default function StatsPanel({ state, mapData }) {
  const prevStats = useRef({});

  // Animate stat values when they change significantly
  useEffect(() => {
    if (!state) return;
    const fields = ['vehicleCount', 'roadCount', 'bikeCount', 'carAutoCount', 'busTruckCount'];
    for (const field of fields) {
      const el = document.getElementById(`stat-${field}`);
      if (el && prevStats.current[field] !== undefined && prevStats.current[field] !== state[field]) {
        animate(el, {
          scale: [1.15, 1],
          duration: 300,
          ease: 'outElastic(1, .8)',
        });
      }
    }
    prevStats.current = { ...state };
  }, [state]);

  if (!state) {
    return (
      <div className={styles.panel}>
        <h3 className={styles.header}>📊 Live Statistics</h3>
        <div className={styles.separator} />
        <span className={styles.stat} style={{ color: '#c86464' }}>⏹ Disconnected</span>
      </div>
    );
  }

  const statusText = state.running ? '▶ Running' : '⏸ Stopped';
  const statusColor = state.running ? '#50c864' : '#c86464';

  return (
    <div className={styles.panel}>
      <h3 className={styles.header}>📊 Live Statistics</h3>
      <div className={styles.separator} />

      <span className={styles.stat} style={{ color: statusColor }}>{statusText}</span>
      <span className={styles.stat} id="stat-vehicleCount">Vehicles: {state.vehicleCount}</span>
      <span className={styles.stat}>Avg Speed: {(state.avgSpeed * 3.6).toFixed(1)} km/h</span>
      <span className={styles.stat}>Tick: {state.tickCount}</span>

      <div className={styles.separator} />
      <h4 className={styles.subheader}>🗺️ Road Network</h4>
      <span className={styles.stat} id="stat-roadCount">Roads: {mapData?.roadCount ?? state.roadCount}</span>

      <div className={styles.separator} />
      <h4 className={styles.subheader}>🚗 Vehicle Mix</h4>
      <span className={styles.stat} id="stat-bikeCount">🏍️ Bikes: {state.bikeCount}</span>
      <span className={styles.stat} id="stat-carAutoCount">🚙 Cars/Autos: {state.carAutoCount}</span>
      <span className={styles.stat} id="stat-busTruckCount">🚌 Buses/Trucks: {state.busTruckCount}</span>
    </div>
  );
}
