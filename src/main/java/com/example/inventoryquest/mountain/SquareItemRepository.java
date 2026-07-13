package com.example.inventoryquest.mountain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SquareItemRepository extends JpaRepository<SquareItem, UUID> {

    List<SquareItem> findByLevelAndSquareIndex(int level, int squareIndex);

    void deleteByLevelAndSquareIndex(int level, int squareIndex);
}
