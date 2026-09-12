# ---------- estagio 1: dependencias (camada cacheada) ----------
# Separado do build para que mudanca em src/ nao rebaixe o cache do .m2.
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests

# ---------- estagio 2: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY --from=deps /root/.m2 /root/.m2
COPY pom.xml checkstyle.xml ./
COPY src ./src
RUN mvn -B clean package -DskipTests
# Quebra o jar em camadas por volatilidade: as classes da aplicacao ficam
# na ultima, entao um deploy transfere ~2 MB em vez do jar inteiro.
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# ---------- estagio 3: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# O `apk upgrade` roda ANTES do add: a imagem do Temurin costuma vir alguns
# dias atras do indice da Alpine, e as CVEs de openssl/libssl3/libcrypto3 que
# o Trivy aponta ja tem correcao publicada no repositorio -- so nao chegaram
# na imagem base ainda. Sem esta linha, o scan do CI reprova por algo que nao
# depende do nosso codigo e nao temos como corrigir de outro jeito.
#
# O custo e determinismo: duas construcoes da mesma tag podem trazer pacotes
# diferentes. Aceito aqui porque a alternativa e publicar imagem com falha
# conhecida, e porque quem fixa a versao do que importa e o jar, nao o SO.
RUN apk upgrade --no-cache \
 && apk add --no-cache curl tzdata \
 && addgroup -S vigencia && adduser -S vigencia -G vigencia
ENV TZ=America/Sao_Paulo

WORKDIR /app
COPY --from=build --chown=vigencia:vigencia /build/extracted/dependencies/ ./
COPY --from=build --chown=vigencia:vigencia /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=vigencia:vigencia /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=vigencia:vigencia /build/extracted/application/ ./

USER vigencia
EXPOSE 8080

# MaxRAMPercentage: a JVM le o limite do CONTAINER. Sem isso ela dimensiona o
# heap pela RAM do host e o Linux mata o processo (OOMKilled).
# ExitOnOutOfMemoryError: morrer rapido e melhor que agonizar em GC.
# urandom: evita o boot travar esperando entropia.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# 'exec' e obrigatorio: sem ele o sh e o PID 1, o SIGTERM nao chega na JVM e o
# shutdown gracioso vira SIGKILL depois de 10s, no meio de uma transacao.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
