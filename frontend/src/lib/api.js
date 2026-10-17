import axios from 'axios';

// VITE_API_BASE_URL is the backend origin in production (set it in Vercel). When unset, requests stay relative
// ("/api/...") so the Vite dev proxy and the bundled Nginx proxy keep working locally and in Docker.
const baseURL = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '');

const api = axios.create({ baseURL });

export default api;
