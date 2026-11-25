/**
 * Ambiente de navegador para testar o front no Node: carrega o index.html real
 * no jsdom (sem executar scripts) e expõe window, document, localStorage etc.
 * como globais, do jeito que os módulos de frontend/js esperam.
 *
 * Também troca o fetch por um servidor falso, que grava cada requisição feita
 * pelo front para o teste conferir URL, método, cabeçalhos e corpo.
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
export const FRONT = RAIZ + 'frontend/';
export const API = 'http://api.teste';

/** URL de import de um módulo do front: frontModule('js/util.js'). */
export const frontModule = caminho => pathToFileURL(FRONT + caminho).href;

/**
 * Prepara o DOM. Chame ANTES de importar qualquer módulo do front, porque
 * config.js e session.js leem window/localStorage no momento do import.
 *
 * @param {object} opcoes
 * @param {object|null} opcoes.sessao  cliente logado ({id,nome,email,perfil}) ou null
 * @param {Function} opcoes.rotas      (metodo, caminho, corpo) => {status, body}
 */
export function montarNavegador({ sessao = null, rotas = () => ({ status: 404, body: { erro: 'rota de teste ausente' } }) } = {}) {
  const html = readFileSync(FRONT + 'index.html', 'utf8');
  const dom = new JSDOM(html, { url: 'http://localhost:5500/', pretendToBeVisual: true });
  const w = dom.window;

  w.APP_CONFIG = { API_URL: API, TIMEOUT_MS: 2000, LIMITE_SUSPEITA: 1.2 };
  w.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
  w.HTMLDialogElement.prototype.showModal ??= function () { this.open = true; };
  w.HTMLDialogElement.prototype.close ??= function () { this.open = false; };

  if (sessao) {
    w.localStorage.setItem('jwt', 'token-de-teste');
    w.localStorage.setItem('cli', JSON.stringify(sessao));
  }

  const chamadas = [];
  globalThis.fetch = async (url, opt = {}) => {
    const u = new URL(url);
    const corpo = opt.body ? JSON.parse(opt.body) : undefined;
    chamadas.push({ url: String(url), caminho: u.pathname + u.search, metodo: opt.method || 'GET', headers: opt.headers || {}, corpo });
    const { status = 200, body = {} } = (await rotas(opt.method || 'GET', u.pathname + u.search, corpo)) || {};
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  };

  for (const nome of ['window', 'document', 'localStorage', 'history', 'location', 'Event', 'CustomEvent',
    'HTMLElement', 'requestAnimationFrame', 'matchMedia', 'getComputedStyle']) {
    Object.defineProperty(globalThis, nome, { value: w[nome], configurable: true, writable: true });
  }
  globalThis.requestAnimationFrame ??= f => setTimeout(f, 0);
  globalThis.addEventListener = w.addEventListener.bind(w);
  globalThis.dispatchEvent = w.dispatchEvent.bind(w);

  return { dom, window: w, document: w.document, chamadas };
}

/** Espera as promessas pendentes (respostas do fetch falso) serem processadas. */
export const aguardar = (ms = 20) => new Promise(r => setTimeout(r, ms));
