-- 노인(피보호자) 계정 도입 — VOICE_CHECK STT/TTS를 노인 앱으로 수행하기 위한 스키마 확장

-- 1) role CHECK 교체: CARE_RECIPIENT 추가
ALTER TABLE tb_user DROP CONSTRAINT ck_user_role;
ALTER TABLE tb_user ADD CONSTRAINT ck_user_role
    CHECK (role IN ('GUARDIAN', 'SOCIAL_WORKER', 'ADMIN', 'CARE_RECIPIENT'));

-- 2) 노인 → 계정 연결. 계정 없는 기존 노인은 NULL 공존
ALTER TABLE tb_care_target
    ADD COLUMN user_id BIGINT REFERENCES tb_user (user_id);

-- 3) 계정 1개당 노인 1명 — NULL 다수 허용, 값은 유일
CREATE UNIQUE INDEX uq_care_target_user_id
    ON tb_care_target (user_id) WHERE user_id IS NOT NULL;
