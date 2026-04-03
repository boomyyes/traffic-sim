'use client';

import { useRef, useEffect } from 'react';
import { animate, stagger } from 'animejs';
import { useSimulation } from '../hooks/useSimulation';
import SimulationCanvas from '../components/SimulationCanvas';
import StatsPanel from '../components/StatsPanel';
import ControlBar from '../components/ControlBar';
import styles from './page.module.css';

export default function Home() {
  const state = useSimulation();
  const dashboardRef = useRef(null);

  // Entrance animation on mount
  useEffect(() => {
    animate(dashboardRef.current?.children, {
      opacity: [0, 1],
      translateY: [20, 0],
      delay: stagger(100),
      duration: 600,
      ease: 'outCubic',
    });
  }, []);

  const mapName = state?.running !== undefined
    ? (state.roadCount > 0 ? `${state.roadCount} roads loaded` : 'No map loaded')
    : 'Connecting...';

  return (
    <div className={styles.dashboard} ref={dashboardRef}>
      {/* Title Bar */}
      <header className={styles.titleBar}>
        <div className={styles.titleLeft}>
          <h1 className={styles.title}>🚦 Indian Traffic Simulation Engine</h1>
          <span className={styles.subtitle}>Strip-Based Lane-Free Model</span>
        </div>
        <span className={styles.mapName}>📍 {mapName}</span>
      </header>

      {/* Main Content */}
      <div className={styles.main}>
        <div className={styles.canvasWrapper}>
          <SimulationCanvas state={state} />
        </div>
        <StatsPanel state={state} />
      </div>

      {/* Control Bar */}
      <ControlBar />
    </div>
  );
}
