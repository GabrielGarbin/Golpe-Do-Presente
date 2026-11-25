/**
 * Contrato front ↔ back, conferido no código-fonte (não precisa subir nada):
 *  - toda rota chamada em frontend/js/api.js existe num controller Java, com o mesmo método HTTP;
 *  - os campos que o front envia na denúncia existem no DenunciaRequest;
 *  - os canais do <select> do front existem no enum CanalContato.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const JAVA = RAIZ + 'backend/src/main/java/com/Presente/A3/';
const ler = p => readFileSync(p, 'utf8');

function arquivosJava(dir) {
  return readdirSync(dir).flatMap(n => {
    const p = dir + n;
    return statSync(p).isDirectory() ? arquivosJava(p + '/') : p.endsWith('.java') ? [p] : [];
  });
}

/** Rotas do back: "GET /denuncias/estatisticas", "GET /golpistas/{id}"... */
function rotasDoBack() {
  const rotas = new Set();
  for (const arq of arquivosJava(JAVA)) {
    const src = ler(arq);
    if (!/@RestController/.test(src)) continue;
    const base = (src.match(/@RequestMapping\(\s*"([^"]*)"\s*\)/) || [, ''])[1];
    for (const m of src.matchAll(/@(Get|Post|Put|Delete)Mapping(?:\(\s*"([^"]*)"\s*\))?/g)) {
      rotas.add(`${m[1].toUpperCase()} ${base}${m[2] || ''}`);
    }
  }
  return rotas;
}

/** Rotas do front: lê as chamadas request('/caminho', { method: 'POST' }) de api.js. */
function rotasDoFront() {
  const src = ler(RAIZ + 'frontend/js/api.js');
  const chamadas = [];
  for (const m of src.matchAll(/request\(\s*'([^'?]+)[^']*'(?:\s*\+[^,)]*)?(?:,\s*\{([^}]*)\})?/g)) {
    const metodo = (m[2]?.match(/method:\s*'(\w+)'/) || [, 'GET'])[1];
    chamadas.push(`${metodo} ${m[1]}`);
  }
  return chamadas;
}

test('toda rota usada pelo front existe no back-end com o mesmo método', () => {
  const back = rotasDoBack();
  const front = rotasDoFront();
  assert.ok(front.length >= 10, 'encontrou as chamadas de api.js: ' + front.join(', '));
  const faltando = front.filter(r => !back.has(r));
  assert.deepEqual(faltando, [], 'rotas do front sem endpoint no back');
});

test('campos enviados pela aba Denunciar existem no DenunciaRequest do back', () => {
  const dto = ler(JAVA + 'Dto/DenunciaRequest.java');
  const view = ler(RAIZ + 'frontend/js/views/denunciar.js');
  const corpo = view.match(/Api\.denunciar\(\{([\s\S]*?)\}\)/)[1];
  const campos = [...corpo.matchAll(/(\w+):/g)].map(m => m[1]);
  assert.deepEqual(campos.sort(), ['canal', 'descricao', 'estabelecimentoFalsificado', 'telefoneGolpista']);
  for (const c of campos) assert.match(dto, new RegExp(`private \\w+ ${c};`), `DenunciaRequest.${c}`);
});

test('canais do formulário existem no enum CanalContato', () => {
  const enumJava = ler(JAVA + 'Modelo/CanalContato.java');
  const html = ler(RAIZ + 'frontend/index.html');
  const select = html.match(/<select id="dCanal">([\s\S]*?)<\/select>/)[1];
  const canais = [...select.matchAll(/value="(\w+)"/g)].map(m => m[1]);
  assert.ok(canais.length > 0);
  for (const c of canais) assert.match(enumJava, new RegExp(`\\b${c}\\b`), `CanalContato.${c}`);
});

test('a descrição respeita o limite de 500 caracteres da coluna no banco', () => {
  const modelo = ler(JAVA + 'Modelo/Denuncia.java');
  assert.match(modelo, /@Column\(length = 500\)\s*private String descricao;/);
  assert.match(ler(RAIZ + 'frontend/index.html'), /id="dDesc" maxlength="500"/);
  assert.match(ler(RAIZ + 'frontend/js/views/denunciar.js'), /desc\.length > 500/);
});
