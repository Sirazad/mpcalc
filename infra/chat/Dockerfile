# Build context: /Users/Imola_Saraine_Lajos/MPCalc/chat (set in docker-compose.yml). Tested 2026-10-14 (see infra/README.md).
# The service code is in chat/app (FastAPI, ASGI object app.main:app); chat/requirements.txt lists the pinned dependencies.
ARG PYTHON_IMAGE=python:3.13-slim
FROM ${PYTHON_IMAGE}
ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
EXPOSE 8000
# ASSUMPTION: an ASGI app "app.main:app" served by uvicorn (uvicorn must be in requirements.txt). See QUESTIONS.md.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
