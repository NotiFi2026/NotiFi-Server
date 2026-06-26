-- ── tb_sensing_event ──────────────────────────────────────────────────────
CREATE TABLE tb_sensing_event (
    sensing_event_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    care_target_id    BIGINT          NOT NULL REFERENCES tb_care_target (care_target_id),
    device_id         BIGINT                   REFERENCES tb_device (device_id),
    event_type        VARCHAR(30)     NOT NULL,
    risk_probability  NUMERIC(4,3),
    anomaly_score     NUMERIC(4,3),
    trend_score       NUMERIC(4,3),
    sensor_status     VARCHAR(20),
    model_version     VARCHAR(30)     NOT NULL,
    features          JSONB,
    detected_at       TIMESTAMPTZ     NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_sensing_event_type CHECK (event_type IN (
        'FALL', 'INACTIVITY', 'RESPIRATION_ABNORMAL', 'ANOMALY', 'SENSOR_ERROR', 'NORMAL'
    )),
    CONSTRAINT ck_sensing_sensor_status CHECK (sensor_status IN ('OK', 'ERROR') OR sensor_status IS NULL),
    CONSTRAINT ck_sensing_risk_probability  CHECK (risk_probability  BETWEEN 0 AND 1 OR risk_probability  IS NULL),
    CONSTRAINT ck_sensing_anomaly_score     CHECK (anomaly_score     BETWEEN 0 AND 1 OR anomaly_score     IS NULL),
    CONSTRAINT ck_sensing_trend_score       CHECK (trend_score       BETWEEN 0 AND 1 OR trend_score       IS NULL),
    CONSTRAINT uq_sensing_event_identity UNIQUE (care_target_id, detected_at, event_type)
);

CREATE INDEX idx_sensing_event_care_target_detected ON tb_sensing_event (care_target_id, detected_at DESC);

-- ── tb_risk_assessment ────────────────────────────────────────────────────
CREATE TABLE tb_risk_assessment (
    risk_assessment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sensing_event_id   BIGINT      NOT NULL REFERENCES tb_sensing_event (sensing_event_id),
    risk_score         SMALLINT    NOT NULL,
    risk_level         VARCHAR(20) NOT NULL,
    score_breakdown    JSONB,
    model_version      VARCHAR(30) NOT NULL,
    assessed_at        TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_risk_assessment_event UNIQUE (sensing_event_id),
    CONSTRAINT ck_risk_score CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT ck_risk_level CHECK (risk_level IN ('SAFE', 'WARNING', 'DANGER'))
);

-- ── tb_escalation ─────────────────────────────────────────────────────────
CREATE TABLE tb_escalation (
    escalation_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_assessment_id  BIGINT          NOT NULL REFERENCES tb_risk_assessment (risk_assessment_id),
    status              VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS',
    resolution_type     VARCHAR(30),
    summary             TEXT,
    started_at          TIMESTAMPTZ     NOT NULL,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_escalation_risk_assessment UNIQUE (risk_assessment_id),
    CONSTRAINT ck_escalation_status CHECK (status IN ('IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_escalation_resolution_type CHECK (
        resolution_type IN ('FALSE_ALARM', 'SELF_RESOLVED', 'GUARDIAN_HANDLED', 'EMERGENCY_DISPATCHED')
        OR resolution_type IS NULL
    )
);

CREATE INDEX idx_escalation_status ON tb_escalation (status);

CREATE TRIGGER trg_escalation_updated_at
    BEFORE UPDATE ON tb_escalation
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
