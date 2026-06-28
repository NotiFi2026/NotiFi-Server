# 데이터베이스 명세서 — 노인 Safety Agent

| 항목 | 내용 |
|---|---|
| **프로젝트** | 카메라·웨어러블 없는 노인 Safety Agent |
| **DBMS** | PostgreSQL 15+ |
| **백엔드** | Spring Boot (JPA / Hibernate) |
| **문서 버전** | v0.7 |
| **작성일** | 2026-06-09 |
| **최종 수정일** | 2026-06-28 |

### 수정 이력

| 버전 | 날짜 | 내용 | 작성자 |
|---|---|---|---|
| v0.1 | 2026-06-09 | 초안 — 12개 테이블, 7개 도메인 정의 | 안재현 |
| v0.2 | 2026-06-09 | 상세 명세 형식 전환 (Dev Note·제약/인덱스 의도·개발 가이드 추가) | 안재현 |
| v0.3 | 2026-06-09 | AI팀 인터페이스 정합 — event_type/severity 통일, ANOMALY·SENSOR_ERROR 추가, tb_escalation.summary 추가, AI 산출값(risk_probability/anomaly_score/trend_score) 컬럼 반영 | 안재현 |
| v0.4 | 2026-06-27 | risk_level 3단계로 교체 — NORMAL/WATCH/WARNING/CRITICAL → SAFE/WARNING/DANGER (AI팀 재합의 필요). 에스컬레이션 트리거 기준 = DANGER | 안재현 |
| v0.5 | 2026-06-27 | PR 리뷰 반영 — tb_device.device_uid non-blank CHECK 추가, tb_escalation.risk_assessment_id INDEX → UNIQUE(평가당 에스컬레이션 1건 보장) | 안재현 |
| v0.6 | 2026-06-27 | tb_pose_clip 추가 — 사고 순간 스켈레톤 리플레이(CSI-to-Pose 복원 산출물, sensing_event 1:1) | 안재현 |
| v0.7 | 2026-06-28 | tb_escalation.resolution_memo 추가 — 보호자 확인·해제 메모(summary는 AI 사건요약 전용으로 의미 분리) | 안재현 |

---

## 1. 설계 원칙

| 원칙 | 적용 방식 |
|---|---|
| **정규화 (3NF)** | 코드성·로그성 데이터 분리, 이행적 종속 제거 |
| **도메인 분리** | 7개 도메인으로 분할, 도메인 간 단방향 FK만 사용 (느슨한 결합) |
| **하드웨어 독립성** | raw CSI 미저장. 신호처리 결과(이벤트)만 적재 → 하드웨어·수집방식 변경이 DB 무영향 |
| **로그 불변성** | 이벤트·에스컬레이션·알림은 append-only |
| **스키마 유연성** | 모델 입출력 등 가변 구조는 JSONB |
| **확장성** | model_version 명시, 시계열 집계 분리, 다가구·MSA 전환 대비 |

> **명명 규칙**: 테이블 `tb_` 접두사, 모든 컬럼·필드 **snake_case** (AI 서버·API 전 구간 통일). PK는 `{단수명}_id`. 시각 컬럼은 `TIMESTAMPTZ`(UTC 저장, 표시 시 KST 변환). `updated_at`은 트리거로 자동 갱신.

---

## 2. 도메인 구조

```
[User]          tb_user, tb_care_target, tb_care_relationship
[Device]        tb_device                                      ← WiFi 센싱 하드웨어
[Sensing]       tb_sensing_event, tb_pose_clip                 ← 감지 이벤트 (raw CSI 미저장) + 스켈레톤 리플레이
[Risk]          tb_risk_assessment                             ← 다층 위험도 평가
[Escalation]    tb_escalation, tb_escalation_step              ← 응급 대응 로그
[Notification]  tb_notification                                ← 알림 발송 로그
[Report·Pattern] tb_daily_report, tb_activity_aggregate, tb_personal_baseline
```

---

## 3. 상세 테이블 명세

### 3.1 `tb_user` (앱 사용자 / 보호자·사회복지사 Root)

