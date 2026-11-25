/**
 * Teste ponta a ponta contra uma API DE VERDADE rodando (com o banco ligado).
 *
 * Usa o próprio frontend/js/api.js (o mesmo código que roda no navegador) para:
 *   1. criar uma conta;  2. enviar uma denúncia;  3. ver o risco do número subir;
 *   4. (com credenciais de admin) ler a denúncia de volta e confirmar que foi gravada.
 *
 * Como rodar (com o back-end já rodando):
 *   API_URL=http://localhost:8081 npm run e2e
 *   API_URL=http://localhost:8081 ADMIN_EMAIL=admin@x.com ADMIN_SENHA=... npm run e2e
 *
 * ATENÇÃO: grava dados reais (uma conta e uma denúncia de teste) no banco
 * apontado pela API. Prefira um banco de desenvolvimento, não o de produção.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { frontModule } from '../helpers/dom.mjs';

const API_URL = process.env.API_URL;
const pular = !API_URL && 'defina API_URL (ex.: http://localhost:8081) com o back-end rodando';

// Ambiente mínimo de navegador para api.js: config e localStorage.
const guardado = new Map();
globalThis.window = Object.assign(new EventTarget(), { APP_CONFIG: { API_URL, TIMEOUT_MS: 20000 } });
globalThis.location = new URL('http://localhost:5500/');
globalThis.localStorage = {
  getItem: k => (guardado.has(k) ? guardado.get(k) : null),
  setItem: (k, v) => guardado.set(k, String(v)),
  removeItem: k => guardado.delete(k)
};

const { Api } = pular ? {} : await import(frontModule('js/api.js'));
const aleatorio = (n = 8) => String(Math.floor(Math.random() * 10 ** n)).padStart(n, '0');
const golpista = '119' + aleatorio();
const relato = `Teste automatizado e2e ${new Date().toISOString()}`;
let idDenuncia;

test('API responde (indicador "API conectada" do front)', { skip: pular }, async () => {
  const camadas = await Api.ping();
  assert.equal(typeof camadas, 'object');
});

test('cadastro + denúncia são aceitos pela API', { skip: pular }, async () => {
  const reg = await Api.registrar({
    nome: 'Teste E2E', email: `e2e${Date.now()}${aleatorio(4)}@teste.com`, telefone: '118' + aleatorio(), senha: 'senha-e2e-' + aleatorio()
  });
  assert.ok(reg.token, 'token devolvido');
  localStorage.setItem('jwt', reg.token);

  const antes = await Api.estatisticas();
  const d = await Api.denunciar({
    telefoneGolpista: `(${golpista.slice(0, 2)}) ${golpista.slice(2, 7)}-${golpista.slice(7)}`,
    canal: 'WHATSAPP', estabelecimentoFalsificado: 'Loja E2E', descricao: relato
  });
  assert.ok(d.id, 'denúncia com id gerado pelo banco');
  assert.equal(d.descricao, relato);
  idDenuncia = d.id;

  const depois = await Api.estatisticas();
  assert.equal(depois.totalDenuncias, antes.totalDenuncias + 1, 'total de denúncias aumentou 1 no banco');
});

test('a consulta pública já enxerga a denúncia gravada', { skip: pular }, async () => {
  const r = await Api.telefone(golpista);
  assert.notEqual(r.nivelRisco, 'BAIXO', 'o número denunciado não pode continuar como risco baixo');
});

test('admin lê a denúncia direto da base', { skip: pular || (!process.env.ADMIN_EMAIL && 'defina ADMIN_EMAIL e ADMIN_SENHA') }, async () => {
  const login = await Api.login({ email: process.env.ADMIN_EMAIL, senha: process.env.ADMIN_SENHA });
  assert.equal(login.cliente.perfil, 'ADMIN');
  localStorage.setItem('jwt', login.token);
  const todas = await Api.denuncias();
  const gravada = todas.find(x => x.id === idDenuncia);
  assert.ok(gravada, 'denúncia encontrada na listagem do admin');
  assert.equal(gravada.descricao, relato);
  assert.equal(gravada.estabelecimentoFalsificado, 'Loja E2E');
});
