# ---------- 1) Compilação ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Dependências primeiro: enquanto o pom.xml não mudar, esta camada fica em cache
# e os próximos builds não precisam baixar tudo de novo (deploy mais rápido).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
# Os testes rodam com ./mvnw test (veja o README); aqui só empacotamos para a imagem sair rápido.
RUN mvn -B -q -DskipTests package

# ---------- 2) Execução (imagem menor, só com o Java) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Roda sem privilégios de administrador dentro do contêiner.
RUN useradd --system --uid 1001 --no-create-home app
COPY --from=build /app/target/*.jar app.jar
USER app

# A nuvem define PORT; localmente fica 8081. Defina também JWT_SECRET, SUPABASE_DB_*,
# CORS_ORIGENS e ADMINS_EMAIL (veja o README). Nenhum segredo é gravado na imagem.
# SerialGC: o coletor mais leve, ideal para contêineres pequenos (1 CPU / 512 MB do plano gratuito).
ENV PORT=8081 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