> 앱 로그인 주체. 보호자·사회복지사·관리자를 통합 관리한다.
> 노인 본인은 앱을 사용하지 않으므로 별도 테이블(`tb_care_target`)로 분리한다.
> `is_active`는 계정 비활성화 Kill Switch 역할.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| user_id | BIGINT (IDENTITY, PK) | N | 사용자 PK |
| email | VARCHAR(255) | N | 로그인 이메일. **UNIQUE**. 애플리케이션에서 소문자 정규화 |
| password_hash | VARCHAR(255) | N | 해시(bcrypt/argon2). **평문 저장 금지** |
| name | VARCHAR(100) | N | 이름 |
| phone | VARCHAR(20) | Y | 연락처 (SMS 알림용) |
| role | VARCHAR(20) | N | `GUARDIAN` / `SOCIAL_WORKER` / `ADMIN` |
| is_active | BOOLEAN | N | 기본 TRUE. FALSE면 로그인/기능 차단 |
| last_login_at | TIMESTAMPTZ | Y | 마지막 로그인 시각 |
| created_at | TIMESTAMPTZ | N | 생성 시각 |
| updated_at | TIMESTAMPTZ | N | 업데이트 시각 (트리거 자동 갱신) |
| deleted_at | TIMESTAMPTZ | Y | soft delete. NULL=유효 |

**제약/인덱스 의도**
- `UNIQUE(email)`: 이메일 중복 방지
- `INDEX(role, is_active)`: 활성 관리자/역할 조회 최적화

**개발 가이드**
- 로그인 시 `email`은 소문자 정규화하여 조회
- soft delete된 계정(`deleted_at IS NOT NULL`)은 모든 조회에서 제외 (JPA `@Where` 또는 Hibernate Filter)
- `role`은 JPA `@Enumerated(EnumType.STRING)`으로 매핑

---

### 3.2 `tb_care_target` (피보호 노인)

> 모니터링 대상 노인. 계정(로그인) 없이 보호자가 등록·관리한다.
> 모든 센싱·이벤트·리포트 데이터의 기준 축(Root)이 되는 핵심 엔티티.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| care_target_id | BIGINT (IDENTITY, PK) | N | 노인 PK. 하위 데이터의 Root Key |
| name | VARCHAR(100) | N | 이름 |
| birth_date | DATE | Y | 생년월일. 연령 기반 위험도 보정에 사용 |
| gender | VARCHAR(10) | Y | `MALE` / `FEMALE` / `OTHER` |
| address | VARCHAR(255) | Y | 주소. 119 신고 시 사용 |
| emergency_memo | TEXT | Y | 기저질환·복약 등 응급 참고 정보 |
| created_at | TIMESTAMPTZ | N | 생성 시각 |
| updated_at | TIMESTAMPTZ | N | 업데이트 시각 (트리거 자동 갱신) |
| deleted_at | TIMESTAMPTZ | Y | soft delete. NULL=유효 |

**제약/인덱스 의도**
- `INDEX(deleted_at)`: 유효 노인 목록 조회 최적화

**개발 가이드**
- 모든 센싱·이벤트·리포트 요청은 `care_target_id`를 확정한 후 처리 (키오스크 없이 device → care_target 역추적)
- `address`·`emergency_memo`는 민감정보 → 조회 시 권한 체크 필수, 향후 감사 로그 대상

---

### 3.3 `tb_care_relationship` (노인 ↔ 보호자 매핑)

> 노인과 보호자의 N:N 관계를 관리하는 매핑 테이블.
> 이 테이블 하나로 1:1(보호자 1명), 1:N(가족 다수), N:1(사회복지사가 노인 다수)을 모두 수용.
> 알림 발송 우선순위도 여기서 관리.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| relationship_id | BIGINT (IDENTITY, PK) | N | 매핑 PK |
| user_id | BIGINT (FK) | N | `tb_user.user_id` 참조 (보호자). 삭제 시 CASCADE |
| care_target_id | BIGINT (FK) | N | `tb_care_target.care_target_id` 참조. 삭제 시 CASCADE |
| relationship_type | VARCHAR(20) | N | `FAMILY` / `SOCIAL_WORKER` / `CAREGIVER` |
| is_primary | BOOLEAN | N | 기본 FALSE. 주 보호자 여부 |
| notify_priority | SMALLINT | N | 기본 1. 알림 순서 (1=최우선) |
| created_at | TIMESTAMPTZ | N | 매핑 생성 시각 |

