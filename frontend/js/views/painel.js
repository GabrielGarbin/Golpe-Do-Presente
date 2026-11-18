/**
 * Painel: indicadores, alertas recentes, canais, lojas imitadas e camadas.
 *
 * Paralelismo: as quatro consultas à API saem ao mesmo tempo e cada bloco
 * é desenhado assim que a SUA resposta chega. Se uma falhar, só aquele
 * bloco mostra erro; o restante do painel continua funcionando.
 */
import { Api } from '../api.js';
import { $, $$, esc, maskTel, tempoAtras, plural, validTel, invalido } from '../util.js';
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

  const lojas = Object.entries(est.estabelecimentosMaisImitados || {});
  $('#lojas').innerHTML = lojas.length
    ? `<ol class="rank">${lojas.map(([l, n]) => `<li><span>${esc(l)}</span><b>${plural(n, 'denúncia', 'denúncias')}</b></li>`).join('')}</ol>`
    : '<p class="muted">Nenhuma loja informada nas denúncias.</p>';
}

function pintarCamadas(cam) {
  const chaves = Object.keys(CAMADAS).filter(k => k in cam);
  const ativas = chaves.filter(k => cam[k]).length;
  kpi('camadas', `${ativas}/${chaves.length}`);
  $('[data-k-sub="camadas"]').textContent = cam.ia ? 'Camadas ativas · IA ligada' : 'Camadas ativas · IA opcional';
  $('#camadas').innerHTML = chaves
    .map(k => `<li class="chip ${cam[k] ? 'on' : ''}" title="${cam[k] ? 'Ativa' : 'Desligada'}">${CAMADAS[k]}</li>`).join('');
}

function pintarAlertas(golpistas, denuncias) {
  const ultimaPor = new Map();
  [...denuncias]
    .sort((a, b) => String(a.data || '').localeCompare(String(b.data || '')))
    .forEach(d => ultimaPor.set(d.golpistaId, d));

  const recentes = [...golpistas]
    .sort((a, b) => String(b.dataUltimaDenuncia || '').localeCompare(String(a.dataUltimaDenuncia || '')))
    .slice(0, 8);

  const el = $('#alertas');
  if (!recentes.length) { el.innerHTML = '<p class="muted">Nenhum alerta ainda.</p>'; return; }

  el.innerHTML = recentes.map(g => {
    const d = ultimaPor.get(g.id);
    const alto = g.status === 'CONFIRMADO';
    const info = [d?.estabelecimentoFalsificado || 'Número denunciado', CANAIS[d?.canal] || null, tempoAtras(g.dataUltimaDenuncia)]
      .filter(Boolean).map(esc).join(' · ');
    return `<button class="alert-row" data-tel="${esc(g.telefone)}">
      <span class="alert-ic ${alto ? 'alto' : ''}">${icon(alto ? 'alert' : 'phone')}</span>
      <span class="alert-txt"><b>${maskTel(g.telefone)}</b><small>${info}</small></span>
      <span class="badge ${alto ? '' : 'medio'}">${alto ? 'Alto risco' : 'Atenção'}</span>
    </button>`;
  }).join('');
  $$('.alert-row', el).forEach(b => (b.onclick = () => ir('consultar', { numero: b.dataset.tel })));
}

async function carregar() {
  $('#alertas').innerHTML = carregandoTxt;

  const est = Api.estatisticas();
  const golp = Api.golpistas();
  const den = Api.denuncias();
  const cam = Api.camadas();

  // Cada bloco reage à própria promessa (sem esperar as outras).
  est.then(pintarEstatisticas).catch(e => { $('#canais').innerHTML = erroBox(e); $('#lojas').innerHTML = ''; });
  cam.then(pintarCamadas).catch(e => { $('#camadas').innerHTML = `<li>${erroBox(e)}</li>`; });
  Promise.all([golp, den]).then(([g, d]) => pintarAlertas(g, d)).catch(e => { $('#alertas').innerHTML = erroBox(e); });
}

export function init() {
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
