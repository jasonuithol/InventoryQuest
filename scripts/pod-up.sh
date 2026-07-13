#!/usr/bin/env bash
# Bring up InventoryQuest as a single Podman pod: the app + Postgres sharing one network
# namespace (so the app reaches the database on localhost:5432, matching application.yml).
set -euo pipefail

POD=iq-pod
# Default to the image CI publishes to GHCR; override with IMAGE=localhost/inventoryquest:local
# (or any tag) to run a locally-built image instead.
IMAGE="${IMAGE:-ghcr.io/jasonuithol/inventoryquest:latest}"

podman pod exists "$POD" && podman pod rm -f "$POD"
podman pod create --name "$POD" -p 8080:8080

echo "→ starting Postgres…"
# Named volume so the database survives pod recreation and host reboots.
# (Reset local state any time with: podman volume rm iq-pgdata)
podman run -d --pod "$POD" --name "${POD}-db" \
  -e POSTGRES_DB=inventoryquest -e POSTGRES_USER=iq -e POSTGRES_PASSWORD=iq \
  -v iq-pgdata:/var/lib/postgresql/data \
  docker.io/library/postgres:16 >/dev/null

echo -n "→ waiting for Postgres to accept connections"
for _ in $(seq 1 30); do
  if podman exec "${POD}-db" pg_isready -U iq -d inventoryquest >/dev/null 2>&1; then
    echo " ✓"; break
  fi
  echo -n "."; sleep 1
done

echo "→ starting the app…"
podman run -d --pod "$POD" --name "${POD}-app" "$IMAGE" >/dev/null

echo "InventoryQuest is coming up at http://localhost:8080"
echo "  logs:  podman logs -f ${POD}-app"
echo "  down:  podman pod rm -f ${POD}"
