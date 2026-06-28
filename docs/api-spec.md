# API 명세서 — 노인 Safety Agent

| 항목 | 내용 |
|---|---|
| **프로젝트** | 카메라·웨어러블 없는 노인 Safety Agent |
| **백엔드** | Spring Boot (REST API) |
| **인증** | JWT (앱) / API Key (내부 서버 간) |
| **문서 버전** | v1.3 |
| **작성일** | 2026-06-09 |
| **최종 수정일** | 2026-06-28 |

### 수정 이력

| 버전 | 날짜 | 내용 | 작성자 |
|---|---|---|---|
| v0.1 | 2026-06-09 | 초안 — 외부 API 9개 도메인, 내부 API 4종 정의 | 안재현 |
| v0.2 | 2026-06-09 | AI팀 인터페이스 정합 — 전 필드 snake_case 전환, event_type/severity 통일, 내부 API 페이로드 합의안 반영 | 안재현 |
| v0.3 | 2026-06-24 | C1·C2 구현 노트 반영 — 관계 타입 파생 규칙, C2 미구현 도메인 필드 null/0 명시 (계약 불변) | 안재현 |
| v0.4 | 2026-06-26 | R1~R4 상세 계약 추가 — 초대코드 기반 보호자 연결(발급/수락), 목록/수정/해제, 에러코드 추가 | 안재현 |
| v0.5 | 2026-06-26 | 초대 링크 UX 확장 — R1-a 응답에 `invite_url`(HTTPS 딥링크) 추가, R1-c 미리보기 조회 API 추가 | 안재현 |
| v0.6 | 2026-06-27 | I1·I4 구현 반영 — risk_level 3단계 교체(SAFE/WARNING/DANGER), I4 응답 204→200(envelope 일관성), C2·S1·S2 예시 값 3단계로 갱신. DEVICE_NOT_FOUND 에러코드 추가 | 안재현 |
| v0.7 | 2026-06-27 | PR 리뷰 반영 — I1 score 필드(risk_probability/anomaly_score/trend_score) 정밀도 소수 3자리 명시 | 안재현 |
| v0.8 | 2026-06-27 | S3(복원 리플레이 조회)·I5(클립 적재) 추가 — 사고 순간 스켈레톤 리플레이 기능 반영. S2 응답에 has_replay 추가. 에러코드 SENSING_EVENT_NOT_FOUND·POSE_CLIP_NOT_FOUND 추가 | 안재현 |
| v0.9 | 2026-06-27 | D1~D4 구현 반영 — 디바이스(Device) 도메인 상세 절 신설(3-4). device_uid=MAC 주소 명기. DEVICE_ALREADY_EXISTS(409) 에러코드 추가. C2 device_count 실제 값 연동 완료 노트 추가 | 안재현 |
| v1.0 | 2026-06-27 | WiFi 프로비저닝 흐름 추가 — 3-4절 앞에 BLE 온보딩 단계 기술, 담당 분리(AI팀 펌웨어·앱 RN SDK·Spring D1) 명시 | 안재현 |
| v1.1 | 2026-06-27 | S1·S2 구현 반영 — S1 구현 노트(today_metrics·active_escalation null 사유) 추가, S2 쿼리 파라미터 snake_case(event_type) 확정 | 안재현 |
| v1.2 | 2026-06-28 | N3 구현 반영 — tb_fcm_token 신규 테이블 추가(토큰 저장), N3 응답 204→200(envelope 일관성), platform 허용값(IOS/ANDROID) 명시, 멱등 갱신 동작 설명 추가 | 안재현 |
| v1.3 | 2026-06-28 | E1·E2·E3 구현 반영 — E1 상세 절 신설, E2·E3 응답에 resolution_memo 추가, E3 resolution_type 허용값·에러코드(409·400) 명시 | 안재현 |

---

## 1. 공통 규약

### 1-1. 기본 정보

| 항목 | 값 |
|---|---|
| Base URL (운영) | `https://api.bloom-safety.app` |
| API 버전 prefix | `/api/v1` (외부) · `/internal/v1` (서버 간) |
| 통신 포맷 | JSON (UTF-8), **모든 필드 snake_case** (AI 서버·DB와 통일) |
| 시각 표기 | ISO 8601, UTC (`2026-06-09T05:30:00Z`) |

### 1-2. 인증

| 구분 | 방식 | 헤더 |
|---|---|---|
| 외부 API (보호자 앱) | JWT Bearer | `Authorization: Bearer {accessToken}` |
| 내부 API (AI 서버 → Spring) | API Key | `X-Internal-Key: {serviceKey}` |

> 외부 API는 모든 요청에서 JWT의 `user_id`를 추출해 **요청 대상 노인에 대한 접근 권한**(`tb_care_relationship` 존재 여부)을 검증한다. 권한 없으면 `403 FORBIDDEN`.

### 1-3. 공통 응답 포맷

