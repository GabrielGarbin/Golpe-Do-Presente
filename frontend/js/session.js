/**
 * Sessão do protótipo: qual cliente cadastrado está usando o sistema
 * (necessário para registrar denúncias). Sem senha, apenas demonstração.
 */
import { Api } from './api.js';
import { $, esc, msg } from './util.js';
import { ehAdmin } from './config.js';

let cliente = ler();
const ouvintes = new Set();

function ler() {
  try { return JSON.parse(localStorage.getItem('cli') || 'null'); } catch { return null; }
}
function gravar(c) {
  cliente = c;
  try { c ? localStorage.setItem('cli', JSON.stringify(c)) : localStorage.removeItem('cli'); } catch { /* sem storage */ }
  pintar();
  ouvintes.forEach(f => f(cliente));
}

export const getCliente = () => cliente;
export const isAdmin = () => ehAdmin(cliente);
export const onClienteChange = f => ouvintes.add(f);

function iniciais(nome) {
  const p = nome.trim().split(/\s+/);
  return ((p[0]?.[0] || '') + (p.length > 1 ? p[p.length - 1][0] : '')).toUpperCase();
}

function pintar() {
  // Visão técnica (status da API, camadas, subtítulos) só para administradores.
  document.body.classList.toggle('is-admin', ehAdmin(cliente));
  $('#avatar').textContent = cliente ? iniciais(cliente.nome) : '?';
  $('#perfilNome').textContent = cliente ? cliente.nome.split(' ')[0] : 'Entrar';
  $('#perfilSub').textContent = !cliente ? 'Escolha um cliente' : ehAdmin(cliente) ? 'Administrador' : 'Cliente conectado';
}

export async function abrirPerfil() {
  const dlg = $('#dlgPerfil');
  $('#perfilMsg').innerHTML = '';
  $('#btnSair').hidden = !cliente;
  $('#selCli').innerHTML = '<option>Carregando…</option>';
  dlg.showModal();
  try {
    const lista = await Api.clientes();
    $('#selCli').innerHTML = lista.map(c => `<option value="${c.id}">${esc(c.nome)}</option>`).join('');
    if (cliente) $('#selCli').value = cliente.id;
    $('#btnEntrar').onclick = () => {
      const c = lista.find(x => String(x.id) === $('#selCli').value);
      if (!c) return;
      gravar({ id: c.id, nome: c.nome });
      dlg.close();
    };
  } catch (e) {
    $('#selCli').innerHTML = '';
    msg('#perfilMsg', e.message);
  }
}

export function initSession() {
  pintar();
  $('#btnPerfil').addEventListener('click', abrirPerfil);
  $('#btnSair').addEventListener('click', () => { gravar(null); $('#dlgPerfil').close(); });
}
