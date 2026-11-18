/**
 * Componentes visuais do painel de resultado (lado direito das ferramentas).
 * Recebem o ResultadoVerificacao da API e devolvem HTML.
 */
import { esc } from '../util.js';
import { icon } from '../icons.js';

/* [rótulo do selo, tom, título, explicação] por nível de risco */
export const TEXTO_TELEFONE = {
  ALTO: ['Alto risco', 'alto', 'Número denunciado', 'Várias denúncias de Golpe do Presente. Não pague nenhuma taxa de entrega.'],
  MEDIO: ['Atenção', 'medio', 'Número com denúncias', 'Já houve denúncias. Confirme com a loja por um canal oficial antes de pagar.'],
  BAIXO: ['Sem alertas', 'baixo', 'Nenhuma denúncia registrada', 'Isso não garante que seja seguro. Em caso de dúvida, confirme com a loja.']
};
export const TEXTO_MENSAGEM = {
  ALTO: ['Alto risco', 'alto', 'Muito provável Golpe do Presente', 'Esta mensagem tem vários sinais do golpe. Não pague nenhuma taxa e não clique em links.'],
  MEDIO: ['Atenção', 'medio', 'Tem sinais de golpe', 'Alguns pontos lembram o golpe. Confirme com a loja por um canal oficial antes de pagar qualquer coisa.'],
  BAIXO: ['Sem alertas', 'baixo', 'Nenhum sinal forte encontrado', 'Isso não garante que seja seguro. Em caso de dúvida, confirme com a loja.']
};
export const TEXTO_CNPJ = {
  ALTO: ['Alto risco', 'alto', 'CNPJ com problema', 'A empresa não está ativa na Receita Federal. Não pague nada.'],
  MEDIO: ['Atenção', 'medio', 'Empresa recente', 'Empresas novas podem ser legítimas, mas confirme o contato oficial antes de pagar.'],
  BAIXO: ['Sem alertas', 'baixo', 'CNPJ ativo', 'O CNPJ não prova que a mensagem seja legítima: golpistas também usam CNPJ de empresas reais.']
};
const TEXTO_INCONCLUSIVO = ['Sem dados suficientes', 'neutro', 'Ainda não dá para concluir',
  'Há pouca informação para uma conclusão confiável. Isso não significa que seja seguro: na dúvida, não pague nada e confirme com a loja por um canal oficial.'];

const CONFIANCA = { ALTA: 'Alta', MEDIA: 'Média', BAIXA: 'Baixa' };
const CAMADA = {
  REGRAS: 'Regras', CLASSIFICADOR_LOCAL: 'Classificador local', BASE_COLABORATIVA: 'Base colaborativa',
  IA: 'IA', CNPJ_RECEITA: 'Receita Federal'
};

/** Sem histórico e risco baixo => "sem dados suficientes" (resposta honesta). */
function escolher(r, textos) {
  return r.inconclusivo && r.nivelRisco === 'BAIXO' ? TEXTO_INCONCLUSIVO : (textos[r.nivelRisco] || textos.BAIXO);
}

const lista = (itens, ordenada = false) => {
  const tag = ordenada ? 'ol' : 'ul';
  return `<${tag} class="r-list">${(itens || []).map(x => `<li>${esc(x)}</li>`).join('')}</${tag}>`;
};

export function cabecalho({ tom, selo, titulo, texto, medidor }) {
  return `<div class="r-head t-${tom}">
    <div class="r-head-txt">
      <span class="badge ${tom === 'alto' ? '' : tom}">${esc(selo)}</span>
      <h3>${esc(titulo)}</h3>
      <p>${esc(texto)}</p>
    </div>
    <div class="r-side">${medidor ?? ''}
      <button type="button" class="ouvir" data-ouvir aria-label="Ouvir este resultado em voz alta">${icon('volume')}<span>Ouvir</span></button>
    </div>
  </div>`;
}

export function medidor(valor, legenda = 'de 100') {
  const v = Math.max(0, Math.min(100, Number(valor) || 0));
  return `<div class="gauge" style="--v:${v}" role="img" aria-label="Pontuação ${v} ${legenda}">
    <div><span><b>${v}</b><small>${esc(legenda)}</small></span></div></div>`;
}

/**
 * Resultado completo de uma verificação.
 * @param r        ResultadoVerificacao vindo da API
 * @param textos   TEXTO_TELEFONE | TEXTO_MENSAGEM | TEXTO_CNPJ
 * @param extra    { antes: html (após o cabeçalho), acoes: html (rodapé) }
 */
export function resultado(r, textos, extra = {}) {
  const [selo, tom, titulo, texto] = escolher(r, textos);
  const aviso = r.inconclusivo && r.nivelRisco !== 'BAIXO'
    ? '<p class="r-warn">Sem dados suficientes: as análises discordam entre si. Trate como suspeito e confirme por outro canal.</p>'
    : '';
  const fontes = (r.camadas || []).map(c => `<span class="chip plain">${esc(CAMADA[c] || c)}</span>`).join('');
  const meta = r.confianca
    ? `<div class="r-meta"><span>Confiança: <b>${CONFIANCA[r.confianca] || esc(r.confianca)}</b></span>${fontes ? `<span>Fontes:</span><span class="chips">${fontes}</span>` : ''}</div>`
    : '';

  return `${cabecalho({ tom, selo, titulo, texto, medidor: medidor(r.pontuacao, 'de risco') })}
  <div class="r-body t-${tom}">
    ${meta}${aviso}${extra.antes || ''}
    <div class="r-cols">
      <div><h4>O que fazer</h4>${lista(r.recomendacoes, true)}</div>
      <div><h4>Por que esse resultado</h4>${lista(r.motivos)}</div>
    </div>
  </div>
  ${extra.acoes ? `<div class="r-acts">${extra.acoes}</div>` : ''}`;
}

export const vazio = (ic, titulo, texto) =>
  `<div class="empty"><span class="empty-ic">${icon(ic)}</span><b>${esc(titulo)}</b><p>${esc(texto)}</p></div>`;

export const carregando = texto => `<div class="loading"><span class="spinner"></span><span>${esc(texto)}</span></div>`;

export const erro = texto =>
  `<div class="empty"><span class="empty-ic" style="color:var(--brand);background:var(--brand-50)">${icon('alert')}</span><b>Não foi possível concluir</b><p>${esc(texto)}</p></div>`;
