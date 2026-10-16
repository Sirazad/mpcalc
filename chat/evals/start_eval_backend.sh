#!/bin/sh
# Starts a backend on port 8090 with the dedicated eval database mpcalc_eval (created if it does not exist) and its own MCP key.
# The eval plants hostile data in this database, so it must never be the dev or Docker database. Runs in the foreground.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
host="${EVAL_PSQL_HOST:-/tmp}"
db=mpcalc_eval
psql -h "$host" -d postgres -Atc "select 1 from pg_database where datname = '$db'" | grep -q 1 || psql -h "$host" -d postgres -c "create database $db"
if [ ! -f "$here/.eval.env" ]; then
  umask 077
  printf 'MCP_API_KEY=%s\nBACKEND_URL=http://localhost:8090\nEVAL_DB_CONFIRM=%s\nEVAL_PSQL_HOST=%s\n' "$(openssl rand -hex 24)" "$db" "$host" > "$here/.eval.env"
fi
. "$here/.eval.env"
jar="$(ls "$repo"/backend/target/mpcalc-backend-*.jar | grep -v original | head -1)"
exec env MCP_API_KEY="$MCP_API_KEY" SERVER_PORT=8090 SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/$db" SPRING_DATASOURCE_USERNAME="$(whoami)" java -jar "$jar"
