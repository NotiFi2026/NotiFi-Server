-- tb_pose_clip 누락 CHECK 제약 추가 (V6 forward-only 보완)
ALTER TABLE tb_pose_clip
    ADD CONSTRAINT ck_pose_clip_duration CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD CONSTRAINT ck_pose_clip_window   CHECK (window_end_at >= window_start_at);