**제약/인덱스 의도**
- `UNIQUE(user_id, care_target_id)`: 동일 보호자-노인 중복 연결 방지
- `INDEX(care_target_id, notify_priority)`: 알림 우선순위 순 보호자 조회 최적화
- `INDEX(user_id)`: 보호자가 담당하는 노인 목록 조회 (사회복지사 1:N)

**개발 가이드**
- 에스컬레이션 알림은 `notify_priority` 오름차순으로 발송
- `is_primary=TRUE`는 노인 1명당 최대 1명 권장 (애플리케이션 레벨 검증)
- 추가 보호자 연결은 초대코드 방식 — 코드는 Redis(`invite_code:{code}` key, TTL 24h)에 단명 저장. 별도 SQL 테이블 없음

---

### 3.4 `tb_device` (ESP32 센싱 노드)

> WiFi CSI를 수집하는 물리 하드웨어. 노인 가구에 설치된 노드를 등록·관리한다.
> **raw CSI는 저장하지 않으며**, 노드의 식별·위치·상태만 관리한다.
> `status`는 노드 장애 시 모니터링 제외용 스위치.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| device_id | BIGINT (IDENTITY, PK) | N | 디바이스 PK |
| care_target_id | BIGINT (FK) | N | `tb_care_target` 참조. 설치된 노인 가구 |
| device_uid | VARCHAR(64) | N | 하드웨어 식별자(MAC 등). **UNIQUE** |
| room | VARCHAR(50) | Y | 설치 공간 (거실/침실/화장실) |
| position_label | VARCHAR(100) | Y | 배치 위치 메모 |
| node_role | VARCHAR(20) | Y | `SENDER` / `RECEIVER` (3노드 구성 역할) |
| status | VARCHAR(20) | N | 기본 `ACTIVE`. `ACTIVE`/`INACTIVE`/`ERROR` |
| firmware_version | VARCHAR(30) | Y | 펌웨어 버전. 수집 일관성 추적 |
| last_seen_at | TIMESTAMPTZ | Y | 마지막 신호 수신 시각. 헬스체크용 |
| registered_at | TIMESTAMPTZ | N | 등록 시각 |
| created_at | TIMESTAMPTZ | N | 생성 시각 |
| updated_at | TIMESTAMPTZ | N | 업데이트 시각 (트리거 자동 갱신) |

**제약/인덱스 의도**
- `UNIQUE(device_uid)`: 물리 노드 중복 등록 방지
- `CHECK(length(trim(device_uid)) > 0)`: 공백·whitespace 식별자 차단 (heartbeat 조회 키 보호)
- `INDEX(care_target_id, status)`: 가구별 활성 노드 조회

**개발 가이드**
- **WiFi 센싱 ↔ 앱 연결 지점**: ESP32 → raw CSI → 신호처리 서버(FastAPI) → 판정 결과만 `tb_sensing_event`에 적재. DB는 raw 신호와 분리되어 `device_id`로 "어느 노드가 감지했는지"만 연결
- `last_seen_at`이 일정 시간(예: 5분) 이상 갱신 안 되면 status를 `ERROR`로 전환하는 배치/헬스체크 필요
- 하드웨어 변경(C6→타 칩)·수집방식 변경(CSV/소켓)이 이 테이블 외 다른 테이블에 영향 주지 않도록 격리

---

### 3.5 `tb_sensing_event` (감지 이벤트)

> 신호처리·1차 모델(LightGBM→1D-CNN/GRU)이 판정한 결과를 저장하는 append-only 테이블.
> raw CSI가 아닌 "판정된 이벤트"만 기록한다.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| sensing_event_id | BIGINT (IDENTITY, PK) | N | 이벤트 PK |
| care_target_id | BIGINT (FK) | N | `tb_care_target` 참조 |
| device_id | BIGINT (FK) | Y | `tb_device` 참조. 주 감지 노드 (선택) |
| event_type | VARCHAR(30) | N | `FALL`/`INACTIVITY`/`RESPIRATION_ABNORMAL`/`ANOMALY`/`SENSOR_ERROR`/`NORMAL` |
| risk_probability | NUMERIC(4,3) | Y | 모델 즉각 위험 확률 0.000~1.000 (AI 서버 산출) |
| anomaly_score | NUMERIC(4,3) | Y | World Model 개인화 이상 점수 (AI 서버 산출) |
| trend_score | NUMERIC(4,3) | Y | CUSUM 누적 변화 점수 (AI 서버 산출) |
| sensor_status | VARCHAR(20) | Y | `OK`/`ERROR`. 센서 오류 여부 |
| model_version | VARCHAR(30) | N | 판정 모델 버전. 재현성·확장성 |
| features | JSONB | Y | 신호처리 특징값. 디버깅·재학습용 (가변 구조) |
| detected_at | TIMESTAMPTZ | N | 실제 감지 시각 |
| created_at | TIMESTAMPTZ | N | 적재 시각 |