모든 응답은 동일한 envelope로 감싼다.

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패 시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "CARE_TARGET_NOT_FOUND",
    "message": "해당 노인을 찾을 수 없습니다."
  }
}
```

### 1-4. 페이지네이션

목록 조회는 쿼리 파라미터로 페이징한다.

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | 0 | 0-based 페이지 번호 |
| `size` | 20 | 페이지당 항목 수 (최대 100) |
| `sort` | 도메인별 | 정렬 기준 (예: `createdAt,desc`) |

페이징 응답 `data` 구조:

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7
}
```

### 1-5. HTTP 상태 코드

| 코드 | 의미 | 사용 |
|---|---|---|
| 200 | OK | 조회·수정 성공 |
| 201 | Created | 생성 성공 |
| 204 | No Content | 삭제 성공 |
| 400 | Bad Request | 유효성 검증 실패 |
| 401 | Unauthorized | 인증 실패(토큰 없음·만료) |
| 403 | Forbidden | 권한 없음(접근 불가 노인) |
| 404 | Not Found | 리소스 없음 |
| 409 | Conflict | 중복(이메일·연결) |
| 500 | Internal Error | 서버 오류 |

### 1-6. 주요 에러 코드

| 코드 | HTTP | 의미 |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | 로그인 실패 |
| `TOKEN_EXPIRED` | 401 | 액세스 토큰 만료 |
| `ACCESS_DENIED` | 403 | 해당 노인 접근 권한 없음 |
| `CARE_TARGET_NOT_FOUND` | 404 | 노인 없음 |
| `DEVICE_NOT_FOUND` | 404 | 디바이스 없음 |
| `DEVICE_ALREADY_EXISTS` | 409 | device_uid 중복 등록 |
| `EMAIL_ALREADY_EXISTS` | 409 | 이메일 중복 |
| `RELATIONSHIP_ALREADY_EXISTS` | 409 | 이미 연결된 보호자-노인 |
| `INVALID_INTERNAL_KEY` | 401 | 내부 API 키 오류 |
| `INVALID_INVITE_CODE` | 404 | 유효하지 않거나 만료된 초대 코드 |
| `RELATIONSHIP_ALREADY_EXISTS` | 409 | 이미 연결된 보호자-노인 |
| `RELATIONSHIP_NOT_FOUND` | 404 | 보호자 관계 없음 |
| `CANNOT_DELETE_PRIMARY` | 409 | 주 보호자 연결 해제 불가 |
| `SENSING_EVENT_NOT_FOUND` | 404 | 감지 이벤트 없음 (I5 클립 적재 시) |
| `POSE_CLIP_NOT_FOUND` | 404 | 복원 클립 없음 (S3 조회 시 — NORMAL 이벤트 등 클립 미생성 케이스) |

---

## 2. API 목록 (한눈에)

### 엔드포인트 코드 범례

각 API는 `도메인 약자 + 번호`로 식별한다.

| 코드 | 도메인 | 설명 |
|---|---|---|
| **A** | Auth | 인증 (회원가입·로그인·토큰) |
| **C** | Care Target | 노인(피보호 대상) 관리 |
| **R** | Relationship | 노인-보호자 연결 관리 |
| **D** | Device | ESP32 센싱 노드 관리 |
| **S** | Status / Sensing | 실시간 상태·감지 이벤트 |
| **E** | Escalation | 응급 대응 흐름 |
| **N** | Notification | 알림 |
| **P** | Report (rePort) | 일일 리포트 |
| **I** | Internal | AI 서버 → Spring 내부 통신 |

### 외부 API (보호자 앱)

| # | Method | Path | 설명 | 권한 |
|---|---|---|---|---|
| A1 | POST | `/api/v1/auth/signup` | 보호자 회원가입 | 공개 |
| A2 | POST | `/api/v1/auth/login` | 로그인 | 공개 |
| A3 | POST | `/api/v1/auth/refresh` | 토큰 갱신 | 공개 |
| A4 | POST | `/api/v1/auth/logout` | 로그아웃 | 인증 |
| C1 | POST | `/api/v1/care-targets` | 노인 등록 | 인증 |
| C2 | GET | `/api/v1/care-targets` | 내가 보는 노인 목록 | 인증 |
| C3 | GET | `/api/v1/care-targets/{id}` | 노인 상세 | 관계 |
| C4 | PATCH | `/api/v1/care-targets/{id}` | 노인 정보 수정 | 관계 |
| C5 | DELETE | `/api/v1/care-targets/{id}` | 노인 삭제 | 관계(주 보호자) |
| R1-a | POST | `/api/v1/care-targets/{id}/invite-codes` | 초대코드 발급 (코드 + 공유 링크 반환) | 관계(주 보호자) |
| R1-b | POST | `/api/v1/invite-codes/{code}/accept` | 초대코드 수락 → 자가 연결 | 인증 |
| R1-c | GET | `/api/v1/invite-codes/{code}` | 초대코드 미리보기 (코드 유지, 다이얼로그 정보) | 인증 |
| R2 | GET | `/api/v1/care-targets/{id}/guardians` | 보호자 목록 | 관계 |
| R3 | PATCH | `/api/v1/relationships/{id}` | 우선순위·역할 수정 | 관계(주 보호자) |
| R4 | DELETE | `/api/v1/relationships/{id}` | 연결 해제 | 관계(주 보호자) |
| D1 | POST | `/api/v1/care-targets/{id}/devices` | 노드 등록 | 관계 |
| D2 | GET | `/api/v1/care-targets/{id}/devices` | 노드 목록 | 관계 |
| D3 | PATCH | `/api/v1/devices/{id}` | 노드 정보 수정 | 관계 |
| D4 | DELETE | `/api/v1/devices/{id}` | 노드 삭제 | 관계 |
| S1 | GET | `/api/v1/care-targets/{id}/status` | 실시간 상태 대시보드 | 관계 |
| S2 | GET | `/api/v1/care-targets/{id}/events` | 감지 이벤트 목록 | 관계 |
| S3 | GET | `/api/v1/sensing-events/{sensing_event_id}/pose-clip` | 복원 스켈레톤 리플레이 조회 | 관계 |
| E1 | GET | `/api/v1/care-targets/{id}/escalations` | 에스컬레이션 목록 | 관계 |
| E2 | GET | `/api/v1/escalations/{id}` | 에스컬레이션 상세(단계 로그) | 관계 |
| E3 | POST | `/api/v1/escalations/{id}/resolve` | 보호자 확인·해제 | 관계 |
| N1 | GET | `/api/v1/notifications` | 내 알림 목록 | 인증 |
| N2 | PATCH | `/api/v1/notifications/{id}/read` | 알림 읽음 처리 | 인증 |
| N3 | POST | `/api/v1/me/fcm-token` | FCM 토큰 등록 | 인증 |
| P1 | GET | `/api/v1/care-targets/{id}/reports` | 일일 리포트 목록 | 관계 |
| P2 | GET | `/api/v1/reports/{id}` | 리포트 상세 | 관계 |

