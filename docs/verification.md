# API 검증 시나리오

Swagger(`http://localhost:8080/swagger-ui.html`) 또는 Postman에서 **위에서부터 순서대로** 실행.
각 스텝의 응답값을 다음 스텝에 그대로 붙여 넣으면 하나의 흐름으로 모든 API가 검증된다.

> 신규 API 스텝은 **A4(logout) 이전**에 append.

---

## 현재 서버 기준 (2026-06-24)

### 1. A1 — 회원가입

```
POST /api/v1/auth/signup
Content-Type: application/json

{
  "email": "test@notifi.dev",
  "password": "password123!",
  "name": "김보호",
  "role": "GUARDIAN"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.role` | `"GUARDIAN"` |

---

### 2. A2 — 로그인 → 토큰 저장

```
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "test@notifi.dev",
  "password": "password123!"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.user.name` | `"김보호"` |

> **저장**: `access_token` → `{{access_token}}`, `refresh_token` → `{{refresh_token}}`

---

### 3. C1 — 노인 등록 → care_target_id 저장

```
POST /api/v1/care-targets
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "박순자",
  "birth_date": "1945-03-15",
  "gender": "FEMALE",
  "address": "서울시 강남구 테헤란로 1",
  "emergency_memo": "당뇨 약 복용 중"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.care_target_id` | 숫자 (예: `1`) |

> **저장**: `care_target_id` → `{{care_target_id}}`

---

### 4. C2 — 내가 보는 노인 목록

```
GET /api/v1/care-targets
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.content[0].name` | `"박순자"` |
| `data.content[0].is_primary` | `true` |
| `data.content[0].current_risk_level` | `null` (Sensing 도메인 미구현) |
| `data.content[0].device_count` | `0` (Device 도메인 미구현) |
| `data.total_elements` | `1` |

> **추가 검증**: `GET /api/v1/care-targets?sort=["string"]`(Swagger 기본 junk sort)도 동일하게 200.

---

### 5. C3 — 노인 상세 조회

```
GET /api/v1/care-targets/{{care_target_id}}
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.care_target_id` | `{{care_target_id}}` |
| `data.name` | `"박순자"` |
| `data.gender` | `"FEMALE"` |
| `data.is_primary` | `true` |

---

### 6. C4 — 노인 정보 수정

```
PATCH /api/v1/care-targets/{{care_target_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "박순이",
  "emergency_memo": "당뇨 약 복용 중, 무릎 관절 주의"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.name` | `"박순이"` (변경됨) |
| `data.gender` | `"FEMALE"` (null 전달 → 유지) |
| `data.emergency_memo` | `"당뇨 약 복용 중, 무릎 관절 주의"` |

---

### 7. C5 — 노인 삭제 (시나리오 검증 후 수행)

```
DELETE /api/v1/care-targets/{{care_target_id}}
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data` | `null` |

**삭제 후 검증**: `GET /api/v1/care-targets` 재호출 → `total_elements: 0` (soft-deleted 제외).

---

### 8. A1(user2) — 두 번째 보호자 회원가입

```
POST /api/v1/auth/signup
Content-Type: application/json

{
  "email": "test2@notifi.dev",
  "password": "password123!",
  "name": "이보호",
  "role": "GUARDIAN"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |

---

### 9. A2(user2) — 두 번째 보호자 로그인

```
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "test2@notifi.dev",
  "password": "password123!"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |

> **저장**: `access_token` → `{{access_token_2}}`

---

### 10. R1-a — 초대코드 발급 (user1, 주 보호자)

```
POST /api/v1/care-targets/{{care_target_id}}/invite-codes
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "relationship_type": "FAMILY"
}
```

> `notify_priority`는 선택 필드 — 생략 시 기본값 1로 설정됨. Swagger UI엔 표시되지만 검증 시나리오에서는 기본값 동작을 확인하기 위해 의도적으로 생략.

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.code` | 8자리 영숫자 (예: `A3B7CD2E`) |
| `data.invite_url` | `https://app.bloom-safety.app/invite/{code}` 형태 |
| `data.expires_at` | 24시간 후 UTC |

> **저장**: `data.code` → `{{invite_code}}`, `data.invite_url` → `{{invite_url}}`

---

### 11. R1-c — 초대코드 미리보기 (user2, 코드 소모 없음)

