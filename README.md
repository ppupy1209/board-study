
# 모두의 광장

> 일상부터 질문까지, 주제에 경계 없이 자유롭게 소통하는 1,500만 건 규모 이벤트 기반 커뮤니티

모두의 광장은 강의 코드를 따라 작성한 흔적에 머물지 않고, 실제 사용자가 보는 자유게시판 UI·한 번에 실행되는 로컬 인프라·재현 가능한 대용량 데이터·부하 테스트·관측성까지 갖춘 서비스형 프로젝트입니다.

- Web UI: `http://localhost:3000`
- Grafana: `http://localhost:3001` (`admin` / `admin`, 로컬 전용)
- Prometheus: `http://localhost:9090`
- Article API: `http://localhost:9000`
- Media API: `http://localhost:9007`
- MinIO Console: `http://localhost:9101` (`modu-square` / `local-development`, 로컬 전용)

## 3분 실행

```bash
docker compose up --build
```

기본 웹 포트는 `3000`이다. 다른 프로그램이 사용 중이면 저장소 루트의 `.env`에 `WEB_PORT=3002`처럼 지정한다.

실행 직후 자유게시판(board ID `1`)의 **100건 데모 데이터**로 UI와 API를 바로 사용할 수 있습니다. 기본 실행에서는 대용량 적재기를 시작하지 않습니다.

1,500만 건 테스트 데이터는 아래 명령으로 **로컬 MySQL 볼륨에 한 번만** 적재합니다.

```bash
# 최초 1회: 자유게시판을 총 15,000,100건 규모로 확장
docker compose --profile large-data run --rm seed-large

# 1,500만 건 적재 완료 후 부하 테스트 (배선 확인용 smoke)
docker compose run --rm k6 run /scripts/smoke.js
```

### 부하 테스트 스위트

k6 결과를 Prometheus에 적재해 애플리케이션 지표와 같은 시간축에서 보려면 `-o experimental-prometheus-rw`를 붙입니다.

```bash
# 배선 확인 (1분). 실패하면 더 큰 부하를 실행하지 않습니다.
TEST_ID=smoke-1 docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/smoke.js

# 평상시 기준선
TEST_ID=avg-1 RATE=100 DURATION=10m docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/average-load.js

# 안정 처리량과 breakpoint 탐색
TEST_ID=bp-1 STAGES=50,100,200,400,600,800,1000 STAGE_DURATION=3m \
  docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/breakpoint.js

# 1,500만 건 깊은 페이지 (page 1 / 100k / 300k / 500k / keyset)
TEST_ID=deep-1 docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/deep-pagination.js

# 실제 사용자 혼합 트래픽 (조회 + 쓰기)
TEST_ID=mixed-1 RATE=100 DURATION=5m docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/mixed-workload.js

# 인기글 Top 10 조회의 호출 증폭과 CQRS 조회 모델 비교
TEST_ID=hot-1 HOT_RATES=75,100,150,200,250 \
  docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/hot-article-list.js

# 급증 / 장시간 (STABLE_RATE는 breakpoint 실측값을 넣습니다)
TEST_ID=spike-1 STABLE_RATE=1000 docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/spike.js
TEST_ID=soak-1 STABLE_RATE=1000 DURATION=60m docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/soak.js

# Kafka 장애 -> Outbox 보존 -> 복구 -> 정합성 확인 (Kafka 중단/복구까지 자동)
bash load-tests/kafka-recovery.sh
```

Grafana의 **모두의 광장 성능·병목 분석** 대시보드에서 `testid`로 실행을 골라 결과를 확인합니다.

> k6 0.57.0에서 Prometheus remote write output(`experimental-prometheus-rw`)은 아직 **experimental**입니다.
> 출력 이름과 동작이 버전에 따라 바뀔 수 있어 이미지 버전을 `grafana/k6:0.57.0`으로 고정했습니다.
> 또한 k6 텍스트 요약은 **ms**, remote write로 적재되는 `k6_http_req_duration_*`는 **초** 단위입니다.

대용량 데이터는 Compose의 `mysql-data` named volume에 저장됩니다. `docker compose stop`, `restart`, `down`과 컴퓨터 재부팅 후에도 유지되며, 이후 `docker compose up`은 데이터를 다시 넣지 않습니다. **`docker compose down -v`는 볼륨과 1,500만 건을 삭제하므로 사용하지 마세요.** 중간에 적재를 멈춰도 같은 명령을 다시 실행하면 마지막 10만 건 배치부터 이어집니다.

> **주의**: `src/test` 아래의 `DataInitializer` 계열은 성능 실험용 데이터를 만드는 **수동 도구**입니다.
> `@Test`가 붙어 있어 `./gradlew test`에 딸려 실행되면 자유게시판에 1,200만 건을 추가해 데이터셋을 오염시킵니다.
> 현재는 `@Disabled`로 막아 두었습니다.

