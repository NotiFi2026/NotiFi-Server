-- ── tb_device ─────────────────────────────────────────────────────────────
CREATE TABLE tb_device (
    device_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    care_target_id   BIGINT          NOT NULL REFERENCES tb_care_target (care_target_id),
    device_uid       VARCHAR(64)     NOT NULL,
    room             VARCHAR(50),
    position_label   VARCHAR(100),
    node_role        VARCHAR(20),
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    firmware_version VARCHAR(30),
    last_seen_at     TIMESTAMPTZ,
    registered_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_device_uid          UNIQUE (device_uid),
    CONSTRAINT ck_device_uid_not_blank CHECK (length(trim(device_uid)) > 0),
    CONSTRAINT ck_device_status        CHECK (status    IN ('ACTIVE', 'INACTIVE', 'ERROR')),
    CONSTRAINT ck_device_node_role CHECK (node_role IN ('SENDER', 'RECEIVER') OR node_role IS NULL)
);

CREATE INDEX idx_device_care_target_status ON tb_device (care_target_id, status);

CREATE TRIGGER trg_device_updated_at
    BEFORE UPDATE ON tb_device
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
