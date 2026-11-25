/**
 * Área de administração: moderação de denúncias, gestão de usuários e da base
 * de números.
 *
 * Segurança: esconder a tela é só conforto visual. Quem decide é o back-end,
 * que confere no banco, a cada chamada, se a pessoa ainda é administradora.
 */
import { Api } from '../api.js';
import { $, $$, esc, fmtTel, digits, validTel, msg, toast, bindMasks, plural } from '../util.js';
import { icon } from '../icons.js';
import { isAdmin, getCliente, onClienteChange } from '../session.js';

const ABAS = {
  denuncias: {
    busca: 'Buscar por número, denunciante, loja ou relato',
    filtros: [['todas', 'Todas as denúncias'], ['CONFIRMADO', 'Número confirmado'], ['SUSPEITO', 'Número suspeito'],
      ['anonima', 'Denunciante excluído']],
    carregar: () => Api.admin.denuncias()
  },
  usuarios: {
    busca: 'Buscar por nome, e-mail ou telefone',
    filtros: [['todos', 'Todos os usuários'], ['ADMIN', 'Administradores'], ['USUARIO', 'Usuários comuns'],
      ['semSenha', 'Sem senha definida']],
    carregar: () => Api.admin.usuarios()
  },
  numeros: {
    busca: 'Buscar por número',
    filtros: [['todos', 'Todos os números'], ['CONFIRMADO', 'Confirmados'], ['SUSPEITO', 'Suspeitos']],
    carregar: () => Api.admin.numeros()
  }
};

const CANAIS = {
  WHATSAPP: 'WhatsApp', SMS: 'SMS', LIGACAO: 'Ligação', ENTREGADOR_PRESENCIAL: 'Entregador', OUTRO: 'Outro'
};

let aba = 'denuncias';
const dados = { denuncias: null, usuarios: null, numeros: null };
let acaoDialogo = null;

/* ------------------------------------------------------------- formatação */

