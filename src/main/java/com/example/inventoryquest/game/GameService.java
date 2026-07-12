package com.example.inventoryquest.game;

import com.example.inventoryquest.combat.CombatService;
import com.example.inventoryquest.combat.VoteOption;
import com.example.inventoryquest.crafting.CraftingService;
import com.example.inventoryquest.crafting.Recipe;
import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.Cell;
import com.example.inventoryquest.inventory.EquippedItem;
import com.example.inventoryquest.inventory.InventoryService;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.ClimbGear;
import com.example.inventoryquest.mountain.Direction;
import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.mountain.Position;
import com.example.inventoryquest.mountain.RingMath;
import com.example.inventoryquest.mountain.SquareItem;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import com.example.inventoryquest.trade.TradeSession;
import com.example.inventoryquest.trade.TradeState;
import com.example.inventoryquest.trade.TradeTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The application service that turns UI intents into server-authoritative state changes. It is the
 * only place the feature services are woven together; controllers talk to this and nothing else.
 * Inventory actions (pick up / drop / equip / craft) are legal in every state — the backpack stays
 * interactive mid-vote and mid-fight — while movement is gated on the player's {@link GameState}.
 */
@Service
public class GameService {

    private final PlayerService players;
    private final MountainService mountain;
    private final InventoryService inventory;
    private final CraftingService crafting;
    private final SquareCoordinator coordinator;
    private final GameWebSocketHandler broadcaster;

