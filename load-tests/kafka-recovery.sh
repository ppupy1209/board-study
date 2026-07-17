#!/usr/bin/env bash
# Kafka 장애 -> Outbox 보존 -> 복구 -> backlog 정상화 -> 정합성 확인
#
# 절차
#   1. 게시글 생성 부하를 시작한다 (별도 test board 사용, 자유게시판은 건드리지 않는다)
#   2. 정상 상태를 관찰한다
#   3. Kafka 컨테이너만 중단한다
#   4. 쓰기 API 응답과 Outbox pending 증가를 관찰한다
#   5. Kafka를 다시 시작한다
#   6. Outbox backlog와 consumer lag이 0으로 수렴하는지 본다
#   7. 원본 게시글 수와 Query Model 반영 결과를 비교한다
#
# 하지 않는 것
#   - 전체 compose 종료
#   - MySQL/Redis volume 삭제
#   - 결과를 맞추기 위한 임의의 DB 수정
#
# 실행: bash load-tests/kafka-recovery.sh

set -uo pipefail
cd "$(dirname "$0")/.."

TEST_ID="${TEST_ID:-kafka-recovery-$(date +%Y%m%d-%H%M%S)}"
TEST_BOARD_ID="${TEST_BOARD_ID:-9001}"
RATE="${RATE:-10}"
DURATION="${DURATION:-6m}"
OUTAGE_SECONDS="${OUTAGE_SECONDS:-60}"
OBSERVE_BEFORE="${OBSERVE_BEFORE:-90}"
PROM="http://localhost:9090"
LOG="load-tests/results/${TEST_ID}-timeline.log"

mkdir -p load-tests/results

say() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

promq() {
  curl -s --data-urlencode "query=$1" "${PROM}/api/v1/query" \
    | python3 -c "import sys,json; r=json.load(sys.stdin)['data']['result']; print(sum(float(x['value'][1]) for x in r) if r else 0)" 2>/dev/null || echo "0"
}

mysqlq() {
  docker compose exec -T mysql mysql -uroot -proot -N -s -e "$1" 2>/dev/null | grep -v Warning
}

snapshot() {
  local label="$1"
  say "--- ${label}"
  say "    outbox pending        : $(promq 'sum(modu_outbox_pending_events)')"
  say "    outbox oldest age (s) : $(promq 'max(modu_outbox_oldest_event_seconds)')"
  say "    kafka consumer lag    : $(promq 'sum(kafka_consumergroup_lag)')"
  say "    ARTICLE_CREATED 소비   : $(promq 'sum(modu_event_consume_total{event_type="ARTICLE_CREATED",result="success"})')"
  say "    test board 게시글 수   : $(mysqlq "SELECT COUNT(*) FROM article.article WHERE board_id=${TEST_BOARD_ID}")"
}

say "=============================================="
say "Kafka 장애/복구 테스트 시작  testid=${TEST_ID}"
say "  test board=${TEST_BOARD_ID}  rate=${RATE}/s  duration=${DURATION}  outage=${OUTAGE_SECONDS}s"
say "=============================================="

BEFORE_ARTICLES=$(mysqlq "SELECT COUNT(*) FROM article.article WHERE board_id=${TEST_BOARD_ID}")
BEFORE_CONSUMED=$(promq 'sum(modu_event_consume_total{event_type="ARTICLE_CREATED",result="success"})')
say "시작 시점: test board 게시글=${BEFORE_ARTICLES}, ARTICLE_CREATED 누적 소비=${BEFORE_CONSUMED}"

# 1. 부하 시작 (백그라운드)
say "부하 시작 (게시글 생성 ${RATE}/s, ${DURATION})"
TEST_ID="$TEST_ID" RATE="$RATE" DURATION="$DURATION" TEST_BOARD_ID="$TEST_BOARD_ID" \
  docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/kafka-recovery.js \
  > "load-tests/results/${TEST_ID}-k6.log" 2>&1 &
K6_PID=$!

# 2. 정상 상태 관찰
say "정상 상태 관찰 ${OBSERVE_BEFORE}초..."
sleep "$OBSERVE_BEFORE"
snapshot "정상 상태 (Kafka 정상)"

# 3. Kafka 중단
say ">>> Kafka 중단 (${OUTAGE_SECONDS}초)"
docker compose stop kafka >> "$LOG" 2>&1
OUTAGE_START=$(date +%s)

sleep $((OUTAGE_SECONDS / 2))
snapshot "장애 중 (중단 $((OUTAGE_SECONDS / 2))초 경과)"

sleep $((OUTAGE_SECONDS - OUTAGE_SECONDS / 2))
snapshot "장애 종료 직전"

