-- 배포 전 µs 정밀도 행을 ms로 정규화 — 멱등키(care_target_id, detected_at, event_type) 공간을 ms로 통일
UPDATE tb_sensing_event SET detected_at = date_trunc('milliseconds', detected_at);

-- AI v1 17행동 클래스 수용 — event_type의 세부 활동 분류 (nullable, AI 서버가 채움)
ALTER TABLE tb_sensing_event
    ADD COLUMN activity_class VARCHAR(30);

ALTER TABLE tb_sensing_event
    ADD CONSTRAINT ck_sensing_activity_class CHECK (activity_class IN (
        'WALKING', 'STANDING_STILL', 'SITTING_STILL', 'LYING_STILL',
        'LIE_TO_STAND', 'STAND_TO_LIE_NORMAL', 'ABSENCE', 'SIT_TO_STAND', 'STAND_TO_SIT',
        'UNSTABLE_WALKING', 'STUMBLE_RECOVER', 'BED_EXIT_FAILED',
        'FALL_FROM_STANDING', 'FALL_WHILE_WALKING', 'BED_EXIT_FALL', 'BED_FALL', 'CHAIR_EXIT_FALL'
    ) OR activity_class IS NULL);
