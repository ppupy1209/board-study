// 동일한 검색어 분포와 도착률로 LIKE 기준선과 Elasticsearch + Nori를 비교한다.
//
// SEARCH_TEST_ENGINE=like TEST_ID=search-like-1 RATE=1 TIME_UNIT=30s DURATION=6m docker compose run --rm k6 run \
//   -o experimental-prometheus-rw /scripts/article-search.js

import { check } from "k6";
import http from "k6/http";
import exec from "k6/execution";
import {
  urls, baseTags, summaryTrendStats, slo, envNum, envStr, vuPool,
} from "./config.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "article-search";
const ENGINE = envStr("SEARCH_TEST_ENGINE", "like");
const RATE = envNum("RATE", 1);
const TIME_UNIT = envStr("TIME_UNIT", "30s");
const DURATION = envStr("DURATION", "6m");
const REQUEST_TIMEOUT = envStr("REQUEST_TIMEOUT", "120s");

const queries = [
  // 반복 시드 문구는 수백만 건이 적중한다. 검색 엔진 비교가 대량 집계·정렬
  // 실험으로 변질되지 않도록 실제 존재하는 저빈도 문구를 사용한다.
  { value: "묶음 알림", kind: "hit" },
  { value: "인기글 부하", kind: "hit" },
  { value: "집중 조회 시나리오", kind: "hit" },
  { value: "반응이 집중", kind: "hit" },
  { value: "초고속전문검색검증", kind: "miss" },
  { value: "존재하지않는희귀검색어", kind: "miss" },
];

export const options = {
  scenarios: {
    search: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: TIME_UNIT,
      duration: DURATION,
      ...vuPool(RATE),
      gracefulStop: REQUEST_TIMEOUT,
    },
  },
  tags: { ...baseTags(TEST_TYPE), engine: ENGINE },
  summaryTrendStats,
  thresholds: {
    http_req_failed: [`rate<${slo.httpFailRate}`],
    checks: [`rate>${slo.checkRate}`],
  },
};

function endpoint(query) {
  const encoded = encodeURIComponent(query);
  if (ENGINE === "like") {
    return `${urls.article}/v1/articles/search/like?boardId=1&q=${encoded}&limit=20`;
  }
  if (ENGINE === "elasticsearch") {
    return `${urls.search}/v1/search/articles?boardId=1&q=${encoded}&limit=20`;
  }
  throw new Error(`Unsupported SEARCH_TEST_ENGINE: ${ENGINE}`);
}

export default function () {
  // __ITER는 VU별 카운터라 느린 엔진이 VU를 더 쓰면 검색어 분포가 달라진다.
  // 시나리오 전체 반복 번호로 고정해 모든 엔진이 정확히 같은 순서로 요청한다.
  const selected = queries[exec.scenario.iterationInTest % queries.length];
  const response = http.get(endpoint(selected.value), {
    timeout: REQUEST_TIMEOUT,
    tags: {
      name: `article_search_${ENGINE}`,
      endpoint: "GET article search",
      workload: TEST_TYPE,
      engine: ENGINE,
      query_kind: selected.kind,
    },
  });

  check(response, {
    "search: status 200": (result) => result.status === 200,
    "search: response is array": (result) => {
      try {
        return Array.isArray(result.json());
      } catch (_) {
        return false;
      }
    },
    "search: hit/miss result is correct": (result) => {
      try {
        const items = result.json();
        return selected.kind === "hit" ? items.length > 0 : items.length === 0;
      } catch (_) {
        return false;
      }
    },
  }, { name: `article_search_${ENGINE}`, engine: ENGINE, query_kind: selected.kind });
}

export function handleSummary(data) {
  return summarize(`${TEST_TYPE}-${ENGINE}`, data);
}
