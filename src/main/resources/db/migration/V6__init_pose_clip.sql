-- ── tb_pose_clip ─────────────────────────────────────────────────────────
CREATE TABLE tb_pose_clip (
    pose_clip_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sensing_event_id   BIGINT       NOT NULL REFERENCES tb_sensing_event (sensing_event_id),
    model_version      VARCHAR(30)  NOT NULL,
    joint_schema       VARCHAR(30)  NOT NULL,
    fps                SMALLINT     NOT NULL,
    frame_count        INTEGER      NOT NULL,
    duration_ms        INTEGER,
    window_start_at    TIMESTAMPTZ  NOT NULL,
    window_end_at      TIMESTAMPTZ  NOT NULL,
    frames             JSONB        NOT NULL,
    event_timeline     JSONB,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_pose_clip_event    UNIQUE (sensing_event_id),
    CONSTRAINT ck_pose_clip_fps      CHECK (fps > 0),
    CONSTRAINT ck_pose_clip_frame_count CHECK (frame_count >= 0)
);
