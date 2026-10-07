--liquibase formatted sql

--changeset vttg:003-lock-links-and-supersede
-- Первое одобрение фиксирует ссылки заявки: сменить их можно только новой
-- заявкой, которая после одобрения заменяет прежнюю (статус SUPERSEDED).
ALTER TABLE module_submissions ADD COLUMN approved_at TIMESTAMPTZ;

UPDATE module_submissions SET approved_at = reviewed_at WHERE status = 'APPROVED';

ALTER TABLE module_submissions DROP CONSTRAINT ck_module_submissions_status;
ALTER TABLE module_submissions ADD CONSTRAINT ck_module_submissions_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED'));

-- Раньше id модуля держала одна живая заявка. Теперь у автора одобренного
-- модуля может быть ещё и заявка-замена на рассмотрении: по одной каждого вида.
DROP INDEX ux_module_submissions_live_module_id;

CREATE UNIQUE INDEX ux_module_submissions_approved_module_id
    ON module_submissions (module_id)
    WHERE status = 'APPROVED';

CREATE UNIQUE INDEX ux_module_submissions_pending_module_id
    ON module_submissions (module_id)
    WHERE status = 'PENDING';
