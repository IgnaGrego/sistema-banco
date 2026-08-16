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
# Editar .env:
#   - GITHUB_TOKEN   -> PAT de GitHub con scope "repo"
#   - WEBHOOK_SECRET -> openssl rand -hex 32
#   - MODEL          -> opencode-go/deepseek-v4-flash (ya viene así)
```

> La API key de **OpenCode Go** no va en `.env`. Se autentica dentro del
> contenedor del worker (paso 3) y queda persistida en el volumen.

> **Contexto de este VPS:** el puerto 443 ya lo ocupa el gym (Laravel en Docker).
> Por eso usamos un túnel outbound de Cloudflare (`cloudflared`) que da una URL
> HTTPS pública sin tocar los puertos del host, sin DNS y sin certificados.

## 2. Levantar los servicios (incluye el túnel)

```bash
docker compose up -d --build
```

Verificación del webhook local: `curl http://localhost:8080/health` → `{"ok":true}`.

## 3. Autenticar OpenCode Go en el worker

```bash
docker compose exec -it worker opencode auth login
```

Seleccionar **OpenCode Go**, pegar la API key de tu suscripción y confirmar.
Queda guardada en el volumen `opencode-data` (persistente).

## 4. Obtener la URL HTTPS del túnel

```bash
docker compose logs -f tunnel
```

Buscar una línea como:

```
Registered tunnel connection conn_index=0 ... https://XXXXX-XXXX.trycloudflare.com
```

Esa URL es la `PAYLOAD URL` del webhook. Queda estable mientras el contenedor
`tunnel` esté corriendo.

## 5. Registrar el webhook en GitHub

En el repo `IgnaGrego/sistema-banco` (o con `gh`):

- Payload URL: `https://XXXXX-XXXX.trycloudflare.com/webhook`
- Content type: `application/json`
- Secret: el valor de `WEBHOOK_SECRET`
- Events: **Issues** e **Issue comments**

Con `gh`:

```bash
gh api -X POST repos/IgnaGrego/sistema-banco/hooks \
  -f config[url]="https://XXXXX-XXXX.trycloudflare.com/webhook" \
  -f config[content_type]="json" \
  -f config[secret]="<WEBHOOK_SECRET>" \
  -f config[insecure_ssl]="0" \
  -f events[]="issues" \
  -f events[]="issue_comment"
```

> Nota: con un túnel `trycloudflare` gratuito, la URL cambia si el contenedor se
> recrea. Para una URL estable sobre tu dominio `sdd.ignagrego.online`, la
> opción a futuro es un túnel Cloudflare con nombre (requiere tener el dominio
> en Cloudflare).

## 6. Probar

Abrir un issue en el repo con el texto de un requisito, o comentar `/sdd` en uno.
El worker lo toma, corre `opencode run --agent orchestrator` y comenta el resultado.

## Seguridad

- `vps/.env` queda fuera de git (ya está en `.gitignore`).
- `ALLOWED_REPOS` restringe qué repos procesa el worker.
- El webhook valida `X-Hub-Signature-256`.