### 내부 API (AI 서버 → Spring)

| # | Method | Path | 설명 |
|---|---|---|---|
| I1 | POST | `/internal/v1/sensing-events` | 감지 이벤트 + 위험도 적재 (에스컬레이션 생성 + escalation_id 반환) |
| I2 | POST | `/internal/v1/escalations/{escalation_id}/steps` | 에스컬레이션 단계 진행 기록 (+ 알림 문구) |
| I3 | POST | `/internal/v1/reports` | 일일 리포트 적재 |
| I4 | POST | `/internal/v1/devices/{device_uid}/heartbeat` | 노드 헬스체크 |
| I5 | POST | `/internal/v1/sensing-events/{sensing_event_id}/pose-clip` | 복원 스켈레톤 클립 적재 (sensing_event 1:1, 멱등) |

> **권한 표기**: 공개=인증 불필요 / 인증=로그인 필요 / 관계=해당 노인과 `tb_care_relationship` 존재 / 관계(주 보호자)=`is_primary=true`

---

## 3. 외부 API 상세

### 3-1. 인증 (Auth)

#### A2. 로그인

```
POST /api/v1/auth/login
```

> 이메일·비밀번호로 로그인하고 JWT 토큰 쌍을 발급받는다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| email | string | Y | 이메일 (소문자 정규화) |
| password | string | Y | 비밀번호 |

**Response 200**

```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGc...",
    "refresh_token": "eyJhbGc...",
    "user": {
      "user_id": 12,
      "name": "김보호",
      "role": "GUARDIAN"
    }
  },
  "error": null
}
```

**에러**: `401 INVALID_CREDENTIALS`

---

### 3-2. 노인 (Care Target)

#### C1. 노인 등록

```
POST /api/v1/care-targets
```

> 보호자가 모니터링할 노인을 등록한다. 등록한 보호자는 자동으로 주 보호자(`is_primary=true`)로 `tb_care_relationship`에 연결된다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | Y | 이름 |
| birth_date | date | N | 생년월일 (YYYY-MM-DD) |
| gender | string | N | MALE/FEMALE/OTHER |
| address | string | N | 주소 |
| emergency_memo | string | N | 기저질환·복약 정보 |

**Response 201**

```json
{
  "success": true,
  "data": { "care_target_id": 45 },
  "error": null
}
```

> **구현 노트 (v0.3)**: 등록자의 `role`에 따라 `tb_care_relationship.relationship_type`이 자동 결정됨 — `SOCIAL_WORKER`이면 `SOCIAL_WORKER`, 그 외(`GUARDIAN` 등)는 `FAMILY`. `is_primary=true`, `notify_priority=1`로 고정.

#### C2. 노인 목록

```
GET /api/v1/care-targets?page=0&size=20
```

> JWT 사용자가 접근 가능한 노인 목록. 사회복지사는 담당 노인 다수가 반환된다.

