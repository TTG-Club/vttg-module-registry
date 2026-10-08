--liquibase formatted sql

--changeset vttg:004-systems-from-manifest
-- Системы заявки теперь берутся из compatibleSystems манифеста, а не из
-- формы. Автор вправе назвать систему, которой нет в справочнике: VTTG
-- сверяет совместимость по id из манифеста, справочник даёт только названия.
-- Системы уже поданных заявок остаются прежними до перечитывания манифеста.
ALTER TABLE module_submission_systems
    DROP CONSTRAINT IF EXISTS module_submission_systems_system_id_fkey;
