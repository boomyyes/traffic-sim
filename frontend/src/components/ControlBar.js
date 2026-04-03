'use client';

import { useRef, useCallback } from 'react';
import { animate } from 'animejs';
import { startSimulation, stopSimulation, resetSimulation, uploadOsmFile, loadNerulDemo } from '../lib/api';
import styles from './ControlBar.module.css';

/**
 * Bottom control bar with simulation controls, zoom, and map loading.
 * Uses anime.js for button press animations.
 */
export default function ControlBar() {
  const fileInputRef = useRef(null);

  const getCanvas = useCallback(() => document.getElementById('simulation-canvas'), []);

  const animateButton = (e) => {
    animate(e.currentTarget, {
      scale: [0.92, 1],
      duration: 300,
      ease: 'outElastic(1, .6)',
    });
  };

  const handleStart = (e) => {
    animateButton(e);
    startSimulation();
  };

  const handleStop = (e) => {
    animateButton(e);
    stopSimulation();
  };

  const handleReset = (e) => {
    animateButton(e);
    resetSimulation();
  };

  const handleFit = (e) => {
    animateButton(e);
    const canvas = getCanvas();
    if (canvas && canvas._fitView) {
      canvas._fitView();
    }
  };

  const handleLoadOsm = (e) => {
    animateButton(e);
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e) => {
    const file = e.target.files[0];
    if (file) {
      await uploadOsmFile(file);
      setTimeout(() => {
        const canvas = getCanvas();
        if (canvas && canvas._fitView) canvas._fitView();
      }, 500);
    }
    e.target.value = '';
  };

  const handleNerul = async (e) => {
    animateButton(e);
    await loadNerulDemo();
    setTimeout(() => {
      const canvas = getCanvas();
      if (canvas && canvas._fitView) canvas._fitView();
    }, 500);
  };

  const handleZoomIn = () => {
    const canvas = getCanvas();
    if (canvas && canvas._viewRef) {
      canvas._viewRef.current.scale = Math.min(15, canvas._viewRef.current.scale + 0.3);
    }
  };

  const handleZoomOut = () => {
    const canvas = getCanvas();
    if (canvas && canvas._viewRef) {
      canvas._viewRef.current.scale = Math.max(0.3, canvas._viewRef.current.scale - 0.3);
    }
  };

  return (
    <div className={styles.bar}>
      <button className={`${styles.btn} ${styles.startBtn}`} onClick={handleStart} id="btn-start">
        ▶ Start
      </button>
      <button className={`${styles.btn} ${styles.pauseBtn}`} onClick={handleStop} id="btn-pause">
        ⏸ Pause
      </button>
      <button className={`${styles.btn} ${styles.resetBtn}`} onClick={handleReset} id="btn-reset">
        🔄 Reset
      </button>

      <div className={styles.separator} />

      <span className={styles.label}>Zoom:</span>
      <button className={`${styles.btn} ${styles.zoomBtn}`} onClick={handleZoomOut}>−</button>
      <button className={`${styles.btn} ${styles.zoomBtn}`} onClick={handleZoomIn}>+</button>
      <button className={`${styles.btn} ${styles.fitBtn}`} onClick={handleFit} id="btn-fit">
        🔍 Fit
      </button>

      <div className={styles.separator} />

      <button className={`${styles.btn} ${styles.osmBtn}`} onClick={handleLoadOsm} id="btn-load-osm">
        📂 Load OSM
      </button>
      <button className={`${styles.btn} ${styles.nerulBtn}`} onClick={handleNerul} id="btn-nerul">
        📍 Nerul Demo
      </button>

      <input
        ref={fileInputRef}
        type="file"
        accept=".osm,.xml"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
    </div>
  );
}
