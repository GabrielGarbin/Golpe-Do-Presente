/** Analisar o texto de uma mensagem recebida. */
import { Api, ultimaVence, foiCancelada } from '../api.js';
import { $, revelar, validTel, invalido, setField } from '../util.js';
import { resultado, vazio, carregando, erro, TEXTO_MENSAGEM } from '../ui/resultado.js';
import { ir } from '../router.js';

const novoSinal = ultimaVence();
const box = () => $('#rMensagem');
const EXEMPLO = 'Parabéns! Você ganhou um brinde da Floricultura Bela Flor. O entregador está a caminho, só precisa pagar a taxa de entrega na maquininha hoje.';

async function analisar() {
  const texto = $('#mTxt').value.trim();
  const rem = $('#mRem').value.trim();
  if (!texto) {
    box().innerHTML = vazio('message', 'Cole a mensagem', 'Cole o texto da mensagem recebida para analisar.');
    return invalido('#mTxt');
  }
  if (rem && !validTel(rem)) {
    box().innerHTML = vazio('phone', 'Número do remetente inválido', 'Use DDD + número (10 ou 11 dígitos) ou deixe o campo em branco.');
    return invalido('#mRem');
  }

  box().innerHTML = carregando('Analisando a mensagem…');
  try {
    const r = await Api.mensagem(texto, rem || null, { signal: novoSinal() });
    box().innerHTML = resultado(r, TEXTO_MENSAGEM, {
      acoes: `${rem ? '<button class="btn" data-a="den">Denunciar este número</button>' : ''}
              <button class="btn-ghost" data-a="ori">Ver orientações</button>`
    });
    if (rem) box().querySelector('[data-a="den"]').onclick = () => ir('denunciar', { tel: rem });
    box().querySelector('[data-a="ori"]').onclick = () => ir('orientacoes');
    revelar(box());
  } catch (e) {
    if (!foiCancelada(e)) box().innerHTML = erro(e.message);
  }
}

export function init() {
  box().innerHTML = vazio('message', 'Nenhuma análise ainda', 'O sistema combina regras, um classificador local e a base de denúncias para avaliar a mensagem.');
  $('#mTxt').addEventListener('input', e => ($('#mCnt').textContent = e.target.value.length));
  $('#mExemplo').addEventListener('click', () => { setField('#mTxt', EXEMPLO); $('#mTxt').focus(); });
  $('#fMensagem').addEventListener('submit', e => { e.preventDefault(); analisar(); });
}

export function show() {}
