package com.example.inventoryquest.mountain;

import com.example.inventoryquest.item.ItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An item lying on the ground in a particular square, waiting to be picked up (if it fits).
 * The mountain provides: items appear as players climb.
 */
@Entity
@Table(name = "square_item")
@Getter
@Setter
@NoArgsConstructor
public class SquareItem {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int squareIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType type;

    public static SquareItem drop(Position at, ItemType type) {
        SquareItem item = new SquareItem();
        item.id = UUID.randomUUID();
        item.level = at.level();
        item.squareIndex = at.index();
        item.type = type;
        return item;
    }

    public Position position() {
        return new Position(level, squareIndex);
    }
}
