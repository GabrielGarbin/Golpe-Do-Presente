# Golpe do Presente - A3 (Sistemas Distribuídos)

Protótipo que ajuda a pessoa a **prevenir** o Golpe do Presente (brinde falso + cobrança
inflada na maquininha) e a **denunciar** os golpistas, alimentando uma base colaborativa.

- **Back-end:** Spring Boot (Java) + Spring Data JPA, na pasta `backend/`. Só expõe a API REST (JSON).
- **Banco de dados:** Supabase (PostgreSQL), projeto `dieflaymwtpupagshxyz`.
- **Front-end:** HTML/CSS/JavaScript (módulos ES, sem build), na pasta `frontend/`.

## Arquitetura: front e back separados

```
Golpe-Do-Presente/
├── backend/                 API REST (Spring Boot) - porta 8081
│   ├── src/main/java/...    controllers, services, análise, repositórios
│   ├── supabase/migrations/ esquema do banco
│   └── demonstracao.http    roteiro de chamadas da API
└── frontend/                Interface (qualquer servidor de arquivos estáticos)
    ├── index.html           estrutura das telas
    ├── config.js            ÚNICO ponto de ligação com o back (API_URL)
    ├── css/tokens.css       cores, fontes, espaçamentos (identidade visual)
    ├── css/app.css          layout e componentes
    ├── assets/              logo do Bradesco (ver assets/LEIA-ME.md)
    └── js/
        ├── api.js           contrato com a API: todas as URLs ficam aqui
        ├── router.js        navegação por abas (#/painel, #/consultar...)
        ├── session.js       cliente escolhido (protótipo, sem senha)
        ├── ui/resultado.js  componente de resultado de risco
        └── views/           uma tela por arquivo
```

- Os dois lados **só conversam por HTTP/JSON**. O back não serve mais o HTML e o front não
  conhece nada do Java: dá para alterar, publicar ou substituir um sem tocar no outro.
- Se um endpoint mudar, ajuste apenas `frontend/js/api.js`. Para apontar o front para outra API,
  altere `frontend/config.js` (ou abra a página com `?api=http://IP:8081`; `?api=padrao` volta ao valor do arquivo).
- CORS: a API aceita as origens de `app.cors.origens` (`application.properties`). No protótipo é `*`;
  ao publicar, informe o endereço real do front.

### Requisições em paralelo no front

- O **Painel** dispara as 4 consultas (estatísticas, golpistas, denúncias, camadas) ao mesmo tempo, e
  cada bloco aparece assim que a sua resposta chega. Se uma falhar, só aquele bloco mostra erro.
- **Consultar número** busca o risco e o histórico do número em paralelo (`Promise.all`).
- Toda requisição tem **tempo limite** e é **cancelável** (`AbortController`): se a pessoa consultar de novo
  antes da resposta, a consulta antiga é cancelada e não sobrescreve a nova ("a última vence").
- Respostas que quase não mudam (checklist, camadas) ficam em cache durante a sessão.

## Interface

Layout de aplicação (cabeçalho + menu lateral + abas). No computador, **cada aba cabe inteira na
tela, sem rolar a página** (testado em 1366×768 e 1920×1080); listas longas rolam dentro do próprio
card. No celular o menu vira uma barra de abas e o conteúdo empilha.

| Aba | O que mostra |
|-----|--------------|
| Painel | Indicadores, alertas recentes, denúncias por canal, lojas mais imitadas, como o golpe funciona, camadas ativas |
| Consultar número | Risco do número + histórico de denúncias |
| Analisar mensagem | Nota de risco, confiança, fontes, motivos e recomendações |
| Verificar loja (CNPJ) | Situação na Receita Federal |
| Posso receber? | Assistente de prevenção: 4 perguntas de sim/não e uma recomendação clara (funciona sem depender da base) |
| Denunciar golpe | Formulário da base colaborativa; para quem já pagou, compara o valor anunciado com o cobrado e mostra o que fazer |
| Orientações | Checklist (antes, na hora da entrega e se já pagou) + teste interativo "Golpe ou não?" |

### Acessibilidade

Botão **Acessibilidade** no cabeçalho (as escolhas ficam salvas no navegador):

- **Tamanho do texto** de 100% a 145% (com texto grande, a área de conteúdo passa a rolar).
- **Alto contraste**: texto preto, bordas fortes e foco destacado em amarelo.
- **Ouvir**: cada resultado tem um botão que lê em voz alta o risco e o que fazer
  (Web Speech API do próprio navegador, sem serviço externo).
