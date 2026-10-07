--liquibase formatted sql

--changeset vttg:002-create-module-submissions
CREATE TABLE module_submissions
(
    id                 UUID          NOT NULL PRIMARY KEY,
    author_id          UUID          NOT NULL,
    author_name        VARCHAR(100),
    repository_url     VARCHAR(2048) NOT NULL,
    manifest_url       VARCHAR(2048) NOT NULL,
    description        VARCHAR(1000) NOT NULL,

    -- Снимок module.json на момент подачи или последнего обновления.
    module_id          VARCHAR(64)   NOT NULL,
    module_name        VARCHAR(150)  NOT NULL,
    module_version     VARCHAR(50)   NOT NULL,
    module_author      VARCHAR(150),
    module_icon        VARCHAR(100),
    download_url       VARCHAR(2048) NOT NULL,
    manifest_json      TEXT          NOT NULL,
    manifest_synced_at TIMESTAMPTZ   NOT NULL,

    status             VARCHAR(20)   NOT NULL,
    moderator_id       UUID,
    moderation_comment VARCHAR(2000),
    reviewed_at        TIMESTAMPTZ,

    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_module_submissions_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_module_submissions_rejection_comment
        CHECK (status <> 'REJECTED' OR moderation_comment IS NOT NULL)
);

-- Один id модуля — одна живая заявка: на рассмотрении или одобренная.
-- Отклонённые и отозванные id не держат, их можно подать заново.
CREATE UNIQUE INDEX ux_module_submissions_live_module_id
    ON module_submissions (module_id)
    WHERE status IN ('PENDING', 'APPROVED');

CREATE INDEX ix_module_submissions_author ON module_submissions (author_id, created_at DESC);
CREATE INDEX ix_module_submissions_status ON module_submissions (status, created_at);

-- Пустой набор систем = модуль универсальный.
CREATE TABLE module_submission_systems
(
    submission_id UUID        NOT NULL REFERENCES module_submissions (id) ON DELETE CASCADE,
    system_id     VARCHAR(64) NOT NULL REFERENCES game_systems (id),

    PRIMARY KEY (submission_id, system_id)
);

CREATE INDEX ix_module_submission_systems_system ON module_submission_systems (system_id);
