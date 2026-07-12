# ⚔️ InventoryQuest

*Climb the mountain. Manage your backpack. Become King of the Mountain.*

InventoryQuest is a server-authoritative multiplayer game where 256 players start
on the lower slopes of a mountain and climb toward a single summit square. Combat
happens. Trading happens. But the real game — the one that decides who reaches the
top with a legendary sword instead of a backpack full of iron bars — is
**inventory management**.

> 🏔️ **Backstory:** At the summit waits the title of **King of the Mountain**,
> and anyone else who thought they deserved it more than you. The mountain
> provides: items lie on the ground as you climb. The mountain also takes: your
> backpack is only so big.

---

## Gameplay

### The mountain

The map is a stack of rings wrapped around the mountain. Each level up, **four
adjacent squares merge into one**:

| Level | Squares | |
|---|---|---|
| 0 (base) | 256 | ring around the lower slopes |
| 1 | 64 | |
| 2 | 16 | |
| 3 | 4 | |
| 4 (summit) | 1 | 👑 |

Square `i` on level `n` feeds square `⌊i/4⌋` on level `n+1`. You can move
**left/right** around your ring (it wraps) or **up** into your parent square.
There is no climbing down. The mountain does not do refunds.

### Meeting other players

When a square holds multiple players, they hold a vote:
**⚔️ Fight, 🤝 Trade, or 🚶 Leave**.

- Anyone voting *Fight* visibly draws their weapon — no ambushes, just bad vibes.
- If **anyone** votes Fight, **everyone** fights — including the ones who voted
  Leave. The mountain respects commitment, not exit strategies.
- If no one votes Fight: a **trade table opens between every pair of Trade
  voters** — three traders means each player sees two tables, four traders
  means three — and Leave voters must immediately move — left, right, or up.
- After each combat round, the square votes again (all three options). Fighting
  ends when no one votes Fight, or when **1 or 0** players remain alive in the
  square.
- **Arrivals:** a player entering during a vote joins that vote. A player
  entering during a **trade interrupts it** — the table clears, items return to
  their owners, and everyone now present votes again. A player entering during a
  **fight joins the ongoing fight**. Climb carefully.

### Trading

Trades are **strictly pairwise** — a table always has exactly two sides — but a
trader trades with **everyone at once**: a table exists between every pair of
Trade voters, so with three traders you're working two tables simultaneously,
and with four, three.

Each table is split in two halves — your side and theirs — each with item
slots. Both players place and remove items freely until one side hits
**Propose Trade**, which locks the table contents. The other player then
**Accepts** (items swap, table clears) or **Rejects** (table unlocks, haggling
resumes).

Two rules give trading its teeth:

- **Every table is visible to every trader in the square.** The moment your
  💎 hits one table, the other traders can see it — and start outbidding on
  theirs. Bidding wars are not a bug; they're the economy.
- **An item can only be on one table at a time.** Dangling the same jewel in
  front of two rivals means physically moving it between tables — visibly.

If a new player enters the square mid-trade, **all** tables are interrupted:
placed items return to their owners and the square votes again.

### Inventory — the actual game

- Your **backpack** is a grid of slots.
- Items are **emojis**, and occupy square footprints: 1×1, 2×2, 3×3, 4×4
  (🗡️ might be 1×1; ⚔️ a 2×2; 🛡️ a 3×3 tower shield you will absolutely
  regret picking up on level 0).
- Items on the ground can only be picked up **if they fit** in your backpack.
- Items are either usable **artifacts** (sword, shield, ring, amulet) or
  **crafting ingredients** (🧱 iron bar, 💎 jewel) that **combine** into
  artifacts.
- Four **equipment slots** sit outside the backpack: sword, shield, ring, amulet.

**Actions:** select item(s) · combine · equip · drop · move up/left/right ·
(when others are present) vote **Fight / Trade / Leave**.

---

## UI layout

One screen, three regions. The layout never changes; the *right side's contents*
do.

