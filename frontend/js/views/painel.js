/**
 * Painel: indicadores, alertas recentes, canais e camadas.
 *
 * Paralelismo: as quatro consultas à API saem ao mesmo tempo e cada bloco
 * é desenhado assim que a SUA resposta chega. Se uma falhar, só aquele
 * bloco mostra erro; o restante do painel continua funcionando.
 */
import { Api } from '../api.js';
import { $, $$, esc, telParaExibir, tempoAtras, plural, validTel, invalido } from '../util.js';
import { isAdmin, onClienteChange } from '../session.js';
import { icon } from '../icons.js';
import { ir } from '../router.js';

export const CANAIS = {
  WHATSAPP: 'WhatsApp', SMS: 'SMS', LIGACAO: 'Ligação', ENTREGADOR_PRESENCIAL: 'Entregador presencial', OUTRO: 'Outro'
};
const CAMADAS = {
  regras: 'Regras', classificadorLocal: 'Classificador local', baseColaborativa: 'Base colaborativa',
  consultaCnpj: 'Receita Federal', ia: 'IA externa'
};

const carregandoTxt = '<p class="muted">Carregando…</p>';
const erroBox = e => `<p class="err-box">${esc(e.message)}</p>`;

function kpi(chave, valor) {
  const el = $(`[data-k="${chave}"]`);
  if (el) el.textContent = valor;
}

function pintarEstatisticas(est) {
  const fmt = n => Number(n || 0).toLocaleString('pt-BR');
  kpi('totalDenuncias', fmt(est.totalDenuncias));
  kpi('numerosDenunciados', fmt(est.numerosDenunciados));
  kpi('numerosNaBlacklist', fmt(est.numerosNaBlacklist));
  $('[data-k-sub="blacklist"]').textContent = `Confirmados (${est.limiteConfirmacao}+ denúncias)`;

  const canais = Object.entries(est.denunciasPorCanal || {}).sort((a, b) => b[1] - a[1]);
  const max = Math.max(1, ...canais.map(([, n]) => n));
  $('#canais').innerHTML = canais.length
    ? `<div class="bars">${canais.map(([c, n]) => `
        <div class="bar-row"><span>${esc(CANAIS[c] || c)}</span><b>${n}</b>
          <div class="bar"><i data-w="${(n / max) * 100}"></i></div></div>`).join('')}</div>`
    : '<p class="muted">Sem denúncias ainda.</p>';
  requestAnimationFrame(() => $$('#canais .bar i').forEach(i => (i.style.width = i.dataset.w + '%')));
}

function pintarCamadas(cam) {
  const chaves = Object.keys(CAMADAS).filter(k => k in cam);
  const ativas = chaves.filter(k => cam[k]).length;
  kpi('camadas', `${ativas}/${chaves.length}`);
  $('[data-k-sub="camadas"]').textContent = cam.ia ? 'Camadas ativas · IA ligada' : 'Camadas ativas · IA opcional';
  $('#camadas').innerHTML = chaves
    .map(k => `<li class="chip ${cam[k] ? 'on' : ''}" title="${cam[k] ? 'Ativa' : 'Desligada'}">${CAMADAS[k]}</li>`).join('');
}

let ultimosGolpistas = null;

function pintarAlertas(golpistas) {
  ultimosGolpistas = golpistas;
  const admin = isAdmin();
  const recentes = [...golpistas]
    .sort((a, b) => String(b.dataUltimaDenuncia || '').localeCompare(String(a.dataUltimaDenuncia || '')))
    .slice(0, 8);

  const el = $('#alertas');
  if (!recentes.length) { el.innerHTML = '<p class="muted">Nenhum número confirmado na blacklist ainda.</p>'; return; }

  el.innerHTML = recentes.map(g => {
    const info = [plural(g.totalDenuncias, 'denúncia', 'denúncias'), tempoAtras(g.dataUltimaDenuncia)]
      .filter(Boolean).map(esc).join(' · ');
    return `<button class="alert-row" data-tel="${esc(g.telefone)}">
      <span class="alert-ic alto">${icon('alert')}</span>
      <span class="alert-txt"><b>${esc(telParaExibir(g.telefone, admin))}</b><small>${info}</small></span>
      <span class="badge">Confirmado</span>
    </button>`;
  }).join('');
  $$('.alert-row', el).forEach(b => (b.onclick = () => ir('consultar', { numero: b.dataset.tel })));
}

async function carregar() {
  $('#alertas').innerHTML = carregandoTxt;

  const est = Api.estatisticas();
  // O painel público consulta apenas a blacklist pública (números confirmados).
  // A lista completa e as denúncias individuais permanecem protegidas no backend.
  const golp = Api.blacklist();
  const cam = Api.camadas();

  // Cada bloco reage à própria promessa (sem esperar as outras).
  est.then(pintarEstatisticas).catch(e => { $('#canais').innerHTML = erroBox(e); $('#lojas').innerHTML = ''; });
  cam.then(pintarCamadas).catch(e => { $('#camadas').innerHTML = `<li>${erroBox(e)}</li>`; });
  golp.then(pintarAlertas).catch(e => { $('#alertas').innerHTML = erroBox(e); });
}

export function init() {
  // Entrou ou saiu como admin: redesenha os alertas com o número completo ou mascarado.
  onClienteChange(() => { if (ultimosGolpistas) pintarAlertas(ultimosGolpistas); });
  $('#fQuick').addEventListener('submit', e => {
    e.preventDefault();
    const n = $('#qNum').value;
    if (!validTel(n)) return invalido('#qNum');
    $('#qNum').value = '';
    ir('consultar', { numero: n });
  });
}

export function show() {
  carregar();
}
