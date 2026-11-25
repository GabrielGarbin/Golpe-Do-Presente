/**
 * Área de administração: carrega os dados do admin, filtra, remove denúncia,
 * valida o cadastro de usuário e bloqueia quem não é administrador.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { montarNavegador, frontModule, aguardar } from '../helpers/dom.mjs';

const DENUNCIAS = [
  { id: 100, data: new Date().toISOString(), canal: 'WHATSAPP', estabelecimentoFalsificado: 'Floricultura Bela Flor',
    descricao: 'Cobrou taxa na maquininha.', clienteId: 2, clienteNome: 'Arthur', clienteEmail: 'a@t.com',
    golpistaId: 10, golpistaTelefone: '11900000001', golpistaStatus: 'CONFIRMADO', golpistaTotalDenuncias: 3 },
  { id: 101, data: new Date().toISOString(), canal: 'SMS', estabelecimentoFalsificado: null,
    descricao: 'Link de entrega.', clienteId: null, clienteNome: null, clienteEmail: null,
    golpistaId: 11, golpistaTelefone: '21912345678', golpistaStatus: 'SUSPEITO', golpistaTotalDenuncias: 1 }
];
const USUARIOS = [
  { id: 7, nome: 'Admin Teste', email: 'admin@teste.com', telefone: '11987650001', perfil: 'ADMIN',
    adminFixo: true, temSenha: true, totalDenuncias: 0 },
  { id: 8, nome: 'Pessoa Comum', email: 'p@teste.com', telefone: '11987650002', perfil: 'USUARIO',
    adminFixo: false, temSenha: true, totalDenuncias: 2 },
  { id: 6, nome: 'Admin Promovido', email: 'promovido@teste.com', telefone: '11987650003', perfil: 'ADMIN',
    adminFixo: false, temSenha: true, totalDenuncias: 0 }
];

const { document, window, chamadas } = montarNavegador({
  sessao: { id: 7, nome: 'Admin Teste', email: 'admin@teste.com', perfil: 'ADMIN' },
  rotas: (metodo, caminho) => {
    if (caminho === '/auth/eu') return { body: { id: 7, nome: 'Admin Teste', email: 'admin@teste.com', perfil: 'ADMIN' } };
    if (caminho === '/admin/resumo') {
      return { body: { usuarios: 2, administradores: 1, denuncias: 2, denunciasUltimos7Dias: 2, numeros: 2, numerosConfirmados: 1, limiteConfirmacao: 3 } };
    }
    if (caminho === '/admin/denuncias') return { body: DENUNCIAS };
    if (caminho === '/admin/usuarios' && metodo === 'GET') return { body: USUARIOS };
    if (caminho === '/admin/usuarios' && metodo === 'POST') return { status: 201, body: { ...USUARIOS[1], id: 9, nome: 'Nova Pessoa' } };
    if (metodo === 'DELETE' && caminho === '/admin/denuncias/100') {
      return { body: { denunciaId: 100, golpistaId: 10, numeroRemovido: false, totalDenunciasRestantes: 2, statusAtual: 'SUSPEITO' } };
    }
    return { status: 404, body: { erro: 'não usado no teste' } };
  }
});
const { initSession } = await import(frontModule('js/session.js'));
const admin = await import(frontModule('js/views/admin.js'));
initSession();
admin.init();
admin.show({});
await aguardar(60);

const $ = s => document.querySelector(s);
const enviarDialogo = async () => {
  $('#formAdmin').dispatchEvent(new window.Event('submit', { cancelable: true }));
  await aguardar(60);
};

test('admin vê o resumo e as denúncias, com o número completo', () => {
  assert.equal($('[data-ak="usuarios"]').textContent, '2');
  assert.equal(document.querySelectorAll('#admTabela tbody tr').length, 2);
  assert.match($('#admTabela').textContent, /\(11\) 90000-0001/);
  assert.match($('#admTabela').textContent, /Conta excluída/, 'denúncia de conta excluída aparece como anônima');
  const chamada = chamadas.find(c => c.caminho === '/admin/denuncias');
  assert.equal(chamada.headers.Authorization, 'Bearer token-de-teste');
});

test('busca e filtro funcionam sem chamar a API de novo', () => {
  const antes = chamadas.length;
  $('#admBusca').value = 'bela flor';
  $('#admBusca').dispatchEvent(new window.Event('input'));
  assert.equal(document.querySelectorAll('#admTabela tbody tr').length, 1);
  $('#admBusca').value = '';
  $('#admBusca').dispatchEvent(new window.Event('input'));
  $('#admFiltro').value = 'anonima';
  $('#admFiltro').dispatchEvent(new window.Event('change'));
  assert.equal(document.querySelectorAll('#admTabela tbody tr').length, 1);
  assert.equal(chamadas.length, antes);
  $('#admFiltro').value = 'todas';
  $('#admFiltro').dispatchEvent(new window.Event('change'));
});

test('remover denúncia pede confirmação e chama DELETE', async () => {
  $('[data-acao="remover-denuncia"][data-id="100"]').click();
  await aguardar();
  assert.ok($('#dlgAdmin').open, 'abre o diálogo de confirmação');
  await enviarDialogo();
  assert.ok(chamadas.some(c => c.metodo === 'DELETE' && c.caminho === '/admin/denuncias/100'));
  assert.ok(!$('#dlgAdmin').open);
  assert.match($('#toast').textContent, /agora tem 2 denúncias/);
});

test('cadastro de usuário valida no front e envia os dados certos', async () => {
  document.querySelector('#v-admin [data-aba="usuarios"]').click();
  await aguardar(60);
  assert.ok(!$('#admNovo').hidden);
  // O próprio admin (fixo) não pode ser excluído: o botão vem desativado.
  assert.ok($('[data-acao="excluir-usuario"][data-id="7"]').disabled);
  // Logado como admin fixo: pode mexer em tudo de um admin promovido.
  assert.ok(!$('[data-acao="editar-usuario"][data-id="6"]').disabled);
  assert.ok(!$('[data-acao="senha-usuario"][data-id="6"]').disabled);
  assert.ok(!$('[data-acao="excluir-usuario"][data-id="6"]').disabled);
  assert.ok(!$('[data-acao="rebaixar"][data-id="6"]').disabled);
  // Os próprios dados continuam editáveis.
  assert.ok(!$('[data-acao="editar-usuario"][data-id="7"]').disabled);

  $('#admNovo').click();
  await aguardar();
  $('#adm_nome').value = 'Nova Pessoa';
  $('#adm_email').value = 'nova@teste.com';
  $('#adm_telefone').value = '(11) 99999-8888';
  $('#adm_senha').value = 'curta';
  await enviarDialogo();
  assert.match($('#dlgAdminMsg').textContent, /pelo menos 8/);
  assert.ok(!chamadas.some(c => c.metodo === 'POST' && c.caminho === '/admin/usuarios'), 'não envia dado inválido');

  $('#adm_senha').value = 'senha-forte-1';
  await enviarDialogo();
  const post = chamadas.find(c => c.metodo === 'POST' && c.caminho === '/admin/usuarios');
  assert.deepEqual(post.corpo, { nome: 'Nova Pessoa', email: 'nova@teste.com', telefone: '(11) 99999-8888', senha: 'senha-forte-1', perfil: 'USUARIO' });
});

test('quem não é admin vê "acesso restrito" e nada é carregado', async () => {
  const antes = chamadas.length;
  window.dispatchEvent(new window.Event('sessao-expirada'));
  admin.show({});
  await aguardar();
  assert.ok(!$('#admRestrito').hidden);
  assert.ok($('#admCorpo').hidden);
  assert.match($('#admRestrito').textContent, /Acesso restrito/);
  assert.equal(chamadas.slice(antes).filter(c => c.caminho.startsWith('/admin')).length, 0);
});