## 검증 포인트

| 주제 | 구현 | 확인 위치 |
|---|---|---|
| 대용량 목록 조회 | Covering Index + ID 선조회 JOIN, 무한 스크롤 Keyset | `ArticleRepository`, `load-tests/k6/deep-pagination.js` |
| 이벤트 전달 신뢰성 | Transactional Outbox, **전송 확인 후에만 삭제** + 미발행 재처리 | `common:outbox-message-relay`, `MessageRelayTest` |
| 이벤트 멱등성 | 절대값 반영으로 중복 소비에도 결과 동일 | `QueryModelIdempotencyTest` |
| 조회 모델 분리 | CQRS 읽기 모델 + Redis Sorted Set + Request Collapsing | `service:article-read`, `OptimizedCacheManagerTest` |
| 실시간 인기글 | Kafka 이벤트 + Redis ZSet | `service:hot-article` |
| 관측성 | Actuator + Prometheus + Grafana + MySQL/Redis/Kafka exporter + k6 remote write | `infra/`, `docker-compose.yml` |
| 부하 검증 | smoke/average/breakpoint/deep-page/mixed/spike/soak/kafka-recovery 분리 | `load-tests/k6/` |
| 동시성 정확성 | count 행 원자적 upsert로 deadlock 제거 | `BoardArticleCountRepository`, `ArticleCommentCountRepository`, `ArticleLikeCountRepository` |
| 이미지 전송 비용 | Presigned URL 직접 업로드 + Kafka 비동기 WebP 변환 | `service:media`, `load-tests/k6/media-upload.js` |

개발 과정의 선택 이유와 한계는 [개발 기록](docs/development-log.md), 대용량 실험 재현 절차는 [성능 테스트 가이드](docs/performance-test.md), 인기글 호출 증폭 개선은 [인기글 조회 부하 테스트](docs/hot-article-load-test.md), 이미지 첨부와 전송량 개선은 [이미지 전송 비용 최적화](docs/media-image-delivery.md)에 정리했습니다.

### 실측 요약 (자유게시판 15,000,100건, 로컬 단일 머신)

| 항목 | 실측 |
|---|---|
| 일반 목록 조회 안정 처리량 | **1,000 RPS 이상**에서 p95 3ms, 오류 0% (이 범위에서 한계 미발견) |
| 깊은 페이지 (page 500,000) 단일 요청 | **9.42초** (OFFSET 14,999,970) |
| 같은 위치 Keyset 조회 | **0.0055초** (약 1,700배 차이) |
| 가장 먼저 포화된 자원 | article-service **HikariCP 커넥션 풀**(max 10, pending peak 31) |
| **article-read 상세 조회 팬아웃** | 요청 45만 건 → **내부 호출 178만 건 (1:4)**, Query Model 적중률 ≈ 0% |
| Kafka 60초 장애 | 이벤트 유실 **0건**, backlog 정상화 **25초**, 최종 정합성 일치 |
| spike (300 → 1,100 RPS) | p99 변화 **없음**, 오류 0% |
| soak (50 iter/s, 20분) | 누수·열화 징후 **없음** (heap -15.8%, GC pause -61%) |

> 로컬 단일 머신에서 k6 부하 생성기와 모든 서비스가 CPU를 공유한 결과이며, 운영 환경 최대 처리량이 아닙니다.

---

## 프로젝트 배경

대규모 게시판 시스템에서 자주 사용되는 기능을 직접 구현한 뒤, 서비스 형태로 확장한 프로젝트

게시글, 댓글, 좋아요, 조회수, 인기글 기능을 하나의 애플리케이션에 모두 넣기보다 각각의 책임을 나누어 모듈로 분리  
Kafka 기반 이벤트 처리와 Outbox Pattern을 적용해 서비스 간 데이터 변경 이벤트를 안정적으로 전달하는 구조 실습

단순 CRUD보다 아래 내용에 집중했습니다.

- 멀티 모듈 기반 게시판 구조 설계
- 게시글, 댓글, 좋아요, 조회수 기능 분리
- Kafka 기반 이벤트 발행과 소비
- Outbox Pattern을 통한 이벤트 유실 방지
- Redis를 활용한 조회수 중복 증가 방지
- 이벤트 기반 인기글 계산
- Snowflake 기반 분산 ID 생성

---

## 기술 스택

### Backend

- Java 21
- Spring Boot 3.5.4
- Spring Web
- Spring Data JPA
- Spring Kafka
- Spring Data Redis
- Lombok

### Database / Infra

- MySQL
- Redis
- Kafka

### Build / Test

