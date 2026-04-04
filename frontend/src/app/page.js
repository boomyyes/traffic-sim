'use client';

import { useRef, useEffect } from 'react';
import { animate, stagger } from 'animejs';
import { useSimulation } from '../hooks/useSimulation';
import SimulationCanvas from '../components/SimulationCanvas';
import SpeedGauge from '../components/SpeedGauge';
import ControlBar from '../components/ControlBar';
import styles from './page.module.css';

export default function Home() {
  const { state, mapData } = useSimulation();
  const dashboardRef = useRef(null);
  const prevStats = useRef({});

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

  // Stat value pulse animation
  useEffect(() => {
    if (!state) return;
    const el = document.getElementById('stat-vehicleCount');
    if (el && prevStats.current.vehicleCount !== undefined && prevStats.current.vehicleCount !== state.vehicleCount) {
      animate(el, { scale: [1.15, 1], duration: 300, ease: 'outElastic(1, .8)' });
    }
    prevStats.current = { vehicleCount: state.vehicleCount };
  }, [state]);

  const isRunning = state?.running ?? false;
  const avgSpeedKmh = (state?.avgSpeed ?? 0) * 3.6;
  const roadCount = mapData?.roadCount ?? state?.roadCount ?? 0;

  return (
    <div className={styles.dashboard} ref={dashboardRef}>
      {/* Full-viewport canvas */}
      <div className={styles.canvasLayer}>
        <SimulationCanvas state={state} mapData={mapData} />
      </div>

      {/* Top HUD overlay */}
      <header className={styles.topHud}>
        <div className={styles.topLeft}>
          <span className={`${styles.hudDot} ${isRunning ? styles.hudDotGreen : styles.hudDotRed}`} />
          <h1 className={styles.appTitle}>Traffic Sim</h1>
          <span className={styles.subtitle}>Strip-Based Lane-Free Model</span>
        </div>

        <div className={styles.hudStats}>
          <span className={styles.hudStat}>
            <span className={styles.hudStatLabel}>Vehicles</span>
            <span className={styles.hudStatValue} id="stat-vehicleCount">{state?.vehicleCount ?? 0}</span>
          </span>
          <span className={styles.hudStat}>
            <span className={styles.hudStatLabel}>Tick</span>
            <span className={styles.hudStatValue}>{state?.tickCount ?? 0}</span>
          </span>
          <span className={styles.hudStat}>
            <span className={styles.hudStatLabel}>Roads</span>
            <span className={styles.hudStatValue}>{roadCount}</span>
          </span>
        </div>

        <div className={styles.gaugeWrapper}>
          <SpeedGauge speed={avgSpeedKmh} />
        </div>
      </header>

      {/* Bottom floating control bar */}
      <div className={styles.bottomBar}>
        <ControlBar />
      </div>
    </div>
  );
}
