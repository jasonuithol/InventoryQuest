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
**left/right** around your ring (it wraps), **up** into your parent square, or
**down** — which drops you into one of the four child squares that feed into
yours, chosen **at random** (you can't pick which). Climbing up is gated on gear;
climbing down is free.

**Loot never sits still.** Every few seconds a handful of random squares anywhere
on the mountain have their ground cleared and re-seeded with fresh piles, so
hoards go stale and new loot keeps surfacing across the map rather than only where
players first tread.

### Meeting other players

When a square holds multiple players, they hold a vote:
**⚔️ Fight, 🤝 Trade, or 🚶 Leave**.

- Anyone voting *Fight* visibly draws their weapon — no ambushes, just bad vibes.
- If **anyone** votes Fight, **everyone** fights — including the ones who voted
  Leave. The mountain respects commitment, not exit strategies. A single Fight
  vote resolves the round **immediately** — the fight starts and drags in
  everyone present, without waiting for the undecided to finish voting.
- If no one votes Fight: a **trade table opens between every pair of Trade
  voters** — three traders means each player sees two tables, four traders
  means three — and Leave voters must immediately move — left, right, or up.
- **Arrivals:** a player entering during a vote joins that vote. A player
  entering during a **trade interrupts it** — the table clears, items return to
  their owners, and everyone now present votes again. A player entering during a
  **fight joins the ongoing fight** (and any parley on the table is called off).
  Climb carefully.

### Combat — turns and parley

A fight is **turn-based**. Fighters act in turn order (the order they entered
the fight); a newcomer takes the back of the line. On your turn you do exactly
one thing, and it ends your turn — **no one can advance the fight on your
behalf**:

- **Attack one chosen opponent.** With three or more fighters you pick *which*
  one — there's a separate attack button per opponent — because one attack is
  one turn, so you can only strike a single rival at a time. A swing deals your
  equipped weapon's damage in **hit-points** and **can miss** (every weapon has
  a miss chance; a missed swing still costs you your turn). A 🗡️ dagger does
  **less than a full heart**; a ⚔️ sword does more; bare hands do 1.
- **Call for parley** instead of attacking. The parley is offered to every other
  living fighter, who each **accept** or **reject**. If they *all* accept, the
  fight ends without further blood and the survivors re-vote (talk, trade, or —
  if someone insists — fight again). If **anyone rejects**, the parley collapses
  and the fight resumes at the next fighter's turn (your turn was spent proposing).

The fight is over once **at most one fighter is still standing** (last one wins
the square) or a parley truce is struck.

**Health** is tracked in **hit-points** — 4 HP to a heart, four hearts full.
Damage subtracts hit-points; a fighter reaching zero is eliminated. Health is
not automatically restored: you **eat** 🍎🍞🍖 food items (an action on the item)
to heal, capped at full, consuming the food.

### Presence, timeouts, and freezing

The mountain does not wait. A server-side reaper ticks every second and enforces:

- **Idle limit — 10 minutes.** Do nothing (no action, no page load) for ten
  minutes and you freeze. Watching counts as idle. The status strip shows a live
  countdown (💤 m:ss) that any action resets.
- **Disconnect.** If your browser drops the connection you lose your session and
  freeze — after a short grace (≈20s) so a refresh or a move between squares
  doesn't kill you.
