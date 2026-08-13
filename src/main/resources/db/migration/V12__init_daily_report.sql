-- ── tb_daily_report ──────────────────────────────────────────────────────
-- 하루 누적 센싱을 LLM이 요약한 리포트. 노인·일자별 1건(I3 UPSERT).
-- summary_text(TEXT) 단일 문장이 아니라 sections(JSONB) 배열이다 — 태그가 늘어도 스키마 변경이 없다.
CREATE TABLE tb_daily_report (
    daily_report_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    care_target_id   BIGINT       NOT NULL,
    report_date      DATE         NOT NULL,
    sections         JSONB        NOT NULL,
    metrics          JSONB,
    -- P1 목록 카드용 비정규화. 매 행 sections를 파싱하지 않게 하고,
    -- "대표 등급" 규칙을 적재 한 곳(I3)에만 두기 위해 컬럼으로 뽑는다.
    top_risk_level   VARCHAR(20)  NOT NULL,
    headline         VARCHAR(200),
    generated_at     TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- 제약명이 계약이다 — I3의 UPSERT 경합 분기가 uq_ 이름으로 중복을 판정하고,
    -- 그 판정이 FK 위반까지 삼키지 않는지를 fk_ 이름으로 테스트가 대조한다.
    -- 인라인 REFERENCES로 두면 Postgres가 이름을 자동 생성해 테스트가 허구의 이름을 쓰게 된다.
    CONSTRAINT fk_daily_report_care_target FOREIGN KEY (care_target_id)
        REFERENCES tb_care_target (care_target_id),
    CONSTRAINT uq_daily_report_target_date UNIQUE (care_target_id, report_date),
    CONSTRAINT ck_daily_report_top_risk_level CHECK (top_risk_level IN ('SAFE', 'WARNING', 'DANGER'))
);

-- P1 목록의 (care_target_id, report_date DESC) 조회는 uq_daily_report_target_date가 만드는
-- B-tree로 충분하다 — Postgres가 역방향 스캔을 하므로 DESC 전용 인덱스를 따로 두면 쓰기 비용만 는다.

CREATE TRIGGER trg_daily_report_updated_at
    BEFORE UPDATE ON tb_daily_report
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