**제약/인덱스 의도**
- `INDEX(care_target_id, detected_at DESC)`: 노인별 최신 이벤트 조회
- `CHECK(event_type IN ('FALL','INACTIVITY','RESPIRATION_ABNORMAL','ANOMALY','SENSOR_ERROR','NORMAL'))`: AI팀 정의와 일치
- `CHECK(risk_probability BETWEEN 0 AND 1)`: 확률 범위 보장
- 향후 데이터 급증 시 `detected_at` 기준 월별 파티셔닝 검토

**개발 가이드**
- append-only. UPDATE 금지 (수정 필요 시 새 이벤트 생성)
- `event_type`·`risk_probability`·`anomaly_score`·`trend_score`는 AI 서버가 산출한 값을 그대로 저장. Backend는 재계산하지 않음
- `features`는 모델 입력이 바뀌어도 스키마 변경 없이 수용 → 모델 고도화 시 마이그레이션 불필요
- `event_type=NORMAL`도 기록하여 정상 패턴 학습 데이터로 활용 가능

---

### 3.6 `tb_risk_assessment` (다층 위험도 평가)

> 3개 모델(즉각/개인화/점진적)의 결과를 종합한 위험도. 이벤트와 1:1.
> `score_breakdown`에 모델별 기여도를 남겨 판단 근거를 추적한다.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| risk_assessment_id | BIGINT (IDENTITY, PK) | N | 평가 PK |
| sensing_event_id | BIGINT (FK) | N | `tb_sensing_event` 참조. **UNIQUE** (1:1) |
| risk_score | SMALLINT | N | 최종 위험도 0~100 (AI 서버 산출) |
| risk_level | VARCHAR(20) | N | `SAFE`/`WARNING`/`DANGER` (AI팀과 3단계로 재합의. 에스컬레이션 트리거 = DANGER) |
| score_breakdown | JSONB | Y | 모델별 기여도 `{lightgbm, lstm_ae, cusum}` |
| model_version | VARCHAR(30) | N | 평가 모델 버전 |
| assessed_at | TIMESTAMPTZ | N | 평가 시각 |
| created_at | TIMESTAMPTZ | N | 적재 시각 |

**제약/인덱스 의도**
- `UNIQUE(sensing_event_id)`: 이벤트당 평가 1건 보장 + 조인 키
- `CHECK(risk_score BETWEEN 0 AND 100)`: 점수 범위 보장
- `CHECK(risk_level IN ('SAFE','WARNING','DANGER'))`: AI팀과 3단계로 재합의

**개발 가이드**
- `risk_score`·`risk_level`은 **AI 서버가 규칙 기반으로 산출한 값을 그대로 저장**. Backend는 위험도를 재계산하지 않음
- `care_target_id`를 중복 저장하지 않고 `tb_sensing_event` 조인으로 접근 (3NF, 이행적 종속 제거). 조회 빈번 시 조인 인덱스 또는 뷰로 대응

---

### 3.7 `tb_escalation` (에스컬레이션 인스턴스)

