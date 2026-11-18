/**
 * Camada de acesso à API (contrato front ↔ back).
 *
 * É o único arquivo do front que conhece URLs e formato das requisições.
 * Se o back-end mudar um endpoint, só este arquivo precisa ser ajustado;
 * as telas continuam iguais.
 */
import { API_URL, TIMEOUT_MS } from './config.js';

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/** true quando a requisição foi cancelada de propósito (nova consulta substituiu a anterior). */
export const foiCancelada = e => e?.name === 'AbortError';

async function request(path, { method = 'GET', body, signal } = {}) {
  // Cada requisição tem seu próprio tempo limite e pode ser cancelada por quem chamou.
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  const repassar = () => ctrl.abort();
  signal?.addEventListener('abort', repassar, { once: true });

  let resp;
  try {
    resp = await fetch(API_URL + path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: ctrl.signal
    });
  } catch (e) {
    if (signal?.aborted) throw new DOMException('Cancelada', 'AbortError');
    if (ctrl.signal.aborted) throw new ApiError('A API demorou demais para responder. Tente novamente.');
    throw new ApiError(`Não foi possível conectar à API (${API_URL || 'mesma origem'}). Confira se o back-end está rodando.`);
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', repassar);
  }

  const data = await resp.json().catch(() => null);
  if (!resp.ok) throw new ApiError((data && (data.erro || data.message)) || `Erro ${resp.status} na API.`, resp.status);
  return data;
}

/* Respostas que quase não mudam ficam em memória durante a sessão. */
const memo = new Map();
function cached(key, fn) {
  if (!memo.has(key)) memo.set(key, fn().catch(e => { memo.delete(key); throw e; }));
  return memo.get(key);
}

export const Api = {
  url: API_URL,

  /* Saúde da API (sem cache, usado pelo indicador do cabeçalho). */
  ping: () => request('/prevencao/camadas'),

  /* Painel */
  camadas: () => cached('camadas', () => request('/prevencao/camadas')),
  estatisticas: () => request('/denuncias/estatisticas'),
  golpistas: () => request('/golpistas'),
  denuncias: () => request('/denuncias'),

  /* Prevenção */
  telefone: (numero, opt) => request('/prevencao/telefone?numero=' + encodeURIComponent(numero), opt),
  mensagem: (mensagem, telefoneRemetente, opt) =>
    request('/prevencao/mensagem', { method: 'POST', body: { mensagem, telefoneRemetente }, ...opt }),
  cnpj: (numero, opt) => request('/prevencao/cnpj?numero=' + encodeURIComponent(numero), opt),
  checklist: () => cached('checklist', () => request('/prevencao/checklist')),

  /* Denúncia e clientes */
  clientes: () => request('/clientes'),
  denunciar: payload => request('/denuncias', { method: 'POST', body: payload })
};

/**
 * "A última vence": ao disparar uma nova consulta, cancela a anterior que
 * ainda estiver em andamento, evitando que uma resposta antiga sobrescreva
 * a nova na tela.
 */
export function ultimaVence() {
  let atual = null;
  return () => {
    atual?.abort();
    atual = new AbortController();
    return atual.signal;
  };
}
