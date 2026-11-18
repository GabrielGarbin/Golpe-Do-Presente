/**
 * Acessibilidade: tamanho do texto, alto contraste, leitura em voz alta
 * (Web Speech API do próprio navegador, sem serviço externo) e VLibras.
 * As preferências ficam salvas no navegador.
 */
import { $, $$ } from './util.js';
import { svg } from './icons.js';

const ESCALAS = [1, 1.15, 1.3, 1.45];
const PADRAO = { escala: 0, contraste: false, voz: true, libras: true };
let pref = carregar();

function carregar() {
  try { return { ...PADRAO, ...JSON.parse(localStorage.getItem('a11y') || '{}') }; } catch { return { ...PADRAO }; }
}
function salvar() {
  try { localStorage.setItem('a11y', JSON.stringify(pref)); } catch { /* sem storage */ }
}

function aplicar() {
  const b = document.body;
  pref.escala = Math.max(0, Math.min(ESCALAS.length - 1, pref.escala));
  document.documentElement.style.setProperty('--escala', ESCALAS[pref.escala]);
  b.classList.toggle('texto-grande', pref.escala > 0);
  b.classList.toggle('alto-contraste', pref.contraste);
  b.classList.toggle('sem-voz', !pref.voz || !('speechSynthesis' in window));
  b.classList.toggle('sem-libras', !pref.libras);
  $('#fonteValor').textContent = Math.round(ESCALAS[pref.escala] * 100) + '%';
  $('#fonteMenos').disabled = pref.escala === 0;
  $('#fonteMais').disabled = pref.escala === ESCALAS.length - 1;
  $('#swContraste').setAttribute('aria-checked', String(pref.contraste));
  $('#swVoz').setAttribute('aria-checked', String(pref.voz));
  $('#swLibras').setAttribute('aria-checked', String(pref.libras));
  if (!pref.voz) pararVoz();
}

/* ---------- Leitura em voz alta ---------- */
let botaoAtivo = null;

function textoDoResultado(card) {
  const partes = [];
  const pega = sel => $$(sel, card).map(e => e.textContent.trim()).filter(Boolean);
  partes.push(...pega('.r-head .badge'), ...pega('.r-head h3'), ...pega('.r-head p'));
  $$('.r-cols > div', card).forEach(col => {
    const titulo = col.querySelector('h4')?.textContent.trim();
    if (titulo) partes.push(titulo + ':');
    partes.push(...$$('li', col).map(li => li.textContent.trim()));
  });
  return partes.join('. ').replace(/\.\s*\./g, '.').replace(/:\./g, ':');
}

function marcar(botao, falando) {
  if (!botao) return;
  botao.classList.toggle('falando', falando);
  botao.innerHTML = svg(falando ? 'stop' : 'volume') + `<span>${falando ? 'Parar' : 'Ouvir'}</span>`;
  botao.setAttribute('aria-label', falando ? 'Parar a leitura' : 'Ouvir este resultado em voz alta');
}

export function pararVoz() {
  if ('speechSynthesis' in window) speechSynthesis.cancel();
  marcar(botaoAtivo, false);
  botaoAtivo = null;
}

function falar(botao) {
  if (botaoAtivo === botao) { pararVoz(); return; }
  pararVoz();
  const card = botao.closest('.result, .card');
  const texto = card ? textoDoResultado(card) : '';
  if (!texto) return;
  const fala = new SpeechSynthesisUtterance(texto);
  fala.lang = 'pt-BR';
  fala.rate = 0.95;
  const voz = speechSynthesis.getVoices().find(v => v.lang && v.lang.toLowerCase().startsWith('pt'));
  if (voz) fala.voice = voz;
  fala.onend = fala.onerror = () => { if (botaoAtivo === botao) { marcar(botao, false); botaoAtivo = null; } };
  botaoAtivo = botao;
  marcar(botao, true);
  speechSynthesis.speak(fala);
}

/* ---------- Painel ---------- */
function abrir(aberto) {
  $('#a11yPanel').hidden = !aberto;
  $('#btnA11y').setAttribute('aria-expanded', String(aberto));
  if (aberto) $('#fonteMais').focus();
}

export function initAcessibilidade() {
  const painel = $('#a11yPanel');
  $('#btnA11y').addEventListener('click', e => { e.stopPropagation(); abrir(painel.hidden); });
  document.addEventListener('click', e => { if (!painel.hidden && !e.target.closest('.a11y')) abrir(false); });
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && !painel.hidden) { abrir(false); $('#btnA11y').focus(); }
  });

  $('#fonteMais').onclick = () => { pref.escala++; aplicar(); salvar(); };
  $('#fonteMenos').onclick = () => { pref.escala--; aplicar(); salvar(); };
  $('#swContraste').onclick = () => { pref.contraste = !pref.contraste; aplicar(); salvar(); };
  $('#swVoz').onclick = () => { pref.voz = !pref.voz; aplicar(); salvar(); };
  $('#swLibras').onclick = () => { pref.libras = !pref.libras; aplicar(); salvar(); };
  $('#a11yReset').onclick = () => { pref = { ...PADRAO }; aplicar(); salvar(); };

  // Botões "Ouvir" são criados junto com cada resultado: um único ouvinte cuida de todos.
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-ouvir]');
    if (b) falar(b);
  });
  addEventListener('hashchange', pararVoz);

  aplicar();
}
