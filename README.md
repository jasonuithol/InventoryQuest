# ⚔️ InventoryQuest

[![CI](https://github.com/jasonuithol/InventoryQuest/actions/workflows/ci.yml/badge.svg)](https://github.com/jasonuithol/InventoryQuest/actions/workflows/ci.yml)

A server-authoritative multiplayer climb-the-mountain game where the real game is
**inventory management**. See [`DESIGN.md`](DESIGN.md) for the full design;
this README is how to run and work on it.

The client renders state and sends intents; every rule — does it fit, is the vote
unanimous, is that trade table locked — is enforced on the server. Every interaction is an
htmx request that returns a re-rendered Thymeleaf fragment; the browser holds no game state.

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21 (virtual threads; plain blocking code) |
| Framework | Spring Boot 3.5 (Maven, Boot BOM) |
| Realtime | Spring WebSocket + htmx `ws` extension (square-scoped fragment nudges) |
| UI | Thymeleaf + htmx, emoji items scaled by CSS to their N×N footprint, zero JS framework |
| Persistence | PostgreSQL + Spring Data JPA (Hibernate, HikariCP); **JSONB** backpack layouts |
| Migrations | Flyway (`ddl-auto: validate`, always) |
| Testing | JUnit 5 + AssertJ + Mockito · Testcontainers (`@ServiceConnection`) · ArchUnit · Awaitility · Instancio |
| Containers | Podman — multi-stage `Containerfile`, app + Postgres in one pod |

## Architecture (package-by-feature)

```
item/        shared kernel: ItemType catalog, footprints, equip slots
inventory/   Backpack grid + N×N fit-checking, equip/unequip (pure, immutable)
mountain/    ring geometry (RingMath), movement, ground items
crafting/    recipes: ingredient multiset → artifact, live selection filter
combat/      vote round state machine (Fight/Trade/Leave), combat rounds
trade/       trade-table state machine + complete-graph TradeSession
player/      Player aggregate (JSONB backpack/equipment), optimistic locking
realtime/    WebSocket handler, square-scoped broadcast
game/        application services: orchestrates the features + SquareCoordinator
web/         controllers + Thymeleaf (the one screen)
```

Feature boundaries, constructor-injection-only, and controllers-never-touch-repositories are
enforced by `ArchitectureTest` (ArchUnit).

## Prerequisites

- JDK 21 (Temurin) and Maven — or use the containerised toolchain (below), which needs only Podman.
- Podman with the rootless socket enabled (for Testcontainers):
  ```bash
  systemctl --user enable --now podman.socket
  export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
  export TESTCONTAINERS_RYUK_DISABLED=true
  ```

## Dev loop (Postgres in a container, app on the host)

```bash
podman compose up -d                 # starts Postgres (compose.yaml)
./mvnw spring-boot:run               # app on http://localhost:8080
# spawn a climber, start hoarding. Add bots: GET /dev/bots?count=2&near=<id>&vote=TRADE
```

`application.yml` points at `localhost:5432`; override with `SPRING_DATASOURCE_URL` etc.

## Tests

```bash
./mvnw verify    # unit + slice via Surefire, *IT (Testcontainers Postgres) via Failsafe
```

## Full pod (everything containerised as one unit)

```bash
podman build -t inventoryquest:local -f Containerfile .
./scripts/pod-up.sh                          # pod: app + Postgres, only :8080 published
podman kube generate iq-pod > iq-pod.yaml    # export the same stack as a K8s manifest
```

Inside the pod the app and Postgres share a network namespace, so the app reaches the
database on `localhost:5432` — no extra configuration.

## URLs

- `/` — spawn screen
- `/game/{id}` — the game
- `/dev/bots?count=N&near={id}&vote=TRADE` — spawn bots (dev profile only)
- `/actuator/health` · `/actuator/metrics`

## What's implemented

The full solo loop (spawn · move with ring wrap and `⌊i/4⌋` climb · pick-up with fit-check ·
drop · equip/unequip · craft with post-consumption fit-check · eat food to heal) is drivable
end-to-end in the UI. Each ascent is gated on carrying that level's mountaineering gear —
🧥 snow jacket → 🥾 cleats → ⛏️ ice pick → 🫁 oxygen tank — which the mountain seeds at each
level, so the climb competes with loot for scarce backpack space.
The multiplayer coordination — vote rounds with three-way routing (a single Fight vote resolves
the round immediately and drags everyone in), complete-graph trading with the single-table
invariant, and **turn-based combat** where fighters act in turn (one action each: attack a chosen
opponent — weapon damage in hit-points, subject to a miss — or call for parley, which ends the
fight only if every opponent accepts) — is implemented as tested domain state machines, wired
through an in-memory per-square coordinator with WebSocket nudges, and reachable in the UI via the
dev bot spawner. Health is tracked in hit-points (4 HP = 1 heart); food items (🍎🍞🍖) are eaten to
restore it.