> 위험도 임계 초과 시 시작되는 하나의 응급 대응 흐름(트랜잭션 단위).
> 단계별 상세는 `tb_escalation_step`에 분리 기록.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| escalation_id | BIGINT (IDENTITY, PK) | N | 에스컬레이션 PK. **Backend가 발급** → AI Agent에 전달 |
| risk_assessment_id | BIGINT (FK) | N | `tb_risk_assessment` 참조. 트리거된 평가 |
| status | VARCHAR(20) | N | 기본 `IN_PROGRESS`. `IN_PROGRESS`/`RESOLVED`/`CANCELLED` |
| resolution_type | VARCHAR(30) | Y | `FALSE_ALARM`/`SELF_RESOLVED`/`GUARDIAN_HANDLED`/`EMERGENCY_DISPATCHED` |
| resolution_memo | TEXT | Y | 보호자가 E3(확인·해제) 시 입력하는 메모. `summary`(AI 사건요약)와 의미 분리 |
| summary | TEXT | Y | AI Agent가 생성한 사건 요약(report_summary) |
| started_at | TIMESTAMPTZ | N | 시작 시각 |
| resolved_at | TIMESTAMPTZ | Y | 종료 시각 |
| created_at | TIMESTAMPTZ | N | 생성 시각 |
| updated_at | TIMESTAMPTZ | N | 업데이트 시각 (트리거 자동 갱신) |

**제약/인덱스 의도**
- `INDEX(status)`: 진행 중 에스컬레이션 모니터링
- `UNIQUE(risk_assessment_id)`: 평가당 에스컬레이션 1건 보장 (멱등 응답의 단건 조회 정합, 인덱스 겸함)

**개발 가이드**
- **상태의 진실(Source of Truth)은 이 테이블이다.** AI Agent의 내부 상태(16종)는 Agent 메모리에서 관리되지만, DB에는 핵심 상태(IN_PROGRESS/RESOLVED/CANCELLED)로 압축 저장. Agent 프로세스가 죽어도 DB 상태로 복구 가능
- escalation_id는 Backend가 발급하고 AI Agent가 받아쓴다 (ID 충돌 방지)
- 상태 전이: `IN_PROGRESS` → (`RESOLVED` | `CANCELLED`). 종료 시 `resolved_at`·`resolution_type` 동시 기록
- 단계 진행 자체는 `tb_escalation_step`에서 관리, 이 테이블은 흐름의 전체 상태만 보유
- `summary`는 AI Agent가 생성한 사건 요약을 저장 (앱 이력 화면·발표용 로그에 사용)

---

### 3.8 `tb_escalation_step` (단계별 진행 로그)

> 음성확인 → 보호자알림 → 119신고 각 단계를 독립 row로 기록하는 append-only 로그.
> 로그를 본 엔티티에서 분리해 로그 증가가 핵심 테이블 성능에 영향을 주지 않도록 한다.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| step_id | BIGINT (IDENTITY, PK) | N | 단계 로그 PK |
| escalation_id | BIGINT (FK) | N | `tb_escalation` 참조. 삭제 시 CASCADE |
| step_type | VARCHAR(30) | N | `VOICE_CHECK`/`GUARDIAN_NOTIFY`/`EMERGENCY_CALL` |
| step_order | SMALLINT | N | 단계 순서 (1,2,3) |
| status | VARCHAR(20) | N | `PENDING`/`EXECUTED`/`RESPONDED`/`NO_RESPONSE`/`SKIPPED` |
| executed_at | TIMESTAMPTZ | Y | 실행 시각 |
| responded_at | TIMESTAMPTZ | Y | 응답 시각 |
| response_detail | JSONB | Y | 음성 응답·STT 결과 등 (가변 구조) |
| created_at | TIMESTAMPTZ | N | 생성 시각 |

**제약/인덱스 의도**
- `INDEX(escalation_id, step_order)`: 단계 순서대로 조회
- `UNIQUE(escalation_id, step_type)`: 동일 에스컬레이션 내 단계 중복 방지

**개발 가이드**
- append-only. 단계 상태 변화는 status 컬럼 UPDATE로 허용하되, 단계 자체를 삭제하지 않음
- 각 단계 60초 무응답 시 다음 step 생성 → LangGraph 에이전트가 step 단위로 상태 기록

---

### 3.9 `tb_notification` (알림 발송 로그)