const dataHora = s => (s ? new Date(s).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—');
const data = s => (s ? new Date(s).toLocaleDateString('pt-BR') : '—');
const normal = t => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

function seloStatus(status) {
  if (status === 'CONFIRMADO') return '<span class="badge">Confirmado</span>';
  if (status === 'SUSPEITO') return '<span class="badge medio">Suspeito</span>';
  return '<span class="badge neutro">Fora da base</span>';
}

function seloPerfil(u) {
  if (u.perfil === 'ADMIN') return `<span class="badge">${u.adminFixo ? 'Admin fixo' : 'Admin'}</span>`;
  return '<span class="badge neutro">Usuário</span>';
}

function botao(acao, id, ic, rotulo, { perigo = false, desativado = '' } = {}) {
  return `<button type="button" class="icon-btn${perigo ? ' perigo' : ''}" data-acao="${acao}" data-id="${id}"
    aria-label="${esc(rotulo)}" title="${esc(desativado || rotulo)}" ${desativado ? 'disabled' : ''}>${icon(ic)}</button>`;
}

/* ------------------------------------------------------------------ tabelas */

function filtrar(lista) {
  const termo = normal($('#admBusca').value.trim());
  const termoNum = digits($('#admBusca').value);
  const filtro = $('#admFiltro').value;
  return lista.filter(item => {
    if (aba === 'denuncias') {
      if (filtro === 'anonima' && item.clienteId) return false;
      if ((filtro === 'CONFIRMADO' || filtro === 'SUSPEITO') && item.golpistaStatus !== filtro) return false;
      if (!termo) return true;
      return (termoNum && String(item.golpistaTelefone || '').includes(termoNum))
        || [item.clienteNome, item.clienteEmail, item.estabelecimentoFalsificado, item.descricao].some(v => normal(v).includes(termo));
    }
    if (aba === 'usuarios') {
      if ((filtro === 'ADMIN' || filtro === 'USUARIO') && item.perfil !== filtro) return false;
      if (filtro === 'semSenha' && item.temSenha) return false;
      if (!termo) return true;
      return (termoNum && String(item.telefone || '').includes(termoNum))
        || [item.nome, item.email].some(v => normal(v).includes(termo));
    }
    if ((filtro === 'CONFIRMADO' || filtro === 'SUSPEITO') && item.status !== filtro) return false;
    return !termoNum || String(item.telefone || '').includes(termoNum);
  });
}

function tabelaDenuncias(lista) {
  return `<table class="adm-table"><thead><tr>
      <th>Data</th><th>Número denunciado</th><th>Denunciante</th><th>Canal / loja</th><th>Relato</th><th class="col-acoes">Ações</th>
    </tr></thead><tbody>${lista.map(d => `<tr>
      <td data-label="Data">${esc(dataHora(d.data))}</td>
      <td data-label="Número"><b class="tabnum">${esc(fmtTel(d.golpistaTelefone))}</b>
        <span class="adm-sub">${seloStatus(d.golpistaStatus)} ${d.golpistaTotalDenuncias != null ? esc(plural(d.golpistaTotalDenuncias, 'denúncia', 'denúncias')) : ''}</span></td>
      <td data-label="Denunciante">${d.clienteId
        ? `${esc(d.clienteNome || '—')}<span class="adm-sub">${esc(d.clienteEmail || '')}</span>`
        : '<span class="muted">Conta excluída</span>'}</td>
      <td data-label="Canal / loja">${esc(CANAIS[d.canal] || d.canal || '—')}<span class="adm-sub">${esc(d.estabelecimentoFalsificado || 'Loja não informada')}</span></td>
      <td data-label="Relato" class="adm-relato" title="${esc(d.descricao || '')}">${esc(d.descricao || '—')}</td>
      <td class="col-acoes"><div class="adm-acoes">${botao('remover-denuncia', d.id, 'trash', 'Remover denúncia', { perigo: true })}</div></td>
    </tr>`).join('')}</tbody></table>`;
}

function tabelaUsuarios(lista) {
  const eu = getCliente()?.id;
  const euFixo = Boolean(usuario(eu)?.adminFixo);
  return `<table class="adm-table"><thead><tr>
      <th>Nome</th><th>E-mail</th><th>Telefone</th><th>Perfil</th><th>Denúncias</th><th class="col-acoes">Ações</th>
    </tr></thead><tbody>${lista.map(u => {
      const souEu = u.id === eu;
      // Regra: só o admin fixo mexe em outro administrador; os demais, só em si mesmos.
      const outroAdmin = !souEu && u.perfil === 'ADMIN';
      const travaDados = outroAdmin && !euFixo ? 'Só o administrador principal pode alterar outro administrador' : '';
      const travaPerfil = u.adminFixo ? 'Administrador fixo: o perfil só muda na configuração do servidor'
        : souEu && u.perfil === 'ADMIN' ? 'Você não pode remover o seu próprio acesso'
        : outroAdmin && !euFixo ? 'Só o administrador principal pode alterar outro administrador' : '';
      const travaExcluir = u.adminFixo ? 'Administrador fixo não pode ser excluído pelo painel'
        : souEu ? 'Você não pode excluir a sua própria conta' : travaDados;
      return `<tr>
      <td data-label="Nome"><b>${esc(u.nome)}</b>${souEu ? ' <span class="chip plain">você</span>' : ''}${u.temSenha ? '' : '<span class="adm-sub">Sem senha: use “Redefinir senha”</span>'}</td>
      <td data-label="E-mail">${esc(u.email || '—')}</td>
      <td data-label="Telefone" class="tabnum">${esc(u.telefone ? fmtTel(u.telefone) : '—')}</td>
      <td data-label="Perfil">${seloPerfil(u)}</td>
      <td data-label="Denúncias" class="tabnum">${u.totalDenuncias}</td>
      <td class="col-acoes"><div class="adm-acoes">
        ${botao('editar-usuario', u.id, 'edit', 'Editar dados', { desativado: travaDados })}
        ${botao('senha-usuario', u.id, 'key', 'Redefinir senha', { desativado: travaDados })}
        ${u.perfil === 'ADMIN'
          ? botao('rebaixar', u.id, 'users', 'Tornar usuário comum', { desativado: travaPerfil })
          : botao('promover', u.id, 'shield', 'Tornar administrador')}
        ${botao('excluir-usuario', u.id, 'trash', 'Excluir usuário', { perigo: true, desativado: travaExcluir })}
      </div></td></tr>`;
    }).join('')}</tbody></table>`;
}

function tabelaNumeros(lista) {
  return `<table class="adm-table"><thead><tr>
      <th>Número</th><th>Situação</th><th>Denúncias</th><th>Primeira</th><th>Última</th><th class="col-acoes">Ações</th>
    </tr></thead><tbody>${lista.map(g => `<tr>
      <td data-label="Número"><b class="tabnum">${esc(fmtTel(g.telefone))}</b></td>
      <td data-label="Situação">${seloStatus(g.status)}</td>
      <td data-label="Denúncias" class="tabnum">${g.totalDenuncias}</td>
      <td data-label="Primeira">${esc(data(g.dataPrimeiraDenuncia))}</td>
      <td data-label="Última">${esc(data(g.dataUltimaDenuncia))}</td>
      <td class="col-acoes"><div class="adm-acoes">
        ${g.status === 'CONFIRMADO'
          ? botao('suspeito', g.id, 'clock', 'Voltar a suspeito (sai da blacklist)')
          : botao('confirmar', g.id, 'check', 'Confirmar (entra na blacklist)')}
        ${botao('remover-numero', g.id, 'trash', 'Remover número e denúncias', { perigo: true })}
      </div></td></tr>`).join('')}</tbody></table>`;
}

function pintarTabela() {
  const el = $('#admTabela');
  const lista = dados[aba];
  if (!lista) return;
  const visiveis = filtrar(lista);
  $('#admConta').textContent = visiveis.length === lista.length
    ? plural(lista.length, 'registro', 'registros')
    : `${visiveis.length} de ${lista.length}`;
  if (!visiveis.length) {
    el.innerHTML = `<div class="empty"><span class="empty-ic">${icon('inbox')}</span><b>Nada por aqui</b>
      <p>${lista.length ? 'Nenhum registro corresponde à busca ou ao filtro.' : 'Ainda não há registros.'}</p></div>`;
    return;
  }
  el.innerHTML = aba === 'denuncias' ? tabelaDenuncias(visiveis)
    : aba === 'usuarios' ? tabelaUsuarios(visiveis) : tabelaNumeros(visiveis);
}

/* ------------------------------------------------------------------ dados */

function pintarResumo(r) {
  const fmt = n => Number(n || 0).toLocaleString('pt-BR');
  for (const k of ['usuarios', 'administradores', 'denuncias', 'numerosConfirmados']) {
    const el = $(`[data-ak="${k}"]`);
    if (el) el.textContent = fmt(r[k]);
  }
  $('[data-ak-sub="denuncias"]').textContent = `Denúncias · ${fmt(r.denunciasUltimos7Dias)} nos últimos 7 dias`;
  $('[data-ak-sub="numeros"]').textContent = `Confirmados de ${fmt(r.numeros)} números`;
}

async function carregarAba() {
  const el = $('#admTabela');
  if (!dados[aba]) el.innerHTML = '<div class="loading"><span class="spinner"></span><span>Carregando…</span></div>';
  try {
    dados[aba] = await ABAS[aba].carregar();
    pintarTabela();
  } catch (e) {
    el.innerHTML = `<div class="empty"><span class="empty-ic">${icon('alert')}</span><b>Não foi possível carregar</b><p>${esc(e.message)}</p></div>`;
  }
}

/** Resumo e lista da aba atual em paralelo; as outras abas recarregam quando forem abertas. */
async function recarregar() {
  dados.denuncias = dados.usuarios = dados.numeros = null;
  await Promise.allSettled([Api.admin.resumo().then(pintarResumo), carregarAba()]);
}

function trocarAba(nova, { carregar = true } = {}) {
  aba = nova;
  $$('#v-admin [data-aba]').forEach(b => b.setAttribute('aria-selected', String(b.dataset.aba === aba)));
  $('#admBusca').value = '';
  $('#admBusca').placeholder = ABAS[aba].busca;
  $('#admFiltro').innerHTML = ABAS[aba].filtros.map(([v, t]) => `<option value="${v}">${esc(t)}</option>`).join('');
  $('#admNovo').hidden = aba !== 'usuarios';
  if (dados[aba]) pintarTabela(); else if (carregar) carregarAba();
}

/* --------------------------------------------------------------- diálogos */

function abrirDialogo({ titulo, sub = '', corpo = '', ok = 'Salvar', perigo = false, enviar }) {
  $('#t-dlgAdmin').textContent = titulo;
  $('#dlgAdminSub').innerHTML = sub;
  $('#dlgAdminCorpo').innerHTML = corpo;
  $('#dlgAdminMsg').innerHTML = '';
  const btn = $('#dlgAdminOk');
  btn.textContent = ok;
  btn.classList.toggle('btn-perigo', perigo);
  btn.disabled = false;
  bindMasks($('#dlgAdminCorpo'));
  acaoDialogo = enviar;
  $('#dlgAdmin').showModal();
  ($('#dlgAdminCorpo input:not([disabled])') || btn).focus();
}

/** Lê os campos do diálogo pelo nome (mesma ideia do FormData, sem depender dele). */
function lerFormulario(form) {
  return {
    get(nome) {
      const el = form.elements.namedItem(nome);
      if (!el) return null;
      if (el.type === 'checkbox') return el.checked ? el.value : null;
      return el.value;
    }
  };
}

async function enviarDialogo(e) {
  e.preventDefault();
  if (!acaoDialogo) return;
  const btn = $('#dlgAdminOk');
  btn.disabled = true;
  $('#dlgAdminMsg').innerHTML = '';
  try {
    const aviso = await acaoDialogo(lerFormulario($('#formAdmin')));
    $('#dlgAdmin').close();
    if (aviso) toast(aviso);
    await recarregar();
  } catch (erro) {
    msg('#dlgAdminMsg', erro.message || 'Não foi possível concluir.');
  } finally {
    btn.disabled = false;
  }
}

const campo = (nome, rotulo, valor = '', extra = '') => `<label for="adm_${nome}">${rotulo}</label>
  <input id="adm_${nome}" name="${nome}" value="${esc(valor)}" ${extra}>`;

function lerUsuario(fd, { comSenha }) {
  const u = {
    nome: String(fd.get('nome') || '').trim(),
    email: String(fd.get('email') || '').trim(),
    telefone: String(fd.get('telefone') || '').trim()
  };
  if (u.nome.length < 2) throw new Error('Informe o nome (2 a 100 caracteres).');
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(u.email)) throw new Error('E-mail inválido.');
  if (!validTel(u.telefone)) throw new Error('Informe o telefone com DDD (10 ou 11 dígitos).');
  if (comSenha) {
    u.senha = String(fd.get('senha') || '');
    if (u.senha.length < 8) throw new Error('A senha deve ter pelo menos 8 caracteres.');
    u.perfil = fd.get('perfil') || 'USUARIO';
  }
  return u;
}