    public GameService(PlayerService players, MountainService mountain, InventoryService inventory,
                       CraftingService crafting, SquareCoordinator coordinator,
                       GameWebSocketHandler broadcaster) {
        this.players = players;
        this.mountain = mountain;
        this.inventory = inventory;
        this.crafting = crafting;
        this.coordinator = coordinator;
        this.broadcaster = broadcaster;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────────

    @Transactional
    public Player spawn(String name) {
        Player player = players.spawn(name);
        mountain.seedIfEmpty(player.position());
        announceArrival(player);
        return player;
    }

    /**
     * Spawn a bot at a given square and, optionally, have it immediately cast a vote — enough to
     * trigger a real vote/trade/fight without recruiting 255 acquaintances. Dev use only.
     */
    @Transactional
    public Player addBot(String name, int level, int index, VoteOption vote) {
        Player bot = players.spawnAt(name, level, index);
        mountain.seedIfEmpty(bot.position());
        announceArrival(bot);
        if (vote != null) {
            Position pos = bot.position();
            coordinator.castVote(level, index, bot.getId(), vote, healthByPlayer(pos), attackDamageByPlayer(pos));
        }
        return bot;
    }

    // ── Movement ───────────────────────────────────────────────────────────────────

    @Transactional
    public Player move(UUID playerId, Direction direction) {
        Player player = players.require(playerId);
        GameState state = stateFor(player);
        if (state != GameState.IDLE && state != GameState.MUST_MOVE) {
            throw new GameException("You can't move while " + state.name().toLowerCase().replace('_', ' '));
        }
        Position from = player.position();
        if (!RingMath.canMove(from, direction)) {
            throw new GameException("There is no way " + direction.name().toLowerCase() + " from here");
        }
        if (direction == Direction.UP) {
            ClimbGear.requiredToLeave(from.level()).ifPresent(gear -> {
                if (!carries(player, gear)) {
                    throw new GameException("You need " + gear.emoji() + " " + gear.displayName()
                            + " in your backpack to climb from here");
                }
            });
        }
        Position to = RingMath.move(from, direction);
        player.moveTo(to);
        mountain.seedIfEmpty(to);
        players.save(player);

        coordinator.onDeparture(from.level(), from.index(), playerId, rosterIds(from, playerId));
        announceArrival(player);
        return player;
    }

    // ── Inventory (legal in every state) ─────────────────────────────────────────────

    @Transactional
    public void pickUp(UUID playerId, UUID squareItemId) {
        Player player = players.require(playerId);
        Optional<SquareItem> found = mountain.groundItems(player.position()).stream()
                .filter(i -> i.getId().equals(squareItemId)).findFirst();
        if (found.isEmpty()) {
            throw new GameException("That item is gone — someone else grabbed it");
        }
        ItemType type = found.get().getType();
        Cell cell = player.getBackpack().firstFreeFor(type)
                .orElseThrow(() -> new com.example.inventoryquest.inventory.InventoryException(
                        type.emoji() + " doesn't fit in your backpack"));
        SquareItem taken = mountain.take(squareItemId)
                .orElseThrow(() -> new GameException("That item is gone — someone else grabbed it"));
        PlacedItem placed = new PlacedItem(taken.getId(), type, cell.row(), cell.col());
        player.setBackpack(inventory.place(player.getBackpack(), placed));
        players.save(player);
        broadcaster.broadcastSquare(player.getLevel(), player.getSquareIndex());
    }

    @Transactional
    public void drop(UUID playerId, UUID instanceId) {
        Player player = players.require(playerId);
        InventoryService.Removed removed = inventory.remove(player.getBackpack(), instanceId);
        player.setBackpack(removed.backpack());
        players.save(player);
        mountain.scatter(player.position(), removed.item().type());
        broadcaster.broadcastSquare(player.getLevel(), player.getSquareIndex());
    }

    @Transactional
    public void equip(UUID playerId, UUID instanceId) {
        Player player = players.require(playerId);
        InventoryService.InventoryState state = inventory.equip(player.getBackpack(), player.getEquipment(), instanceId);
        player.setBackpack(state.backpack());
        player.setEquipment(state.equipment());
        players.save(player);
    }

    @Transactional
    public void unequip(UUID playerId, EquipSlot slot) {
        Player player = players.require(playerId);
        EquippedItem worn = player.getEquipment().get(slot);
        if (worn == null) {
            throw new com.example.inventoryquest.inventory.InventoryException(
                    "Nothing equipped in the " + slot.name().toLowerCase() + " slot");
        }
        Cell cell = player.getBackpack().firstFreeFor(worn.type())
                .orElseThrow(() -> new com.example.inventoryquest.inventory.InventoryException(
                        "No room to unequip " + worn.type().emoji()));
        InventoryService.InventoryState state =
                inventory.unequip(player.getBackpack(), player.getEquipment(), slot, cell.row(), cell.col());
        player.setBackpack(state.backpack());
        player.setEquipment(state.equipment());
        players.save(player);
    }

    @Transactional
    public void craft(UUID playerId, ItemType result) {
        Player player = players.require(playerId);
        Recipe recipe = crafting.recipeBook().producing(result).stream().findFirst()
                .orElseThrow(() -> new com.example.inventoryquest.crafting.CraftingException(
                        "No recipe produces " + result.emoji()));
        Set<UUID> selected = pickIngredientInstances(player.getBackpack(), recipe);
        Optional<Cell> cell = player.getBackpack().firstFreeFor(result, selected);
        if (cell.isPresent()) {
            player.setBackpack(crafting.craft(player.getBackpack(), recipe, selected, cell.get().row(), cell.get().col()));
            players.save(player);
        } else {
            // No room even after the ingredients are consumed: craft it anyway and drop the
            // artifact onto the square's ground, to be picked up once space is freed.
            player.setBackpack(crafting.consume(player.getBackpack(), recipe, selected));
            players.save(player);
            mountain.scatter(player.position(), result);
            broadcaster.broadcastSquare(player.getLevel(), player.getSquareIndex());
        }
    }

    /** Choose exactly the instances a recipe needs from the backpack (first matching of each type). */
    private Set<UUID> pickIngredientInstances(Backpack backpack, Recipe recipe) {
        Set<UUID> chosen = new java.util.LinkedHashSet<>();
        recipe.ingredientCounts().forEach((type, count) -> {
            List<PlacedItem> matching = backpack.items().stream()
                    .filter(i -> i.type() == type)
                    .limit(count)
                    .toList();
            if (matching.size() < count) {
                throw new com.example.inventoryquest.crafting.CraftingException(
                        "Not enough " + type.emoji() + " to craft " + recipe.result().emoji());
            }
            matching.forEach(i -> chosen.add(i.id()));
        });
        return chosen;
    }

    /** Eat a food item to restore health (capped at full), consuming it from the backpack. */
    @Transactional
    public void eat(UUID playerId, UUID instanceId) {
        Player player = players.require(playerId);
        InventoryService.Removed removed = inventory.remove(player.getBackpack(), instanceId);
        ItemType type = removed.item().type();
        if (!type.isFood()) {
            throw new com.example.inventoryquest.inventory.InventoryException(
                    type.emoji() + " isn't something you can eat");
        }
        player.setBackpack(removed.backpack());
        player.setHealth(Math.min(Player.MAX_HEALTH, player.getHealth() + type.heal()));
        players.save(player);
    }

    // ── Voting & combat ──────────────────────────────────────────────────────────────

    @Transactional
    public void castVote(UUID playerId, VoteOption option) {
        Player player = players.require(playerId);
        Position pos = player.position();
        coordinator.castVote(pos.level(), pos.index(), playerId, option,
                healthByPlayer(pos), attackDamageByPlayer(pos));
    }

    @Transactional
    public void stepFight(UUID playerId) {
        Player player = players.require(playerId);
        Position pos = player.position();
        Set<UUID> eliminated = coordinator.stepFight(pos.level(), pos.index());
        // Persist the round's outcome onto the players.
        Map<UUID, Integer> health = coordinator.fight(pos.level(), pos.index());
        health.forEach((id, hp) -> {
            Player p = players.require(id);
            p.setHealth(hp);
            players.save(p);
        });
        for (UUID id : eliminated) {
            Player dead = players.require(id);
            dead.setHealth(0);
            dead.setAlive(false);
            players.save(dead);
            coordinator.onDeparture(pos.level(), pos.index(), id, rosterIds(pos, id));
        }
    }

    // ── Trading ───────────────────────────────────────────────────────────────────

    @Transactional
    public void offer(UUID playerId, String tableId, UUID instanceId) {
        Player player = players.require(playerId);
        TradeSession session = requireTrade(player.position());
        session.place(UUID.fromString(tableId), playerId, instanceId);
        broadcaster.broadcastSquare(player.getLevel(), player.getSquareIndex());
    }

    @Transactional
    public void withdraw(UUID playerId, String tableId, UUID instanceId) {
        Player player = players.require(playerId);
        TradeSession session = requireTrade(player.position());
        session.remove(UUID.fromString(tableId), playerId, instanceId);
        broadcaster.broadcastSquare(player.getLevel(), player.getSquareIndex());
    }

    @Transactional
    public void propose(UUID playerId, String tableId) {
        Player player = players.require(playerId);
        requireTrade(player.position()).table(UUID.fromString(tableId)).propose(playerId);
        broadcaster.broadcastSquare(player.getLevel(), player.getSquareIndex());
    }

    @Transactional
    public void rejectTrade(UUID playerId, String tableId) {
        Player player = players.require(playerId);
        requireTrade(player.position()).table(UUID.fromString(tableId)).reject(playerId);
        broadcaster.broadcastSquare(player.getLevel(), player.getSquareIndex());
    }

    /** Accept a proposed table: swap the offered items between both backpacks, atomically. */
    @Transactional
    public void acceptTrade(UUID playerId, String tableId) {
        Player accepter = players.require(playerId);
        TradeSession session = requireTrade(accepter.position());
        TradeTable table = session.table(UUID.fromString(tableId));
        UUID otherId = table.leftPlayer().equals(playerId) ? table.rightPlayer() : table.leftPlayer();
        Player other = players.require(otherId);

        Set<UUID> accepterGives = table.itemsFor(playerId);
        Set<UUID> otherGives = table.itemsFor(otherId);
        table.accept(playerId); // validates state / proposer

        Backpack accepterBp = moveItems(accepter.getBackpack(), other, accepterGives);
        Backpack otherBp = moveItems(other.getBackpack(), accepter, otherGives);
        accepter.setBackpack(accepterBp);
        other.setBackpack(otherBp);
        players.save(accepter);
        players.save(other);
        broadcaster.broadcastSquare(accepter.getLevel(), accepter.getSquareIndex());
    }

    /** Remove {@code ids} from {@code from}'s backpack and place them into {@code recipient}'s. */
    private Backpack moveItems(Backpack from, Player recipient, Set<UUID> ids) {
        Backpack sender = from;
        Backpack recipientBp = recipient.getBackpack();
        for (UUID id : ids) {
            PlacedItem item = sender.find(id).orElseThrow(() ->
                    new com.example.inventoryquest.inventory.InventoryException("Offered item vanished: " + id));
            Cell cell = recipientBp.firstFreeFor(item.type()).orElseThrow(() ->
                    new com.example.inventoryquest.inventory.InventoryException(
                            recipient.getName() + "'s backpack has no room for " + item.type().emoji()));
            recipientBp = recipientBp.place(new PlacedItem(item.id(), item.type(), cell.row(), cell.col())).orElseThrow();
            sender = sender.remove(id);
        }
        recipient.setBackpack(recipientBp);
        return sender;
    }

    private TradeSession requireTrade(Position pos) {
        return coordinator.trade(pos.level(), pos.index())
                .orElseThrow(() -> new GameException("No trade is open in this square"));
    }

    // ── Snapshots for rendering ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GameSnapshot snapshot(UUID playerId) {
        return snapshot(playerId, Set.of(), null);
    }

