/*
 * Configuração do front-end.
 *
 * Este é o ÚNICO ponto que liga o front ao back-end. Para apontar para outra
 * API (outro computador, servidor de homologação etc.), altere API_URL aqui
 * ou abra a página com ?api=http://IP:8081 (o valor fica salvo no navegador;
 * use ?api=padrao para voltar ao valor deste arquivo).
 */
window.APP_CONFIG = {
  API_URL: 'http://localhost:8081',
  /* Tempo máximo de cada requisição, em milissegundos. */
  TIMEOUT_MS: 12000,
  /* Cobrado >= 1,2x o anunciado = cobrança suspeita. */
  LIMITE_SUSPEITA: 1.2,
  /* Clientes que veem a visão técnica (status da API, camadas de análise).
     Compara pelo nome, sem diferenciar maiúsculas/acentos. Os demais veem
     a versão "de lançamento". Isto é só visual, não é controle de acesso. */
  ADMINS: ['Ana Souza']
};
