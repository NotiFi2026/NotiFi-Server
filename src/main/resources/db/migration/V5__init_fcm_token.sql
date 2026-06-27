-- ── tb_fcm_token ────────────────────────────────────────────────────────────
CREATE TABLE tb_fcm_token (
    fcm_token_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES tb_user (user_id),
    token        VARCHAR(512) NOT NULL,
    platform     VARCHAR(10)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_fcm_token           UNIQUE (token),
    CONSTRAINT ck_fcm_token_platform  CHECK (platform IN ('IOS', 'ANDROID')),
    CONSTRAINT ck_fcm_token_not_blank CHECK (length(trim(token)) > 0)
);

CREATE INDEX idx_fcm_token_user ON tb_fcm_token (user_id);

CREATE TRIGGER trg_fcm_token_updated_at
    BEFORE UPDATE ON tb_fcm_token
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