```
GET /api/v1/invite-codes/{{invite_code}}
Authorization: Bearer {{access_token_2}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.care_target_name` | `"박순이"` (C4에서 수정된 이름) |
| `data.inviter_name` | `"김보호"` |
| `data.relationship_type` | `"FAMILY"` |
| `data.expires_at` | 24시간 내 UTC |

**코드 유지 확인**: 이 호출 직후 바로 수락(스텝 12)이 성공해야 함 — 미리보기가 코드를 소멸시키지 않았음을 입증.

**에러 케이스**: 잘못된 코드(`GET /api/v1/invite-codes/XXXXXXXX`) → `404 INVALID_INVITE_CODE`

---

### 12. R1-b — 초대코드 수락 (user2)

```
POST /api/v1/invite-codes/{{invite_code}}/accept
Authorization: Bearer {{access_token_2}}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.care_target_id` | `{{care_target_id}}` |

> **저장**: `data.relationship_id` → `{{rel_id_user2}}`

**일회성 검증**: 동일 코드 재수락 → `404 INVALID_INVITE_CODE`

---

### 13. R2 — 보호자 목록 조회 (user1)

```
GET /api/v1/care-targets/{{care_target_id}}/guardians
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| 배열 길이 | `2` |
| `[0].is_primary` | `true` (user1, priority 1) |
| `[1].is_primary` | `false` (user2) |
| 정렬 | `notify_priority` 오름차순 |

---

### 14. R3 — 관계 수정 (user1)

```
PATCH /api/v1/relationships/{{rel_id_user2}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "notify_priority": 2
}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.notify_priority` | `2` |
| `data.is_primary` | `false` (변경 안 됨) |

---

### 15. R4 — 주 보호자 해제 차단

```
DELETE /api/v1/relationships/{user1의 relationship_id}
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `409 Conflict` |
| `error.code` | `CANNOT_DELETE_PRIMARY` |

---

### 16. R4 — 연결 해제 (user2 관계 삭제)

```
DELETE /api/v1/relationships/{{rel_id_user2}}
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data` | `null` |

**삭제 후 검증**: R2 재호출 → 배열 길이 `1`

---

### 17. A3 — 토큰 갱신

```
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refresh_token": "{{refresh_token}}"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.access_token` | 이전 값과 다른 새 토큰 |

> **저장**: 새 `access_token` → `{{access_token}}` 교체

---

### 18. I1 — 센싱 이벤트 적재 (DANGER → 에스컬레이션 자동 생성)

> 실제 키는 `.env`의 `INTERNAL_API_KEY` 값 사용. `change-me-before-deploy`는 동작하지 않음.

```
POST /internal/v1/sensing-events
X-Internal-Key: {.env의 INTERNAL_API_KEY 값}
Content-Type: application/json

{
  "care_target_id": {{care_target_id}},
  "event_type": "FALL",
  "model_version": "v0.1",
  "detected_at": "2026-06-27T03:22:00Z",
  "risk_score": 85,
  "risk_level": "DANGER",
  "risk_probability": 0.92,
  "features": { "amplitude": 1.23 },
  "score_breakdown": { "lightgbm": 0.8, "lstm_ae": 0.9, "cusum": 0.7 }
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.sensing_event_id` | 새로 생성된 ID |
| `data.risk_assessment_id` | 새로 생성된 ID |
| `data.escalation_triggered` | `true` |
| `data.escalation_id` | 새로 생성된 ID |

> **저장**: `data.escalation_id` → `{{escalation_id}}`

**멱등 확인**: 동일 바디 재요청 → 동일한 ids 반환 (새 레코드 미생성)

**에스컬레이션 미생성 확인**: `"risk_level": "WARNING"` 으로 변경 후 요청 → `escalation_triggered: false`, `escalation_id: null`

**정밀도 초과 차단**: `"risk_probability": 0.1234`(소수 4자리) 포함 → `400 INVALID_INPUT_VALUE` (`riskProbability: 숫자 값이 한계를 초과합니다(<1 자리>.<3 자리> 예상)`)

**인증 오류**: `X-Internal-Key` 헤더 누락·오류 → `401 INVALID_INTERNAL_KEY`

---

### 19. I4 — 노드 헬스체크 (D1 등록 후)

> D1으로 노드 등록 후 실행. 실제 키는 `.env`의 `INTERNAL_API_KEY` 값 사용.