# 4. Kafka 복구
say ">>> Kafka 재시작"
docker compose start kafka >> "$LOG" 2>&1
RECOVERY_START=$(date +%s)

# 5. backlog가 0으로 수렴하는지 관찰
#
# 주의: "backlog 정상화"는 Outbox pending이 0으로 떨어진 시점이다.
#       부하가 끝났는지와는 무관하다. 이전 버전은 `! kill -0 $K6_PID`(부하 종료)까지 조건에 넣어,
#       사실상 "부하가 끝난 시각"을 재고 있었다. 그래서 실제로는 80초에 정상화됐는데 183초로 기록됐다.
#       측정 대상과 측정 방법이 어긋난 전형적인 예라 그대로 남겨 둔다.
say "backlog 정상화 관찰 (최대 300초)"
NORMALIZED_AT=""
for i in $(seq 1 60); do
  sleep 5
  PENDING=$(promq 'sum(modu_outbox_pending_events)')
  LAG=$(promq 'sum(kafka_consumergroup_lag)')
  say "    +$((i*5))s  outbox pending=${PENDING}  consumer lag=${LAG}"
  # pending이 0이 된 첫 시점을 정상화로 본다. 부하는 계속 돌고 있어도 상관없다.
  if [ -z "$NORMALIZED_AT" ] && [ "${PENDING%.*}" = "0" ]; then
    NORMALIZED_AT=$(( $(date +%s) - RECOVERY_START ))
    say ">>> backlog 정상화 확인: 복구 후 ${NORMALIZED_AT}초 (Outbox pending = 0)"
  fi
  # 부하까지 끝나고 안정되면 관찰을 멈춘다
  if [ -n "$NORMALIZED_AT" ] && ! kill -0 $K6_PID 2>/dev/null; then
    break
  fi
done

# 6. 부하 종료 대기
wait $K6_PID 2>/dev/null
say "부하 종료"
sleep 20

snapshot "복구 후 최종"

# 7. 정합성 확인
AFTER_ARTICLES=$(mysqlq "SELECT COUNT(*) FROM article.article WHERE board_id=${TEST_BOARD_ID}")
AFTER_CONSUMED=$(promq 'sum(modu_event_consume_total{event_type="ARTICLE_CREATED",result="success"})')
CREATED=$((AFTER_ARTICLES - BEFORE_ARTICLES))
CONSUMED=$(python3 -c "print(int(float('${AFTER_CONSUMED}') - float('${BEFORE_CONSUMED}')))")
FINAL_PENDING=$(promq 'sum(modu_outbox_pending_events)')
PUBLISH_FAILED=$(promq 'sum(modu_outbox_publish_seconds_count{result="failed"})')

# article-read가 반영한 test board 게시글 수 (Query Model)
# key 형식은 article-read의 BoardArticleCountRepository.KEY_FORMAT과 반드시 일치해야 한다.
QM_COUNT=$(docker compose exec -T redis redis-cli GET "article-read::board-article-count::board::${TEST_BOARD_ID}" 2>/dev/null | tr -d '\r')
SOURCE_COUNT=$(mysqlq "SELECT article_count FROM article.board_article_count WHERE board_id=${TEST_BOARD_ID}")

say "=============================================="
say "결과"
say "  생성한 게시글 수            : ${CREATED}"
say "  소비된 ARTICLE_CREATED 수   : ${CONSUMED}  (재시도로 중복될 수 있어 >= 생성 수)"
say "  최종 Outbox pending         : ${FINAL_PENDING}   (목표: 0)"
say "  Outbox publish 실패 누적    : ${PUBLISH_FAILED}  (장애 중 발생분 포함)"
say "  backlog 정상화 시간         : ${NORMALIZED_AT:-미수렴}초  (목표: 120초 이내)"
say "  원본 게시글 수(MySQL 실제 행) : ${AFTER_ARTICLES}"
say "  원본 count(board_article_count): ${SOURCE_COUNT:-없음}"
say "  Query Model(Redis)            : ${QM_COUNT:-없음}   <- 위 두 값과 같아야 정합"
say "  자유게시판 무결성             : $(mysqlq "SELECT COUNT(*) FROM article.article WHERE board_id=1")"
say "=============================================="
if [ "${QM_COUNT:-x}" = "${SOURCE_COUNT:-y}" ] && [ "${QM_COUNT:-x}" = "${AFTER_ARTICLES:-z}" ]; then
  say "정합성: 일치 (중복 소비가 있어도 절대값 반영이라 결과가 같다)"
else
  say "정합성: 불일치 — 원인 확인 필요"
fi
say "타임라인 로그: ${LOG}"
