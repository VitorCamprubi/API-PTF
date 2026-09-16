# ---------- estagio de build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# O pom entra sozinho e as dependencias sao baixadas antes do codigo.
#
# Cada instrucao do Dockerfile vira uma camada em cache, e uma camada so e
# refeita quando o que entrou nela muda. Se o codigo fosse copiado junto com o
# pom, qualquer alteracao numa linha de Java invalidaria a camada e o Maven
# baixaria a internet inteira de novo a cada build. Separando, o download so
# acontece quando as dependencias realmente mudam.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Testes nao rodam aqui: eles precisam de Docker (Testcontainers) e o build da
# imagem acontece dentro de um container. Quem roda a suite e o pipeline, antes
# de mandar construir a imagem.
RUN mvn -B -q clean package -DskipTests

# ---------- imagem final ----------
# JRE, nao JDK: a imagem de execucao nao precisa de compilador, e o que nao esta
# na imagem nao pode ser explorado. Alpine pelo tamanho.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Processo nao roda como root.
#
# Se alguem conseguir execucao de codigo dentro do container, a diferenca entre
# ser root e ser um usuario comum decide se o estrago fica contido. Imagem oficial
# de linguagem roda como root por padrao; corrigir isso e uma linha.
RUN addgroup -S ptf && adduser -S -G ptf ptf

COPY --from=build /build/target/*.jar app.jar
RUN chown -R ptf:ptf /app
USER ptf

EXPOSE 8081

# Sem shell no entrypoint: a JVM vira o PID 1 e recebe o SIGTERM do orquestrador
# diretamente, entao o Spring consegue desligar com calma (terminar o que esta na
# mao, fechar conexoes) em vez de ser morto no meio de uma transacao.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
