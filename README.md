# Coupon Service

선착순 쿠폰 발급 시스템의 동시성, 비동기 처리, 캐시, 데이터 정합성 문제를 단계적으로 해결하는 개인 프로젝트입니다.

## 기술 스택

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.3 |
| Framework | Spring Boot 4.1 |
| Database | MySQL 8.4, Redis 8.0 |
| Messaging | Apache Kafka 3.8 (KRaft) |
| Build | Gradle (Kotlin DSL), Jib |
| Test | k6 (부하 테스트) |

## 아키텍처 발전 과정

각 브랜치는 특정 문제를 해결하는 구현 단계를 나타냅니다.

### Part 1: 기본 구현
| Branch | 설명 |
|--------|------|
| `part-1` | 쿠폰 도메인, 발급 API 기본 구현 |

### Part 2: 동시성 제어
| Branch | 설명 | 해결한 문제             |
|--------|------|--------------------|
| `part-2-1-load-test` | 부하 테스트 환경 구축 | 초과 발급 현상 확인        |
| `part-2-2-pessimistic-lock` | DB 비관적 락 적용 | 정합성 확보, 그러나 처리량 한계 |
| `part-2-3-redis-lua` | Redis Lua 스크립트 원자 연산 | 높은 처리량 + 정합성       |

### Part 3: 비동기 처리
| Branch | 설명 | 해결한 문제               |
|--------|------|----------------------|
| `part-3-1-load-test` | 동기 방식 한계 측정 | 응답 지연, 타임아웃          |
| `part-3-2a-inmemory-queue` | 인메모리 큐로 분리 | 응답 속도 개선, 그러나 유실 위험  |
| `part-3-2b-event-listener` | Spring Event 활용 | 코드 분리, 그러나 여전히 인프로세스 |
| `part-3-2c-kafka` | Kafka 도입 | 내구성 + 확장성 확보         |

### Part 4: 캐시 최적화
| Branch | 설명 | 해결한 문제 |
|--------|------|-------------|
| `part-4-0-load-test` | 캐시 없는 상태 측정 | DB 부하 집중 |
| `part-4-1a-cache-naive` | 단순 캐시 적용 | Cache Stampede 발생 |
| `part-4-1b-single-flight` | Single Flight 패턴 | 동시 요청 병합 |
| `part-4-1c-swr` | Stale-While-Revalidate | 만료 시점 부하 분산 |
| `part-4-2-sold-out-signal` | 매진 시그널 캐시 | 매진 후 불필요한 조회 차단 |

### Part 5: 데이터 정합성
| Branch | 설명 | 해결한 문제 |
|--------|------|-------------|
| `part-5-0-drift-injection` | Redis-DB 불일치 시뮬레이션 | 문제 상황 재현 |
| `part-5-1-dlt-replay` | Dead Letter Topic 재처리 | 실패 메시지 복구 |
| `part-5-2-reconcile` | 주기적 대사(Reconciliation) | Redis-DB 자동 보정 |

## 주요 구현 포인트

### Redis Lua 원자 연산
```lua
-- 재고 차감 + 중복 체크 + 매진 시그널을 단일 트랜잭션으로
if redis.call('SISMEMBER', KEYS[2], userId) == 1 then return -1 end
local remaining = tonumber(redis.call('GET', KEYS[1]) or '0')
if remaining <= 0 then return 0 end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], userId)
return 1
```

### Stale-While-Revalidate 캐시
- 캐시 만료 전 백그라운드 갱신으로 Stampede 방지
- `fresh` 기간 내: 캐시 즉시 반환
- `stale` 기간: 캐시 반환 + 비동기 갱신

### Reconciliation 배치
- Redis 발급 수 vs DB 발급 수 주기적 비교
- 불일치 감지 시 자동 보정 또는 알림

## 실행 방법

```bash
# 1. 인프라 실행
docker compose up -d mysql redis kafka

# 2. 애플리케이션 빌드 및 실행
./gradlew jibDockerBuild
docker compose up -d coupon-service

# 3. 쿠폰 생성
curl -X POST http://localhost:8080/coupons \
  -H "Content-Type: application/json" \
  -d '{"name": "신규 가입 쿠폰", "totalQuantity": 100}'

# 4. 쿠폰 발급
curl -X POST http://localhost:8080/users/1/issuances \
  -H "Content-Type: application/json" \
  -d '{"couponId": 1}'
```

## 부하 테스트

```bash
# k6 설치 (macOS)
brew install k6

# 테스트 실행
k6 run scripts/load/part-2/over_issuance.js
```

## 프로젝트 구조

```
src/main/kotlin/com/example/coupon/
├── api/                    # REST Controllers
├── application/            # Service Layer
├── domain/                 # Entity, Repository
├── infrastructure/
│   ├── cache/              # Redis 관련 구현
│   └── messaging/          # Kafka Producer/Consumer
├── batch/                  # Reconciliation 배치
└── support/                # 공통 유틸리티

scripts/load/               # k6 부하 테스트 스크립트
```
