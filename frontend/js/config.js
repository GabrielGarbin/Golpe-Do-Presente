/**
 * Lê a configuração de config.js (window.APP_CONFIG) e aplica a
 * sobrescrita opcional ?api=http://IP:8081 (salva no navegador).
 */
const cfg = window.APP_CONFIG || {};

function apiDaUrl() {
  const q = new URLSearchParams(location.search).get('api');
  try {
    if (q === 'padrao') localStorage.removeItem('api');
    else if (q) localStorage.setItem('api', q);
    return localStorage.getItem('api');
  } catch {
    return q; // navegador sem localStorage (modo privado restrito)
  }
}

export const API_URL = (apiDaUrl() || cfg.API_URL || '').replace(/\/$/, '');
export const TIMEOUT_MS = Number(cfg.TIMEOUT_MS) || 12000;
export const LIMITE_SUSPEITA = Number(cfg.LIMITE_SUSPEITA) || 1.2;

/** O perfil ADMIN vem do JWT validado pelo backend; o front não decide permissões por nome. */
export const ehAdmin = cliente => !!cliente && cliente.perfil === 'ADMIN';
