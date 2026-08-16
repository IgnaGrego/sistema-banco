# Setup del VPS — Pipeline SDD autónomo

Pasos para correr el pipeline SDD en tu VPS, disparado por webhooks de GitHub.

## Requisitos

- VPS con Docker y Docker Compose.
- Un dominio o URL HTTPS pública hacia el VPS (GitHub solo entrega webhooks por HTTPS).
- Un PAT de GitHub con scope `repo`.

## 1. Preparar el entorno

```bash
cd vps
cp .env.example .env
# Editar .env: GITHUB_TOKEN, WEBHOOK_SECRET (openssl rand -hex 32), DEEPSEEK_API_KEY
```

## 2. Reverse proxy (HTTPS)

Con Caddy (TLS automático):

```bash
cp Caddyfile.example /etc/caddy/Caddyfile
# Editar el dominio en /etc/caddy/Caddyfile
systemctl restart caddy
```

Alternativa sin dominio: túnel `cloudflared tunnel --url http://localhost:8080` y usar esa URL HTTPS.

## 3. Levantar los servicios

```bash
docker compose up -d --build
docker compose logs -f
```

Verificación: `curl http://localhost:8080/health` → `{"ok":true}`.

## 4. Registrar el webhook en GitHub

En el repo `IgnaGrego/sistema-banco` (o con `gh`):

- Payload URL: `https://<TU-DOMINIO>/webhook`
- Content type: `application/json`
- Secret: el valor de `WEBHOOK_SECRET`
- Events: **Issues** e **Issue comments**

Con `gh`:

```bash
gh api -X POST repos/IgnaGrego/sistema-banco/hooks \
  -f config[url]="https://<TU-DOMINIO>/webhook" \
  -f config[content_type]="json" \
  -f config[secret]="<WEBHOOK_SECRET>" \
  -f config[insecure_ssl]="0" \
  -f events[]="issues" \
  -f events[]="issue_comment"
```

## 5. Probar

Abrir un issue en el repo con el texto de un requisito, o comentar `/sdd` en uno.
El worker lo toma, corre `opencode run --agent orchestrator` y comenta el resultado.

## Seguridad

- `vps/.env` queda fuera de git (ya está en `.gitignore`).
- `ALLOWED_REPOS` restringe qué repos procesa el worker.
- El webhook valida `X-Hub-Signature-256`.