- **VLibras**: tradutor oficial do governo para Libras (precisa de internet; pode ser desligado).
- Base: rótulos em todos os campos, resultados anunciados a leitores de tela, navegação por teclado
  com foco visível, risco sempre escrito (nunca só pela cor) e respeito a "reduzir movimento".

> O logo é carregado de `frontend/assets/logo-bradesco.png` (para trocar, veja
> `assets/LEIA-ME.md`). A interface traz o aviso "Protótipo acadêmico · A3" para deixar claro
> que não é um canal oficial do banco.

## Banco de dados (Supabase)

Clientes, golpistas e denúncias ficam salvos no PostgreSQL do Supabase. O esquema está em
`supabase/migrations/` (tabelas `clientes`, `golpistas`, `denuncias`, com RLS ligado).

**1. Criar as tabelas (uma vez)** — com a [Supabase CLI](https://supabase.com/docs/guides/cli):

```bash
cd backend
supabase login
supabase init                                   # cria supabase/config.toml (mantém a pasta migrations)
supabase link --project-ref dieflaymwtpupagshxyz
supabase db push                                # aplica supabase/migrations/*.sql
```

Sem a CLI: abra o **SQL Editor** do painel do Supabase e execute
os arquivos de `backend/supabase/migrations/`, em ordem.

**2. Informar a conexão à API (uma vez por computador)** — crie o arquivo
`backend/config/application.properties` (ele está no `.gitignore` e **nunca** vai para o GitHub):

```properties
spring.datasource.url=jdbc:postgresql://aws-1-us-west-2.pooler.supabase.com:5432/postgres?sslmode=require
spring.datasource.username=postgres.dieflaymwtpupagshxyz
spring.datasource.password=SENHA_DO_BANCO
```

O endereço vem do Supabase em **Connect → Direct → Session pooler**; a senha, de
**Project Settings → Database → Database password**. O Spring Boot lê esse arquivo sozinho quando
a API é iniciada dentro da pasta `backend`. (Alternativa: as variáveis `SUPABASE_DB_URL`,
`SUPABASE_DB_USER` e `SUPABASE_DB_PASSWORD` em `backend/.env`.)

> Nunca escreva senhas ou chaves em `src/main/resources/application.properties`: esse arquivo vai para o GitHub.

Os dados de demonstração só são inseridos quando o banco está vazio (primeira execução).
Para não inseri-los, use `app.demo.seed=false`.

## Como rodar

### Com um comando (recomendado)

| Sistema | Como iniciar |
|---------|--------------|
| Windows | Dois cliques em **`iniciar.cmd`** (ou `.\iniciar.cmd` no terminal) |
| Mac / Linux | `./iniciar.sh` |

O script confere a configuração do banco, sobe a API e o front-end, **espera a API ficar pronta**
e abre o navegador em `http://localhost:5500`. Se algo já estiver rodando, ele reaproveita.
No Windows, a API e o front abrem em duas janelas (feche-as para parar); no Mac/Linux, Ctrl+C
encerra os dois (logs em `api.log` e `front.log`).

Requisitos: Java 21 e Python 3 (ou Node.js), além de `backend/config/application.properties`
com a conexão do banco (seção "Banco de dados").

### Com Docker

```bash
docker compose up --build      # depois abra http://localhost:5500
```

Precisa de `backend/.env` (copie de `backend/.env.example`) e de `.env` na raiz com `JWT_SECRET`.

### Manualmente (dois terminais)

```bash
cd backend && ./mvnw spring-boot:run          # API em http://localhost:8081 (Windows: .\mvnw.cmd)
cd frontend && python -m http.server 5500     # front em http://localhost:5500
```

Testes: `cd backend && ./mvnw test` (H2 em memória, não toca no Supabase) e
`cd tests && npm ci && npm test` (front-end). O selo **"API conectada"** no cabeçalho confirma a
comunicação. Roteiro de chamadas prontas da API em `backend/demonstracao.http`.

## Área de administração

Menu **Administração** (aparece só para administradores). Três abas, com busca e filtros:

| Aba | O que o administrador faz |
|-----|---------------------------|
| Denúncias | Vê quem denunciou, o número, canal, loja e relato; **remove** denúncias. O número é recalculado: sai da blacklist se cair abaixo do limite e sai da base se ficar sem denúncias. |
| Usuários | **Cadastra**, **edita**, **redefine a senha**, **promove a administrador** ou **rebaixa** e **exclui** contas (mantendo as denúncias como anônimas ou apagando-as junto). |
| Números | **Confirma manualmente** (entra na blacklist), **volta a suspeito** ou **remove** o número e suas denúncias. |

**Quem é administrador**: os e-mails de `ADMINS_EMAIL` (administradores **fixos**, que garantem
sempre existir um) e quem for **promovido** no painel (tabela `administradores`).

**Travas de segurança**: o back-end confere no banco, **a cada chamada**, se a pessoa ainda é
administradora (quem é rebaixado ou excluído perde o acesso na hora, sem esperar o token
expirar). Ninguém pode se excluir ou se rebaixar, e administradores fixos não podem ser
rebaixados, excluídos nem ter o e-mail trocado pelo painel. Esconder o menu no front é só
conforto visual: a proteção é do back-end.

**Migração necessária para promover administradores**: aplique
`backend/supabase/migrations/20261125000000_administradores.sql` (SQL Editor do Supabase ou
`supabase db push`). Sem ela, todo o resto funciona normalmente; só a promoção fica indisponível
(com uma mensagem explicando o motivo).

Rotas (todas exigem administrador): `GET /admin/resumo`, `GET|POST /admin/usuarios`,
`GET|PUT|DELETE /admin/usuarios/{id}`, `PUT /admin/usuarios/{id}/senha`,
`PUT /admin/usuarios/{id}/perfil`, `GET /admin/denuncias`, `DELETE /admin/denuncias/{id}`,
`PUT /admin/numeros/{id}/status`, `DELETE /admin/numeros/{id}`.

## Publicação no Render (Docker)

### Jeito rápido: Blueprint (`render.yaml`)

Render → **New + → Blueprint** → escolha o repositório → **Apply**. Os dois serviços (API e
front) são criados juntos, já com health check, região perto do banco e `JWT_SECRET` gerado
automaticamente. A tela pede só o que não pode ficar no Git:

| Serviço | Preencha |
|---------|----------|
| API | `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `ADMINS_EMAIL`, `CORS_ORIGENS` (URL do front) |
| Front | `API_URL` (URL da API) |

As URLs só existem depois que os serviços são criados: na primeira vez, aplique, copie as duas
URLs (`https://...onrender.com`) e preencha `CORS_ORIGENS` e `API_URL` em **Environment**.

O front chama a API **direto pela URL dela**: assim o back-end enxerga o IP real de cada pessoa,
e as cotas por usuário (CNPJ, IA) e o bloqueio de login funcionam como esperado.

### Deploy mais rápido

- O `backend/Dockerfile` baixa as dependências do Maven numa camada separada: se o `pom.xml`
  não mudou, os próximos builds reaproveitam essa camada.
- A API roda com `-XX:+UseSerialGC`, o coletor mais leve para o contêiner pequeno do plano
  gratuito, e sem privilégios de administrador.
- **Plano gratuito**: o serviço "dorme" após 15 min sem acesso e a primeira requisição demora
  para acordar. **Antes de apresentar**, abra o front e a URL `/saude` da API alguns minutos antes.
- `JWT_SECRET` precisa ser fixo: se mudar, todos os logins caem.
- O modo `/api` (proxy do nginx) é o usado pelo `docker compose` local. Na nuvem, se for usá-lo,
  defina também `API_UPSTREAM=https://SUA-API.onrender.com` e `NGINX_RESOLVER` com um DNS válido
  no serviço (o padrão `127.0.0.11` só existe dentro do Docker Compose); nesse modo as cotas
  passam a ver o IP do front, não o de cada pessoa.

## Testes

| Onde | Comando | O que verifica |
|------|---------|----------------|
| `backend/` | `./mvnw test` | API + banco (H2 em memória). `IntegracaoFrontBackApiTest` envia o mesmo JSON que o front envia e confere a denúncia **direto no banco** (todos os campos, contador do golpista, nada gravado em caso de erro, CORS da origem do front). |
| `tests/` | `npm install` e `npm test` | Front no Node (jsdom, sem navegador): envio da denúncia, botão do WebDenúncia (SSP-SP), número completo para admin e mascarado para cliente, e o **contrato** (toda rota de `api.js` existe num controller Java; campos e canais batem com o back). |
| `tests/` | `API_URL=http://localhost:8081 npm run e2e` | Ponta a ponta com a API **rodando de verdade**, usando o próprio `frontend/js/api.js`: cadastro → denúncia → estatísticas e consulta atualizadas. Com `ADMIN_EMAIL` e `ADMIN_SENHA`, lê a denúncia de volta da base. **Grava dados reais**: use um banco de desenvolvimento. |

## Problema da "partida a frio" (feedback da orientadora)

A base de denúncias começa vazia, então só ela não protege ninguém no início. A solução é
**não depender só da base**: a análise tem camadas que funcionam desde o primeiro dia, e o
sistema é honesto quando não tem informação suficiente.

### Camadas de análise da mensagem (`POST /prevencao/mensagem`)

| # | Camada | Custo | O que faz |
|---|--------|-------|-----------|
| 1 | **Regras** (`AnalisadorRegras`) | zero | Palavras-chave com pesos (brinde, entregador, taxa, maquininha, link, urgência...). Explicável: cada regra vira um motivo. |
| 2 | **Classificador local** (`ClassificadorLocal`) | zero | Naive Bayes em Java puro, treinado com `resources/analise/corpus.txt`. Mede a semelhança com mensagens de golpe. |
| 3 | **Base colaborativa** (`GolpistaService`) | zero | Se o remetente for informado, cruza com a blacklist. |
| 4 | **IA externa** (`AnalisadorIA`) | pago, **opcional** | Só é consultada na "zona de dúvida". Desligada por padrão. |

- Nota local = 50% regras + 50% classificador. O classificador sozinho não chega a ALTO.
- A resposta traz `confianca` (ALTA/MEDIA/BAIXA), `inconclusivo` (true = "sem dados suficientes")
  e `camadas` (quais fontes foram usadas).
- Número sem denúncias (`GET /prevencao/telefone`) vem como BAIXO **e inconclusivo**: a ausência de
  denúncias não é prova de segurança.
- O classificador **aprende com a comunidade**: relatos de números CONFIRMADOS (denunciados por
  várias pessoas distintas) entram no modelo. Uma denúncia isolada não treina nada, o que impede
  envenenamento por denúncia falsa.

### Outros endpoints de prevenção

| Endpoint | Para quê |
|----------|----------|
| `GET /prevencao/telefone?numero=` | Consulta um número na base colaborativa |
| `GET /prevencao/cnpj?numero=` | Confere a loja na Receita (via BrasilAPI): situação e tempo de abertura |
| `GET /prevencao/checklist` | O que fazer antes, durante e depois da entrega |
| `GET /prevencao/camadas` | Mostra quais camadas estão ativas |

## Ligando a IA externa (opcional)

Sem IA o sistema já funciona. Para ligar, em `application.properties`:

```properties
app.ia.habilitada=true
```

e defina a chave **em variável de ambiente** (nunca no código, nunca no GitHub):

```bash
export ANTHROPIC_API_KEY=sua-chave      # Windows (PowerShell): $env:ANTHROPIC_API_KEY="sua-chave"
```

Proteções de custo (ajustáveis nas propriedades `app.ia.*`): consulta só quando as camadas
locais ficam em dúvida; cota diária por IP (10) e global (200); cache por mensagem; texto
truncado em 1000 caracteres; resposta curta; timeout de 10 s. Se a IA falhar ou a cota acabar,
o sistema continua só com as camadas locais. Para outro provedor, implemente `AnalisadorIA`.

> Defina também um **teto de gasto** no painel do provedor da IA.

## Como melhorar o classificador

Edite `backend/src/main/resources/analise/corpus.txt` (formato `G|texto` para golpe, `L|texto` para
mensagem legítima). Prefira mensagens reais, anonimizadas. Misture exemplos legítimos parecidos
com golpe (avisos de entrega, promoções com "taxa de entrega grátis") para reduzir falsos alertas.

## Limitações assumidas (para o artigo e a apresentação)

- O corpus inicial é **pequeno e sintético** (escrito à mão): a nota do classificador é uma
  estimativa, não uma medida de acurácia. Há risco de falso alerta em promoções legítimas.
- Um resultado BAIXO **nunca** garante segurança; toda resposta traz recomendações.
- A consulta de CNPJ usa a BrasilAPI (serviço gratuito de terceiros, sem garantia de
  disponibilidade). Golpistas também usam CNPJ de empresas reais; o CNPJ é só um indício.
- Os dados ficam no Supabase. O classificador local continua em memória, mas é re-treinado
  ao iniciar com os relatos de números CONFIRMADOS salvos no banco.
- A cota da IA também fica em memória e zera ao reiniciar.
