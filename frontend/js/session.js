/** Sessão autenticada: login/registro por e-mail e senha com JWT. */
import { Api } from './api.js';
import { $, esc, msg } from './util.js';
import { ehAdmin } from './config.js';

let cliente = lerCliente();
const ouvintes = new Set();
let modo = 'login';

function lerCliente() {
  try {
    const salvo = JSON.parse(localStorage.getItem('cli') || 'null');
    // Sessões antigas do protótipo não tinham JWT e não podem ser consideradas autenticadas.
    if (!localStorage.getItem('jwt') || !salvo?.id || !salvo?.perfil) {
      localStorage.removeItem('cli');
      return null;
    }
    return salvo;
  } catch { return null; }
}

function gravar(c, token = null) {
  cliente = c;
  try {
    if (c && token) {
      localStorage.setItem('cli', JSON.stringify(c));
      localStorage.setItem('jwt', token);
    } else if (!c) {
      localStorage.removeItem('cli');
      localStorage.removeItem('jwt');
    }
  } catch { /* sem storage */ }
  pintar();
  ouvintes.forEach(f => f(cliente));
}

export const getCliente = () => cliente;
export const isAdmin = () => ehAdmin(cliente);
export const onClienteChange = f => ouvintes.add(f);

function iniciais(nome) {
  const p = String(nome || '').trim().split(/\s+/);
  return ((p[0]?.[0] || '') + (p.length > 1 ? p[p.length - 1][0] : '')).toUpperCase();
}

function pintar() {
  document.body.classList.toggle('is-admin', ehAdmin(cliente));
  $('#avatar').textContent = cliente ? iniciais(cliente.nome) : '?';
  $('#perfilNome').textContent = cliente ? cliente.nome.split(' ')[0] : 'Entrar';
  $('#perfilSub').textContent = !cliente ? 'Acesse sua conta' : ehAdmin(cliente) ? 'Administrador' : 'Cliente conectado';
}

function definirModo(novo) {
  modo = novo;
  const cadastro = modo === 'registro';
  $('#t-perfil').textContent = cadastro ? 'Criar conta' : 'Entrar';
  $('#perfilSubtitulo').textContent = cadastro
    ? 'Cadastre-se para registrar denúncias.'
    : 'Entre com seu e-mail e senha para continuar.';
  $('#campoNome').hidden = !cadastro;
  $('#campoTelefone').hidden = !cadastro;
  $('#perfilNomeInput').required = cadastro;
  $('#perfilTelefone').required = cadastro;
  $('#perfilSenha').autocomplete = cadastro ? 'new-password' : 'current-password';
  $('#btnEntrar').textContent = cadastro ? 'Criar conta' : 'Entrar';
  $('#alternarModo').textContent = cadastro ? 'Já tenho uma conta' : 'Criar uma conta';
  $('#perfilMsg').innerHTML = '';
}

function mostrarAutenticado() {
  $('#perfilForm').hidden = true;
  $('#perfilAutenticado').hidden = false;
  $('#perfilLogadoNome').textContent = cliente.nome;
  $('#perfilLogadoEmail').textContent = cliente.email || '';
  $('#btnSair').hidden = false;
}

function mostrarFormulario() {
  $('#perfilForm').hidden = false;
  $('#perfilAutenticado').hidden = true;
  $('#btnSair').hidden = true;
  $('#formPerfil').reset();
  definirModo('login');
}

export async function abrirPerfil() {
  const dlg = $('#dlgPerfil');
  if (cliente) mostrarAutenticado();
  else mostrarFormulario();
  dlg.showModal();
  if (!cliente) $('#perfilEmail').focus();
}

async function autenticar(e) {
  e.preventDefault();
  const btn = $('#btnEntrar');
  btn.disabled = true;
  $('#perfilMsg').innerHTML = '';
  const email = $('#perfilEmail').value.trim();
  const senha = $('#perfilSenha').value;

  try {
    const resposta = modo === 'registro'
      ? await Api.registrar({
          nome: $('#perfilNomeInput').value.trim(),
          email,
          telefone: $('#perfilTelefone').value.trim(),
          senha
        })
      : await Api.login({ email, senha });

    const c = resposta.cliente;
    if (!resposta.token || !c) throw new Error('A API não retornou os dados da sessão.');
    gravar({ id: c.id, nome: c.nome, email: c.email, perfil: c.perfil }, resposta.token);
    $('#dlgPerfil').close();
  } catch (erro) {
    msg('#perfilMsg', erro.message || 'Não foi possível autenticar.');
  } finally {
    btn.disabled = false;
  }
}

async function validarSessaoSalva() {
  if (!cliente) return;
  try {
    const atual = await Api.eu();
    cliente = { id: atual.id, nome: atual.nome, email: atual.email, perfil: atual.perfil };
    try { localStorage.setItem('cli', JSON.stringify(cliente)); } catch { /* sem storage */ }
    pintar();
    ouvintes.forEach(f => f(cliente));
  } catch (erro) {
    // Token inválido/expirado: encerra a sessão. Falha de rede não apaga a sessão salva.
    if (erro?.status === 401) gravar(null);
  }
}

export function initSession() {
  pintar();
  $('#btnPerfil').addEventListener('click', abrirPerfil);
  $('#formPerfil').addEventListener('submit', autenticar);
  $('#alternarModo').addEventListener('click', () => definirModo(modo === 'login' ? 'registro' : 'login'));
  $('#btnSair').addEventListener('click', () => { gravar(null); $('#dlgPerfil').close(); });
  window.addEventListener('sessao-expirada', () => gravar(null));
  validarSessaoSalva();
}