> 보호자 알림 발송 이력. 에스컬레이션 알림과 일반 알림(리포트 도착 등)을 모두 수용하는 append-only 로그.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| notification_id | BIGINT (IDENTITY, PK) | N | 알림 PK |
| escalation_step_id | BIGINT (FK) | Y | `tb_escalation_step` 참조. 일반 알림은 NULL |
| recipient_user_id | BIGINT (FK) | N | `tb_user` 참조. 수신 보호자 |
| care_target_id | BIGINT (FK) | N | `tb_care_target` 참조. 관련 노인 |
| channel | VARCHAR(20) | N | `FCM_PUSH`/`SMS`/`EMAIL` |
| category | VARCHAR(30) | N | `EMERGENCY`/`DAILY_REPORT`/`SYSTEM` |
| title | VARCHAR(200) | N | 제목 |
| body | TEXT | Y | 본문 |
| status | VARCHAR(20) | N | 기본 `QUEUED`. `QUEUED`/`SENT`/`DELIVERED`/`FAILED`/`READ` |
| sent_at | TIMESTAMPTZ | Y | 발송 시각 |
| read_at | TIMESTAMPTZ | Y | 확인 시각 |
| created_at | TIMESTAMPTZ | N | 생성 시각 |

**제약/인덱스 의도**
- `INDEX(recipient_user_id, created_at DESC)`: 보호자별 알림 목록
- `INDEX(care_target_id, category)`: 노인별·유형별 필터
- `INDEX(status)`: 발송 실패(`FAILED`) 재시도 배치 조회

**개발 가이드**
- `escalation_step_id`가 NULL이면 일반 알림(리포트·시스템), NOT NULL이면 응급 알림
- 발송 실패 시 status=`FAILED` 후 재시도 큐 처리. `read_at`은 앱에서 확인 시 갱신

---

### 3.9a `tb_fcm_token` (FCM 디바이스 토큰) ← 2026-06-28 신규

> 보호자 앱의 FCM 디바이스 토큰 저장소. 앱 실행 시(N3) 등록·갱신. I2에서 발송 대상 조회에 사용.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| fcm_token_id | BIGINT (IDENTITY, PK) | N | PK |
| user_id | BIGINT (FK) | N | `tb_user` 참조. 현재 로그인 보호자 |
| token | VARCHAR(512) | N | Firebase 디바이스 토큰. UNIQUE 자연키 |
| platform | VARCHAR(10) | N | `IOS` / `ANDROID` |
| created_at | TIMESTAMPTZ | N | 최초 등록 시각 |
| updated_at | TIMESTAMPTZ | N | 마지막 갱신 시각 (트리거) |

**제약/인덱스 의도**
- `UNIQUE(token)`: 토큰 = 디바이스당 1개, 자연키로 UPSERT 구현
- `INDEX(user_id)`: I2 발송 시 유저별 토큰 전체 조회
- `CHECK(platform IN ('IOS','ANDROID'))`: enum 허용값 강제
- `CHECK(length(trim(token)) > 0)`: 공백 토큰 차단

**개발 가이드**
- N3에서 토큰 기준 UPSERT: 이미 존재하면 `user_id`·`platform`·`updated_at` 갱신 (다른 보호자 로그인 시 재할당)
- 같은 유저가 여러 디바이스 사용 → 여러 row. I2에서 `findByUserId`로 전체 발송
- FCM 토큰 만료(Firebase 갱신) 시 앱이 새 토큰으로 N3 재호출 → 자동 갱신
- Flyway **V5** 마이그레이션

---

### 3.10 `tb_daily_report` (일일 자연어 리포트)

> 하루 누적 센싱 데이터를 LLM이 자연어로 요약한 리포트. 노인·일자별 1건.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| daily_report_id | BIGINT (IDENTITY, PK) | N | 리포트 PK |
| care_target_id | BIGINT (FK) | N | `tb_care_target` 참조 |
| report_date | DATE | N | 리포트 일자 |
| summary_text | TEXT | Y | LLM 생성 자연어 요약 |
| metrics | JSONB | Y | 활동량·수면·호흡·화장실 횟수 등 (가변 지표) |
| generated_at | TIMESTAMPTZ | N | 생성 시각 |
| created_at | TIMESTAMPTZ | N | 적재 시각 |

**제약/인덱스 의도**
- `UNIQUE(care_target_id, report_date)`: 하루 1건 보장 (재생성 시 UPSERT)
- `INDEX(care_target_id, report_date DESC)`: 최신 리포트 목록

**개발 가이드**
- 매일 정해진 시각 배치로 생성. 재생성 시 `ON CONFLICT (care_target_id, report_date) DO UPDATE`
- `metrics` 구조 변경이 잦으므로 JSONB 유지, 통계 조회 빈번 시 생성 컬럼(Generated Column) 추출 검토

