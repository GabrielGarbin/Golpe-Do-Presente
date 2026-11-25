/**
 * Roteador por hash: #/tela?param=valor
 * Cada tela é um módulo com { init(), show(params) }.
 */
import { $, $$ } from './util.js';
import { getCliente, onClienteChange, pedirLogin } from './session.js';

/**
 * Telas que exigem login. O visitante vê painel, consultas, "Posso receber?",
 * orientações e, em "Denunciar", só o boletim de ocorrência.
 */
const SO_LOGADO = new Set(['mensagem']);

const telas = new Map();
const TITULO_BASE = 'Bradesco · Proteção contra o Golpe do Presente';
let atual = null;

export function registrar(nome, modulo) {
  telas.set(nome, modulo);
  modulo.init?.();
}

/** Navega para uma tela, opcionalmente com parâmetros (ex.: número a consultar). */
export function ir(nome, params = {}) {
  const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v != null && v !== '')).toString();
  const alvo = `#/${nome}${q ? '?' + q : ''}`;
  if (location.hash === alvo) renderizar(); else location.hash = alvo;
}

function ler() {
  const [nome, q] = location.hash.replace(/^#\/?/, '').split('?');
  return { nome: telas.has(nome) ? nome : 'painel', params: Object.fromEntries(new URLSearchParams(q || '')) };
}

function renderizar() {
  const { nome, params } = ler();
  if (SO_LOGADO.has(nome) && !getCliente()) {
    history.replaceState(null, '', '#/painel');
    renderizar();
    pedirLogin('Entre ou crie uma conta para usar esta função.');
    return;
  }
  $$('.view').forEach(v => (v.hidden = v.id !== 'v-' + nome));
  $$('[data-nav]').forEach(a => {
    const on = a.dataset.nav === nome;
    a.classList.toggle('on', on);
    on ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current');
  });
  const h1 = $(`#v-${nome} h1`);
  document.title = h1 ? `${h1.textContent} · ${TITULO_BASE}` : TITULO_BASE;
  if (atual !== nome) $('#conteudo').scrollTop = 0;
  atual = nome;
  telas.get(nome).show?.(params);
}

export function iniciarRotas() {
  addEventListener('hashchange', renderizar);
  // Saiu da conta numa tela que exige login: volta para o painel.
  onClienteChange(c => { if (!c && SO_LOGADO.has(atual)) renderizar(); });
  renderizar();
}
