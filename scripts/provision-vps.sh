#!/usr/bin/env bash
#
# Provision a fresh Ubuntu VPS to run InventoryQuest under *rootless* Podman,
# managed declaratively with **Quadlets** (Podman 5.0+).
#
# Run as root on the VPS:
#   bash provision-vps.sh
#
# The VPS never builds anything: it pulls the image CI publishes to GHCR and runs
# it as an unprivileged `deploy` user. Quadlet unit files (in the deploy user's
# ~/.config/containers/systemd/) describe the pod, its two containers, and the
# Postgres volume; systemd's podman generator turns them into services that
# auto-start on boot. The app container is marked AutoUpdate=registry and the
# podman-auto-update timer is enabled, so a fresh CI publish rolls out on its own.
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
# Small rootless prerequisites (harmless if already pulled in by Podman):
#   uidmap → newuidmap/newgidmap · slirp4netns → rootless net ·
#   fuse-overlayfs → rootless storage · dbus-user-session → per-user systemd/D-Bus ·
#   systemd-container → `machinectl shell`
apt-get install -y uidmap slirp4netns fuse-overlayfs dbus-user-session systemd-container
# Only install the distro Podman if none is present — don't clobber a newer Podman.
if ! command -v podman >/dev/null 2>&1; then
  apt-get install -y podman
fi
echo "    Podman: $(podman --version)"

echo "==> Creating user '${DEPLOY_USER}' (if absent)…"
if ! id -u "${DEPLOY_USER}" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "${DEPLOY_USER}"
fi
DEPLOY_UID="$(id -u "${DEPLOY_USER}")"
DEPLOY_HOME="$(getent passwd "${DEPLOY_USER}" | cut -d: -f6)"

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
# Two footguns handled here:
#   - `sudo -u` alone doesn't set XDG_RUNTIME_DIR / the user bus, which breaks the socket.
#   - `sudo` keeps root's CWD (/root, mode 700); the deploy user can't chdir there and
#     rootless Podman fails with "cannot chdir to /root". So start from the deploy home.
as_deploy() {
  sudo -u "${DEPLOY_USER}" \
    XDG_RUNTIME_DIR="${RUNTIME_DIR}" \
    DBUS_SESSION_BUS_ADDRESS="unix:path=${RUNTIME_DIR}/bus" \
    bash -lc "cd \"\$HOME\" 2>/dev/null || cd /; $*"
}

echo "==> Applying the subuid ranges to Podman storage…"
as_deploy "podman system migrate" || true

echo "==> Pulling ${IMAGE_REF} (fail fast if the registry/image is unreachable)…"
as_deploy "podman pull '${IMAGE_REF}'"

# ── Migrate away from any previous `podman generate systemd` deployment ─────
echo "==> Removing any earlier (generate-systemd) units and pod…"
as_deploy "
  systemctl --user disable --now pod-${POD}.service container-${POD}-db.service container-${POD}-app.service 2>/dev/null || true
  rm -f ~/.config/systemd/user/pod-${POD}.service \
        ~/.config/systemd/user/container-${POD}-db.service \
        ~/.config/systemd/user/container-${POD}-app.service \
        ~/.config/systemd/user/default.target.wants/pod-${POD}.service \
        ~/.config/systemd/user/default.target.wants/container-${POD}-db.service \
        ~/.config/systemd/user/default.target.wants/container-${POD}-app.service
  systemctl --user daemon-reload
  podman pod rm -f '${POD}' 2>/dev/null || true
"

# ── Write the Quadlet unit files ────────────────────────────────────────────
# Root writes them, then chowns to the deploy user (avoids nested-quoting a heredoc
# through sudo). Quadlet reads these on daemon-reload and generates the services.
QUADLET_DIR="${DEPLOY_HOME}/.config/containers/systemd"
echo "==> Writing Quadlet units to ${QUADLET_DIR}…"
mkdir -p "${QUADLET_DIR}"

cat > "${QUADLET_DIR}/iq.pod" <<EOF
# The pod: app + Postgres share one network namespace, so the app reaches the
# database on localhost:5432 (matching application.yml). Ports publish here.
[Pod]
PodName=${POD}
PublishPort=${APP_PORT}:8080
EOF

cat > "${QUADLET_DIR}/${PG_VOLUME}.volume" <<EOF
# Named volume so the database survives redeploys and reboots.
[Volume]
VolumeName=${PG_VOLUME}
EOF

cat > "${QUADLET_DIR}/${POD}-db.container" <<EOF
[Unit]
Description=InventoryQuest Postgres

[Container]
ContainerName=${POD}-db
Image=docker.io/library/postgres:16
Pod=iq.pod
Environment=POSTGRES_DB=inventoryquest
Environment=POSTGRES_USER=iq
Environment=POSTGRES_PASSWORD=iq
Volume=${PG_VOLUME}.volume:/var/lib/postgresql/data

[Service]
Restart=always

[Install]
WantedBy=default.target
EOF

cat > "${QUADLET_DIR}/${POD}-app.container" <<EOF
[Unit]
Description=InventoryQuest app
After=${POD}-db.service

[Container]
ContainerName=${POD}-app
Image=${IMAGE_REF}
Pod=iq.pod
# Let 'podman auto-update' pull a newer :latest and restart this container.
AutoUpdate=registry

[Service]
# The app may start before Postgres is accepting connections (esp. on boot);
# Restart brings it back until the database is ready.
Restart=always

[Install]
WantedBy=default.target
EOF

chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_HOME}/.config"

# ── Start it, and enable auto-updates ───────────────────────────────────────
echo "==> Starting the pod via systemd and enabling auto-updates…"
as_deploy "
  set -e
  systemctl --user daemon-reload
  systemctl --user start ${POD}-db.service ${POD}-app.service
  systemctl --user enable --now podman-auto-update.timer
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
echo "  Quadlet-managed, auto-starts on boot, auto-updates from GHCR."
echo
echo "  Manage it as the deploy user (proper rootless session):"
echo "    machinectl shell ${DEPLOY_USER}@"
echo "    systemctl --user status ${POD}-app.service"
echo "    podman pod ps  &&  podman logs -f ${POD}-app"
echo
echo "  Edit config → re-generate services:"
echo "    \$EDITOR ~/.config/containers/systemd/${POD}-app.container"
echo "    systemctl --user daemon-reload && systemctl --user restart ${POD}-app.service"
echo
echo "  Force an update now (instead of waiting for the timer):"
echo "    podman auto-update"
echo "════════════════════════════════════════════════════════════════════"
