-- ── tb_daily_report ──────────────────────────────────────────────────────
-- 하루 누적 센싱을 LLM이 요약한 리포트. 노인·일자별 1건(I3 UPSERT).
-- summary_text(TEXT) 단일 문장이 아니라 sections(JSONB) 배열이다 — 태그가 늘어도 스키마 변경이 없다.
CREATE TABLE tb_daily_report (
    daily_report_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    care_target_id   BIGINT       NOT NULL REFERENCES tb_care_target (care_target_id),
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

    -- 제약명이 계약이다 — I3의 UPSERT 경합 분기가 이 이름으로 중복을 판정한다
    CONSTRAINT uq_daily_report_target_date UNIQUE (care_target_id, report_date),
    CONSTRAINT ck_daily_report_top_risk_level CHECK (top_risk_level IN ('SAFE', 'WARNING', 'DANGER'))
);

CREATE INDEX idx_daily_report_target_date ON tb_daily_report (care_target_id, report_date DESC);

CREATE TRIGGER trg_daily_report_updated_at
    BEFORE UPDATE ON tb_daily_report
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
