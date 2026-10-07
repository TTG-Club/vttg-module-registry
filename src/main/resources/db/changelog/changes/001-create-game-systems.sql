--liquibase formatted sql

--changeset vttg:001-create-game-systems
-- Справочник игровых систем VTTG. id совпадает с id из system.json системы:
-- его же модуль пишет в compatibleSystems, и по нему VTTG фильтрует каталог.
CREATE TABLE game_systems
(
    id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_game_systems_id CHECK (id ~ '^[a-z0-9][a-z0-9_-]*$')
);

INSERT INTO game_systems (id, name, created_at)
VALUES ('dnd5e-2024', 'D&D 5e (2024)', now());
