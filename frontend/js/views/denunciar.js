/** Registrar denúncia na base colaborativa. */
import { Api } from '../api.js';
import { $, esc, msg, validTel, setField, invalido, toast, num, brl } from '../util.js';
import { LIMITE_SUSPEITA } from '../config.js';
import { getCliente, onClienteChange, abrirPerfil } from '../session.js';

/* Para quem já pagou: compara o valor anunciado com o cobrado. */
function pintarDiferenca() {
  const a = num($('#dVa').value), b = num($('#dVc').value);
  const el = $('#dDiff');
  if (!(a > 0) || !(b >= 0)) { el.innerHTML = ''; return; }
  const razao = b / a;
  el.innerHTML = razao >= LIMITE_SUSPEITA
    ? `<p class="diff diff-alto"><b>Cobrado ${razao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}x o anunciado (+ ${brl(b - a)}).</b> Ligue agora para o banco e peça a contestação.</p>`
    : '<p class="diff">Os valores conferem. Mesmo assim, registre a denúncia se houve cobrança por um “presente”.</p>';
}

function pintarQuem() {
  const c = getCliente();
  $('#dWho').innerHTML = c
    ? '<b>Denúncia anônima:</b> só a administração vê quem enviou.'
    : 'Você precisa <b>entrar</b> como cliente para enviar.';
}

async function enviar(e) {
  e.preventDefault();
  const cli = getCliente();
  if (!cli) { msg('#dMsg', 'Entre como um cliente para enviar a denúncia.'); return abrirPerfil(); }
  if (!validTel($('#dTel').value)) return invalido('#dTel', 'Informe o número do golpista com DDD (10 ou 11 dígitos).', '#dMsg');

  let desc = $('#dDesc').value.trim();
  const va = $('#dVa').value.trim(), vc = $('#dVc').value.trim();
  if (va || vc) desc = `${desc} [Valor anunciado: R$ ${va || '?'} | cobrado: R$ ${vc || '?'}]`.trim();
  if (desc.length > 500) return invalido('#dDesc', 'O relato, somando os valores, passa de 500 caracteres. Encurte o texto.', '#dMsg');

  const btn = e.submitter || $('#fDenuncia .btn');
  btn.disabled = true;
  $('#dMsg').innerHTML = '';
  try {
    await Api.denunciar({
      telefoneGolpista: $('#dTel').value,
      canal: $('#dCanal').value,
      estabelecimentoFalsificado: $('#dLoja').value.trim() || null,
      descricao: desc || null
    });
    e.target.reset();
    $('#dCnt').textContent = 0;
    pintarDiferenca();
    msg('#dMsg', 'Denúncia enviada. Obrigado por proteger outras pessoas.', 'ok');
    toast('Denúncia registrada na base colaborativa');
  } catch (er) {
    msg('#dMsg', er.message);
  } finally {
    btn.disabled = false;
  }
}

export function init() {
  pintarQuem();
  onClienteChange(pintarQuem);
  $('#dDesc').addEventListener('input', e => ($('#dCnt').textContent = e.target.value.length));
  $('#fDenuncia').addEventListener('submit', enviar);
  $('#dEntrar').addEventListener('click', abrirPerfil);
  ['#dVa', '#dVc'].forEach(id => $(id).addEventListener('input', pintarDiferenca));
}

let regraCarregada = false;
export function show({ tel, va, vc }) {
  if (tel) setField('#dTel', tel);
  if (va) setField('#dVa', va);
  if (vc) setField('#dVc', vc);
  if (tel || va || vc) $('#dMsg').innerHTML = '';
  pintarDiferenca();
  Api.checklist().then(c => {
    if (c.se_ja_pagou?.length) $('#dPagou').innerHTML = c.se_ja_pagou.map(x => `<li>${esc(x)}</li>`).join('');
  }).catch(() => { /* mantém o texto padrão */ });
  if (!regraCarregada) {
    Api.estatisticas().then(est => {
      regraCarregada = true;
      $('#dRegra').innerHTML = `Quando o mesmo número recebe <b>${est.limiteConfirmacao} denúncias</b>, ele passa a ser <b>confirmado</b> e entra na blacklist. Uma denúncia isolada não confirma nada, o que evita denúncias falsas.`;
    }).catch(() => { /* mantém o texto padrão */ });
  }
}