**Response 200** — 각 항목에 최신 위험도 요약 포함

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "care_target_id": 45,
        "name": "박순자",
        "current_risk_level": "SAFE",
        "last_event_at": "2026-06-09T04:12:00Z",
        "device_count": 3,
        "is_primary": true
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1
  },
  "error": null
}
```

> **구현 노트 (v0.3)**: `current_risk_level`·`last_event_at`은 Sensing 도메인, `device_count`는 Device 도메인 구현 전까지 각각 `null`/`null`/`0`으로 반환됨. 계약(필드 존재)은 동일, 값만 미채움.
>
> **정렬**: 현재 서버 고정 `createdAt DESC`. 클라이언트 `sort` 쿼리 파라미터는 무시됨(무효 값으로 인한 500 방지).

---

### 3-3. 보호자 연결 (Relationship)

#### R1-a. 초대코드 발급

```
POST /api/v1/care-targets/{id}/invite-codes
```

> 주 보호자가 다른 사람을 보호자로 초대하는 코드를 발급한다. 코드는 8자리 영숫자로 24시간 유효하며 일회성이다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| relationship_type | string | Y | FAMILY / SOCIAL_WORKER / CAREGIVER |
| notify_priority | int | N | 알림 순서 (기본 1) |

**Response 201**

```json
{
  "success": true,
  "data": {
    "code": "A3B7CD2E",
    "invite_url": "https://app.bloom-safety.app/invite/A3B7CD2E",
    "expires_at": "2026-06-27T03:00:00Z"
  },
  "error": null
}
```

> **딥링크 계약**: `invite_url`은 iOS Universal Link / Android App Link 형식의 HTTPS URL이다. 앱이 설치된 경우 OS가 앱을 직접 실행하며, 미설치 시 웹 fallback 페이지로 연결된다(fallback 호스팅은 인프라 추후 설정). URL 형식: `https://app.bloom-safety.app/invite/{code}`. base URL은 환경변수 `INVITE_LINK_BASE_URL`로 변경 가능하다.

**에러**: `403 ACCESS_DENIED`(주 보호자 아님), `404 CARE_TARGET_NOT_FOUND`

---

#### R1-c. 초대코드 미리보기

```
GET /api/v1/invite-codes/{code}
```

> 초대 링크를 클릭한 사용자에게 "수락하시겠습니까?" 다이얼로그 정보를 제공한다. **코드를 소모하지 않으며**, accept 전 노인 이름·초대자 이름을 확인하는 용도다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "care_target_id": 45,
    "care_target_name": "박순자",
    "inviter_name": "김보호",
    "relationship_type": "FAMILY",
    "expires_at": "2026-06-27T03:00:00Z"
  },
  "error": null
}
```

> **앱 동작 계약**: 앱이 딥링크(`/invite/{code}`)를 수신하면 ① `GET /api/v1/invite-codes/{code}`로 미리보기 조회 → ② "박순자님의 보호자로 등록하시겠습니까?" 다이얼로그 표시 → ③ 사용자가 [수락] 클릭 시 `POST /api/v1/invite-codes/{code}/accept` 호출. accept 시 코드 즉시 소멸.

**에러**: `404 INVALID_INVITE_CODE`(만료·사용됨)

---

#### R1-b. 초대코드 수락

```
POST /api/v1/invite-codes/{code}/accept
```

> 초대 코드를 입력해 해당 노인의 보호자로 자가 연결한다. 코드는 한 번 사용 후 즉시 소멸된다(동시 수락 불가).

**Response 201**

```json
{
  "success": true,
  "data": {
    "relationship_id": 7,
    "care_target_id": 45
  },
  "error": null
}
```

**에러**: `404 INVALID_INVITE_CODE`(만료·사용됨), `409 RELATIONSHIP_ALREADY_EXISTS`

---

#### R2. 보호자 목록

```
GET /api/v1/care-targets/{id}/guardians
```

> 해당 노인의 보호자 전체 목록을 `notify_priority` 오름차순으로 반환한다.

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "relationship_id": 1,
      "user_id": 12,
      "name": "김보호",
      "email": "test@notifi.dev",
      "role": "GUARDIAN",
      "relationship_type": "FAMILY",
      "is_primary": true,
      "notify_priority": 1
    }
  ],
  "error": null
}
```

---

#### R3. 관계 수정

```
PATCH /api/v1/relationships/{id}
```

> 주 보호자가 다른 보호자의 유형·알림 우선순위를 수정한다. `is_primary` 변경은 지원하지 않는다.

**Request Body** (null 필드는 미변경)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| relationship_type | string | N | FAMILY / SOCIAL_WORKER / CAREGIVER |
| notify_priority | int | N | 알림 순서 (≥ 1) |

**Response 200**

```json
{
  "success": true,
  "data": {
    "relationship_id": 7,
    "care_target_id": 45,
    "user_id": 99,
    "relationship_type": "FAMILY",
    "is_primary": false,
    "notify_priority": 2
  },
  "error": null
}
```

**에러**: `404 RELATIONSHIP_NOT_FOUND`, `403 ACCESS_DENIED`(주 보호자 아님)

---

#### R4. 연결 해제

```
DELETE /api/v1/relationships/{id}
```

> 주 보호자가 다른 보호자의 연결을 해제한다. 주 보호자 본인 연결은 해제 불가.

**Response 200** — `data: null`

**에러**: `404 RELATIONSHIP_NOT_FOUND`, `403 ACCESS_DENIED`, `409 CANNOT_DELETE_PRIMARY`

---

### 3-4. 디바이스 (Device)