- Gradle
- JUnit 5

---

## 모듈 구조

```text
modu-square
├── common
│   ├── snowflake
│   ├── data-serializer
│   ├── outbox-message-relay
│   └── event
│
└── service
    ├── article
    ├── article-read
    ├── comment
    ├── hot-article
    ├── like
    └── view
├── web
│   └── Apple Design 원칙을 적용한 커뮤니티 UI
├── infra
│   ├── mysql
│   ├── prometheus
│   └── grafana
└── load-tests
    └── k6
```

### 모듈별 역할

| Module | Description |
|---|---|
| common:snowflake | 분산 환경에서 사용할 고유 ID 생성 |
| common:event | 서비스 간 전달되는 이벤트 타입과 페이로드 정의 |
| common:outbox-message-relay | Outbox Pattern 기반 Kafka 이벤트 발행 |
| service:article | 게시글 생성, 수정, 삭제, 조회 |
| service:comment | 댓글 생성, 삭제, 무한 depth 댓글 처리 |
| service:like | 게시글 좋아요와 좋아요 수 관리 |
| service:view | 게시글 조회수 증가와 중복 조회 방지 |
| service:hot-article | 이벤트 기반 인기글 점수 계산 |
| service:article-read | 게시글 조회 모델 분리 학습용 모듈 |

---

## 핵심 기능

## 1. 게시글 기능

게시글 생성, 수정, 삭제, 단건 조회, 목록 조회 구현  
게시글 목록은 일반 페이지 조회와 무한 스크롤 조회 모두 고려

게시글 생성 시 게시판별 게시글 수 증가 처리  
게시글 생성 이벤트를 Outbox로 발행

```java
@Transactional
public ArticleResponse create(ArticleCreateRequest request) {
    Article article = articleRepository.save(
            Article.create(
                    snowflake.nextId(),
                    request.getTitle(),
                    request.getContent(),
                    request.getBoardId(),
                    request.getWriterId()
            )
    );

    // 행이 없으면 만들고 있으면 증가시키는 것을 한 문장으로 처리한다.
    // "UPDATE 해보고 0건이면 INSERT" 방식은 아직 count 행이 없는 board에 동시 요청이 몰릴 때
    // gap lock 경합으로 deadlock이 발생한다.
    boardArticleCountRepository.increaseOrCreate(request.getBoardId());

    outboxEventPublisher.publish(
            EventType.ARTICLE_CREATED,
            ArticleCreatedEventPayload.builder()
                    .articleId(article.getArticleId())
                    .title(article.getTitle())
                    .content(article.getContent())
                    .boardId(article.getBoardId())
                    .writerId(article.getWriterId())
                    .createdAt(article.getCreatedAt())
                    .modifiedAt(article.getModifiedAt())
                    .boardArticleCount(count(article.getBoardId()))
                    .build(),
            article.getBoardId()
    );

    return ArticleResponse.from(article);
}
```

---

## 2. 댓글 기능

댓글 생성, 삭제, 댓글 수 조회 구현  
일반적인 단일 depth 댓글뿐 아니라 무한 depth 댓글 구조 학습

댓글 생성과 삭제 시 댓글 수 변경 이벤트 발행  

---

## 3. 좋아요 기능

게시글 좋아요, 좋아요 취소, 사용자별 좋아요 여부 조회, 게시글별 좋아요 수 관리 구현

좋아요는 동시에 요청이 많이 들어올 수 있는 기능  
좋아요 이력과 좋아요 수를 분리해 관리

사용자가 이미 좋아요를 눌렀는지 확인하면서도 게시글별 좋아요 수를 빠르게 조회할 수 있는 구조 구성

---

## 4. 조회수 기능

Redis 기반 조회수 처리

같은 사용자가 짧은 시간 안에 같은 게시글을 반복 조회할 경우 조회수가 계속 증가하지 않도록 사용자별 조회 이력 저장

```java
public Long increase(Long articleId, Long userId) {
    if (!articleViewDistributedLockRepository.lock(articleId, userId, TTL)) {
        return articleViewCountRepository.read(articleId);
    }

    Long count = articleViewCountRepository.increase(articleId);

    if (count % BACK_UP_BATCH_SIZE == 0) {
        articleViewCountBackUpProcessor.backUp(articleId, count);
    }

    return count;
}
```

조회수는 Redis에서 먼저 증가 처리  
일정 단위마다 DB에 백업

매 요청마다 DB를 갱신하지 않아도 되도록 구성해 조회가 많은 상황에서 DB 부하 감소

---

## 5. 인기글 기능

게시글, 댓글, 좋아요, 조회수 이벤트 기반 인기글 계산

각 서비스가 인기글 서비스를 직접 호출하지 않고 Kafka 이벤트 발행  
인기글 서비스는 해당 이벤트를 소비해 점수 갱신