- **Move clock — 5 seconds.** Every vote and every fight move (your turn, or a
  parley you've been asked to answer) has a 5-second limit. Miss it and the move
  is **forfeited**: a vote timeout shoves you out (auto-Leave), a fight turn is
  skipped, an unanswered parley is treated as a rejection.
- **Three strikes.** Forfeit three moves in a row and you freeze. A move made in
  time resets the streak.

Whatever the cause — slain in combat, idled out, disconnected, or forfeited into
oblivion — you become a **frozen corpsical** at that spot: eliminated, and a
🧊 **shard of corpsical** is left on the square for whoever passes through to pick
up. (Presence, connection, and forfeit state are in-memory coordination state,
never persisted; only the death itself is.)

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
  (🗡️ a dagger is 1×1; ⚔️ a sword a hefty 3×3; 🛡️ a 3×3 tower shield you will
  absolutely regret picking up on level 0; 🧱 iron bar and 🪵 wood are 2×2).
- Items on the ground can only be picked up **if they fit** in your backpack.
- Items come in a few kinds:
  - usable **artifacts** (sword, shield, ring, amulet);
  - **crafting ingredients** (🧱 iron bar, 💎 jewel, 🪵 wood, 🧵 leather) that
    **combine** into artifacts;
  - **food** (🍎 apple, 🍞 bread, 🍖 meat) — eaten to restore hit-points;
  - **mountaineering gear** (🧥 snow jacket, 🥾 cleats, ⛏️ ice pick, 🫁 oxygen
    tank) — required to climb (see below);
  - **relics** — a 🧊 shard of corpsical, dropped where a player froze.
- **Equipment slots** sit outside the backpack: sword, shield, ring, amulet, plus
  one for each piece of mountaineering gear (jacket, boots, pick, tank).

**Actions:** select item(s) · combine · equip · drop · eat · move up/left/right ·
(when others are present) vote **Fight / Trade / Leave**, and in a fight
**attack an opponent** or **call for parley**.

### Climbing gear — every level demands its own

Each ascent is gated on **having that level's gear** — either **worn** in its own
equipment slot or carried in the backpack. Wearing it keeps the pack free; carrying
it costs space you may want for loot:

| Climb from | Requires | Crafted from (monster drop + a raw material) |
|---|---|---|
| Level 0 → 1 | 🧥 snow jacket (2×2) | 🧶 yeti pelt + 🧵 leather |
| Level 1 → 2 | 🥾 cleats (1×1) | 🦷 wolf teeth + 🧵 leather |
| Level 2 → 3 | ⛏️ ice pick (2×2) | 🪄 wizard's staff + 🪵 wood |
| Level 3 → 4 (summit) | 🫁 oxygen tank (3×3) | 🥽 alien suit + 🧱 iron bar |

The gear is **not lying around** — it must be **crafted** from the level's monster
drop plus a raw material (the drop alone won't do). In fact **nothing craftable is
ever found on the ground**: no weapons, no armour, no gear — only their raw
ingredients spawn, and the drop comes solely from the level's roaming monster. So each ascent is a
little quest: hunt the beast, take its drop, craft the kit, and *then* climb.
Once made, each piece can be **worn** in its own slot — so a full summit run wears
all four rather than filling four chunks of pack (the 🫁 oxygen tank alone is nine
cells). Leave it in the backpack and it competes with loot; wear it and the pack
stays free. You keep the gear as you climb.

### Monsters — one per level, each guarding the way up

Every level below the summit is prowled by its own **roaming monster**, wandering
one square at a time. Meet one and you can **hunt** it (a solo action, off the PvP
vote): you swing your equipped weapon — it can miss — and if it survives, it hits
back. Fell it and it drops the crafting ingredient for that level's gateway gear,
then another of its kind respawns to keep the level dangerous.

| Level | Monster | Drops | HP / bite |
|---|---|---|---|
| 0 | 🧟 yeti | 🧶 yeti pelt | 6 / 1 |
| 1 | 🐺 wolf | 🦷 wolf teeth | 8 / 2 |
| 2 | 🧙 evil wizard | 🪄 staff | 12 / 3 |
| 3 | 👽 alien | 🥽 space suit | 16 / 4 |

The ramp is deliberate: a bare-handed climber can just about wrestle a yeti, but
the alien wants a real weapon and a few snacks first — so the loop *find loot →
craft a weapon → hunt → craft gear → climb* is the spine of a run. Die to a
monster and you freeze into a corpsical, same as any other death.

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
│  💍 ring    📿 amulet    │  FIGHTING  on your turn: attack  │
│                          │            a foe / call parley   │
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
  combat/      # fight/trade/leave voting; turn-based fight state machine: per-turn
               # attack-a-chosen-foe (weapon damage in HP, can miss) or parley, elimination
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
  added to the roster and votes like anyone else. Any Fight vote resolves the
  round *immediately* and sends **everyone** to combat without waiting for the
  undecided; a peaceful outcome waits until all have voted, then opens tables
  between all Trade-voter pairs and puts Leave voters into a must-move state.
- **The fight is a turn-based state machine** (`Fight`), tested as one: fighters
  act in insertion order, one action per turn — **attack a chosen opponent**
  (weapon damage in hit-points, subject to a miss roll) or **call for parley**.
  A parley is offered to every other living fighter; unanimous acceptance ends
  the fight peacefully (survivors re-vote), a single rejection resumes it at the
  next turn. The fight ends at ≤1 standing or a truce. Turn ownership is enforced
  server-side — an out-of-turn action is rejected, so no player can advance the
  fight for anyone else. Arrivals join the fight and call off any pending parley.
- **Placement is bin-fitting, not bin-packing**: the player chooses where an item
  goes; the server only validates the footprint is free. The suffering is the
  content.
- **Concurrency**: a square is the unit of contention — actions within a square
  serialize on the square aggregate (optimistic locking + retry), so two players
  can't both grab the same ground item. Everything else scales out on virtual
  threads.
- **A fresh mountain every version**: the database persists across restarts and
  reboots, but each *new build* starts a clean ladder. On boot the app compares
  its build marker (a per-build timestamp stamped into `build-info.properties`) to
  the one in `deploy_marker`; if it differs it wipes every player and ground item,
  then records the new marker. So a reboot of the same build keeps the world, but
  a deploy — which may change item, gear, or monster rules — never leaves stale or
  now-invalid state behind. In-memory state (monsters, votes, presence) resets with
  the process regardless.

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

MIT. The only art asset is a tiny subset of **Noto Color Emoji** (SIL OFL 1.1),
bundled so every glyph the game uses renders identically across browsers instead
of depending on the player's system emoji font.
Inventory Tetris agony inspired by every game that ever made a sword take up
four slots.
