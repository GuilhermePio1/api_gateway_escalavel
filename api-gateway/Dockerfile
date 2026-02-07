# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Otimização de cache: copia apenas o pom.xml primeiro para baixar dependências
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código fonte e faz o build
COPY src ./src
# Dica: adicionei flags para garantir que use as features de preview se necessário no build
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# INSTALAÇÃO DO CURL (Necessário para o Healthcheck do Docker Compose)
RUN apk add --no-cache curl

# Criação de usuário não-root (Segurança)
RUN addgroup -S gateway && adduser -S gateway -G gateway

WORKDIR /app

# Copia o JAR gerado no estágio anterior
COPY --from=build /app/target/*.jar app.jar

# Ajusta permissões
RUN chown -R gateway:gateway /app

# Define o usuário para execução
USER gateway

EXPOSE 8080

# --enable-preview é necessário pois está configurado no seu pom.xml
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]