#!/usr/bin/env bash
#
# Provision a fresh Ubuntu VPS to run InventoryQuest under *rootless* Podman.
#
# Run as root on the VPS:
#   bash provision-vps.sh
#
# The VPS never builds anything: it pulls the image CI publishes to GHCR and runs
# it as an unprivileged `deploy` user, under a user systemd unit so the pod comes
# back on its own after a reboot. Postgres data lives on a named volume, so it
# survives reboots and redeploys.
#
# Override any config via the environment, e.g.:
#   IMAGE_REF=ghcr.io/jasonuithol/inventoryquest:<sha> bash provision-vps.sh
#
set -euo pipefail

# ── Config ─────────────────────────────────────────────────────────────────
DEPLOY_USER="${DEPLOY_USER:-deploy}"
IMAGE_REF="${IMAGE_REF:-ghcr.io/jasonuithol/inventoryquest:latest}"
POD="${POD:-iq-pod}"
PG_VOLUME="${PG_VOLUME:-iq-pgdata}"
APP_PORT="${APP_PORT:-8080}"
OPEN_FIREWALL="${OPEN_FIREWALL:-yes}"   # open APP_PORT in ufw if ufw is active

# ── Preconditions ──────────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then
  echo "This script must be run as root." >&2
  exit 1
fi

echo "==> Installing Podman + rootless prerequisites (no JDK/Maven/git needed)…"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
# uidmap            → newuidmap/newgidmap for the user-namespace mapping
# slirp4netns       → rootless container networking
# fuse-overlayfs    → rootless overlay storage driver
# dbus-user-session → the per-user D-Bus/systemd session (needed for `systemctl --user`)
# systemd-container → `machinectl shell` for a proper rootless login session
apt-get install -y \
  podman uidmap slirp4netns fuse-overlayfs dbus-user-session systemd-container

echo "==> Creating user '${DEPLOY_USER}' (if absent)…"
if ! id -u "${DEPLOY_USER}" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "${DEPLOY_USER}"
fi
DEPLOY_UID="$(id -u "${DEPLOY_USER}")"

echo "==> Ensuring subuid/subgid ranges for rootless Podman…"
grep -q "^${DEPLOY_USER}:" /etc/subuid || usermod --add-subuids 100000-165535 "${DEPLOY_USER}"
grep -q "^${DEPLOY_USER}:" /etc/subgid || usermod --add-subgids 100000-165535 "${DEPLOY_USER}"

echo "==> Enabling linger so user services run without a login session (and across reboots)…"
loginctl enable-linger "${DEPLOY_USER}"

# When a user lingers, logind starts user@UID.service which creates /run/user/UID.
# Rootless Podman and `systemctl --user` both need that runtime dir to exist.
RUNTIME_DIR="/run/user/${DEPLOY_UID}"
echo -n "==> Waiting for ${RUNTIME_DIR}"
for _ in $(seq 1 30); do
  [[ -d "${RUNTIME_DIR}" ]] && { echo " ✓"; break; }
  echo -n "."; sleep 1
done
if [[ ! -d "${RUNTIME_DIR}" ]]; then
  echo
  echo "   ${RUNTIME_DIR} not present yet — starting user@${DEPLOY_UID}.service manually…"
  systemctl start "user@${DEPLOY_UID}.service" || true
  sleep 2
fi

# Run a command AS the deploy user with a working rootless environment.
# (`sudo -u` alone doesn't set XDG_RUNTIME_DIR / the user bus, which breaks the socket.)
as_deploy() {
  sudo -u "${DEPLOY_USER}" \
    XDG_RUNTIME_DIR="${RUNTIME_DIR}" \
    DBUS_SESSION_BUS_ADDRESS="unix:path=${RUNTIME_DIR}/bus" \
    bash -lc "$*"
}

echo "==> Applying the subuid ranges to Podman storage…"
as_deploy "podman system migrate" || true

echo "==> Pulling ${IMAGE_REF}…"
as_deploy "podman pull '${IMAGE_REF}'"

# ── Bring the pod up once, so we can capture it as systemd units ────────────
# Mirrors scripts/pod-up.sh (app + Postgres in one pod, Postgres on a named volume).
echo "==> Creating the pod (app + Postgres on volume '${PG_VOLUME}')…"
as_deploy "
  set -e
  podman pod exists '${POD}' && podman pod rm -f '${POD}'
  podman pod create --name '${POD}' -p ${APP_PORT}:8080
  podman run -d --pod '${POD}' --name '${POD}-db' \
    -e POSTGRES_DB=inventoryquest -e POSTGRES_USER=iq -e POSTGRES_PASSWORD=iq \
    -v '${PG_VOLUME}':/var/lib/postgresql/data \
    docker.io/library/postgres:16
  echo -n '   waiting for Postgres'
  for _ in \$(seq 1 30); do
    podman exec '${POD}-db' pg_isready -U iq -d inventoryquest >/dev/null 2>&1 && { echo ' ✓'; break; }
    echo -n '.'; sleep 1
  done
  podman run -d --pod '${POD}' --name '${POD}-app' '${IMAGE_REF}'
"

# ── Generate user systemd units and hand ownership of the pod to systemd ────
# `--new` makes each unit recreate its container from the image on start, so a
# reboot (or a `podman pull` + restart) always runs the current published image.
echo "==> Generating user systemd units for reboot persistence…"
as_deploy "
  set -e
  UNIT_DIR=\"\$HOME/.config/systemd/user\"
  mkdir -p \"\$UNIT_DIR\"
  TMP=\$(mktemp -d)
  cd \"\$TMP\"
  podman generate systemd --new --files --name '${POD}'
  mv ./*.service \"\$UNIT_DIR\"/
  rmdir \"\$TMP\" 2>/dev/null || true
  systemctl --user daemon-reload
  # Enable all three generated units (pod + both containers) so they come up on boot…
  systemctl --user enable pod-${POD}.service container-${POD}-db.service container-${POD}-app.service
  # …then hand the running pod over to systemd: drop the hand-made one and let the
  # --new units recreate it (the named volume keeps the database).
  podman pod rm -f '${POD}'
  systemctl --user start container-${POD}-db.service container-${POD}-app.service
"

# ── Optional: open the port in ufw ─────────────────────────────────────────
if [[ "${OPEN_FIREWALL}" == "yes" ]] && command -v ufw >/dev/null 2>&1; then
  if ufw status 2>/dev/null | grep -q "Status: active"; then
    echo "==> Opening ${APP_PORT}/tcp in ufw…"
    ufw allow "${APP_PORT}/tcp" || true
  fi
fi

echo
echo "════════════════════════════════════════════════════════════════════"
echo "  InventoryQuest is up on  http://<this-vps-ip>:${APP_PORT}"
echo "  systemd-managed and set to auto-start on boot."
echo
echo "  Manage it as the deploy user (proper rootless session):"
echo "    machinectl shell ${DEPLOY_USER}@"
echo "    systemctl --user status pod-${POD}.service"
echo "    podman pod ps  &&  podman logs -f ${POD}-app"
echo
echo "  Deploy a new build (after CI publishes a fresh image):"
echo "    podman pull ${IMAGE_REF}"
echo "    systemctl --user restart container-${POD}-db.service container-${POD}-app.service"
echo "════════════════════════════════════════════════════════════════════"