> ESP32-C6 센싱 노드의 메타데이터를 등록·관리한다.
> `device_uid`는 ESP32의 MAC 주소(예: `AA:BB:CC:DD:EE:FF`)를 사용한다.

#### 디바이스 온보딩 흐름 (D1 호출 전)

보호자가 노드를 처음 설치할 때의 전체 흐름이다. Spring API(D1~D4)는 **WiFi 프로비저닝 완료 후**에 호출된다.

```
① 전원 ON  →  ESP32가 BLE 광고 시작 ("NotiFi-XXXX")
② 앱 "노드 추가"  →  BLE로 주변 장치 스캔·발견
③ 앱에서 집 WiFi 선택 + 비밀번호 입력
    앱 ──(BLE)──▶ ESP32  {ssid, password, ai_server_ip}
④ ESP32: 집 WiFi 접속 → AI 서버 연결 → CSI 스트리밍 시작
⑤ 앱: 방 이름·역할 선택  →  D1 호출  →  Spring 등록 완료
```

| 레이어 | 담당 | 기술 |
|---|---|---|
| BLE 프로비저닝 (펌웨어) | AI팀 | ESP-IDF `wifi_provisioning` 컴포넌트 |
| BLE 프로비저닝 (앱) | 앱 (RN) | Espressif 공식 React Native SDK |
| 메타데이터 등록 (Spring) | 백엔드 | D1 API (아래) |

> **Spring은 프로비저닝에 관여하지 않는다.** WiFi 설정은 앱↔ESP32 간 BLE 통신이며, 완료 후 앱이 D1을 호출해 메타데이터만 저장한다.

---

#### D1. 노드 등록

```
POST /api/v1/care-targets/{id}/devices
Authorization: Bearer {token}
```

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| device_uid | string | Y | ESP32 MAC 주소 (최대 64자, 전역 고유) |
| room | string | N | 설치 공간 (예: "거실", "침실") |
| position_label | string | N | 세부 위치 설명 |
| node_role | string | N | SENDER / RECEIVER |
| firmware_version | string | N | 펌웨어 버전 |

**Response 201**

```json
{
  "success": true,
  "data": { "device_id": 1 },
  "error": null
}
```

**에러**: `404 CARE_TARGET_NOT_FOUND`, `403 ACCESS_DENIED`, `409 DEVICE_ALREADY_EXISTS`

---

#### D2. 노드 목록

```
GET /api/v1/care-targets/{id}/devices
Authorization: Bearer {token}
```

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "device_id": 1,
      "device_uid": "AA:BB:CC:DD:EE:FF",
      "room": "거실",
      "position_label": null,
      "node_role": "RECEIVER",
      "status": "ACTIVE",
      "firmware_version": null,
      "last_seen_at": "2026-06-27T04:22:00Z",
      "registered_at": "2026-06-27T03:00:00Z"
    }
  ],
  "error": null
}
```

> `last_seen_at`: I4 헬스체크 최종 수신 시각. `null`이면 아직 헬스체크 신호 미수신.

**에러**: `404 CARE_TARGET_NOT_FOUND`, `403 ACCESS_DENIED`

---

#### D3. 노드 수정

```
PATCH /api/v1/devices/{id}
Authorization: Bearer {token}
```

> null 필드는 변경하지 않는다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| room | string | N | 설치 공간 |
| position_label | string | N | 세부 위치 설명 |
| node_role | string | N | SENDER / RECEIVER |
| status | string | N | ACTIVE / INACTIVE / ERROR |

**Response 200** — 수정된 디바이스 전체 필드 반환 (D2 응답 형식과 동일)

**에러**: `404 DEVICE_NOT_FOUND`, `403 ACCESS_DENIED`

---

#### D4. 노드 삭제

```
DELETE /api/v1/devices/{id}
Authorization: Bearer {token}
```

**Response 200** — `data: null`

**에러**: `404 DEVICE_NOT_FOUND`, `403 ACCESS_DENIED`

---

### 3-5. 상태·이벤트 (Status / Sensing)

#### S1. 실시간 상태 대시보드

```
GET /api/v1/care-targets/{id}/status
```

> 앱 메인 화면용. 현재 위험도, 최근 활동, 노드 상태, 진행 중 에스컬레이션을 한 번에 반환한다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "care_target_id": 45,
    "current_risk_level": "WARNING",
    "last_activity_at": "2026-06-09T05:01:00Z",
    "today_metrics": {
      "movement_level": 0.62,
      "avg_breathing_rate": 16.4,
      "inactivity_minutes": 25
    },
    "devices": [
      { "device_id": 1, "room": "거실", "status": "ACTIVE" },
      { "device_id": 2, "room": "침실", "status": "ACTIVE" }
    ],
    "active_escalation": null
  },
  "error": null
}
```

#### S2. 감지 이벤트 목록

```
GET /api/v1/care-targets/{id}/events?page=0&size=20&eventType=FALL
```

**Query**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| event_type | string | N | 필터 (FALL/RESPIRATION_ABNORMAL/INACTIVITY/ANOMALY) |
| from | datetime | N | 시작 시각 |
| to | datetime | N | 종료 시각 |

