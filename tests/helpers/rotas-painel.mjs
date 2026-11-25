/** Respostas falsas da API usadas pelos testes do Painel. */
export const NUMERO = '11987650001';

export function rotasPainel(perfil) {
  return (metodo, caminho) => {
    if (caminho === '/golpistas/blacklist') {
      return { body: [{ id: 1, telefone: NUMERO, totalDenuncias: 3, status: 'CONFIRMADO', dataUltimaDenuncia: new Date().toISOString() }] };
    }
    if (caminho === '/denuncias/estatisticas') {
      return { body: { totalDenuncias: 3, numerosDenunciados: 1, numerosNaBlacklist: 1, limiteConfirmacao: 3, denunciasPorCanal: {}, estabelecimentosMaisImitados: {} } };
    }
    if (caminho === '/prevencao/camadas') return { body: { regras: true, classificadorLocal: true, baseColaborativa: true, ia: false } };
    if (caminho === '/auth/eu') {
      return perfil ? { body: { id: 7, nome: 'Pessoa Teste', email: 't@t.com', perfil } } : { status: 401, body: { erro: 'Faça login.' } };
    }
    return { status: 404, body: { erro: 'não usado no teste' } };
  };
}
