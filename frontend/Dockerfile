FROM nginx:1.27-alpine

# Modelo do nginx: a imagem oficial troca ${VARIAVEIS} pelos valores do ambiente ao iniciar.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
# Gera o config.js na partida, para apontar o front para qualquer API sem recompilar nada.
COPY docker-entrypoint.d/40-gerar-config.sh /docker-entrypoint.d/40-gerar-config.sh
RUN chmod +x /docker-entrypoint.d/40-gerar-config.sh

COPY index.html /usr/share/nginx/html/
COPY css /usr/share/nginx/html/css
COPY js /usr/share/nginx/html/js
COPY assets /usr/share/nginx/html/assets

# Padrão: o front chama "/api" (mesmo domínio) e o nginx repassa ao back-end.
# Para API em outro endereço (serviço separado na nuvem), defina API_URL com a URL completa.
ENV API_URL=/api \
    API_UPSTREAM=http://backend:8081 \
    NGINX_RESOLVER=127.0.0.11
EXPOSE 80
