-- InventoryQuest initial schema.
-- Flyway owns the schema; Hibernate runs with ddl-auto=validate and only checks it matches.

CREATE TABLE player (
    id           UUID PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL,
    level        INT          NOT NULL,
    square_index INT          NOT NULL,
    health       INT          NOT NULL,
    alive        BOOLEAN      NOT NULL,
    backpack     JSONB        NOT NULL,
    equipment    JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL
);

-- Fast roster lookups: everyone standing in a given square.
CREATE INDEX idx_player_square ON player (level, square_index) WHERE alive;

CREATE TABLE square_item (
    id           UUID PRIMARY KEY,
    level        INT          NOT NULL,
    square_index INT          NOT NULL,
    type         VARCHAR(32)  NOT NULL
);

CREATE INDEX idx_square_item_square ON square_item (level, square_index);
