FROM node:20-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
      git curl ca-certificates gnupg && \
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
      -o /usr/share/keyrings/githubcli-archive-keyring.gpg && \
    chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
      > /etc/apt/sources.list.d/github-cli.list && \
    apt-get update && apt-get install -y --no-install-recommends gh && \
    rm -rf /var/lib/apt/lists/*

# Versión pineada para builds reproducibles. Actualizar deliberadamente y
# probar antes de subir (npm view opencode-ai version).
RUN npm install -g opencode-ai@1.18.18

# Toolchain de build del backend (el developer compila y corre tests localmente;
# no depender del CI para feedback). Misma versión que instaló el orchestrator
# en el Sprint 1 (Temurin 21.0.12+8 + Maven 3.9.9).
RUN mkdir -p /opt/tools && \
    curl -fsSL "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12_8.tar.gz" -o /tmp/jdk.tar.gz && \
    tar -xzf /tmp/jdk.tar.gz -C /opt/tools && \
    rm /tmp/jdk.tar.gz && \
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" -o /tmp/maven.tar.gz && \
    tar -xzf /tmp/maven.tar.gz -C /opt/tools && \
    rm /tmp/maven.tar.gz

ENV JAVA_HOME=/opt/tools/jdk-21.0.12+8
ENV PATH="$JAVA_HOME/bin:/opt/tools/apache-maven-3.9.9/bin:$PATH"

WORKDIR /app

COPY package.json ./
COPY worker.mjs ./

CMD ["node", "worker.mjs"]
