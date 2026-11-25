# ---------- 1) Compilação ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Os testes rodam com ./mvnw test (veja o README); aqui só empacotamos para a imagem sair rápido.
RUN mvn -B -q -DskipTests package

# ---------- 2) Execução (imagem menor, só com o Java) ----------
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar


# A nuvem define PORT; localmente fica 8081. Defina também JWT_SECRET, SUPABASE_DB_*,
# CORS_ORIGENS e ADMINS_EMAIL (veja o README). Nenhum segredo é gravado na imagem.
ENV PORT=8081 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
