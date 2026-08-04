# 존재하지 않는 게시글 반복 조회의 캐시 관통 방지

## 문제

게시글 상세 조회는 Redis 조회 모델을 먼저 확인하고, 값이 없으면 Article Service에서 원본 게시글을 조회한다. 존재하지 않는 게시글은 원본에도 값이 없으므로 Redis에 아무것도 저장하지 않았다.

오래된 링크, 삭제된 게시글 URL, 봇의 임의 ID 요청이 같은 ID로 반복되면 매 요청이 Redis를 그대로 통과해 Article Service와 MySQL까지 전달된다. 캐시를 두었지만 `값이 없음`을 저장하지 않아 원본 부하를 줄이지 못하는 캐시 관통 상황이다.

## 변경 전 재현

동일한 존재하지 않는 게시글 ID를 Article Read Service에 100 req/s로 30초간 요청했다.

| 지표 | 변경 전 |
|:---:|:---:|
| 외부 요청 | 3,001건 |
| Article Service 원본 조회 | 3,001건 |
| p95 | 9.75ms |
| p99 | 16.29ms |
| 응답 | 500 |

존재 여부를 확인하기 전 댓글 수와 좋아요 수도 병렬 조회하던 구조라, 잘못된 ID 하나가 여러 서비스 호출로 번질 수 있었다.

## 기술 선택

- Bloom Filter — 제외
  - 존재 가능성을 빠르게 거를 수 있지만 데이터 추가·삭제에 맞춘 동기화와 재구축 경로가 필요
  - 게시글 ID 부재만 막으려는 현재 범위에는 운영 복잡도가 과도

- 매 요청을 분산 락으로 직렬화 — 제외
  - 최초 동시 요청까지 한 번으로 합칠 수 있지만 잠금 획득·해제와 장애 시 만료 정책을 추가로 관리해야 함
  - 부재 캐시 적용 후 원본 호출이 3,001건에서 2건으로 줄어 현재 단계에서는 이득보다 복잡도가 큼

- 짧은 부재 캐시 — 채택
  - Article Service가 404를 반환한 ID에 `MISSING` 값을 Redis에 저장
  - 60초에 0~10초 지터를 더한 TTL로 오래된 부재 정보와 동시 만료 위험을 함께 제한
  - 게시글 생성 이벤트에서는 같은 ID의 부재 표시를 제거하고, 삭제 이벤트에서는 미리 부재 표시 저장

## 구현

```java
if (missingArticleCacheRepository.isMissing(articleId)) {
    articleMissingCacheMetrics.hit();
    throw articleNotFound(articleId);
}

Optional<ArticleQueryModel> loaded = fetch(articleId);
if (loaded.isEmpty()) {
    missingArticleCacheRepository.markMissing(articleId);
    articleMissingCacheMetrics.stored();
    throw articleNotFound(articleId);
}
```

원본 게시글의 404만 부재로 저장한다. 타임아웃이나 5xx 응답은 예외로 전파해 원본 서비스 장애를 `게시글 없음`으로 잘못 캐시하지 않는다.

또한 게시글 존재 여부를 먼저 확인하고, 게시글이 있을 때만 댓글 수와 좋아요 수를 병렬 조회하도록 순서를 바꿨다.

## 동일 조건 재검증

| 지표 | 변경 전 | 부재 캐시 적용 후 |
|:---:|:---:|:---:|
| 외부 요청 | 3,001건 | 3,000건 |
| Article Service 원본 조회 | 3,001건 | 2건 |
| 부재 캐시 적중 | 0건 | 2,998건 |
| p95 | 9.75ms | 2.76ms |
| p99 | 16.29ms | 4.06ms |
| 응답 | 500 | 404 |
| dropped iteration | 0건 | 0건 |

- 원본 조회 99.93% 감소
- p95 71.7% 감소
- 첫 요청이 부재 값을 저장하기 전 겹친 요청 때문에 원본 조회 2건 발생
- 분산 락을 추가하면 1건으로 줄일 수 있지만, 3,000건 중 2건만 원본으로 전달되는 현재 결과에서는 추가하지 않음

## 검증 범위와 한계

- 로컬 Docker Compose 환경의 상대 비교이며 운영 최대 처리량을 의미하지 않음
- 한 개의 존재하지 않는 ID에 요청이 집중되는 조건으로 캐시 관통을 재현
- 무작위 ID가 계속 바뀌는 공격은 키 수가 늘 수 있으므로 API Rate Limit과 Redis 메모리 관측이 별도로 필요
- 부재 TTL 동안 생성될 수 있는 동일 ID는 게시글 생성 이벤트로 부재 표시를 제거

참고: [토스 기술 블로그 - 캐시 문제 해결 가이드](https://toss.tech/article/cache-traffic-tip)