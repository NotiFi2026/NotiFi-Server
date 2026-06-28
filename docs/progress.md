# API 구현 진행도

**last-updated**: 2026-06-28 | **완료**: 24 / 32

> MVP 우선 구현 기준이므로 API 개수·순서는 변경될 수 있고, 중간 항목이 생략되거나 나중에 추가될 수 있음.

> ✅ 완료 / 🚧 진행 중 / ⬜ 예정

---

## Auth (A) — 회원가입·로그인·JWT 토큰 관리

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| A1 | POST | `/api/v1/auth/signup` | 보호자 회원가입 | ✅ |
| A2 | POST | `/api/v1/auth/login` | 로그인 → 액세스·리프레시 토큰 발급 | ✅ |
| A3 | POST | `/api/v1/auth/refresh` | 리프레시 토큰으로 액세스 토큰 갱신 | ✅ |
| A4 | POST | `/api/v1/auth/logout` | 로그아웃 (리프레시 토큰 폐기) | ✅ |

## Care Target (C) — 모니터링 대상 노인 관리

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| C1 | POST | `/api/v1/care-targets` | 노인 등록 (등록자가 자동으로 주 보호자 연결) | ✅ |
| C2 | GET | `/api/v1/care-targets` | 내가 담당하는 노인 목록 (위험도·디바이스 수 요약 포함) | ✅ |
| C3 | GET | `/api/v1/care-targets/{id}` | 노인 상세 정보 조회 | ✅ |
| C4 | PATCH | `/api/v1/care-targets/{id}` | 노인 정보 수정 | ✅ |
| C5 | DELETE | `/api/v1/care-targets/{id}` | 노인 삭제 (soft delete, 주 보호자만 가능) | ✅ |

## Relationship (R) — 노인↔보호자 N:N 연결 관리

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| R1-a | POST | `/api/v1/care-targets/{id}/invite-codes` | 초대코드 발급 + 공유 링크(invite_url) 반환 | ✅ |
| R1-b | POST | `/api/v1/invite-codes/{code}/accept` | 초대코드 수락 → 보호자 자가 연결 | ✅ |
| R1-c | GET | `/api/v1/invite-codes/{code}` | 초대코드 미리보기 (코드 유지, 다이얼로그 정보) | ✅ |
| R2 | GET | `/api/v1/care-targets/{id}/guardians` | 해당 노인의 보호자 목록 조회 | ✅ |
| R3 | PATCH | `/api/v1/relationships/{id}` | 우선순위·관계 유형 수정 | ✅ |
| R4 | DELETE | `/api/v1/relationships/{id}` | 보호자-노인 연결 해제 (주 보호자 차단) | ✅ |

## Device (D) — ESP32 센싱 노드 등록·관리

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| D1 | POST | `/api/v1/care-targets/{id}/devices` | 노드 등록 (MAC, 설치 공간, 역할 지정) | ✅ |
| D2 | GET | `/api/v1/care-targets/{id}/devices` | 노인 가구의 노드 목록 및 상태 조회 | ✅ |
| D3 | PATCH | `/api/v1/devices/{id}` | 노드 정보 수정 (위치, 역할 등) | ✅ |
| D4 | DELETE | `/api/v1/devices/{id}` | 노드 삭제 | ✅ |

## Status / Sensing (S) — 실시간 상태 및 감지 이벤트

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| S1 | GET | `/api/v1/care-targets/{id}/status` | 앱 메인 대시보드용 — 현재 위험도·노드 상태·진행 중 에스컬레이션 한번에 반환 | ✅ |
| S2 | GET | `/api/v1/care-targets/{id}/events` | 낙상·호흡이상·무활동 등 감지 이벤트 목록 (type/시간 필터, `has_replay` 포함) | ✅ |
| S3 | GET | `/api/v1/sensing-events/{sensing_event_id}/pose-clip` | 사고 순간 복원 스켈레톤 리플레이 조회 (AI CSI-to-Pose 모델 산출, I5 적재 후 제공) | ⬜ |

## Escalation (E) — 응급 대응 흐름 (음성확인→보호자알림→119)

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| E1 | GET | `/api/v1/care-targets/{id}/escalations` | 해당 노인의 에스컬레이션 목록 | ⬜ |
| E2 | GET | `/api/v1/escalations/{id}` | 에스컬레이션 상세 + 단계별 진행 로그 | ⬜ |
| E3 | POST | `/api/v1/escalations/{id}/resolve` | 보호자가 앱에서 "확인 완료" → 119 자동신고 중단 | ⬜ |

## Notification (N) — 알림 수신 및 FCM 토큰

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| N1 | GET | `/api/v1/notifications` | 내 알림 목록 (응급·리포트·시스템) | ⬜ |
| N2 | PATCH | `/api/v1/notifications/{id}/read` | 알림 읽음 처리 | ⬜ |
| N3 | POST | `/api/v1/me/fcm-token` | 앱 실행 시 FCM 디바이스 토큰 등록·갱신 | ✅ |

## Report (P) — LLM 생성 일일 자연어 리포트

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| P1 | GET | `/api/v1/care-targets/{id}/reports` | 일일 리포트 목록 | ⬜ |
| P2 | GET | `/api/v1/reports/{id}` | 리포트 상세 — LLM 요약 + 활동량·수면·호흡 지표 | ⬜ |

## Internal (I) — AI 서버 → Spring 서버 간 통신 (X-Internal-Key 인증)

| 코드 | Method | Path | 설명 | 상태 |
|---|---|---|---|---|
| I1 | POST | `/internal/v1/sensing-events` | 감지 이벤트·위험도 적재 → DANGER 시 에스컬레이션 생성 후 ID 반환 | ✅ |
| I2 | POST | `/internal/v1/escalations/{id}/steps` | 에스컬레이션 단계 기록 (GUARDIAN_NOTIFY 시 FCM 발송 트리거) | ✅ |
| I3 | POST | `/internal/v1/reports` | LLM 생성 일일 리포트 적재 (UPSERT) | ⬜ |
| I4 | POST | `/internal/v1/devices/{uid}/heartbeat` | 노드 생존 신호 수신 → `last_seen_at` 갱신 | ✅ |
| I5 | POST | `/internal/v1/sensing-events/{id}/pose-clip` | 복원 스켈레톤 클립 적재 (CSI-to-Pose 산출물, sensing_event 1:1 멱등) | ✅ |
