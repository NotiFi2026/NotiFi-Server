-- ── updated_at 자동 갱신 트리거 함수 (전체 공통) ─────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── tb_user ───────────────────────────────────────────────────────────────
CREATE TABLE tb_user (
    user_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20)     NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT uq_user_email UNIQUE (email),
    CONSTRAINT ck_user_role  CHECK (role IN ('GUARDIAN', 'SOCIAL_WORKER', 'ADMIN'))
);

CREATE INDEX idx_user_role_active ON tb_user (role, is_active);

CREATE TRIGGER trg_user_updated_at
    BEFORE UPDATE ON tb_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