const usuario = id => (dados.usuarios || []).find(u => u.id === id);
const denuncia = id => (dados.denuncias || []).find(d => d.id === id);
const numero = id => (dados.numeros || []).find(g => g.id === id);

const ACOES = {
  'remover-denuncia': id => {
    const d = denuncia(id);
    abrirDialogo({
      titulo: 'Remover denúncia?', ok: 'Remover', perigo: true,
      sub: `Denúncia contra <b>${esc(fmtTel(d?.golpistaTelefone))}</b>${d?.clienteNome ? `, feita por ${esc(d.clienteNome)}` : ''}.`,
      corpo: '<p class="adm-nota">O contador do número é recalculado. Se ele ficar sem denúncias, sai da base; se cair abaixo do limite, sai da blacklist.</p>',
      enviar: async () => {
        const r = await Api.admin.removerDenuncia(id);
        return r?.numeroRemovido ? 'Denúncia removida. O número ficou sem denúncias e saiu da base.'
          : `Denúncia removida. O número agora tem ${plural(r?.totalDenunciasRestantes ?? 0, 'denúncia', 'denúncias')}.`;
      }
    });
  },

  'novo-usuario': () => abrirDialogo({
    titulo: 'Novo usuário', ok: 'Cadastrar',
    sub: 'A pessoa poderá entrar com este e-mail e esta senha.',
    corpo: campo('nome', 'Nome completo', '', 'autocomplete="off" maxlength="100"')
      + campo('email', 'E-mail', '', 'type="email" autocomplete="off" maxlength="200"')
      + campo('telefone', 'Telefone com DDD', '', 'data-mask="tel" inputmode="tel" placeholder="(11) 99999-9999"')
      + campo('senha', 'Senha inicial (mínimo 8 caracteres)', '', 'type="password" autocomplete="new-password" maxlength="128"')
      + `<label for="adm_perfil">Perfil</label><select id="adm_perfil" name="perfil">
           <option value="USUARIO">Usuário comum</option><option value="ADMIN">Administrador</option></select>`,
    enviar: async fd => {
      const u = await Api.admin.criarUsuario(lerUsuario(fd, { comSenha: true }));
      return `${u.nome} foi cadastrado${u.perfil === 'ADMIN' ? ' como administrador' : ''}.`;
    }
  }),

  'editar-usuario': id => {
    const u = usuario(id);
    abrirDialogo({
      titulo: 'Editar usuário', sub: u.adminFixo ? 'Administrador fixo: o e-mail só muda na configuração do servidor.' : '',
      corpo: campo('nome', 'Nome completo', u.nome, 'maxlength="100"')
        + campo('email', 'E-mail', u.email, `type="email" maxlength="200" ${u.adminFixo ? 'readonly' : ''}`)
        + campo('telefone', 'Telefone com DDD', u.telefone ? fmtTel(u.telefone) : '', 'data-mask="tel" inputmode="tel"'),
      enviar: async fd => {
        await Api.admin.editarUsuario(id, lerUsuario(fd, { comSenha: false }));
        return 'Dados atualizados.';
      }
    });
  },

  'senha-usuario': id => {
    const u = usuario(id);
    abrirDialogo({
      titulo: 'Redefinir senha', ok: 'Redefinir',
      sub: `Nova senha para <b>${esc(u.nome)}</b>. Informe-a à pessoa por um canal seguro.`,
      corpo: campo('senha', 'Nova senha (mínimo 8 caracteres)', '', 'type="password" autocomplete="new-password" maxlength="128"')
        + campo('confirma', 'Repita a nova senha', '', 'type="password" autocomplete="new-password" maxlength="128"'),
      enviar: async fd => {
        const senha = String(fd.get('senha') || '');
        if (senha.length < 8) throw new Error('A senha deve ter pelo menos 8 caracteres.');
        if (senha !== fd.get('confirma')) throw new Error('As duas senhas não são iguais.');
        await Api.admin.redefinirSenha(id, senha);
        return `Senha de ${u.nome} redefinida.`;
      }
    });
  },

  promover: id => {
    const u = usuario(id);
    abrirDialogo({
      titulo: 'Tornar administrador?', ok: 'Tornar administrador',
      sub: `<b>${esc(u.nome)}</b> passará a ver esta área e poderá remover denúncias e gerenciar usuários.`,
      enviar: async () => { await Api.admin.alterarPerfil(id, 'ADMIN'); return `${u.nome} agora é administrador.`; }
    });
  },

  rebaixar: id => {
    const u = usuario(id);
    abrirDialogo({
      titulo: 'Remover acesso de administrador?', ok: 'Tornar usuário comum', perigo: true,
      sub: `<b>${esc(u.nome)}</b> perde o acesso a esta área imediatamente.`,
      enviar: async () => { await Api.admin.alterarPerfil(id, 'USUARIO'); return `${u.nome} agora é usuário comum.`; }
    });
  },

  'excluir-usuario': id => {
    const u = usuario(id);
    abrirDialogo({
      titulo: 'Excluir usuário?', ok: 'Excluir', perigo: true,
      sub: `A conta de <b>${esc(u.nome)}</b> (${esc(u.email || 'sem e-mail')}) será apagada.`,
      corpo: u.totalDenuncias
        ? `<label class="adm-check"><input type="checkbox" name="apagarDenuncias" value="1">
             Apagar também ${u.totalDenuncias === 1 ? 'a denúncia feita' : `as ${u.totalDenuncias} denúncias feitas`} por essa pessoa</label>
           <p class="adm-nota">Sem marcar, as denúncias continuam na base como anônimas.</p>`
        : '<p class="adm-nota">Essa pessoa não fez denúncias.</p>',
      enviar: async fd => {
        await Api.admin.excluirUsuario(id, fd.get('apagarDenuncias') === '1');
        return `Conta de ${u.nome} excluída.`;
      }
    });
  },

  confirmar: async id => {
    await Api.admin.alterarStatusNumero(id, 'CONFIRMADO');
    toast(`${fmtTel(numero(id)?.telefone)} confirmado: entrou na blacklist.`);
    await recarregar();
  },

  suspeito: async id => {
    await Api.admin.alterarStatusNumero(id, 'SUSPEITO');
    toast(`${fmtTel(numero(id)?.telefone)} voltou a suspeito. Novas denúncias podem confirmá-lo de novo.`);
    await recarregar();
  },

  'remover-numero': id => {
    const g = numero(id);
    abrirDialogo({
      titulo: 'Remover número da base?', ok: 'Remover', perigo: true,
      sub: `<b>${esc(fmtTel(g?.telefone))}</b> e ${esc(plural(g?.totalDenuncias ?? 0, 'denúncia', 'denúncias'))} contra ele serão apagados.`,
      corpo: '<p class="adm-nota">Use para números denunciados por engano. Não dá para desfazer.</p>',
      enviar: async () => { await Api.admin.removerNumero(id); return 'Número removido da base.'; }
    });
  }
};

