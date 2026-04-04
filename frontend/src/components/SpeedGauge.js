'use client';

import { useRef, useEffect } from 'react';

const MAX_SPEED = 60; // km/h for full green

function lerpColor(a, b, t) {
  const ah = parseInt(a.slice(1), 16);
  const bh = parseInt(b.slice(1), 16);
  const ar = (ah >> 16) & 0xff, ag = (ah >> 8) & 0xff, ab = ah & 0xff;
  const br = (bh >> 16) & 0xff, bg = (bh >> 8) & 0xff, bb = bh & 0xff;
  const rr = Math.round(ar + (br - ar) * t);
  const rg = Math.round(ag + (bg - ag) * t);
  const rb = Math.round(ab + (bb - ab) * t);
  return `rgb(${rr},${rg},${rb})`;
}

function speedColor(t) {
  if (t < 0.5) return lerpColor('#f44336', '#ffc107', t * 2);
  return lerpColor('#ffc107', '#3ddc84', (t - 0.5) * 2);
}

export default function SpeedGauge({ speed = 0 }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = 64 * dpr;
    canvas.height = 64 * dpr;
    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);

    // Clear
    ctx.clearRect(0, 0, 64, 64);

    const cx = 32, cy = 36;
    const r = 24;
    const startAngle = Math.PI * 0.8;
    const totalArc = Math.PI * 1.4;

    // Background track
    ctx.beginPath();
    ctx.arc(cx, cy, r, startAngle, startAngle + totalArc);
    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.stroke();

    // Filled arc — segmented for color gradient
    const frac = Math.min(speed / MAX_SPEED, 1.0);
    if (frac > 0) {
      const segments = 60;
      const filledSegments = Math.round(segments * frac);
      for (let i = 0; i < filledSegments; i++) {
        const t = i / segments;
        const a1 = startAngle + totalArc * (i / segments);
        const a2 = startAngle + totalArc * ((i + 1) / segments);
        ctx.beginPath();
        ctx.arc(cx, cy, r, a1, a2);
        ctx.strokeStyle = speedColor(t);
        ctx.lineWidth = 4;
        ctx.lineCap = 'round';
        ctx.stroke();
      }
    }

    // Center text — speed value
    ctx.fillStyle = '#e2e4ea';
    ctx.font = '600 13px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(Math.round(speed), cx, cy - 2);

    // Unit label
    ctx.fillStyle = '#6b7080';
    ctx.font = '8px Inter, sans-serif';
    ctx.fillText('km/h', cx, cy + 10);
  }, [speed]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: 64, height: 64, display: 'block' }}
    />
  );
}
