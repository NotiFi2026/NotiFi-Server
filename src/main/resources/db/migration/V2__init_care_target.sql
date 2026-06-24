-- ── tb_care_target ────────────────────────────────────────────────────────
CREATE TABLE tb_care_target (
    care_target_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    birth_date      DATE,
    gender          VARCHAR(10),
    address         VARCHAR(255),
    emergency_memo  TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT ck_care_target_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE INDEX idx_care_target_deleted ON tb_care_target (deleted_at);

CREATE TRIGGER trg_care_target_updated_at
    BEFORE UPDATE ON tb_care_target
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── tb_care_relationship ───────────────────────────────────────────────────
CREATE TABLE tb_care_relationship (
    relationship_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    care_target_id      BIGINT          NOT NULL REFERENCES tb_care_target (care_target_id) ON DELETE CASCADE,
    relationship_type   VARCHAR(20)     NOT NULL,
    is_primary          BOOLEAN         NOT NULL DEFAULT FALSE,
    notify_priority     SMALLINT        NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_care_relationship UNIQUE (user_id, care_target_id),
    CONSTRAINT ck_relationship_type CHECK (relationship_type IN ('FAMILY', 'SOCIAL_WORKER', 'CAREGIVER'))
);

CREATE INDEX idx_care_relationship_target_priority ON tb_care_relationship (care_target_id, notify_priority);
CREATE INDEX idx_care_relationship_user            ON tb_care_relationship (user_id);
