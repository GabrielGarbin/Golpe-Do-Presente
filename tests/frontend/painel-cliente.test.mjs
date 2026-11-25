/** Painel logado como cliente comum (USUARIO): o número continua mascarado. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { montarNavegador, frontModule, aguardar } from '../helpers/dom.mjs';
import { rotasPainel } from '../helpers/rotas-painel.mjs';

const { document } = montarNavegador({
  sessao: { id: 8, nome: 'Cliente Teste', email: 'c@teste.com', perfil: 'USUARIO' },
  rotas: rotasPainel('USUARIO')
});
const { initSession } = await import(frontModule('js/session.js'));
const painel = await import(frontModule('js/views/painel.js'));

initSession();
painel.init();
painel.show();
await aguardar();

test('cliente vê o número mascarado', () => {
  const texto = document.querySelector('#alertas').textContent;
  assert.match(texto, /\(11\) 9\*\*\*\*-0001/);
  assert.ok(!texto.includes('98765'), texto);
  assert.ok(!document.body.classList.contains('is-admin'));
});
