# MPCalc infra (Docker)

Status (2026-10-14): all four services run (`db`, `backend`, `chat`, `proxy`); on 2026-10-13 `db`, `backend` and `proxy` were started with Compose and tested (page, login, character, calculate, cast, audit rows, Flyway V1 to V4 on a fresh Postgres). `chat` runs and answers `/chat/health`; its Claude calls were checked live with a real key on 2026-10-14. On this machine use `docker-compose` (the standalone Compose 5.6.0), not `docker compose`.

Containers: `proxy` (nginx, the only one with a published port), `backend` (Java, port 8080), `chat` (Python, port 8000), `db` (Postgres, named volume `pgdata`).
Networks: `edge` (proxy, backend, chat) and `db_net` (backend, db; `internal`, no outside route). Only the backend can reach Postgres.
Open decision: whether the chat service may reach Postgres. Today it cannot (not on `db_net`).

## Start
1. `cd /Users/Imola_Saraine_Lajos/MPCalc/infra`
2. `cp .env.example .env` and fill in the values (never commit `.env`).
3. Make sure `../backend` exists (Maven project; refresh the engine jar first with `backend/refresh-engine.sh`). `../chat` is needed only when the chat service is started (it needs `requirements.txt` and `app/main.py`).
4. Build the page (`npx ng build` in `frontend/`) and set `FRONTEND_DIST=../frontend/dist/frontend/browser` in `.env`.
5. Without the chat service: `docker-compose up -d db`, wait until it is healthy, then `docker-compose up -d --build --no-deps backend proxy`. Open `http://localhost:8081` (`PROXY_PORT`). With the chat service: `docker-compose up --build -d`.

## Chat service
`chat` (Python, FastAPI) answers questions at `/chat/ask` and calls the backend's MCP tools. It needs a **real Claude API key** in `infra/.env` as `ANTHROPIC_API_KEY` (set on 2026-10-14; with a missing or wrong key every question ends with the Hungarian message "A csevegés most nem érhető el"). After changing the key recreate the service: `docker-compose up -d --no-deps chat`; after changing the code rebuild it: `docker-compose up -d --build --no-deps chat`. The model is `CHAT_MODEL` (default `claude-haiku-5-5`). Live check with the real key: `docker-compose exec chat python scripts/live_smoke.py` (a few cents). Full start of everything: `docker-compose up -d --build` (all four services).

## Stop
- `docker-compose down` (keeps the database data).
- `docker-compose down -v` (also DELETES the database volume).

## MCP
The backend's MCP endpoint `/api/mcp` is for the chat service only: it needs the header `X-API-Key` (= `MCP_API_KEY`) and nginx answers 404 for `/api/mcp` from outside. The chat service calls `http://backend:8080/api/mcp` on the internal `edge` network.

## Routing
`/api/*` to backend:8080, `/chat/*` to chat:8000 (paths unchanged), everything else to the Angular files (fallback `index.html`). HTTPS: phase 7.

See `QUESTIONS.md` for assumptions and open questions.