**Response 200** — 이벤트 + 위험도 조인

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "sensing_event_id": 882,
        "event_type": "FALL",
        "risk_probability": 0.91,
        "risk_score": 85,
        "risk_level": "DANGER",
        "detected_at": "2026-06-09T03:22:00Z",
        "has_replay": true
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1
  },
  "error": null
}
```

> **`has_replay`**: 해당 이벤트에 복원 클립(`tb_pose_clip`)이 존재하면 `true`. 앱이 "리플레이" 버튼 노출 여부를 판단하는 데 사용. NORMAL·SENSOR_ERROR 이벤트는 `false`가 기본값.

#### S3. 복원 스켈레톤 리플레이 조회

```
GET /api/v1/sensing-events/{sensing_event_id}/pose-clip
```

> 해당 감지 이벤트에 AI 서버가 복원·적재한 스켈레톤 클립을 반환한다.
> 카메라 영상이 아닌 **추상 스켈레톤 좌표 시퀀스**이므로 개인정보 침해 없음.

**권한**: 해당 `sensing_event`의 `care_target`에 대한 `tb_care_relationship` 존재 검증

**Response 200**

```json
{
  "success": true,
  "data": {
    "pose_clip_id": 12,
    "sensing_event_id": 882,
    "model_version": "csi-pose-v0.1",
    "joint_schema": "13-point",
    "fps": 30,
    "frame_count": 150,
    "duration_ms": 5000,
    "window_start_at": "2026-06-09T03:22:00Z",
    "window_end_at": "2026-06-09T03:22:05Z",
    "frames": {
      "joints": ["head", "left_shoulder", "right_shoulder",
                 "left_elbow", "right_elbow", "left_wrist", "right_wrist",
                 "left_hip", "right_hip", "left_knee", "right_knee",
                 "left_ankle", "right_ankle"],
      "frames": [
        { "t_ms": 0,   "pose": [[0.5, 1.7, 0.0], ["..."]], "event": "moving" },
        { "t_ms": 2000, "pose": [["..."]], "event": "falling" },
        { "t_ms": 4000, "pose": [["..."]], "event": "inactive" }
      ]
    },
    "event_timeline": {
      "falling": { "start_ms": 2000, "end_ms": 2800 },
      "inactive": { "start_ms": 4000, "end_ms": 5000 }
    }
  },
  "error": null
}
```

**에러**: `404 POSE_CLIP_NOT_FOUND`(클립 미존재·NORMAL 이벤트), `404 CARE_TARGET_NOT_FOUND`, `403 ACCESS_DENIED`

---

### 3-6. 에스컬레이션 (Escalation)

#### E1. 에스컬레이션 목록

```
GET /api/v1/care-targets/{id}/escalations?page=0&size=20
Authorization: Bearer {token}
```

> 해당 노인에 대해 발생한 에스컬레이션 목록을 시작 시각 최신순으로 페이지 반환한다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "escalation_id": 31,
        "status": "RESOLVED",
        "resolution_type": "GUARDIAN_HANDLED",
        "summary": null,
        "started_at": "2026-06-09T03:22:10Z",
        "resolved_at": "2026-06-09T03:25:40Z"
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1
  },
  "error": null
}
```

**에러**: `404 CARE_TARGET_NOT_FOUND`, `403 ACCESS_DENIED`

---

#### E2. 에스컬레이션 상세 (단계 로그)

```
GET /api/v1/escalations/{id}
```

