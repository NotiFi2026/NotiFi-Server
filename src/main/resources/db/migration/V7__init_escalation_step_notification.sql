-- ── tb_escalation_step ───────────────────────────────────────────────────
CREATE TABLE tb_escalation_step (
    step_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    escalation_id   BIGINT      NOT NULL REFERENCES tb_escalation (escalation_id) ON DELETE CASCADE,
    step_type       VARCHAR(30) NOT NULL,
    step_order      SMALLINT    NOT NULL,
    status          VARCHAR(20) NOT NULL,
    executed_at     TIMESTAMPTZ,
    responded_at    TIMESTAMPTZ,
    response_detail JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_escalation_step         UNIQUE (escalation_id, step_type),
    CONSTRAINT ck_escalation_step_type    CHECK (step_type IN ('VOICE_CHECK', 'GUARDIAN_NOTIFY', 'EMERGENCY_CALL')),
    CONSTRAINT ck_escalation_step_status  CHECK (status IN ('PENDING', 'EXECUTED', 'RESPONDED', 'NO_RESPONSE', 'SKIPPED'))
);

CREATE INDEX idx_escalation_step_order ON tb_escalation_step (escalation_id, step_order);

-- ── tb_notification ───────────────────────────────────────────────────────
CREATE TABLE tb_notification (
    notification_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    escalation_step_id  BIGINT       REFERENCES tb_escalation_step (step_id),
    recipient_user_id   BIGINT       NOT NULL REFERENCES tb_user (user_id),
    care_target_id      BIGINT       NOT NULL REFERENCES tb_care_target (care_target_id),
    channel             VARCHAR(20)  NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    body                TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    sent_at             TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_notification_channel   CHECK (channel IN ('FCM_PUSH', 'SMS', 'EMAIL')),
    CONSTRAINT ck_notification_category  CHECK (category IN ('EMERGENCY', 'DAILY_REPORT', 'SYSTEM')),
    CONSTRAINT ck_notification_status    CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'READ'))
);

CREATE INDEX idx_notification_recipient ON tb_notification (recipient_user_id, created_at DESC);
CREATE INDEX idx_notification_care_target ON tb_notification (care_target_id, category);
CREATE INDEX idx_notification_status ON tb_notification (status);
