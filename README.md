# Golpe do Presente - A3 (Sistemas Distribuídos)

Protótipo que ajuda a pessoa a **prevenir** o Golpe do Presente (brinde falso + cobrança
inflada na maquininha) e a **denunciar** os golpistas, alimentando uma base colaborativa.

- **Back-end:** Spring Boot (Java), dados em memória.
- **Front-end:** `index.html` (HTML/CSS/JS puro). Cópia servida pelo Spring em `src/main/resources/static/`.
  Se editar um, copie para o outro.

## Como rodar

```bash
./mvnw spring-boot:run        # sobe em http://localhost:8080 (com dados de demonstração)
./mvnw test                   # testes unitários
```

Abra `http://localhost:8080` ou o `index.html`. Para apontar o front para outra API:
`index.html?api=http://IP:8080`. Roteiro de chamadas prontas em `demonstracao.http`.

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

Edite `src/main/resources/analise/corpus.txt` (formato `G|texto` para golpe, `L|texto` para
mensagem legítima). Prefira mensagens reais, anonimizadas. Misture exemplos legítimos parecidos
com golpe (avisos de entrega, promoções com "taxa de entrega grátis") para reduzir falsos alertas.

## Limitações assumidas (para o artigo e a apresentação)

- O corpus inicial é **pequeno e sintético** (escrito à mão): a nota do classificador é uma
  estimativa, não uma medida de acurácia. Há risco de falso alerta em promoções legítimas.
- Um resultado BAIXO **nunca** garante segurança; toda resposta traz recomendações.
- A consulta de CNPJ usa a BrasilAPI (serviço gratuito de terceiros, sem garantia de
  disponibilidade). Golpistas também usam CNPJ de empresas reais; o CNPJ é só um indício.
- Os dados ficam em memória e se perdem ao reiniciar. Persistência em banco é a evolução natural.
- A cota da IA também fica em memória e zera ao reiniciar.