```
┌─────────────────────────────────────────────────────────────┐
│  ⛰️ Level 2 · 16 squares    ❤️❤️❤️🖤    state: TRADING      │  ← status strip
├──────────────────────────┬──────────────────────────────────┤
│  🎒 BACKPACK             │  CONTEXT PANEL                   │
│  ┌──┬──┬──┬──┬──┬──┐     │                                  │
│  │🧱│  │💎│  │  │  │     │  (contents depend on the state   │
│  ├──┼──┼──┼──┼──┼──┤     │   badge in the strip above:)     │
│  │  │ 🛡️2×2 │  │  │     │                                  │
│  ├──┼──┴──┴──┼──┼──┤     │  IDLE      move ⬅️⬆️➡️ · ground  │
│  │  │  │  │  │  │  │     │            items to pick up      │
│  └──┴──┴──┴──┴──┴──┘     │  VOTING    ⚔️ 🤝 🚶 + who has    │
│                          │            drawn weapons         │
│  EQUIPMENT               │  TRADING   your N−1 tables,      │
│  🗡️ sword   🛡️ shield    │            stacked               │
│  💍 ring    📿 amulet    │  FIGHTING  combat actions +      │
│                          │            next round's vote     │
│                          │  MUST MOVE ⬅️⬆️➡️ (Leave voters)  │
│                          │  + recipes whenever items are    │
│                          │    selected (see below)          │
└──────────────────────────┴──────────────────────────────────┘
```

- **Status strip (top):** elevation and ring size, health, and the current
  state as an explicit badge — the badge and the context panel always agree,
  because both are rendered from the same server-side state machine.
- **Inventory (left, persistent):** the backpack grid and four equipment slots
  are on screen at all times and stay interactive in every state — equipping a
  better sword *mid-fight* or clearing space *mid-negotiation* are legitimate
  plays.
- **Context panel (right):** state-specific actions only. Ground items appear
  here (picking up is an action); trade tables stack here, one independently
  updating fragment per table, your side always on the same side.

### Crafting: selection is the query

Recipes list themselves as you select ingredients — the recipe panel is a live
filter over your selection:

1. Select 🧱 iron in the backpack → the panel lists **every recipe containing
   iron**.
2. Also select leather → the list narrows to recipes containing **both**
   (AND-filter over ingredient sets).
3. Recipes you can't complete from your backpack stay visible but **dimmed,
   missing ingredients ghosted** — the recipe panel doubles as a shopping list
   for the trade tables ("someone here has that 💎...").
4. Click a recipe → confirmation shows full ingredients and the result →
   **Craft**.

Crafting consumes the ingredients, so the result is fit-checked against the
**post-consumption** grid: three 1×1 ingredients can legally become one 2×2
artifact if the freed space accommodates it.

Every interaction is an htmx POST returning re-rendered fragments (selection
toggle → recipe panel; vote → context panel; table change → that table's
fragment for everyone in the square). The client holds no game state.

---

## Tech stack

Server-authoritative from day one: the client renders state and sends intents;
every rule (does it fit, is the vote unanimous, is that trade table locked) is
enforced on the server.

| Concern | Choice |
|---|---|
| Runtime | **Java 21 LTS** — virtual threads carry every player connection; plain blocking code, no async/await |
| Framework | **Spring Boot 3.x** (Maven, Boot BOM manages versions) |
| Realtime | **Spring WebSocket** — server pushes square/trade/combat updates to everyone present |
| UI | **Thymeleaf + htmx** (WebSocket extension) — the backpack grid, equipment slots, vote buttons, and trade table are server-rendered fragments swapped in place; items are emojis scaled by CSS to their N×N footprint; zero custom JS framework |
| Persistence | **PostgreSQL** + Spring Data JPA (Hibernate, HikariCP); **JSONB** for backpack layouts |
| Migrations | **Flyway** (`ddl-auto: validate`, always) |
| JSON | **Jackson** (Boot default) |
| Testing | **JUnit 5 + AssertJ + Mockito** (units) · **Testcontainers** Postgres via `@ServiceConnection` (integration) · **ArchUnit** (rules below) · **Awaitility** (websocket/round assertions) · **Instancio** (test data) |
| Containers | **Podman** (rootless) — multi-stage `Containerfile` with Boot layered jars; app + Postgres wrapped in one **pod**; `podman kube generate` exports the K8s manifest |
| Observability | Actuator + Micrometer (`/actuator/health`, metrics per square occupancy, active fights, trades completed) |

