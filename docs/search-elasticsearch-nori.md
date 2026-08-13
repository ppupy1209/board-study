# Elasticsearch + Nori 검색 조회 모델 검증

## 결정

게시글 검색은 **Elasticsearch + Nori**로 구현한다. `LIKE '%검색어%'` API는 운영 검색 경로가 아니라 선행 와일드카드의 비용을 같은 데이터와 부하에서 확인하기 위한 기준선으로만 둔다.

선행 `%`가 붙은 LIKE는 일반 B-Tree 인덱스로 문자열 시작점을 찾을 수 없다. `board_id`로 후보를 줄이더라도 결과가 없거나 오래된 글에만 있으면 후보 행의 제목과 본문을 끝까지 읽는다. 반면 Elasticsearch는 Nori로 분석한 토큰의 역색인을 조회하므로 원본 게시글 행을 순차 검사하지 않는다.

## 구성

```text
Article Service ── Transactional Outbox ── Kafka
                                              │
                                              ▼
                                      Search Service
                                              │ upsert/delete by articleId
                                              ▼
                                   Elasticsearch + Nori
```

- `analysis-nori`: `mecab-ko-dic` 기반 한국어 형태소 분석
- Nori `decompound_mode=mixed`: 복합어 원형과 분해 토큰을 함께 색인
- 제목 `^2`, 본문 `^1` 가중치의 `multi_match`
- `articleId`를 문서 ID로 사용해 생성·수정 이벤트 중복 소비에도 같은 최종 상태 유지
- 삭제 이벤트 재처리도 문서 부재라는 같은 최종 상태 유지
- Kafka 레코드는 Elasticsearch 반영 성공 후 수동 acknowledge

## 전체 재구축과 체크포인트

Kafka 보존 기간 이전의 1,500만 건은 이벤트 재생만으로 복구할 수 없다. Search Service가 Article Service의 Keyset API를 5,000건씩 읽어 Bulk API에 넣고, 마지막 `articleId`와 처리 건수를 별도 meta index에 저장한다.

```text
checkpoint 읽기 → article_id < lastArticleId → Bulk upsert
        ▲                                      │
        └──── lastArticleId, indexedCount 저장 ┘
```

중간에 중단되면 마지막 완료 배치부터 계속한다. 전체 적재 중에는 refresh를 끄고 완료 후 `5s`로 복구한 다음 명시적으로 refresh한다.

<!-- elasticsearch-reindex-results -->

## Nori 검색 매핑

```json
{
  "analysis": {
    "analyzer": {
      "korean": {
        "type": "custom",
        "tokenizer": "korean_nori",
        "filter": ["lowercase"]
      }
    },
    "tokenizer": {
      "korean_nori": {
        "type": "nori_tokenizer",
        "decompound_mode": "mixed"
      }
    }
  }
}
```

## 정확성 검증에서 발견한 UTF-8 오류

첫 구현에서는 문서 수가 15,000,112건으로 맞았지만 적중어 검색 결과가 0건이었다. Bulk NDJSON을 문자열로 전송하면서 `application/x-ndjson`의 charset을 생략해 한국어가 `?`로 치환된 것이 원인이었다.

`application/x-ndjson;charset=UTF-8`을 명시하고 완료 조건을 다음처럼 강화했다.

1. Bulk 요청 본문에 한국어가 보존되는 회귀 테스트
2. 재색인 도중 실문서 `_source` 원문 확인
3. 완료 후 4개 적중어와 2개 부재어 결과 검증
4. 체크포인트 처리 건수와 Elasticsearch 실제 문서 수 일치 확인

## 동일 조건 부하 검증

LIKE와 Elasticsearch 모두 15,000,112건, 동일한 저빈도 적중어 4개와 부재어 2개, `limit=20`, `1 request / 30s`, 6분 조건을 사용한다. LIKE 한 요청이 1분 이상 걸릴 수 있어 높은 RPS로 시작하면 MySQL 동시 전체 스캔 포화 실험으로 바뀌므로, 느린 경로도 유실 없이 완료할 수 있는 낮은 도착률을 택했다.

종단 간 p95 외에도 Elasticsearch 응답의 `took`, 검색 서비스 타이머, MySQL row read를 Prometheus에 기록해 애플리케이션과 엔진 내부 비용을 분리한다.

<!-- elasticsearch-search-k6-results -->

## 트레이드오프

- MySQL 커밋과 검색 노출 사이에 Kafka 소비·Elasticsearch refresh 지연이 존재한다.
- 검색 조회 모델의 재구축, 체크포인트, 디스크 용량, JVM과 파일시스템 캐시를 별도로 운영해야 한다.
- 재구축 도중 갱신 이벤트가 이미 지나간 배치를 덮어쓰는 경쟁은 운영 단계에서 versioned index와 alias 전환 또는 external versioning으로 보완해야 한다.
- Nori는 형태소 분석 기반이므로 `LIKE '%문자열%'`와 결과 의미가 완전히 같지 않다. 부분 문자열 요구가 있다면 별도의 edge-ngram 필드 등을 검색 정책으로 설계해야 한다.

운영 복잡도는 늘지만, 한국어 검색 품질을 확장하고 검색 부하를 원본 DB에서 격리하려는 요구에 맞아 Elasticsearch + Nori를 최종 선택했다.
