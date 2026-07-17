# 자유게시판 1,500만 건 성능 테스트 가이드

## 목표

동일한 데이터 규모와 쿼리 조건을 다시 만들 수 있게 하고, “몇 초가 나왔다”보다 왜 차이가 났는지 확인합니다.

## 데이터 적재

기본 목표는 자유게시판(board ID `1`)의 대용량 글 15,000,000건입니다. 초기 100건도 같은 자유게시판에 들어가므로 완료 후 총 글 수는 15,000,100건입니다.

```bash
docker compose up --build
docker compose logs -f seed-articles
```

적재기는 100,000건 단위로 커밋하고 `article.seed_progress`에 완료 건수를 기록합니다. 컨테이너를 중단해도 완료한 배치부터 재개합니다.

```sql
SELECT seeded_count, target_count, updated_at
FROM article.seed_progress
WHERE seed_name = 'free-board-15m';
```

## 쿼리 비교

### 기준 쿼리

```sql
SELECT article_id, title, content, board_id, writer_id, created_at, modified_at
FROM article
WHERE board_id = 1
ORDER BY article_id DESC
LIMIT 30 OFFSET 2999970;
```

### 개선 쿼리

```sql
SELECT a.article_id, a.title, a.content, a.board_id, a.writer_id, a.created_at, a.modified_at
FROM (
  SELECT article_id
  FROM article
  WHERE board_id = 1
  ORDER BY article_id DESC
  LIMIT 30 OFFSET 2999970
) page
JOIN article a ON a.article_id = page.article_id;
```

각 쿼리에서 `EXPLAIN ANALYZE`를 실행하고 다음을 기록합니다.

- 실제 반환 행 수
- 인덱스 스캔 행 수
- 본문 테이블 접근 행 수
- 실행 시간
- MySQL buffer pool warm/cold 여부

기존 실험에서는 약 `3.8s → 0.3s`를 관찰했습니다. 이 값은 하드웨어와 캐시 상태에 종속되므로 새 환경에서는 동일 수치보다 실행 계획의 본문 접근 범위가 30건으로 제한되는지를 먼저 확인합니다.

## k6 부하 테스트

```bash
docker compose --profile loadtest run --rm k6
```

시나리오는 두 가지입니다.

1. 첫 페이지: 100 VU까지 증가, p95 500ms 기준
2. 90,000~99,999 페이지: 초당 5회 도착, p95 1,000ms 기준

Grafana(`http://localhost:3001`)에서 테스트 시간대의 RPS, p95, JVM Heap을 함께 확인합니다. 실패 시 애플리케이션 로그뿐 아니라 MySQL CPU·I/O, buffer pool, 실행 계획을 함께 비교합니다.

### 로컬 통합 검증 기록 — 2026-07-17

Compose 배선과 임계값이 실제로 동작하는지 확인하기 위해 100개 데모 글과 1,000개 축소 시드가 들어간 로컬 볼륨에서 같은 스크립트를 실행했습니다. 총 12,365회 요청, 평균 247 req/s, 실패율 0%, 전체 p95 8.82ms였고 첫 페이지 p95 8.86ms, 깊은 페이지 p95 5.67ms로 모든 임계값을 통과했습니다.

이 수치는 1,500만 건 성능 결과로 해석하지 않습니다. 대용량 데이터의 근거 수치는 위의 `EXPLAIN ANALYZE` 절차와 자유게시판 15,000,100건 볼륨에서 다시 측정하고, 이 기록은 애플리케이션·k6·Prometheus·Grafana 통합 경로가 재현 가능함을 확인한 smoke load test로 구분합니다.
