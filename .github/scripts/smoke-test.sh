#!/usr/bin/env bash
# Runs the image that is about to be pushed against a real Postgres and exercises it
# over HTTP. The tests already passed against the jar; this checks the artifact that
# actually ships: layers, entrypoint, non-root user, configuration through env vars.
set -euo pipefail

image="$1"
port="${2:-18080}"
suffix="$$"
network="smoke-${suffix}"
db="smoke-db-${suffix}"
app="smoke-app-${suffix}"

cleanup() {
    status=$?
    if [ "$status" -ne 0 ]; then
        docker logs "$app" 2>&1 | tail -50 || true
    fi
    docker rm -f "$app" "$db" >/dev/null 2>&1 || true
    docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$network" >/dev/null
docker run -d --name "$db" --network "$network" \
    -e POSTGRES_DB=flags -e POSTGRES_USER=flags -e POSTGRES_PASSWORD=flags \
    postgres:16-alpine >/dev/null
for _ in $(seq 1 30); do
    docker exec "$db" pg_isready -U flags -d flags >/dev/null 2>&1 && break
    sleep 1
done

started=$(date +%s%N)
docker run -d --name "$app" --network "$network" -p "${port}:8080" --memory 512m \
    -e DB_URL="jdbc:postgresql://${db}:5432/flags" -e DB_USER=flags -e DB_PASSWORD=flags \
    "$image" >/dev/null

ready=""
for _ in $(seq 1 120); do
    if curl -fsS "http://localhost:${port}/actuator/health/readiness" >/dev/null 2>&1; then
        ready=yes
        break
    fi
    sleep 0.5
done
[ -n "$ready" ] || { echo "::error::container never became ready"; exit 1; }
elapsed_ms=$(( ($(date +%s%N) - started) / 1000000 ))

uid=$(docker exec "$app" id -u)
[ "$uid" != "0" ] || { echo "::error::container runs as root"; exit 1; }

curl -fsS -X POST "http://localhost:${port}/api/flags" -H 'Content-Type: application/json' \
    -d '{"key":"smoke","description":"smoke test","enabled":true,"rolloutPercentage":100}' >/dev/null
evaluation=$(curl -fsS "http://localhost:${port}/api/flags/smoke/evaluation?userId=u1")
echo "$evaluation" | grep -q '"reason":"ROLLOUT_INCLUDED"' \
    || { echo "::error::unexpected evaluation: $evaluation"; exit 1; }
info=$(curl -fsS "http://localhost:${port}/actuator/info")

echo "ready in ${elapsed_ms} ms, uid ${uid}"
echo "info: ${info}"
