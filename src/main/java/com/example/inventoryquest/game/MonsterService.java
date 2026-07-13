package com.example.inventoryquest.game;

import com.example.inventoryquest.combat.CombatService;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.Position;
import com.example.inventoryquest.mountain.RingMath;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * The mountain's roaming monsters — one type per level, kept in memory (like the other transient
 * coordination state). Each level is stocked with a handful of its monster; they wander one square
 * per tick. A player who shares a monster's square can {@link #hunt} it: one exchange per call,
 * reusing the combat miss chance. Slain, it drops its ingredient (the caller scatters it) and a
 * fresh one respawns so the type keeps roaming. Monsters never touch players or the database
 * directly, so the feature graph stays acyclic.
 */
@Component
public class MonsterService {

    /** Roughly one monster per this many squares on a level. */
    private static final int SQUARES_PER_MONSTER = 8;
    private static final long ROAM_PERIOD_MS = 8000;

    /** What is roaming a square, for the UI. */
    public record Sighting(String emoji, String name, int hp, int maxHp) {
    }

    /** The result of one hunting exchange. */
    public record HuntOutcome(boolean encountered, boolean playerHit, boolean monsterSlain, ItemType drop,
                              int monsterHpAfter, int maxHp, boolean monsterHit, int playerDamage,
                              String monsterName, String emoji) {
        static HuntOutcome none() {
            return new HuntOutcome(false, false, false, null, 0, 0, false, 0, "", "");
        }
    }

    private static final class Monster {
        final UUID id = UUID.randomUUID();
        final MonsterKind kind;
        final int level;
        int index;
        int hp;

        Monster(MonsterKind kind, int level, int index) {
            this.kind = kind;
            this.level = level;
            this.index = index;
            this.hp = kind.maxHp();
        }
    }

    private final Map<UUID, Monster> monsters = new ConcurrentHashMap<>();
    private final RandomGenerator rng;
    private final GameWebSocketHandler broadcaster;

    public MonsterService(RandomGenerator rng, GameWebSocketHandler broadcaster) {
        this.rng = rng;
        this.broadcaster = broadcaster;
    }

    @PostConstruct
    synchronized void populate() {
        for (int level = 0; level < RingMath.SUMMIT_LEVEL; level++) {
            MonsterKind kind = MonsterKind.forLevel(level).orElse(null);
            if (kind == null) {
                continue;
            }
            int count = Math.max(1, RingMath.squaresAt(level) / SQUARES_PER_MONSTER);
            for (int i = 0; i < count; i++) {
                spawn(kind, level);
            }
        }
    }

    private void spawn(MonsterKind kind, int level) {
        Monster m = new Monster(kind, level, rng.nextInt(RingMath.squaresAt(level)));
        monsters.put(m.id, m);
    }

    private Optional<Monster> at(int level, int index) {
        return monsters.values().stream()
                .filter(m -> m.level == level && m.index == index)
                .findFirst();
    }

    /** The monster roaming this square, if any, for rendering. */
    public synchronized Optional<Sighting> sighting(int level, int index) {
        return at(level, index)
                .map(m -> new Sighting(m.kind.emoji(), m.kind.displayName(), m.hp, m.kind.maxHp()));
    }

    /**
     * One hunting exchange in a square: the player strikes (may miss); if the monster survives it
     * strikes back (may miss). A slain monster reports its drop and respawns elsewhere on the level.
     */
    public synchronized HuntOutcome hunt(int level, int index, int weaponDamage) {
        Monster m = at(level, index).orElse(null);
        if (m == null) {
            return HuntOutcome.none();
        }
        boolean playerHit = rng.nextDouble() < CombatService.HIT_CHANCE;
        if (playerHit) {
            m.hp -= weaponDamage;
        }
        if (m.hp <= 0) {
            monsters.remove(m.id);
            spawn(m.kind, m.level); // the type keeps roaming the level
            return new HuntOutcome(true, true, true, m.kind.drop(), 0, m.kind.maxHp(),
                    false, 0, m.kind.displayName(), m.kind.emoji());
        }
        boolean monsterHit = rng.nextDouble() < CombatService.HIT_CHANCE;
        int dealt = monsterHit ? m.kind.damage() : 0;
        return new HuntOutcome(true, playerHit, false, null, m.hp, m.kind.maxHp(),
                monsterHit, dealt, m.kind.displayName(), m.kind.emoji());
    }

    /** Nudge every monster one square around its ring, and refresh the squares it left and entered. */
    @Scheduled(fixedDelay = ROAM_PERIOD_MS)
    public void roam() {
        List<int[]> touched = new ArrayList<>();
        synchronized (this) {
            for (Monster m : monsters.values()) {
                int from = m.index;
                Position moved = rng.nextBoolean()
                        ? RingMath.left(new Position(m.level, m.index))
                        : RingMath.right(new Position(m.level, m.index));
                m.index = moved.index();
                touched.add(new int[]{m.level, from});
                touched.add(new int[]{m.level, m.index});
            }
        }
        touched.forEach(li -> broadcaster.broadcastSquare(li[0], li[1]));
    }
}
