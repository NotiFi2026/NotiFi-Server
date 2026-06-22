<div align="center">

# NotiFi

**카메라·웨어러블 없는 노인 안전 모니터링 시스템**

WiFi 신호(CSI)로 낙상·호흡 이상·무활동을 감지하고,<br>
AI 에이전트가 음성 확인부터 119 신고까지 자동으로 대응합니다.

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.0-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-compose-2496ED?style=flat-square&logo=docker&logoColor=white)

</div>

---

## 시스템 흐름

```
ESP32-C6 × 3        AI 서버 (FastAPI)       NotiFi-Server (이 레포)      보호자 앱
─────────────   →   ──────────────────   →   ──────────────────────   →   ─────────────
WiFi CSI 수집       신호처리 · 모델 판정       이벤트 저장 · 에스컬레이션      상태 대시보드
                    낙상 / 호흡 / 무활동       FCM 알림 · 119 자동신고        응급 알림 수신
```

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Spring Boot 4.1.0, Java 21, JPA/Hibernate |
| Database | PostgreSQL 15, Redis 7 |
| 인증 | JWT (앱), API Key (AI 서버 간) |
| 알림 | Firebase Cloud Messaging (FCM) |
| 인프라 | Docker, GitHub Actions, AWS |

## 로컬 실행

```bash
# 1. 환경 변수 설정
cp .env.example .env

# 2. 전체 실행 (앱 + DB + Redis)
docker compose up --build

# 또는 인프라만 띄우고 IntelliJ에서 앱 실행
docker compose up postgres redis
```

`GET http://localhost:8080/actuator/health` → `{"status":"UP"}` 확인

## 프로젝트 구조

```
src/main/java/com/notifi/server/
├── domain/          # 도메인별 Entity · Repository · Service · Controller
└── global/          # 공통 응답 · 예외처리 · 인증 · 설정
```

---

<div align="center">
  경기대학교 컴퓨터공학부 · 팀 피우다(Bloom) · 2026
</div>
