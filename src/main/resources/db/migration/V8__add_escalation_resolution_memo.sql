-- V8: tb_escalation에 보호자 확인·해제 메모 컬럼 추가
-- summary는 AI Agent 사건요약 전용이므로 별도 컬럼으로 분리
ALTER TABLE tb_escalation
    ADD COLUMN resolution_memo TEXT;