---

### 3.11 `tb_activity_aggregate` (시간대별 활동 집계)

> raw CSI 대신 시간 버킷 단위로 집계된 지표. World Model(LSTM AE)·CUSUM의 학습/추론 입력.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| aggregate_id | BIGINT (IDENTITY, PK) | N | 집계 PK |
| care_target_id | BIGINT (FK) | N | `tb_care_target` 참조 |
| bucket_start | TIMESTAMPTZ | N | 집계 구간 시작 |
| bucket_type | VARCHAR(10) | N | `HOURLY`/`DAILY` |
| movement_level | NUMERIC(6,3) | Y | 집계된 움직임 강도 |
| inactivity_seconds | INTEGER | Y | 무활동 지속 시간(초) |
| avg_breathing_rate | NUMERIC(5,2) | Y | 평균 호흡수(BPM) |
| event_count | INTEGER | N | 기본 0. 구간 내 이벤트 수 |
| created_at | TIMESTAMPTZ | N | 적재 시각 |

**제약/인덱스 의도**
- `UNIQUE(care_target_id, bucket_start, bucket_type)`: 중복 집계 방지
- `INDEX(care_target_id, bucket_start)`: World Model 시계열 입력 조회

**개발 가이드**
- 집계 배치가 멱등(idempotent)하도록 UPSERT 사용
- World Model 입력은 이 테이블에서 시계열로 읽어 구성 → raw 데이터 의존 제거

---

### 3.12 `tb_personal_baseline` (개인 정상 패턴)

> World Model이 학습한 개인별 정상 패턴. 버전별 누적으로 모델 갱신·롤백 지원.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| baseline_id | BIGINT (IDENTITY, PK) | N | 베이스라인 PK |
| care_target_id | BIGINT (FK) | N | `tb_care_target` 참조 |
| model_version | VARCHAR(30) | N | 학습 모델 버전 |
| baseline_data | JSONB | N | 학습된 정상 패턴 파라미터 |
| valid_from | TIMESTAMPTZ | N | 적용 시작 |
| valid_to | TIMESTAMPTZ | Y | 적용 종료. NULL=현재 유효 |
| created_at | TIMESTAMPTZ | N | 적재 시각 |

**제약/인덱스 의도**
- `INDEX(care_target_id, valid_to)`: 현재 유효 베이스라인(`valid_to IS NULL`) 조회
- 노인 1명당 현재 유효 베이스라인은 1건 유지

**개발 가이드**
- 모델 재학습 시 기존 row의 `valid_to`를 채우고 새 row를 `valid_to=NULL`로 insert (이력 보존·롤백 가능)
- `baseline_data`는 모델 구조 변경에 대응하기 위해 JSONB 유지

---

### 3.13 `tb_pose_clip` (사고 순간 스켈레톤 리플레이)

> AI 서버(CSI-to-Pose student 모델)가 복원한 13-point skeleton proxy 시퀀스.
> raw CSI가 아닌 **모델 추론 산출물**을 저장하므로 "raw CSI 미저장" 원칙과 충돌하지 않는다.
> 비정상 이벤트(FALL·ANOMALY·RESPIRATION_ABNORMAL·INACTIVITY 등 NORMAL 외) 감지 시 해당 구간을 복원해 보호자 앱에서 리플레이로 재생할 수 있다.
> **카메라 영상이 아닌 추상 스켈레톤 좌표**이므로 침실·화장실에서도 개인정보 침해 없음.

| 컬럼 | 타입 | NULL | 설명 / Dev Note |
| --- | --- | --- | --- |
| pose_clip_id | BIGINT (IDENTITY, PK) | N | 클립 PK |
| sensing_event_id | BIGINT (FK) | N | `tb_sensing_event` 참조. **UNIQUE** (이벤트당 클립 1건) |
| model_version | VARCHAR(30) | N | 복원(CSI-to-Pose student) 모델 버전. 재현성 추적 |
| joint_schema | VARCHAR(30) | N | 관절 셋 식별자 (예: `13-point`, `5-point-proxy`). 앱 렌더링 기준 — 스키마 변경 시 앱과 인터페이스 재합의 필요 |
| fps | SMALLINT | N | 초당 프레임 수. CHECK(fps > 0) |
| frame_count | INTEGER | N | 총 프레임 수. CHECK(frame_count >= 0) |
| duration_ms | INTEGER | Y | 클립 길이(ms). frame_count / fps × 1000 으로 파생 가능하나 명시 저장 |
| window_start_at | TIMESTAMPTZ | N | 복원 구간 시작 시각 |
| window_end_at | TIMESTAMPTZ | N | 복원 구간 종료 시각 |
| frames | JSONB | N | 스켈레톤 프레임 시퀀스 (아래 구조 참조) |
| event_timeline | JSONB | Y | falling/inactive 등 구간 마커. AI event head 출력. 리플레이 하이라이트 표시용 |
| created_at | TIMESTAMPTZ | N | 적재 시각 |

