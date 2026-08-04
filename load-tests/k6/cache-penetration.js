// 존재하지 않는 게시글 반복 조회로 캐시 관통을 재현한다.
//
// 실행 예시:
// TEST_ID=cache-penetration-after CACHE_PENETRATION_RATE=100 \
// docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/cache-penetration.js

import http from "k6/http";
import { check } from "k6";
import {
  urls, baseTags, summaryTrendStats, envNum, envStr, vuPool,
} from "./config.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "cache-penetration";
const RATE = envNum("CACHE_PENETRATION_RATE", 100);
const DURATION = envStr("CACHE_PENETRATION_DURATION", "30s");
const MISSING_ARTICLE_ID = envStr("MISSING_ARTICLE_ID", "9223372036854775000");
const EXPECTED_STATUS = envNum("CACHE_PENETRATION_EXPECTED_STATUS", 404);

export const options = {
  scenarios: {
    repeatedMissingArticle: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      ...vuPool(RATE),
      gracefulStop: "10s",
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    checks: ["rate>0.995"],
    dropped_iterations: ["count<1"],
  },
};

export default function () {
  const response = http.get(
    `${urls.articleRead}/v1/articles/${MISSING_ARTICLE_ID}`,
    {
      responseCallback: http.expectedStatuses(EXPECTED_STATUS),
      tags: {
        name: "missing_article_detail",
        endpoint: "GET /v1/articles/{articleId}",
        workload: TEST_TYPE,
      },
    }
  );

  check(response, {
    [`missing article: status ${EXPECTED_STATUS}`]:
      (result) => result.status === EXPECTED_STATUS,
  });
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}