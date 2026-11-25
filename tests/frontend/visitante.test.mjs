/**
 * Visitante (sem login): vê painel, consultas, "Posso receber?" e orientações.
 * Em "Denunciar" o visitante só usa o boletim de ocorrência. Analisar mensagem
 * fica escondido e, se aberto pelo endereço, volta para o painel pedindo login.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { montarNavegador, frontModule, aguardar } from '../helpers/dom.mjs';

const { document } = montarNavegador({ sessao: null });
const { initSession } = await import(frontModule('js/session.js'));
const { registrar, iniciarRotas } = await import(frontModule('js/router.js'));

initSession();
for (const nome of ['painel', 'consultar', 'mensagem', 'cnpj', 'assistente', 'denunciar', 'orientacoes']) {
  registrar(nome, { show() {} });
}
location.hash = '#/mensagem';
iniciarRotas();
await aguardar();

const $ = s => document.querySelector(s);

test('visitante não tem as classes de logado nem de admin fixo', () => {
  assert.ok(!document.body.classList.contains('is-logado'));
  assert.ok(!document.body.classList.contains('is-admin-fixo'));
});

test('Analisar mensagem é só para logados; Denunciar abre para o visitante', () => {
  assert.ok($('[data-nav="mensagem"]').classList.contains('logado-only'));
  for (const livre of ['painel', 'consultar', 'cnpj', 'assistente', 'denunciar', 'orientacoes']) {
    assert.ok(!$(`[data-nav="${livre}"]`).classList.contains('logado-only'), livre);
  }
});

test('em Denunciar, o visitante vê o boletim de ocorrência e não o formulário', () => {
  assert.ok($('#fDenuncia').classList.contains('logado-only'));
  assert.ok($('#dVisitante').classList.contains('visitante-only'));
  assert.ok(!$('#dWebDenuncia').closest('.logado-only'), 'o link do BO não depende de login');
});

test('abrir #/mensagem sem login volta ao painel e abre o login', () => {
  assert.equal(location.hash, '#/painel');
  assert.ok(!$('#v-painel').hidden);
  assert.ok($('#v-mensagem').hidden);
  assert.ok($('#dlgPerfil').open, 'o login foi aberto');
  assert.match($('#perfilMsg').textContent, /Entre ou crie uma conta/);
});

test('mensagem de exemplo só aparece para o admin fixo', () => {
  assert.ok($('#mExemplo').classList.contains('fixo-only'));
});
