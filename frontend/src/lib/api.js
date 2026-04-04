const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export async function fetchState() {
  const res = await fetch(`${API_BASE}/state`);
  return res.json();
}

export async function fetchMapData() {
  const res = await fetch(`${API_BASE}/map`);
  return res.json();
}

export async function startSimulation() {
  const res = await fetch(`${API_BASE}/start`, { method: 'POST' });
  return res.json();
}

export async function stopSimulation() {
  const res = await fetch(`${API_BASE}/stop`, { method: 'POST' });
  return res.json();
}

export async function resetSimulation() {
  const res = await fetch(`${API_BASE}/reset`, { method: 'POST' });
  return res.json();
}

export async function fetchConfig() {
  const res = await fetch(`${API_BASE}/config`);
  return res.json();
}

export async function uploadOsmFile(file) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${API_BASE}/upload-osm`, {
    method: 'POST',
    body: formData,
  });
  return res.json();
}

export async function loadNerulDemo() {
  const res = await fetch(`${API_BASE}/load-nerul`, { method: 'POST' });
  return res.json();
}
