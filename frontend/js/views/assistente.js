/**
 * "Posso receber?": assistente de prevenção. Quatro perguntas de sim/não
 * levam a uma recomendação clara antes de a pessoa pagar qualquer coisa.
 * Funciona sem consultar a base de denúncias: é prevenção pela informação.
 */
import { $, $$, esc, revelar } from '../util.js';
import { cabecalho, medidor, vazio } from '../ui/resultado.js';
import { ir } from '../router.js';

/* risco: resposta que indica perigo; peso: quanto soma no medidor */
const PERGUNTAS = [
  { id: 'pediu', texto: 'Você fez essa compra ou já sabia que esse presente viria?', risco: 'nao', peso: 10,
    motivo: 'Você não esperava esse presente: é exatamente a isca do golpe.' },
  { id: 'taxa', texto: 'Estão pedindo algum valor para entregar (taxa, frete, seguro)?', risco: 'sim', peso: 45,
    motivo: 'Pedem dinheiro para entregar um presente. Loja de verdade não cobra de quem recebe.' },
  { id: 'loja', texto: 'Você confirmou com a loja por um telefone ou site que você mesmo procurou?', risco: 'nao', peso: 10,
    motivo: 'A promoção não foi confirmada por um canal oficial da loja.' },
  { id: 'maquina', texto: 'O entregador insiste no cartão ou o visor da maquininha está difícil de ler?', risco: 'sim', peso: 35,
    motivo: 'Insistência no cartão ou visor ruim é o sinal mais comum da fraude na maquininha.' }
];

const respostas = {};

function pintarPerguntas() {
  $('#asks').innerHTML = PERGUNTAS.map((q, i) => `
    <div class="ask">
      <p class="ask-q"><span>${i + 1}</span>${esc(q.texto)}</p>
      <div class="yn" role="group" aria-label="${esc(q.texto)}">
        <button type="button" data-q="${q.id}" data-v="sim" aria-pressed="${respostas[q.id] === 'sim'}">Sim</button>
        <button type="button" data-q="${q.id}" data-v="nao" aria-pressed="${respostas[q.id] === 'nao'}">Não</button>
      </div>
    </div>`).join('') +
    `<button type="button" class="btn-text" id="askReset" ${Object.keys(respostas).length ? '' : 'hidden'}>Recomeçar</button>`;

  $$('#asks [data-q]').forEach(b => (b.onclick = () => {
    const antes = PERGUNTAS.filter(q => respostas[q.id]).length;
    respostas[b.dataset.q] = b.dataset.v;
    pintarPerguntas();
    pintarResultado();
    // No celular, ao responder a última pergunta, leva até a recomendação.
    if (antes === PERGUNTAS.length - 1 && PERGUNTAS.every(q => respostas[q.id])) revelar($('#rAssistente'));
  }));
  $('#askReset').onclick = () => {
    PERGUNTAS.forEach(q => delete respostas[q.id]);
    pintarPerguntas();
    pintarResultado();
  };
}

function pintarResultado() {
  const box = $('#rAssistente');
  const feitas = PERGUNTAS.filter(q => respostas[q.id]).length;
  if (feitas < PERGUNTAS.length) {
    box.innerHTML = vazio('gift', feitas ? `Faltam ${PERGUNTAS.length - feitas} pergunta(s)` : 'Responda as perguntas',
      'Com as 4 respostas, você recebe uma recomendação clara do que fazer agora.');
    return;
  }

  const alertas = PERGUNTAS.filter(q => respostas[q.id] === q.risco);
  const pontos = alertas.reduce((t, q) => t + q.peso, 0);
  const grave = alertas.some(q => q.id === 'taxa' || q.id === 'maquina');

  let tom, selo, titulo, texto;
  if (grave) {
    [tom, selo, titulo, texto] = ['alto', 'Alto risco', 'Recuse a entrega e não pague nada',
      'Essa situação tem os sinais do Golpe do Presente. Não digite a senha e não entregue o cartão.'];
  } else if (alertas.length) {
    [tom, selo, titulo, texto] = ['medio', 'Atenção', 'Confirme antes de receber',
      'Não há cobrança, mas algo não confere. Fale com a loja por um canal oficial antes de abrir a porta.'];
  } else {
    [tom, selo, titulo, texto] = ['baixo', 'Sem sinais do golpe', 'Pode receber, com atenção',
      'Nada indica golpe. Mesmo assim, não pague nenhum valor extra na porta.'];
  }

  const motivos = alertas.length
    ? alertas.map(q => `<li>${esc(q.motivo)}</li>`).join('')
    : '<li>Você esperava a entrega, ninguém cobra nada e a loja confirmou.</li>';

  box.innerHTML = cabecalho({ tom, selo, titulo, texto, medidor: medidor(pontos, 'de risco') }) +
    `<div class="r-body t-${tom}"><div class="r-cols">
      <div><h4>O que fazer</h4><ol class="r-list">
        ${grave ? '<li>Diga que não vai receber e feche a porta.</li><li>Ligue para a loja pelo canal oficial.</li><li>Denuncie o número que enviou a mensagem.</li>'
                : alertas.length ? '<li>Procure o telefone oficial da loja (não use o da mensagem).</li><li>Consulte o número que entrou em contato.</li>'
                : '<li>Receba normalmente.</li><li>Se pedirem qualquer valor, recuse.</li>'}
      </ol></div>
      <div><h4>Por que essa recomendação</h4><ul class="r-list">${motivos}</ul></div>
    </div></div>
    <div class="r-acts">
      <button class="btn" data-a="num">Consultar o número</button>
      <button class="btn-ghost logado-only" data-a="msg">Analisar a mensagem</button>
    </div>`;
  box.querySelector('[data-a="num"]').onclick = () => ir('consultar');
  box.querySelector('[data-a="msg"]').onclick = () => ir('mensagem');
}

export function init() {
  pintarPerguntas();
  pintarResultado();
}

export function show() {}
