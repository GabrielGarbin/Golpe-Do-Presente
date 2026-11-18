/**
 * Orientações: checklist (vem da API) + teste educativo "Golpe ou não?".
 * O teste funciona sem depender da base de denúncias: a prevenção começa
 * pela informação, desde o primeiro acesso.
 */
import { Api } from '../api.js';
import { $, $$, esc } from '../util.js';
import { icon } from '../icons.js';
import { carregando, erro } from '../ui/resultado.js';

const SECOES = {
  antes: ['clock', 'Antes', 'Ao receber a mensagem'],
  na_hora_da_entrega: ['gift', 'Na hora da entrega', 'Com o entregador na porta'],
  se_ja_pagou: ['alert', 'Se já pagou', 'Aja o quanto antes']
};

/* resposta: 'golpe' ou 'seguro' */
const SITUACOES = [
  {
    texto: 'Uma floricultura avisa por WhatsApp que você ganhou um buquê. O entregador chega e pede R$ 9,90 de “taxa de entrega” na maquininha.',
    resposta: 'golpe',
    porque: 'Presente de verdade não cobra taxa de quem recebe. Recuse a entrega e confirme a promoção pelo site ou telefone oficial da loja.'
  },
  {
    texto: 'O entregador diz que o visor da maquininha está “queimado”, mas garante que vai cobrar só R$ 5,00.',
    resposta: 'golpe',
    porque: 'Nunca digite a senha sem ver o valor na tela. Visor coberto ou “com defeito” é o sinal mais comum do golpe.'
  },
  {
    texto: 'Você comprou um perfume no site oficial da loja, pagou on-line e acompanha a entrega pelo app. O entregador só pede para conferir seu nome.',
    resposta: 'seguro',
    porque: 'Compra feita por você, já paga e sem cobrança na porta: não há o que pagar. Se pedirem qualquer valor extra, aí sim desconfie.'
  },
  {
    texto: 'O entregador recusa dinheiro e Pix e insiste no cartão, dizendo que “o sistema da loja só aceita cartão”.',
    resposta: 'golpe',
    porque: 'Insistir no cartão é para conseguir passar um valor maior na maquininha. Recuse e, se já pagou, ligue para o banco na hora.'
  }
];

let respostas = [];

function pintarQuiz() {
  const grid = $('#quizGrid');
  grid.innerHTML = SITUACOES.map((s, i) => {
    const r = respostas[i];
    const acertou = r && r === s.resposta;
    const btn = (valor, rotulo) => {
      const escolhida = r === valor;
      return `<button type="button" data-q="${i}" data-v="${valor}" ${r ? 'disabled' : ''}
        class="${escolhida ? 'escolhida ' + (acertou ? 'ok' : 'no') : ''}">${rotulo}</button>`;
    };
    return `<article class="q-card ${r ? (acertou ? 'certo' : 'errado') : ''}">
      <span class="q-n">Situação ${i + 1}</span>
      <p class="q-sit">${esc(s.texto)}</p>
      ${r ? `<p class="q-exp"><b class="${acertou ? 'ok' : 'no'}">${acertou ? 'Acertou!' : 'Atenção!'} ${s.resposta === 'golpe' ? 'É golpe.' : 'Parece seguro.'}</b> ${esc(s.porque)}</p>` : ''}
      <div class="q-acts">${btn('golpe', 'É golpe')}${btn('seguro', 'Parece seguro')}</div>
    </article>`;
  }).join('');

  const feitas = respostas.filter(Boolean).length;
  const acertos = SITUACOES.filter((s, i) => respostas[i] === s.resposta).length;
  const placar = $('#quizScore');
  placar.textContent = feitas < SITUACOES.length
    ? `${feitas} de ${SITUACOES.length} respondidas`
    : `Você acertou ${acertos} de ${SITUACOES.length}`;
  placar.classList.toggle('done', feitas === SITUACOES.length);
  $('#quizReset').hidden = feitas === 0;

  $$('[data-q]', grid).forEach(b => (b.onclick = () => {
    respostas[b.dataset.q] = b.dataset.v;
    pintarQuiz();
  }));
}

function abrirAba(qual) {
  const quiz = qual === 'quiz';
  $('#tabGuia').setAttribute('aria-selected', String(!quiz));
  $('#tabQuiz').setAttribute('aria-selected', String(quiz));
  $('#guia').hidden = quiz;
  $('#quiz').hidden = !quiz;
}

async function carregar() {
  const el = $('#guia');
  el.innerHTML = `<div class="card" style="grid-column:1/-1">${carregando('Carregando orientações…')}</div>`;
  try {
    const c = await Api.checklist();
    el.innerHTML = Object.keys(c).map(k => {
      const [ic, titulo, sub] = SECOES[k] || ['info', k, ''];
      return `<article class="card ${k === 'se_ja_pagou' ? 'guide-alert' : ''}">
        <header class="guide-h"><span>${icon(ic)}</span><h2>${esc(titulo)}<small>${esc(sub)}</small></h2></header>
        <ol class="r-list">${c[k].map(x => `<li>${esc(x)}</li>`).join('')}</ol>
      </article>`;
    }).join('');
  } catch (e) {
    el.innerHTML = `<div class="card" style="grid-column:1/-1">${erro(e.message)}</div>`;
  }
}

export function init() {
  $('#tabGuia').addEventListener('click', () => abrirAba('guia'));
  $('#tabQuiz').addEventListener('click', () => abrirAba('quiz'));
  $('#quizReset').addEventListener('click', () => { respostas = []; pintarQuiz(); });
  pintarQuiz();
}

let carregado = false;
export function show({ aba }) {
  if (aba) abrirAba(aba === 'teste' ? 'quiz' : 'guia');
  if (!carregado) { carregado = true; carregar(); }
}