## Architecture

Package-by-feature; features may only touch each other's public API (ArchUnit-enforced):

```
com.example.inventoryquest/
  player/      # Player, spawn/session, equipment slots
  mountain/    # ring geometry, squares, movement rules (left/right wrap, up = ⌊i/4⌋),
               # ground items per square
  inventory/   # backpack grid, N×N placement/fit checking, pick up, drop, select
  crafting/    # recipes: ingredients → artifacts (combine action)
  combat/      # fight/trade voting, weapon-drawn visibility, combat rounds, elimination
  trade/       # trade table state machine: OPEN → PROPOSED → (ACCEPTED | REJECTED → OPEN)
  realtime/    # websocket sessions, square-scoped broadcast, htmx fragment push
```

Design notes:

- **The trade table is a small state machine** and is tested as one:
  `OPEN → PROPOSED → (ACCEPTED | REJECTED → OPEN | INTERRUPTED)`. Propose locks,
  reject unlocks, accept swaps atomically (`@Transactional` — both inventories
  mutate or neither does; fit-checks run *before* accept). An arrival interrupts
  every table in the square from any state: placed items return to their owners
  in the same transaction, then the square re-votes.
- **Trading is a complete graph**: N Trade voters → one table per pair
  (N·(N−1)/2 tables), each trader participating in N−1 of them, so there is no
  "odd trader out" state. All tables in a square broadcast to all traders
  present (public order book, essentially). Hard invariant: **an item is on at
  most one table at a time** — placing enforces it, accepting a trade
  auto-removes the item's presence everywhere else is unnecessary because it
  was never anywhere else. Accepting one trade re-fit-checks and can invalidate
  proposals on other tables that counted on backpack space — those revert to
  `OPEN`.
- **Votes are per-square rounds with dynamic membership and three-way routing**:
  a vote is immutable once cast for that round; a player entering mid-round is
  added to the roster and votes like anyone else. Resolution is a single
  transactional operation — any Fight vote sends *everyone* to combat; otherwise
  tables open between all Trade-voter pairs and Leave voters are put into a
  must-move state (left/right/up) before they can act again.
- **Placement is bin-fitting, not bin-packing**: the player chooses where an item
  goes; the server only validates the footprint is free. The suffering is the
  content.
- **Concurrency**: a square is the unit of contention — actions within a square
  serialize on the square aggregate (optimistic locking + retry), so two players
  can't both grab the same ground item. Everything else scales out on virtual
  threads.

## Getting started

Prerequisites: JDK 21 (Temurin via SDKMAN), Maven (or `./mvnw`), Podman with the
rootless socket enabled for Testcontainers:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

**Dev loop** (Postgres in a container, app on the host):

```bash
podman compose up -d
./mvnw spring-boot:run
# open http://localhost:8080 — spawn a player, start hoarding
```

**Full pod** (everything containerized as one unit):

```bash
podman build -t inventoryquest:local .
./scripts/pod-up.sh              # pod: app + postgres, only :8080 published
podman kube generate iq-pod > iq-pod.yaml   # same stack as a K8s manifest
```

**Tests:**

```bash
./mvnw verify    # units via Surefire, *IT via Failsafe (needs the Podman socket)
```

## Trying it without friends

`GET /dev/bots?count=N` (dev profile only) spawns N wandering bot players so you
can trigger a four-square merge, a vote, and a trade table without recruiting
255 acquaintances.

## Roadmap

- [ ] Spectator mode for eliminated players (haunt your killer's inventory decisions)
- [ ] Item rarity tiers & recipe discovery
- [ ] Seasonal mountains (fresh 256-player ladders)
- [ ] Combat depth: shields, ring/amulet passives actually doing things

## License & credits

MIT. Emoji are rendered from the player's system font — no bundled art assets.
Inventory Tetris agony inspired by every game that ever made a sword take up
four slots.