    @Transactional(readOnly = true)
    public GameSnapshot snapshot(UUID playerId, Set<UUID> selected, String message) {
        Player player = players.require(playerId);
        Position pos = player.position();
        GameState state = stateFor(player);

        List<GroundView> ground = mountain.groundItems(pos).stream()
                .map(si -> GroundView.of(si.getId(), si.getType(),
                        player.getBackpack().firstFreeFor(si.getType()).isPresent()))
                .toList();

        List<Player> roster = players.inSquare(pos.level(), pos.index());
        List<String> others = roster.stream()
                .filter(p -> !p.getId().equals(playerId))
                .map(Player::getName)
                .toList();

        List<RecipeRow> recipes = selected.isEmpty() ? List.of() : recipesFor(player, selected);

        List<GameSnapshot.TradeTableView> tables = tradeTablesFor(player, state);
        GameSnapshot.FightView fight = fightViewFor(player, state);

        Optional<ItemType> gear = ClimbGear.requiredToLeave(pos.level());
        String climbGear = gear.map(g -> g.emoji() + " " + g.displayName()).orElse(null);
        boolean readyToClimb = gear.map(g -> carries(player, g)).orElse(true);

        return new GameSnapshot(player, Hearts.render(player.getHealth()), state, RingMath.squaresAt(pos.level()),
                pos.level() < RingMath.SUMMIT_LEVEL, climbGear, readyToClimb, ground, others, roster.size(),
                coordinator.hasVoted(pos.level(), pos.index(), playerId), recipes, selected,
                tables, fight, message);
    }

