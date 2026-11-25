/** Formatação de telefone: completo para o admin, mascarado para os demais. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { frontModule } from '../helpers/dom.mjs';

const { maskTel, fmtTel, telParaExibir } = await import(frontModule('js/util.js'));

test('maskTel esconde o meio do número (celular e fixo)', () => {
  assert.equal(maskTel('11987650001'), '(11) 9****-0001');
  assert.equal(maskTel('1132650001'), '(11) ****-0001');
  assert.equal(maskTel('+55 (11) 98765-0001'), '(11) 9****-0001', 'aceita +55 e máscara');
});

test('fmtTel mostra o número completo formatado', () => {
  assert.equal(fmtTel('11987650001'), '(11) 98765-0001');
  assert.equal(fmtTel('1132650001'), '(11) 3265-0001');
  assert.equal(fmtTel('5511987650001'), '(11) 98765-0001', 'remove o 55 do país');
});

test('telParaExibir: admin vê completo, cliente vê mascarado', () => {
  assert.equal(telParaExibir('11987650001', true), '(11) 98765-0001');
  assert.equal(telParaExibir('11987650001', false), '(11) 9****-0001');
  assert.ok(!telParaExibir('11987650001', false).includes('98765'), 'nenhum dígito do meio vaza para o cliente');
});
