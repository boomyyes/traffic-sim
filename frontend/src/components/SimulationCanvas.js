'use client';

import { useRef, useEffect, useCallback } from 'react';

// ---- Color palette (matching TrafficCanvas.java) ----
const COLORS = {
  background: '#23262a',
  road: '#37373c',
  ruralRoad: '#464137',
  roadEdge: '#b4b4b4',
  stripLine: '#505055',
  junction: '#414146',
  centerLine: '#e6c832',
  bike: '#ffc832',
  auto: '#ff8c32',
  car: '#4682e6',
  bus: '#32b450',
  truck: '#dc3c3c',
  signalRed: '#ff3232',
  signalYellow: '#ffdc32',
  signalGreen: '#32dc46',
};

function getVehicleColor(type) {
  switch (type) {
    case 'BIKE': return COLORS.bike;
    case 'AUTO_RICKSHAW': return COLORS.auto;
    case 'CAR': return COLORS.car;
    case 'BUS': return COLORS.bus;
    case 'TRUCK': return COLORS.truck;
    default: return COLORS.car;
  }
}

/**
 * SimulationCanvas — HTML5 Canvas renderer.
 * Direct port of TrafficCanvas.java with parametric road rendering.
 */
export default function SimulationCanvas({ state }) {
  const canvasRef = useRef(null);
  const viewRef = useRef({ scale: 1.5, offsetX: 10, offsetY: 10 });
  const dragRef = useRef({ dragging: false, lastX: 0, lastY: 0 });
  const hasAutoFit = useRef(false);

  // ---- Coordinate transforms ----
  const toScreenX = useCallback((worldX) => {
    const v = viewRef.current;
    return (worldX + v.offsetX) * v.scale;
  }, []);

  const toScreenY = useCallback((worldY) => {
    const v = viewRef.current;
    return (worldY + v.offsetY) * v.scale;
  }, []);

  // ---- Auto-fit to map bounds on first load ----
  useEffect(() => {
    if (!state || !state.roads || state.roads.length === 0 || hasAutoFit.current) return;
    const canvas = canvasRef.current;
    if (!canvas) return;

    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    for (const r of state.roads) {
      minX = Math.min(minX, r.startX, r.endX);
      maxX = Math.max(maxX, r.startX, r.endX);
      minY = Math.min(minY, r.startY, r.endY);
      maxY = Math.max(maxY, r.startY, r.endY);
    }
    const worldW = Math.max(1, (maxX - minX) * 1.1);
    const worldH = Math.max(1, (maxY - minY) * 1.1);
    const scaleX = canvas.width / worldW;
    const scaleY = canvas.height / worldH;
    const scale = Math.min(scaleX, scaleY);
    viewRef.current = {
      scale,
      offsetX: (canvas.width / scale - (maxX + minX)) / 2,
      offsetY: (canvas.height / scale - (maxY + minY)) / 2,
    };
    hasAutoFit.current = true;
  }, [state]);

  // ---- Mouse interaction (pan + zoom) ----
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const onWheel = (e) => {
      e.preventDefault();
      const delta = e.deltaY > 0 ? -0.15 : 0.15;
      viewRef.current.scale = Math.max(0.3, Math.min(15.0, viewRef.current.scale + delta));
    };
    const onMouseDown = (e) => {
      dragRef.current = { dragging: true, lastX: e.clientX, lastY: e.clientY };
    };
    const onMouseMove = (e) => {
      if (!dragRef.current.dragging) return;
      const dx = e.clientX - dragRef.current.lastX;
      const dy = e.clientY - dragRef.current.lastY;
      viewRef.current.offsetX += dx / viewRef.current.scale;
      viewRef.current.offsetY += dy / viewRef.current.scale;
      dragRef.current.lastX = e.clientX;
      dragRef.current.lastY = e.clientY;
    };
    const onMouseUp = () => { dragRef.current.dragging = false; };

    canvas.addEventListener('wheel', onWheel, { passive: false });
    canvas.addEventListener('mousedown', onMouseDown);
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);

    return () => {
      canvas.removeEventListener('wheel', onWheel);
      canvas.removeEventListener('mousedown', onMouseDown);
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };
  }, []);

  // ---- Render loop ----
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let raf;

    const render = () => {
      const { width, height } = canvas;
      // Clear
      ctx.fillStyle = COLORS.background;
      ctx.fillRect(0, 0, width, height);

      if (!state || !state.roads) {
        drawNoConnection(ctx, width, height);
        raf = requestAnimationFrame(render);
        return;
      }

      // Junction zones
      if (state.junctionZones) {
        ctx.fillStyle = COLORS.junction;
        for (const jz of state.junctionZones) {
          const x = toScreenX(jz[0]);
          const y = toScreenY(jz[1]);
          const w = (jz[2] - jz[0]) * viewRef.current.scale;
          const h = (jz[3] - jz[1]) * viewRef.current.scale;
          ctx.fillRect(x, y, w, h);
        }
      }

      // Roads
      for (const road of state.roads) {
        drawRoad(ctx, road);
      }

      // Signals
      if (state.signals) {
        for (const signal of state.signals) {
          drawSignal(ctx, signal, state.roads);
        }
      }

      // Vehicles
      if (state.vehicles) {
        for (const v of state.vehicles) {
          drawVehicle(ctx, v, state.roads);
        }
      }

      // Legend
      drawLegend(ctx, width);

      raf = requestAnimationFrame(render);
    };

    raf = requestAnimationFrame(render);
    return () => cancelAnimationFrame(raf);
  }, [state, toScreenX, toScreenY]);

  // ---- Drawing functions ----

  function drawNoConnection(ctx, w, h) {
    ctx.fillStyle = '#666';
    ctx.font = '16px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Connecting to simulation backend...', w / 2, h / 2);
    ctx.font = '12px Inter, sans-serif';
    ctx.fillStyle = '#555';
    ctx.fillText('Make sure the backend is running on port 8080', w / 2, h / 2 + 24);
    ctx.textAlign = 'left';
  }

  function drawRoad(ctx, road) {
    const sx = road.startX, sy = road.startY;
    const ex = road.endX, ey = road.endY;
    const dx = ex - sx, dy = ey - sy;
    const len = road.roadLength;
    if (len === 0) return;

    const ux = dx / len, uy = dy / len;
    const px = -uy, py = ux;
    const halfW = road.width / 2.0;

    const isRural = road.roadType === 'RURAL' || road.roadType === 'RESIDENTIAL';

    // Road polygon (4 corners)
    const xPts = [
      toScreenX(sx + px * halfW), toScreenX(sx - px * halfW),
      toScreenX(ex - px * halfW), toScreenX(ex + px * halfW),
    ];
    const yPts = [
      toScreenY(sy + py * halfW), toScreenY(sy - py * halfW),
      toScreenY(ey - py * halfW), toScreenY(ey + py * halfW),
    ];

    ctx.fillStyle = isRural ? COLORS.ruralRoad : COLORS.road;
    ctx.beginPath();
    ctx.moveTo(xPts[0], yPts[0]);
    ctx.lineTo(xPts[1], yPts[1]);
    ctx.lineTo(xPts[2], yPts[2]);
    ctx.lineTo(xPts[3], yPts[3]);
    ctx.closePath();
    ctx.fill();

    // Edge lines
    ctx.strokeStyle = COLORS.roadEdge;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(xPts[0], yPts[0]); ctx.lineTo(xPts[3], yPts[3]);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(xPts[1], yPts[1]); ctx.lineTo(xPts[2], yPts[2]);
    ctx.stroke();

    // Center line (rural) or strip lines
    if (isRural) {
      ctx.strokeStyle = COLORS.centerLine;
      ctx.lineWidth = 1.5;
      const dashes = Math.floor(len / 8);
      for (let i = 0; i < dashes; i++) {
        const t1 = (i * 8.0) / len;
        let t2 = (i * 8.0 + 4.0) / len;
        if (t2 > 1.0) t2 = 1.0;
        ctx.beginPath();
        ctx.moveTo(toScreenX(sx + dx * t1), toScreenY(sy + dy * t1));
        ctx.lineTo(toScreenX(sx + dx * t2), toScreenY(sy + dy * t2));
        ctx.stroke();
      }
    } else {
      drawStripLines(ctx, road, sx, sy, dx, dy, len, px, py, halfW);
    }

    // Road label
    ctx.fillStyle = 'rgba(255,255,255,0.3)';
    ctx.font = '8px Inter, sans-serif';
    ctx.fillText('R' + road.id, toScreenX(sx), toScreenY(sy) - 2);
  }

  function drawStripLines(ctx, road, sx, sy, dx, dy, len, px, py, halfW) {
    ctx.strokeStyle = COLORS.stripLine;
    ctx.lineWidth = 0.5;
    const stripCount = road.stripCount;
    if (stripCount <= 1) return;

    for (let i = 1; i < stripCount; i++) {
      const lateralFrac = i / stripCount;
      const lateralOffset = halfW - lateralFrac * road.width;
      const offX = px * lateralOffset;
      const offY = py * lateralOffset;

      const dashes = Math.floor(len / 10);
      for (let d = 0; d < dashes; d++) {
        const t1 = (d * 10.0) / len;
        let t2 = (d * 10.0 + 5.0) / len;
        if (t2 > 1.0) t2 = 1.0;
        ctx.beginPath();
        ctx.moveTo(toScreenX(sx + dx * t1 + offX), toScreenY(sy + dy * t1 + offY));
        ctx.lineTo(toScreenX(sx + dx * t2 + offX), toScreenY(sy + dy * t2 + offY));
        ctx.stroke();
      }
    }
  }

  function drawSignal(ctx, signal, roads) {
    const road = roads.find((r) => r.id === signal.roadSegmentId);
    if (!road) return;

    const len = road.roadLength;
    if (len === 0) return;
    const t = signal.stopLineY / len;
    const dx = road.endX - road.startX;
    const dy = road.endY - road.startY;
    const ux = dx / len, uy = dy / len;
    const px = -uy, py = ux;
    const halfW = road.width / 2.0;

    const stopWorldX = road.startX + dx * t;
    const stopWorldY = road.startY + dy * t;

    // Signal housing
    const sigX = toScreenX(stopWorldX + px * (halfW + 4));
    const sigY = toScreenY(stopWorldY + py * (halfW + 4));
    const scale = viewRef.current.scale;
    const boxW = 8 * scale / 1.5;
    const boxH = 20 * scale / 1.5;

    ctx.fillStyle = '#1e1e1e';
    ctx.fillRect(sigX - boxW / 2, sigY - boxH / 2, boxW, boxH);
    ctx.strokeStyle = '#4d4d4d';
    ctx.lineWidth = 0.5;
    ctx.strokeRect(sigX - boxW / 2, sigY - boxH / 2, boxW, boxH);

    // Three lights
    const r = boxW * 0.35;
    const lightY1 = sigY - boxH / 2 + boxH * 0.2;
    const lightY2 = sigY;
    const lightY3 = sigY + boxH / 2 - boxH * 0.2;

    ctx.fillStyle = signal.state === 'RED' ? COLORS.signalRed : '#3c1414';
    ctx.beginPath(); ctx.arc(sigX, lightY1, r, 0, Math.PI * 2); ctx.fill();

    ctx.fillStyle = signal.state === 'YELLOW' ? COLORS.signalYellow : '#3c3714';
    ctx.beginPath(); ctx.arc(sigX, lightY2, r, 0, Math.PI * 2); ctx.fill();

    ctx.fillStyle = signal.state === 'GREEN' ? COLORS.signalGreen : '#143719';
    ctx.beginPath(); ctx.arc(sigX, lightY3, r, 0, Math.PI * 2); ctx.fill();

    // Stop line across road
    const lineColor = signal.state === 'RED' ? COLORS.signalRed
      : signal.state === 'YELLOW' ? COLORS.signalYellow
      : COLORS.signalGreen;
    ctx.strokeStyle = lineColor;
    ctx.globalAlpha = 0.7;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(toScreenX(stopWorldX + px * halfW), toScreenY(stopWorldY + py * halfW));
    ctx.lineTo(toScreenX(stopWorldX - px * halfW), toScreenY(stopWorldY - py * halfW));
    ctx.stroke();
    ctx.globalAlpha = 1.0;
  }

  function drawVehicle(ctx, v, roads) {
    const road = roads.find((r) => r.id === v.roadSegmentId);
    if (!road) return;

    const len = road.roadLength;
    if (len === 0) return;
    const t = v.y / len;
    const dx = road.endX - road.startX;
    const dy = road.endY - road.startY;
    const ux = dx / len, uy = dy / len;
    const px = -uy, py = ux;

    // World center of vehicle
    let worldCX = road.startX + dx * t;
    let worldCY = road.startY + dy * t;
    const lateralOffset = v.x - road.width / 2.0;
    worldCX += px * lateralOffset;
    worldCY += py * lateralOffset;

    const screenCX = toScreenX(worldCX);
    const screenCY = toScreenY(worldCY);
    const scale = viewRef.current.scale;
    const screenW = v.length * scale;
    const screenH = v.width * scale;
    const angle = Math.atan2(dy, dx);

    ctx.save();
    ctx.translate(screenCX, screenCY);
    ctx.rotate(angle);
    ctx.fillStyle = getVehicleColor(v.type);
    ctx.fillRect(-screenW / 2, -screenH / 2, screenW, screenH);
    ctx.strokeStyle = 'rgba(20,20,20,0.6)';
    ctx.lineWidth = 0.5;
    ctx.strokeRect(-screenW / 2, -screenH / 2, screenW, screenH);
    ctx.restore();
  }

  function drawLegend(ctx, canvasWidth) {
    const lx = canvasWidth - 130;
    const ly = 10;
    ctx.fillStyle = 'rgba(20,20,25,0.88)';
    ctx.fillRect(lx - 8, ly - 5, 128, 125);

    ctx.font = 'bold 10px Inter, sans-serif';
    ctx.fillStyle = '#b3b3b3';
    ctx.fillText('VEHICLE TYPES', lx, ly + 8);

    ctx.font = '10px Inter, sans-serif';
    const legend = [
      [COLORS.bike, 'Bike'],
      [COLORS.auto, 'Auto Rickshaw'],
      [COLORS.car, 'Car'],
      [COLORS.bus, 'Bus'],
      [COLORS.truck, 'Truck'],
    ];
    for (let i = 0; i < legend.length; i++) {
      ctx.fillStyle = legend[i][0];
      ctx.fillRect(lx, ly + 16 + i * 18, 12, 12);
      ctx.fillStyle = '#d2d2d2';
      ctx.fillText(legend[i][1], lx + 18, ly + 16 + i * 18 + 10);
    }
  }

  // ---- Public methods via ref ----
  const fitView = useCallback(() => {
    hasAutoFit.current = false;
    if (state && state.roads && state.roads.length > 0) {
      const canvas = canvasRef.current;
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
      for (const r of state.roads) {
        minX = Math.min(minX, r.startX, r.endX);
        maxX = Math.max(maxX, r.startX, r.endX);
        minY = Math.min(minY, r.startY, r.endY);
        maxY = Math.max(maxY, r.startY, r.endY);
      }
      const worldW = Math.max(1, (maxX - minX) * 1.1);
      const worldH = Math.max(1, (maxY - minY) * 1.1);
      const scaleX = canvas.width / worldW;
      const scaleY = canvas.height / worldH;
      const scale = Math.min(scaleX, scaleY);
      viewRef.current = {
        scale,
        offsetX: (canvas.width / scale - (maxX + minX)) / 2,
        offsetY: (canvas.height / scale - (maxY + minY)) / 2,
      };
    }
  }, [state]);

  // Expose fitView and viewRef for parent components
  useEffect(() => {
    const canvas = canvasRef.current;
    if (canvas) {
      canvas._fitView = fitView;
      canvas._viewRef = viewRef;
    }
  }, [fitView]);

  return (
    <canvas
      ref={canvasRef}
      width={1000}
      height={700}
      id="simulation-canvas"
      style={{
        display: 'block',
        borderRadius: '8px',
        cursor: dragRef.current.dragging ? 'grabbing' : 'grab',
      }}
    />
  );
}
