-- Which item types a player has ever held. Drives the recipe panel's ❓ for ingredients they
-- haven't found yet: once discovered, an ingredient stays revealed even after it's spent.
ALTER TABLE player ADD COLUMN discovered jsonb NOT NULL DEFAULT '[]';
