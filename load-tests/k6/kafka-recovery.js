// Kafka 장애와 Outbox 복구 테스트용 부하
//
// 목적: Kafka가 멈춘 동안에도 쓰기 API가 성공하고 이벤트가 Outbox에 보존되며,
//       Kafka가 돌아온 뒤 backlog가 0으로 수렴하고 Query Model이 정합성을 회복하는지 확인한다.
//
// 이 스크립트는 부하만 만든다. Kafka 중단/복구와 검증은 아래 스크립트가 담당한다.
//   load-tests/kafka-recovery.sh
//
// 이벤트 수를 정확히 세기 위해 게시글 생성만 일정한 속도로 보낸다.
// 생성 대상은 자유게시판 15,000,100건을 오염시키지 않도록 별도 test board다.
//
// 실행:
//   RATE=10 DURATION=6m docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/kafka-recovery.js

import { urls, testBoardId, baseTags, summaryTrendStats, envNum, envStr } from "./config.js";
import { post, checkJsonField } from "./helpers/http.js";
import { uniqueUserId } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "kafka-recovery";
const RATE = envNum("RATE", 10);
const DURATION = envStr("DURATION", "6m");

export const options = {
  scenarios: {
    writes: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    // Kafka가 멈춰도 쓰기 API 자체는 성공해야 한다. 이것이 이 테스트의 핵심 가설이다.
    // 실패하면 threshold가 깨지고, 그 사실이 곧 결과다.
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
  },
};

export default function () {
  const res = post(
    `${urls.article}/v1/articles`,
    {
      title: `kafka recovery test`,
      content: "kafka recovery test content",
      writerId: uniqueUserId(),
      boardId: testBoardId,
    },
    { name: "kr_article_create", endpoint: "POST /v1/articles", workload: TEST_TYPE }
  );
  checkJsonField(res, "articleId", "kr_article_create");
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
