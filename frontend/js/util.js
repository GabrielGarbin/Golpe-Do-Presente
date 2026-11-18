/** Utilitários de DOM, formatação e máscaras de campo. */

export const $ = (s, root = document) => root.querySelector(s);
export const $$ = (s, root = document) => [...root.querySelectorAll(s)];

export const esc = s => String(s ?? '').replace(/[&<>"']/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/* ---------- Telefone ---------- */
export function digits(t) {
  let d = String(t || '').replace(/\D/g, '');
  if ((d.length === 12 || d.length === 13) && d.startsWith('55')) d = d.slice(2);
  return d;
}
export const validTel = t => [10, 11].includes(digits(t).length);
/** Número parcialmente oculto (privacidade): (11) 9****-0001 */
export function maskTel(t) {
  const d = digits(t);
  return `(${d.slice(0, 2)}) ${d.length === 11 ? '9' : ''}****-${d.slice(-4)}`;
}
function fmtTelDigitando(v) {
  const d = digits(v).slice(0, 11);
  if (d.length <= 2) return d ? `(${d}` : '';
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

/* ---------- CNPJ ---------- */
export const cnpjDigits = t => String(t || '').replace(/\D/g, '');
function fmtCnpjDigitando(v) {
  const d = cnpjDigits(v).slice(0, 14);
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2');
}

/* ---------- Dinheiro e datas ---------- */
export const num = t => parseFloat(String(t ?? '').replace(/\./g, '').replace(',', '.'));
export const brl = n => n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
export const fdate = s => (s ? new Date(s).toLocaleDateString('pt-BR') : '—');
export function tempoAtras(s) {
  if (!s) return '';
  const dias = Math.floor((Date.now() - new Date(s).getTime()) / 864e5);
  if (dias <= 0) return 'hoje';
  if (dias === 1) return 'ontem';
  if (dias < 30) return `há ${dias} dias`;
  return fdate(s);
}
export const plural = (n, um, varios) => `${n} ${n === 1 ? um : varios}`;

/* ---------- Máscaras automáticas: <input data-mask="tel|cnpj|money"> ---------- */
const MASKS = {
  tel: fmtTelDigitando,
  cnpj: fmtCnpjDigitando,
  money: v => v.replace(/[^\d,.]/g, '')
};
export function bindMasks(root = document) {
  $$('[data-mask]', root).forEach(inp => {
    const f = MASKS[inp.dataset.mask];
    if (!f) return;
    inp.addEventListener('input', () => {
      inp.value = f(inp.value);
      inp.removeAttribute('aria-invalid');
    });
  });
}
export function setField(sel, valor) {
  const el = $(sel);
  if (!el) return;
  el.value = valor ?? '';
  el.dispatchEvent(new Event('input'));
}

/* ---------- Celular: leva o usuário até o resultado ---------- */
export function revelar(el) {
  if (matchMedia('(max-width: 1023px)').matches) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/* ---------- Feedback ---------- */
export function msg(el, texto, tipo = 'err') {
  (typeof el === 'string' ? $(el) : el).innerHTML = `<p class="msg ${tipo}">${esc(texto)}</p>`;
}
export function invalido(sel, texto, alvoMsg) {
  const el = $(sel);
  el?.setAttribute('aria-invalid', 'true');
  el?.focus();
  if (alvoMsg) msg(alvoMsg, texto);
}
let toastTimer;
export function toast(texto) {
  const t = $('#toast');
  t.textContent = texto;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), 3500);
}