**`frames` JSONB 구조** (AI팀과 재합의 필요 — 아래는 제안 구조)

```json
{
  "joints": ["head", "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
             "left_wrist", "right_wrist", "left_hip", "right_hip",
             "left_knee", "right_knee", "left_ankle", "right_ankle"],
  "frames": [
    { "t_ms": 0,   "pose": [[x,y,z], [x,y,z], "..."], "event": "moving" },
    { "t_ms": 33,  "pose": [["..."]], "event": "falling" },
    { "t_ms": 5000,"pose": [["..."]], "event": "inactive" }
  ]
}
```

> 좌표계(x/y/z 정규화 방식), event 라벨 셋, visibility 포함 여부(3D vs 4D)는 AI팀과 확정 후 이 명세에 반영할 것.

**제약/인덱스 의도**
- `UNIQUE(sensing_event_id)`: 이벤트당 클립 1건 보장 + 조인 키 + I5 멱등 기준
- `CHECK(fps > 0)`: 유효하지 않은 프레임 수 차단
- `CHECK(frame_count >= 0)`: 빈 클립(0프레임) 허용 (복원 실패 케이스 기록 가능)
- `care_target_id`는 `tb_sensing_event` 조인으로 접근 (`tb_risk_assessment`와 동일한 3NF 원칙. 중복 저장 안 함)

**개발 가이드**
- append-only. 클립 내용 수정 필요 시 새 이벤트 기준 새 row 생성
- NORMAL·SENSOR_ERROR 이벤트에는 클립이 생성되지 않을 수 있음 → 조회 시 404 `POSE_CLIP_NOT_FOUND`가 정상 응답
- `frames` JSONB는 인라인 저장(MVP). 클립이 대형화되면 오브젝트 스토리지(S3 등) URL 참조 방식으로 전환 검토
- AI 서버 흐름: I1으로 `sensing_event_id` 확보 → skeleton proxy 복원 → I5로 클립 첨부

---

## 4. 공통 규약

| 항목 | 규약 |
|---|---|
| **시각** | 전부 `TIMESTAMPTZ`, UTC 저장. 표시 시 KST 변환 |
| **updated_at** | `BEFORE UPDATE` 트리거로 자동 갱신 (애플리케이션 의존 안 함) |
| **soft delete** | `tb_user`·`tb_care_target`만 적용. 로그성 테이블은 물리 보존 |
| **ENUM류** | VARCHAR + CHECK 제약. JPA `@Enumerated(EnumType.STRING)` 매핑. 동적 확장 필요 시 코드 테이블로 승격 |
| **FK 삭제 정책** | 매핑·로그 테이블은 CASCADE, 핵심 참조는 RESTRICT(실수 삭제 방지) |

---

## 5. 향후 확장 포인트 (v0.3 이후)

- `tb_audit_log`: 보호자의 민감정보(주소·의료메모) 조회 이력 (개인정보 감사)
- `tb_device_health_log`: 노드별 연결 상태 시계열 (장애 추적)
- 시계열 파티셔닝: `tb_sensing_event`·`tb_activity_aggregate` 월별 파티션
- `tb_notification_template`: 알림 문구 다국어·유형별 관리
- **pose clip 대형화 대응**: `tb_pose_clip.frames`를 인라인 JSONB 대신 오브젝트 스토리지(S3·GCS 등) URL 참조로 전환. MVP에서는 JSONB 인라인으로 충분하나, 클립 길이가 길어지거나 저장 대상이 늘어나면 `frames_url` 컬럼을 추가해 외부 저장소를 가리키도록 마이그레이션
