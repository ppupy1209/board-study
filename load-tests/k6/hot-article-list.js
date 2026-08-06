// 인기글 목록 집중 트래픽 테스트
//
// 목적:
// - Redis에 저장된 상위 10개 ID를 조회한 뒤 게시글 정보를 가져오는 전체 읽기 경로를 검증한다.
// - 변경 전과 조회 모델 적용 후에 동일한 요청률을 사용해 내부 호출 증폭과 응답 지연을 비교한다.
//
// 실행 예시:
// TEST_ID=hot-before docker compose run --rm k6 run \
//   -o experimental-prometheus-rw /scripts/hot-article-list.js

import {
  urls, baseTags, summaryTrendStats, slo, envNum, envStr, vuPool,
} from "./config.js";
import { get } from "./helpers/http.js";
import { summarize } from "./helpers/summary.js";
import { check } from "k6";

const TEST_TYPE = "hot-article-list";
const RATES = envStr("HOT_RATES", "50,100,200,300")
  .split(",")
  .map((value) => Number(value.trim()))
  .filter((value) => value > 0);
const WARMUP_RATE = envNum("HOT_WARMUP_RATE", 10);
const WARMUP_DURATION = envStr("HOT_WARMUP_DURATION", "30s");
const STAGE_DURATION = envStr("HOT_STAGE_DURATION", "1m");
function parseDuration(value) {
  const match = /^(\d+)(s|m)$/.exec(value);
  if (!match) return 60;
  return match[2] === "m" ? Number(match[1]) * 60 : Number(match[1]);
}

function buildScenarios() {
  const scenarios = {
    warmup: {
      executor: "constant-arrival-rate",
      exec: "readHotArticles",
      rate: WARMUP_RATE,
      timeUnit: "1s",
      duration: WARMUP_DURATION,
      ...vuPool(WARMUP_RATE),
      tags: { stage: "warmup" },
      gracefulStop: "10s",
    },
  };

  let offset = parseDuration(WARMUP_DURATION);
  for (const rate of RATES) {
    scenarios[`rate_${rate}`] = {
      executor: "constant-arrival-rate",
      exec: "readHotArticles",
      rate,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      startTime: `${offset}s`,
      ...vuPool(rate),
      tags: { stage: String(rate) },
      gracefulStop: "10s",
    };
    offset += parseDuration(STAGE_DURATION);
  }

  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    http_req_failed: [`rate<${slo.httpFailRate}`],
    checks: [`rate>${slo.checkRate}`],
  },
};

export function readHotArticles() {
  const response = get(
    `${urls.hotArticle}/v1/hot-articles/articles`,
    {
      name: "hot_article_list",
      endpoint: "GET /v1/hot-articles/articles",
      workload: TEST_TYPE,
    }
  );

  check(response, {
    "hot article list: status 200": (result) => result.status === 200,
    "hot article list: contains 10 items": (result) => {
      try {
        return Array.isArray(result.json()) && result.json().length === 10;
      } catch (_) {
        return false;
      }
    },
  }, { name: "hot_article_list" });
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