> 하나의 응급 대응 흐름과 단계별 진행 로그(`tb_escalation_step`)를 함께 반환한다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "escalation_id": 31,
    "status": "RESOLVED",
    "resolution_type": "GUARDIAN_HANDLED",
    "resolution_memo": "직접 방문 확인 완료. 낙상 없음.",
    "started_at": "2026-06-09T03:22:10Z",
    "resolved_at": "2026-06-09T03:25:40Z",
    "steps": [
      {
        "step_type": "VOICE_CHECK",
        "step_order": 1,
        "status": "NO_RESPONSE",
        "executed_at": "2026-06-09T03:22:10Z"
      },
      {
        "step_type": "GUARDIAN_NOTIFY",
        "step_order": 2,
        "status": "RESPONDED",
        "executed_at": "2026-06-09T03:23:10Z",
        "responded_at": "2026-06-09T03:25:40Z"
      }
    ]
  },
  "error": null
}
```

#### E3. 보호자 확인·해제

```
POST /api/v1/escalations/{id}/resolve
```

> 보호자가 앱에서 "확인 완료"를 눌러 에스컬레이션을 해제한다. 이후 119 자동 신고 단계로 진행되지 않는다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| resolution_type | string | Y | `GUARDIAN_HANDLED` / `FALSE_ALARM` 만 허용. 다른 값은 400 반환 |
| memo | string | N | 보호자 확인 메모 (tb_escalation.resolution_memo에 저장) |

**Response 200** — 해제된 에스컬레이션 상태 반환 (E2 응답 형식과 동일)

**에러**: `404 ESCALATION_NOT_FOUND`, `403 ACCESS_DENIED`, `409 ESCALATION_ALREADY_RESOLVED` (이미 종료), `400 INVALID_RESOLUTION_TYPE` (허용 안 되는 resolution_type)

---

### 3-7. 알림 (Notification)

#### N3. FCM 토큰 등록

```
POST /api/v1/me/fcm-token
```

> 앱 실행 시 디바이스의 FCM 토큰을 등록/갱신한다. 푸시 알림 발송 대상이 된다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fcm_token | string | Y | Firebase 디바이스 토큰 (tb_fcm_token.token, UNIQUE) |
| platform | string | Y | `IOS` / `ANDROID` |

**Response 200 — envelope**

```json
{ "success": true, "data": null, "error": null }
```

> **구현 노트**: 기존 void 엔드포인트(A4, D4 등) 컨벤션에 맞춰 200 + envelope 반환 (원래 명세 204에서 변경).
> 같은 토큰 재등록 시 `user_id`·`platform`·`updated_at` 갱신(멱등). 동시 등록 경합도 멱등 처리.
> 실제 FCM 발송은 I2(에스컬레이션 단계 기록) 시 구현 예정.

---

### 3-8. 일일 리포트 (Report)

#### P2. 리포트 상세

```
GET /api/v1/reports/{id}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "daily_report_id": 210,
    "care_target_id": 45,
    "report_date": "2026-06-08",
    "summary_text": "어제 어머니께서는 평소보다 늦게 기상하셨고...",
    "metrics": {
      "wake_up_time": "08:40",
      "activity_level": 0.55,
      "sleep_hours": 7.2,
      "bathroom_visits": 8
    },
    "generated_at": "2026-06-09T00:10:00Z"
  },
  "error": null
}
```

---

## 4. 내부 API 상세 (AI 서버 → Spring)

> AI 신호처리 서버(FastAPI)가 판정 결과를 Spring 백엔드로 전달하는 서버 간 통신.
> `X-Internal-Key` 헤더로 인증하며, 외부에 노출되지 않는다.

### I1. 감지 이벤트 + 위험도 적재

```
POST /internal/v1/sensing-events
```

> AI 서버가 신호처리·모델 판정을 마친 이벤트와 위험도를 한 번에 적재한다.
> Spring은 `tb_sensing_event` + `tb_risk_assessment`를 저장하고, `risk_level=DANGER` 시 **에스컬레이션을 생성하고 escalation_id를 반환**한다. 위험도·risk_level은 AI 서버가 산출한 값을 그대로 저장한다(Backend 재계산 없음).

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| care_target_id | long | Y | 대상 노인 (AI팀 user_id에 해당) |
| device_id | long | N | 주 감지 노드 |
| event_type | string | Y | FALL / INACTIVITY / RESPIRATION_ABNORMAL / ANOMALY / SENSOR_ERROR / NORMAL |
| risk_probability | number | N | 즉각 위험 확률 (0~1, 소수점 3자리) |
| anomaly_score | number | N | World Model 개인화 이상 점수 (0~1, 소수점 3자리) |
| trend_score | number | N | CUSUM 누적 변화 점수 (0~1, 소수점 3자리) |
| sensor_status | string | N | OK / ERROR |
| model_version | string | Y | 판정 모델 버전 |
| features | object | N | 신호처리 특징값 (JSONB) |
| detected_at | datetime | Y | 감지 시각 |
| risk_score | int | Y | 최종 위험도 (0~100, AI 서버 산출) |
| risk_level | string | Y | SAFE / WARNING / DANGER |
| score_breakdown | object | N | 모델별 기여도 |

**Response 201**

```json
{
  "success": true,
  "data": {
    "sensing_event_id": 882,
    "risk_assessment_id": 451,
    "escalation_triggered": true,
    "escalation_id": 31
  },
  "error": null
}
```

**개발 가이드**
- `escalation_triggered=true`면 Backend가 에스컬레이션을 생성하고 **escalation_id를 반환** → AI Agent가 이 id로 이후 단계를 기록
- 위험도·severity는 AI 서버 산출값을 그대로 저장. Backend는 재계산하지 않음
- 멱등 처리: 동일 `(care_target_id, detected_at, event_type)` 중복 요청 방지 (AI 서버 재시도 대비)

---

### I2. 에스컬레이션 단계 진행 기록

```
POST /internal/v1/escalations/{escalation_id}/steps
```

> LangGraph 에이전트가 각 단계(음성확인→보호자알림→119) 실행 결과를 기록한다.
> `GUARDIAN_NOTIFY` 단계면 `guardian_message`를 함께 보내고, Backend가 이를 받아 FCM 발송 + `tb_notification` 기록을 수행한다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| step_type | string | Y | VOICE_CHECK / GUARDIAN_NOTIFY / EMERGENCY_CALL |
| step_order | int | Y | 단계 순서 |
| status | string | Y | EXECUTED / RESPONDED / NO_RESPONSE / SKIPPED |
| executed_at | datetime | Y | 실행 시각 |
| responded_at | datetime | N | 응답 시각 |
| response_detail | object | N | 음성 응답·STT 결과 |
| guardian_message | object | N | (GUARDIAN_NOTIFY 단계) 알림 문구 |

**guardian_message 구조** (GUARDIAN_NOTIFY 단계에서만)

```json
{
  "title": "낙상 의심 상황이 감지되었습니다.",
  "body": "14시 22분 침실에서 낙상 가능성이 높은 움직임이 감지되었습니다. 음성 확인에 응답이 없어 보호자 확인이 필요합니다.",
  "recommendation": "즉시 전화 또는 방문 확인을 권장합니다."
}
```

**Response 201** — 생성된 step 정보 반환. `GUARDIAN_NOTIFY`면 Backend가 `guardian_message`로 FCM 발송 + `tb_notification` 기록을 트리거

---

### I3. 일일 리포트 적재

```
POST /internal/v1/reports
```

> LLM이 생성한 일일 리포트를 적재한다. 동일 `(careTargetId, reportDate)` 존재 시 UPSERT.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| care_target_id | long | Y | 대상 노인 |
| report_date | date | Y | 리포트 일자 |
| summary_text | string | Y | LLM 생성 자연어 |
| metrics | object | N | 활동·수면·호흡 지표 |

**Response 201**

---

### I5. 복원 스켈레톤 클립 적재

```
POST /internal/v1/sensing-events/{sensing_event_id}/pose-clip
```

> AI 서버(CSI-to-Pose student 모델)가 이상 감지 구간의 스켈레톤 복원을 완료한 후 클립을 적재한다.
> Spring은 `tb_pose_clip`에 저장하며, `sensing_event`당 1건 보장(멱등).
> 흐름: AI 서버가 I1으로 `sensing_event_id` 확보 → 스켈레톤 복원(비동기 가능) → I5 호출.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| model_version | string | Y | 복원 모델 버전 |
| joint_schema | string | Y | 관절 셋 식별자 (예: `13-point`) |
| fps | int | Y | 초당 프레임 수 (> 0) |
| frame_count | int | Y | 총 프레임 수 (≥ 0) |
| duration_ms | int | N | 클립 길이(ms) |
| window_start_at | datetime | Y | 복원 구간 시작 (UTC ISO 8601) |
| window_end_at | datetime | Y | 복원 구간 종료 (UTC ISO 8601) |
| frames | object | Y | 스켈레톤 프레임 시퀀스 JSONB (S3 응답 구조와 동일) |
| event_timeline | object | N | 구간 마커 (falling/inactive 등) |

**Response 201**

```json
{
  "success": true,
  "data": {
    "pose_clip_id": 12,
    "sensing_event_id": 882
  },
  "error": null
}
```

**개발 가이드**
- 멱등: `UNIQUE(sensing_event_id)` 기준 동일 이벤트 재요청 시 기존 클립 반환(새 row 미생성). I1 멱등 패턴과 동일
- `frames` JSONB 스키마(관절 순서·좌표계·event 라벨 셋)는 AI팀과 재합의 후 이 명세에 반영할 것

**에러**: `404 SENSING_EVENT_NOT_FOUND`(미존재 이벤트), `401 INVALID_INTERNAL_KEY`

---

### I4. 노드 헬스체크

```
POST /internal/v1/devices/{device_uid}/heartbeat
```

> ESP32 노드의 생존 신호. Spring이 `tb_device.last_seen_at`을 갱신한다.

**Response 200** — `data: null`

> 코드베이스 응답 envelope 일관성 유지를 위해 204 대신 200으로 구현.

**에러**: `404 DEVICE_NOT_FOUND`(미등록 device_uid), `401 INVALID_INTERNAL_KEY`

**개발 가이드**
- `last_seen_at`이 일정 시간(예: 5분) 이상 미갱신 시 배치가 `status=ERROR`로 전환하고 보호자에게 노드 장애 알림 발송

---

## 5. 공통 처리 규약

| 항목 | 규약 |
|---|---|
| **요청 검증** | Bean Validation(`@Valid`). 실패 시 `400 BAD_REQUEST` + 필드별 에러 |
| **권한 검증** | 외부 API는 인터셉터에서 JWT → `user_id` → 노인 접근 권한 확인 |
| **시각** | 요청·응답 모두 UTC ISO 8601. 표시 변환은 클라이언트 책임 |
| **멱등성** | 내부 API(I1~I4)는 재시도 대비 멱등 처리 |
| **로깅** | 내부 API 호출은 전부 접근 로그 기록 (장애 추적) |
| **버저닝** | Breaking change 시 `/api/v2`로 분리, v1 일정 기간 유지 |

---

## 6. 향후 확장 (v0.2 이후)

- 사회복지사 전용 대시보드 API (담당 노인 일괄 모니터링)
- 알림 설정 API (보호자별 알림 채널·시간대 설정)
- 통계 API (주간·월간 추세)

> **실시간 처리 방식 결정**: 응급 알림은 앱 종료 상태에서도 도달해야 하므로 FCM 푸시로 처리하고, 대시보드 상태 조회는 폴링(10~30초)으로 구현한다. WebSocket은 상시 연결 비용 대비 본 시스템의 트래픽 특성(저빈도 상태 변화, 소수 동시 접속)에 이점이 없어 의도적으로 배제했다.