    /** The live recipe filter: recipes containing every selected ingredient type. */
    private List<RecipeRow> recipesFor(Player player, Set<UUID> selectedInstanceIds) {
        Set<ItemType> selectedTypes = player.getBackpack().items().stream()
                .filter(i -> selectedInstanceIds.contains(i.id()))
                .map(PlacedItem::type)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<ItemType, Integer> available = crafting.availableIngredients(player.getBackpack());
        return crafting.recipeBook().containingAll(selectedTypes).stream()
                .map(r -> RecipeRow.from(r, available))
                .toList();
    }

    private List<GameSnapshot.TradeTableView> tradeTablesFor(Player player, GameState state) {
        if (state != GameState.TRADING) {
            return List.of();
        }
        Position pos = player.position();
        TradeSession session = coordinator.trade(pos.level(), pos.index()).orElse(null);
        if (session == null) {
            return List.of();
        }
        Map<UUID, Player> roster = players.inSquare(pos.level(), pos.index()).stream()
                .collect(Collectors.toMap(Player::getId, p -> p));
        List<GameSnapshot.TradeTableView> views = new ArrayList<>();
        for (TradeTable table : session.tablesFor(player.getId())) {
            UUID otherId = table.leftPlayer().equals(player.getId()) ? table.rightPlayer() : table.leftPlayer();
            Player opponent = roster.getOrDefault(otherId, player);
            boolean iProposed = player.getId().equals(table.proposedBy());
            boolean iCanAccept = table.state() == TradeState.PROPOSED && !iProposed;
            views.add(new GameSnapshot.TradeTableView(
                    table.id().toString(),
                    opponent.getName(),
                    offers(player.getBackpack(), table.itemsFor(player.getId())),
                    offers(opponent.getBackpack(), table.itemsFor(otherId)),
                    table.state().name(), iProposed, iCanAccept));
        }
        return views;
    }