/* ------------------------------------------------------------- ciclo de vida */

function mostrarRestrito() {
  $('#admCorpo').hidden = true;
  $('.adm-seg').hidden = true;
  const el = $('#admRestrito');
  el.hidden = false;
  el.innerHTML = `<div class="empty"><span class="empty-ic">${icon('shield')}</span><b>Acesso restrito</b>
    <p>Esta área é só para administradores. ${getCliente() ? 'Sua conta não tem esse acesso.' : 'Entre com uma conta de administrador.'}</p></div>`;
}

export function init() {
  $$('#v-admin [data-aba]').forEach(b => b.addEventListener('click', () => trocarAba(b.dataset.aba)));
  $('#admBusca').addEventListener('input', pintarTabela);
  $('#admFiltro').addEventListener('change', pintarTabela);
  $('#admAtualizar').addEventListener('click', recarregar);
  $('#admNovo').addEventListener('click', () => ACOES['novo-usuario']());
  $('#formAdmin').addEventListener('submit', enviarDialogo);
  $('#dlgAdminCancelar').addEventListener('click', () => $('#dlgAdmin').close());
  $('#admTabela').addEventListener('click', async e => {
    const b = e.target.closest('[data-acao]');
    if (!b || b.disabled) return;
    try {
      await ACOES[b.dataset.acao](Number(b.dataset.id));
    } catch (erro) {
      toast(erro.message || 'Não foi possível concluir.');
    }
  });
  // Entrou, saiu ou mudou de perfil enquanto estava nesta tela: redesenha.
  onClienteChange(() => { if (location.hash.startsWith('#/admin')) show({}); });
  trocarAba('denuncias', { carregar: false }); // só prepara a tela; os dados vêm no show(), se for admin
}

export function show({ aba: pedida } = {}) {
  if (!isAdmin()) {
    mostrarRestrito();
    return;
  }
  $('#admRestrito').hidden = true;
  $('#admCorpo').hidden = false;
  $('.adm-seg').hidden = false;
  if (pedida && ABAS[pedida] && pedida !== aba) trocarAba(pedida);
  recarregar();
}