```java
public void handleEvent(Event<EventPayload> event) {
    EventHandler<EventPayload> eventHandler = findEventHandler(event);

    if (eventHandler == null) {
        return;
    }

    if (isArticleCreatedOrDeleted(event)) {
        eventHandler.handle(event);
    } else {
        hotArticleScoreUpdater.update(event, eventHandler);
    }
}
```

서비스 간 직접 의존성을 줄이고, 기능별 변경이 다른 서비스에 미치는 영향을 줄이는 구조 구성

---

## Outbox Pattern

Kafka 이벤트 발행은 DB 트랜잭션과 함께 고려 필요

게시글 저장은 성공했지만 Kafka 이벤트 발행이 실패하면 다른 서비스는 게시글 생성 사실을 알 수 없는 문제 발생  
이 문제를 줄이기 위해 Outbox Pattern 적용

```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void createOutbox(OutboxEvent outboxEvent) {
    outboxRepository.save(outboxEvent.getOutbox());
}

@Async("messageRelayPublishEventExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void publishEvent(OutboxEvent outboxEvent) {
    publishEvent(outboxEvent.getOutbox());
}
```

비즈니스 데이터와 이벤트 데이터를 같은 트랜잭션에서 저장  
트랜잭션 커밋 이후 Kafka 이벤트 발행

발행에 실패한 이벤트는 Outbox 테이블에 남기고 스케줄러를 통해 재발행

---

## Message Relay 구조

```text
Service Transaction
  ↓
Domain Event Publish
  ↓
Outbox Save
  ↓
Transaction Commit
  ↓
Kafka Publish
  ├─ 전송 확인 성공 → Outbox Delete
  └─ 실패/타임아웃   → Outbox 유지 → 스케줄러가 재발행
```

**Outbox는 Kafka 전송이 확인된 경우에만 삭제한다.** 전송 결과와 무관하게 삭제하면 Kafka 장애 구간의
이벤트가 그대로 유실된다. 이 동작은 성공/예외/타임아웃/재시도 성공 네 경로를 `MessageRelayTest`로 고정해 두었다.

재시도 때문에 같은 이벤트가 중복 전송될 수 있다(at-least-once). Query Model 핸들러는 payload가 실어 보낸
**절대값**을 반영하고 증분 연산을 하지 않으므로 중복 소비에도 결과가 같다(`QueryModelIdempotencyTest`).

여러 인스턴스가 동시에 같은 이벤트를 처리하지 않도록 shard 단위로 담당 이벤트를 나눈다.

```java
@Scheduled(
        fixedDelay = 10,
        initialDelay = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "messageRelayPublishPendingExecutor"
)
public void publishPendingEvent() {
    AssignedShard assignedShard = messageRelayCoordinator.assignedShards();

    for (Long shard : assignedShard.getShards()) {
        List<Outbox> outboxes =
                outboxRepository.findAllByShardKeyAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        shard,
                        LocalDateTime.now().minusSeconds(10),
                        Pageable.ofSize(100)
                );

        for (Outbox outbox : outboxes) {
            publishEvent(outbox);
        }
    }
}
```

---

## 기술 선택 이유

## 멀티 모듈 구조

게시글, 댓글, 좋아요, 조회수, 인기글은 각각 다른 책임을 가진 기능  
하나의 모듈에 모두 넣을 경우 기능이 늘어날수록 의존성이 복잡해질 수 있다고 판단

공통 기능은 common 모듈로 분리  
서비스 기능은 각각의 모듈로 분리

---

## Kafka

인기글은 여러 서비스의 변경 이벤트를 기반으로 갱신되는 기능  
각 서비스가 인기글 서비스를 직접 호출하면 서비스 간 결합도가 높아지고 장애 전파 가능성 증가

Kafka를 사용해 이벤트를 비동기로 전달하는 구조 구성

---

## Redis

조회수는 요청 빈도가 높고 빠른 응답이 필요한 기능  
매번 DB를 직접 갱신하는 대신 Redis에서 먼저 처리하고 일정 단위로 DB에 백업하는 방식 선택

---

## Snowflake

서비스에서 직접 ID를 생성하기 위해 Snowflake 방식 사용  
DB Auto Increment에 의존하지 않고 분산 환경에서도 고유 ID를 생성할 수 있는 구조 학습

---

## 학습한 내용

- 멀티 모듈 기반 백엔드 구조
- Kafka 기반 이벤트 처리
- Outbox Pattern
- Redis 기반 조회수 처리
- 서비스 간 결합도를 낮추는 설계
- 이벤트 타입별 Handler 구조
- 인기글 계산 방식
- Snowflake 기반 ID 생성

---
