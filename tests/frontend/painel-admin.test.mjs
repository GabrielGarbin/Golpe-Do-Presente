/**
 * Painel logado como ADMIN: os números da blacklist aparecem completos.
 * Ao sair (sessão encerrada), a tela volta a mascarar sem recarregar.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { montarNavegador, frontModule, aguardar } from '../helpers/dom.mjs';
import { rotasPainel } from '../helpers/rotas-painel.mjs';

const { document, window } = montarNavegador({
  sessao: { id: 7, nome: 'Admin Teste', email: 'admin@teste.com', perfil: 'ADMIN' },
  rotas: rotasPainel('ADMIN')
});
const { initSession } = await import(frontModule('js/session.js'));
const painel = await import(frontModule('js/views/painel.js'));

initSession();
painel.init();
painel.show();
await aguardar();

test('admin vê o número completo, sem asteriscos', () => {
  const texto = document.querySelector('#alertas').textContent;
  assert.match(texto, /\(11\) 98765-0001/);
  assert.ok(!texto.includes('*'), texto);
  assert.ok(document.body.classList.contains('is-admin'));
});

test('ao sair da conta de admin, o painel volta a mascarar o número', async () => {
  window.dispatchEvent(new window.Event('sessao-expirada'));
  await aguardar();
  const texto = document.querySelector('#alertas').textContent;
  assert.match(texto, /\(11\) 9\*\*\*\*-0001/);
  assert.ok(!texto.includes('98765'), texto);
  assert.ok(!document.body.classList.contains('is-admin'));
});
