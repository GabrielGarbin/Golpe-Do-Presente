/**
 * Aba "Denunciar golpe": botão do WebDenúncia e o envio da denúncia
 * (o que o front manda para a API e como reage à resposta).
 */
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { montarNavegador, frontModule, aguardar } from '../helpers/dom.mjs';

let resposta = { status: 201, body: { id: 1 } };
const { document, chamadas } = montarNavegador({
  sessao: { id: 7, nome: 'Pessoa Teste', email: 't@t.com', perfil: 'USUARIO' },
  rotas: (metodo, caminho) => {
    if (metodo === 'POST' && caminho === '/denuncias') return resposta;
    if (caminho === '/prevencao/checklist') return { body: { se_ja_pagou: ['Ligue para o banco.'] } };
    if (caminho === '/denuncias/estatisticas') return { body: { limiteConfirmacao: 3 } };
    return { status: 404, body: {} };
  }
});
const { bindMasks } = await import(frontModule('js/util.js'));
const denunciar = await import(frontModule('js/views/denunciar.js'));
bindMasks();
denunciar.init();
denunciar.show({});

const $ = s => document.querySelector(s);
function preencher(campos) {
  for (const [id, valor] of Object.entries(campos)) {
    const el = $(id);
    el.value = valor;
    el.dispatchEvent(new document.defaultView.Event('input'));
  }
}
beforeEach(() => { chamadas.length = 0; resposta = { status: 201, body: { id: 1 } }; });

test('botão leva para o WebDenúncia da SSP-SP, em nova aba', () => {
  const link = $('#v-denunciar a#dWebDenuncia');
  assert.ok(link, 'o botão existe dentro da aba Denunciar');
  assert.equal(link.getAttribute('href'), 'https://www.webdenuncia.sp.gov.br/');
  assert.equal(link.getAttribute('target'), '_blank');
  assert.match(link.getAttribute('rel'), /noopener/);
  assert.match(link.textContent, /WebDenúncia/);
});

test('envia a denúncia para POST /denuncias com token e o corpo que a API espera', async () => {
  preencher({ '#dTel': '11987654321', '#dLoja': 'Floricultura Bela Flor', '#dVa': '9,90', '#dVc': '990', '#dDesc': 'Cobrou na maquininha.' });
  $('#dCanal').value = 'ENTREGADOR_PRESENCIAL';
  $('#fDenuncia').requestSubmit();
  await aguardar();

  const post = chamadas.find(c => c.metodo === 'POST');
  assert.ok(post, 'fez a requisição');
  assert.equal(post.url, 'http://api.teste/denuncias');
  assert.equal(post.headers.Authorization, 'Bearer token-de-teste');
  assert.equal(post.headers['Content-Type'], 'application/json');
  assert.deepEqual(post.corpo, {
    telefoneGolpista: '(11) 98765-4321',
    canal: 'ENTREGADOR_PRESENCIAL',
    estabelecimentoFalsificado: 'Floricultura Bela Flor',
    descricao: 'Cobrou na maquininha. [Valor anunciado: R$ 9,90 | cobrado: R$ 990]'
  });
  assert.match($('#dMsg').textContent, /Denúncia enviada/);
  assert.equal($('#dTel').value, '', 'formulário limpo depois do sucesso');
});

test('campos opcionais vazios vão como null', async () => {
  preencher({ '#dTel': '11987654321' });
  $('#fDenuncia').requestSubmit();
  await aguardar();
  const post = chamadas.find(c => c.metodo === 'POST');
  assert.equal(post.corpo.estabelecimentoFalsificado, null);
  assert.equal(post.corpo.descricao, null);
});

test('mostra a mensagem de erro que a API devolve (ex.: 409 denúncia repetida)', async () => {
  resposta = { status: 409, body: { erro: 'Você já denunciou este número.' } };
  preencher({ '#dTel': '11987654321' });
  $('#fDenuncia').requestSubmit();
  await aguardar();
  assert.match($('#dMsg').textContent, /Você já denunciou este número/);
  assert.equal($('#dTel').value, '(11) 98765-4321', 'mantém o que a pessoa digitou');
});

test('telefone inválido não chega a chamar a API', async () => {
  preencher({ '#dTel': '1198' });
  $('#fDenuncia').requestSubmit();
  await aguardar();
  assert.equal(chamadas.filter(c => c.metodo === 'POST').length, 0);
});
