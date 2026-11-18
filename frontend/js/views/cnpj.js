/** Verificar a loja pelo CNPJ (Receita Federal via BrasilAPI, chamada pelo back-end). */
import { Api, ultimaVence, foiCancelada } from '../api.js';
import { $, revelar, cnpjDigits, invalido } from '../util.js';
import { resultado, vazio, carregando, erro, TEXTO_CNPJ } from '../ui/resultado.js';

const novoSinal = ultimaVence();
const box = () => $('#rCnpj');

async function verificar() {
  const c = $('#jNum').value;
  if (cnpjDigits(c).length !== 14) {
    box().innerHTML = vazio('building', 'CNPJ incompleto', 'Informe os 14 dígitos do CNPJ.');
    return invalido('#jNum');
  }
  box().innerHTML = carregando('Consultando a Receita Federal…');
  try {
    const r = await Api.cnpj(c, { signal: novoSinal() });
    box().innerHTML = resultado(r, TEXTO_CNPJ);
    revelar(box());
  } catch (e) {
    if (!foiCancelada(e)) box().innerHTML = erro(e.message);
  }
}

export function init() {
  box().innerHTML = vazio('building', 'Nenhuma verificação ainda', 'Informe o CNPJ que aparece na mensagem ou no site da loja.');
  $('#fCnpj').addEventListener('submit', e => { e.preventDefault(); verificar(); });
}

export function show() {}