```
POST /internal/v1/devices/AA:BB:CC:DD:EE:FF/heartbeat
X-Internal-Key: {.env의 INTERNAL_API_KEY 값}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data` | `null` |

**미등록 device_uid 확인**: `POST /internal/v1/devices/ZZ:ZZ:ZZ:ZZ:ZZ:ZZ/heartbeat` → `404 DEVICE_NOT_FOUND`

---

### 20. D1 — 노드 등록 → device_id 저장

```
POST /api/v1/care-targets/{{care_target_id}}/devices
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "device_uid": "AA:BB:CC:DD:EE:FF",
  "room": "거실",
  "node_role": "RECEIVER"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.device_id` | 숫자 (예: `1`) |

> **저장**: `data.device_id` → `{{device_id}}`

**중복 확인**: 동일 `device_uid`로 재요청 → `409 DEVICE_ALREADY_EXISTS`

**권한 확인**: `{{access_token_2}}`(관계 없는 user2)로 요청 → `403 ACCESS_DENIED`

---

### 21. D2 — 노드 목록 조회

```
GET /api/v1/care-targets/{{care_target_id}}/devices
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| 배열 길이 | `1` |
| `[0].device_uid` | `"AA:BB:CC:DD:EE:FF"` |
| `[0].status` | `"ACTIVE"` |
| `[0].last_seen_at` | `null` (헬스체크 미수신 — 스텝 19는 이전 실행) |

**I4 연동 확인**: 스텝 19(I4) 실행 후 이 조회 재호출 → `last_seen_at`이 최근 시각으로 채워짐.

---

### 22. D3 — 노드 정보 수정

```
PATCH /api/v1/devices/{{device_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "room": "침실",
  "status": "INACTIVE"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.room` | `"침실"` (변경됨) |
| `data.status` | `"INACTIVE"` (변경됨) |
| `data.node_role` | `"RECEIVER"` (null 전달 → 유지) |

---

### 23. C2 — 노인 목록 (device_count 확인)

```
GET /api/v1/care-targets
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.content[0].device_count` | `1` (D1으로 등록한 노드) |

---

### 24. D4 — 노드 삭제

```
DELETE /api/v1/devices/{{device_id}}
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data` | `null` |

**삭제 후 검증**: D2 재호출 → 배열 길이 `0`

**미존재 id 확인**: `DELETE /api/v1/devices/99999` → `404 DEVICE_NOT_FOUND`

---

### 26. S1 — 실시간 상태 대시보드

```
GET /api/v1/care-targets/{{care_target_id}}/status
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.care_target_id` | `{{care_target_id}}` |
| `data.current_risk_level` | I1 적재 이벤트가 있으면 `"SAFE"/"WARNING"/"DANGER"`, 없으면 `null` |
| `data.last_activity_at` | 최신 이벤트 `detected_at` 또는 `null` |
| `data.devices` | D1으로 등록한 디바이스 배열 (`device_id·room·status`) |
| `data.today_metrics` | `null` (tb_activity_aggregate 미구현) |
| `data.active_escalation` | `null` (E 도메인 보류) |

**권한 검증**: 관계 없는 user2 토큰 → `403 ACCESS_DENIED`

---

### 27. S2 — 감지 이벤트 목록

```
GET /api/v1/care-targets/{{care_target_id}}/events?page=0&size=20
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `data.content[0].sensing_event_id` | I1로 적재한 이벤트 id |
| `data.content[0].event_type` | `"FALL"` 등 |
| `data.content[0].risk_level` | `"SAFE"/"WARNING"/"DANGER"` 또는 `null` |
| `data.content[0].has_replay` | `false` (I5 미구현) |
| `data.total_elements` | 적재한 이벤트 수 |

**event_type 필터**: `?event_type=FALL` → FALL 이벤트만 반환 확인
**from/to 필터**: `?from=2026-06-27T00:00:00Z&to=2026-06-28T00:00:00Z` → 해당 기간 이벤트만
**이벤트 0건**: 이벤트 없는 노인 → `data.content` 빈 배열, `data.total_elements: 0`

---

### 28. N3 — FCM 토큰 등록

```
POST /api/v1/me/fcm-token
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "fcm_token": "test-fcm-token-android-001",
  "platform": "ANDROID"
}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |
| `success` | `true` |
| `data` | `null` |

**재등록(멱등)**: 같은 토큰으로 `platform: "IOS"` 변경 후 재호출 → `200 OK`, DB에서 platform 갱신 확인
**유효성 검증**: `fcm_token: ""` 또는 `platform: "WINDOWS"` → `400 VALIDATION_ERROR`

---

### 29. I5 — 복원 스켈레톤 클립 적재

> 스텝 18(I1)에서 저장한 `{{sensing_event_id}}` 재사용.

```
POST /internal/v1/sensing-events/{{sensing_event_id}}/pose-clip
X-Internal-Key: {.env의 INTERNAL_API_KEY 값}
Content-Type: application/json

{
  "model_version": "v0.1",
  "joint_schema": "13-point",
  "fps": 10,
  "frame_count": 300,
  "duration_ms": 30000,
  "window_start_at": "2026-06-28T03:21:55Z",
  "window_end_at": "2026-06-28T03:22:25Z",
  "frames": { "data": [[0.1, 0.2], [0.3, 0.4]] },
  "event_timeline": null
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.pose_clip_id` | 새로 생성된 id |
| `data.sensing_event_id` | `{{sensing_event_id}}` |

> **저장**: `data.pose_clip_id` → `{{pose_clip_id}}`

**멱등 확인**: 동일 바디 재요청 → 동일한 `pose_clip_id` 반환 (새 row 미생성)

**미존재 이벤트 확인**: `{{sensing_event_id}}` 대신 `99999` → `404 SENSING_EVENT_NOT_FOUND`

**인증 오류**: `X-Internal-Key` 헤더 누락·오류 → `401 INVALID_INTERNAL_KEY`

---

### 30. I2 — 에스컬레이션 단계 기록 (VOICE_CHECK)

> 스텝 18(I1)에서 저장한 `{{escalation_id}}` 재사용.

```
POST /internal/v1/escalations/{{escalation_id}}/steps
X-Internal-Key: {.env의 INTERNAL_API_KEY 값}
Content-Type: application/json

{
  "step_type": "VOICE_CHECK",
  "step_order": 1,
  "status": "NO_RESPONSE",
  "executed_at": "2026-06-28T03:22:10Z",
  "response_detail": { "tts_played": true, "stt_result": null }
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.step_id` | 새로 생성된 id |
| `data.step_type` | `"VOICE_CHECK"` |
| `data.status` | `"NO_RESPONSE"` |

**멱등 확인**: 동일 step_type 재요청 → 동일한 row 갱신 (새 row 미생성), `201 Created`

**미존재 에스컬레이션 확인**: `99999` → `404 ESCALATION_NOT_FOUND`

---

### 31. I2 — 에스컬레이션 단계 기록 (GUARDIAN_NOTIFY + FCM 발송 트리거)

```
POST /internal/v1/escalations/{{escalation_id}}/steps
X-Internal-Key: {.env의 INTERNAL_API_KEY 값}
Content-Type: application/json

{
  "step_type": "GUARDIAN_NOTIFY",
  "step_order": 2,
  "status": "EXECUTED",
  "executed_at": "2026-06-28T03:22:10Z",
  "guardian_message": {
    "title": "낙상 의심 상황이 감지되었습니다.",
    "body": "14시 22분 침실에서 낙상 가능성이 높은 움직임이 감지되었습니다. 음성 확인에 응답이 없어 보호자 확인이 필요합니다.",
    "recommendation": "즉시 전화 또는 방문 확인을 권장합니다."
  }
}
```

| 항목 | 기대값 |
|---|---|
| Status | `201 Created` |
| `data.step_type` | `"GUARDIAN_NOTIFY"` |
| DB `tb_notification` | 보호자 수만큼 행 생성 (FCM 토큰 있으면 `status=SENT`, 없으면 `status=FAILED`) |

**알림 확인**: N3으로 FCM 토큰 등록한 보호자(user1)가 있으면 실제 푸시 수신 가능

**멱등 확인**: 동일 step_type 재요청 → FCM 재발송 없음, `tb_notification` 신규 행 없음

---

### 25. A4 — 로그아웃 _(항상 마지막)_

```
POST /api/v1/auth/logout
Authorization: Bearer {{access_token}}
```

| 항목 | 기대값 |
|---|---|
| Status | `200 OK` |

**로그아웃 후 검증**: 동일 토큰으로 `GET /api/v1/care-targets` 재호출 → `401 TOKEN_EXPIRED` 또는 `401 INVALID_CREDENTIALS`.
