package com.example.inventoryquest.player;

import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.EquippedItem;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.mountain.Position;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * A climber. The player is the aggregate root for everything they carry: position on the
 * mountain, health, the {@link Backpack} (stored as JSONB), and the four equipment slots (also
 * JSONB). Mutations go through {@link PlayerService}; the {@code @Version} column gives the
 * optimistic locking that serialises contended actions within a square.
 *
 * <p>Lombok {@code @Getter @Setter} only — never {@code @Data} on an entity, whose generated
 * equals/hashCode misbehaves with Hibernate proxies.
 */
@Entity
@Table(name = "player")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    public static final int BACKPACK_ROWS = 5;
    public static final int BACKPACK_COLS = 6;
    /** Health is tracked in hit-points; 4 HP make up one heart, for 4 hearts of health. */
    public static final int HP_PER_HEART = 4;
    public static final int MAX_HEARTS = 4;
    public static final int MAX_HEALTH = MAX_HEARTS * HP_PER_HEART; // 16 HP

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Mountain level, 0 (base) … 4 (summit). */
    @Column(nullable = false)
    private int level;

    /** Index around the ring at the current level. */
    @Column(nullable = false)
    private int squareIndex;

    @Column(nullable = false)
    private int health;

    @Column(nullable = false)
    private boolean alive;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Backpack backpack;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<EquipSlot, EquippedItem> equipment = new EnumMap<>(EquipSlot.class);

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    /** Create a fresh climber at a base-ring square with a full, empty backpack. */
    public static Player spawn(String name, int squareIndex, Instant now) {
        Player p = new Player();
        p.id = UUID.randomUUID();
        p.name = name;
        p.level = 0;
        p.squareIndex = squareIndex;
        p.health = MAX_HEALTH;
        p.alive = true;
        p.backpack = Backpack.empty(BACKPACK_ROWS, BACKPACK_COLS);
        p.equipment = new EnumMap<>(EquipSlot.class);
        p.createdAt = now;
        return p;
    }

    public Position position() {
        return new Position(level, squareIndex);
    }

    public void moveTo(Position position) {
        this.level = position.level();
        this.squareIndex = position.index();
    }
}
