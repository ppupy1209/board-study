// Average load test
//
// 목적: 평상시 트래픽에서의 지연과 자원 사용 기준선을 잡는다.
// 계획 기본값은 100 RPS / 10분이다. SLO를 이미 위반하면 RATE를 낮춰 다시 기준선을 찾는다.
//
// 실행:
//   docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/average-load.js
//   RATE=50 DURATION=5m docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/average-load.js

import { urls, boardId, pageSize, baseTags, summaryTrendStats, slo, envNum, envStr, vuPool } from "./config.js";
import { get, checkJsonArray, checkJsonField } from "./helpers/http.js";
import { randomArticleId, realisticPage } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "average-load";
const RATE = envNum("RATE", 100);
const DURATION = envStr("DURATION", "10m");

export const options = {
  scenarios: {
    average: {
      // 목표 처리량 테스트이므로 VU가 아니라 도착률로 제어한다.
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      ...vuPool(RATE),
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    "http_req_duration{name:avg_list}": [`p(95)<${slo.listP95}`, `p(99)<${slo.listP99}`],
    "http_req_duration{name:avg_detail}": [`p(95)<${slo.listP95}`, `p(99)<${slo.listP99}`],
    http_req_failed: [`rate<${slo.httpFailRate}`],
    checks: [`rate>${slo.checkRate}`],
    // 목표 도착률을 만들어내지 못하면 이 결과는 "100 RPS 결과"가 아니다. 반드시 0이어야 한다.
    dropped_iterations: ["count<1"],
  },
};

export default function () {
  const list = get(
    `${urls.article}/v1/articles?boardId=${boardId}&page=${realisticPage()}&pageSize=${pageSize}`,
    { name: "avg_list", endpoint: "GET /v1/articles", workload: TEST_TYPE }
  );
  checkJsonArray(list, "articles", "avg_list");

  const detail = get(`${urls.article}/v1/articles/${randomArticleId()}`, {
    name: "avg_detail",
    endpoint: "GET /v1/articles/{articleId}",
    workload: TEST_TYPE,
  });
  checkJsonField(detail, "articleId", "avg_detail");
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