    private List<GameSnapshot.OfferView> offers(Backpack backpack, Set<UUID> ids) {
        return ids.stream()
                .map(backpack::find)
                .filter(Optional::isPresent).map(Optional::get)
                .map(i -> new GameSnapshot.OfferView(i.id().toString(), i.type().emoji()))
                .toList();
    }

    private GameSnapshot.FightView fightViewFor(Player player, GameState state) {
        if (state != GameState.FIGHTING) {
            return null;
        }
        Position pos = player.position();
        Map<UUID, Integer> fight = coordinator.fight(pos.level(), pos.index());
        int myHp = fight.getOrDefault(player.getId(), player.getHealth());
        return new GameSnapshot.FightView(Hearts.render(myHp), fight.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private GameState stateFor(Player player) {
        if (!player.isAlive()) {
            return GameState.ELIMINATED;
        }
        if (player.position().isSummit()) {
            return GameState.SUMMIT;
        }
        Position pos = player.position();
        int rosterSize = players.inSquare(pos.level(), pos.index()).size();
        return coordinator.stateFor(pos.level(), pos.index(), player.getId(), rosterSize);
    }

    private void announceArrival(Player player) {
        Position pos = player.position();
        coordinator.onArrival(pos.level(), pos.index(), player.getId(),
                rosterIds(pos, null), healthByPlayer(pos), attackDamageByPlayer(pos));
    }

    /** Alive player ids in a square, optionally excluding one (e.g. a player who just left). */
    private Set<UUID> rosterIds(Position pos, UUID exclude) {
        return players.inSquare(pos.level(), pos.index()).stream()
                .map(Player::getId)
                .filter(id -> !id.equals(exclude))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Map<UUID, Integer> healthByPlayer(Position pos) {
        Map<UUID, Integer> health = new LinkedHashMap<>();
        players.inSquare(pos.level(), pos.index()).forEach(p -> health.put(p.getId(), p.getHealth()));
        return health;
    }

    /** Each player's attack damage, from their equipped weapon (or bare hands). */
    private Map<UUID, Integer> attackDamageByPlayer(Position pos) {
        Map<UUID, Integer> damage = new LinkedHashMap<>();
        players.inSquare(pos.level(), pos.index()).forEach(p -> damage.put(p.getId(), attackDamage(p)));
        return damage;
    }

    private int attackDamage(Player player) {
        EquippedItem weapon = player.getEquipment().get(EquipSlot.SWORD);
        return weapon != null ? weapon.type().damage() : CombatService.UNARMED_DAMAGE;
    }

    /** Is the player carrying an item of this type in their backpack? */
    private boolean carries(Player player, ItemType type) {
        return player.getBackpack().items().stream().anyMatch(i -> i.type() == type);
    }
}
