# Open questions (phase 0)
1. Folder names: I assumed ../backend (given), ../chat (chat service) and ./site (Angular build, overridable by FRONTEND_DIST). Correct?
2. Does the backend expect the /api prefix, and the chat service the /chat prefix? Conservative choice: the proxy passes paths unchanged.
3. Chat service start command: assumed `uvicorn app.main:app` (ASGI). Which framework and module path will it use?
4. RESOLVED (user): Java 26. The backend Dockerfile now uses maven:3.9-eclipse-temurin-26 and eclipse-temurin:26-jre; both tags exist on Docker Hub (checked 2026-10-11). Still unverified against a registry: python:3.13-slim, postgres:17-alpine, nginx:1.27-alpine.
5. RESOLVED (user): the proxy keeps the /api and /chat prefixes; the backend and the chat service expect them.
5. RESOLVED (user): keep nginx. HTTPS certificates are handled in phase 7. If the chat answers are streamed, response buffering must be switched off for the /chat route (to be done then).
6. Should the chat service reach Postgres? Conservative: no (not on db_net).
7. Is the backend folder allowed to be empty for the placeholder, and may the Compose build context be a missing folder? Docker fails if ../backend or ../chat does not exist; they must be created.
8. Is the backend's port 8080 reachable by the chat service on the internal network only (yes, as built)? Spring env names (SPRING_DATASOURCE_*, MCP_API_KEY) are assumed; the backend agent must confirm.
