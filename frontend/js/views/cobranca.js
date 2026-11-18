/** Conferir cobrança: compara o valor anunciado com o valor realmente cobrado. */
import { Api } from '../api.js';
import { LIMITE_SUSPEITA } from '../config.js';
import { $, $$, num, brl, esc, invalido } from '../util.js';
import { cabecalho, vazio } from '../ui/resultado.js';
import { ir } from '../router.js';

const box = () => $('#rCobranca');
const ACOES_PADRAO = ['Contate seu banco e conteste a compra', 'Bloqueie o cartão', 'Registre um B.O. e faça a denúncia aqui'];

async function calcular() {
  const a = num($('#vAnun').value);
  const b = num($('#vCobr').value);
  if (!(a > 0)) return invalido('#vAnun'), (box().innerHTML = vazio('card', 'Valor anunciado inválido', 'Informe o valor anunciado pelo entregador (maior que zero).'));
  if (!(b >= 0)) return invalido('#vCobr'), (box().innerHTML = vazio('card', 'Valor cobrado inválido', 'Informe o valor que apareceu no extrato ou no app.'));

  const razao = b / a;
  const suspeita = razao >= LIMITE_SUSPEITA;
  const maior = Math.max(a, b);
  const barras = `<div class="compare">
      <div class="compare-row"><span>Valor anunciado</span><div class="compare-bar"><i data-w="${(a / maior) * 100}"></i></div><b>${brl(a)}</b></div>
      <div class="compare-row"><span>Valor cobrado</span><div class="compare-bar"><i class="${suspeita ? 'hot' : ''}" data-w="${(b / maior) * 100}"></i></div><b>${brl(b)}</b></div>
    </div>`;

  if (!suspeita) {
    box().innerHTML = cabecalho({
      tom: 'baixo', selo: 'Valor confere', titulo: 'O valor cobrado está de acordo com o anunciado',
      texto: 'Mesmo assim, ninguém legítimo cobra para entregar um presente.'
    }) + `<div class="r-body t-baixo">${barras}</div>`;
  } else {
    let acoes = ACOES_PADRAO;
    try { acoes = (await Api.checklist()).se_ja_pagou || acoes; } catch { /* usa o padrão */ }
    const vezes = razao.toLocaleString('pt-BR', { maximumFractionDigits: 1 });
    box().innerHTML = cabecalho({
      tom: 'alto', selo: 'Cobrança suspeita', titulo: `Cobrado ${vezes}x o valor anunciado`,
      texto: 'Esse é o padrão do Golpe do Presente: o visor mostra um valor e a maquininha cobra outro.',
      medidor: `<div style="text-align:right"><div class="big">+ ${brl(b - a)}</div><small class="muted">acima do anunciado</small></div>`
    }) + `<div class="r-body t-alto">${barras}
        <div class="r-cols" style="grid-template-columns:1fr"><div><h4>O que fazer agora</h4>
        <ol class="r-list">${acoes.map(x => `<li>${esc(x)}</li>`).join('')}</ol></div></div></div>
      <div class="r-acts"><button class="btn" data-a="den">Denunciar com esses dados</button></div>`;
    box().querySelector('[data-a="den"]').onclick = () => ir('denunciar', { va: $('#vAnun').value, vc: $('#vCobr').value });
  }
  requestAnimationFrame(() => $$('.compare-bar i', box()).forEach(i => (i.style.width = i.dataset.w + '%')));
}

export function init() {
  box().innerHTML = vazio('card', 'Compare os valores', 'Se o valor cobrado for bem maior que o anunciado, mostramos o que fazer imediatamente.');
  $('#fCobranca').addEventListener('submit', e => { e.preventDefault(); calcular(); });
}

export function show() {}
