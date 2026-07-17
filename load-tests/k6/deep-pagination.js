// 1,500만 건 깊은 페이지 테스트
//
// 목적: 페이지 깊이(OFFSET 크기)가 지연에 어떤 영향을 주는지 구간별로 분리해 측정하고,
//       같은 데이터를 keyset(무한 스크롤)으로 읽을 때와 비교한다.
//
// 구간 (pageSize=30 기준):
//   page 1        -> OFFSET 0
//   page 100,000  -> OFFSET 3,000,000
//   page 300,000  -> OFFSET 9,000,000
//   page 500,000  -> OFFSET 14,999,970  (마지막 페이지 부근)
//   keyset        -> OFFSET 없이 article_id 커서로 연속 조회
//
// 부하가 아니라 "쿼리 구조" 차이를 보는 것이 목적이므로 각 구간을 낮은 고정 rate로 돌린다.
// 각 시나리오를 순차 실행해 서로 자원을 뺏지 않게 한다.
//
// 실행:
//   docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/deep-pagination.js

import { urls, boardId, pageSize, baseTags, summaryTrendStats, slo, envStr } from "./config.js";
import { get, checkJsonArray } from "./helpers/http.js";
import { randomInt } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "deep-pagination";

const RATE = Number(envStr("DEEP_RATE", "5"));
const DURATION = envStr("DEEP_DURATION", "1m");
// 구간 사이 간격. 앞 구간의 잔여 요청이 다음 구간 측정에 섞이지 않게 한다.
const GAP_SECONDS = Number(envStr("DEEP_GAP_SECONDS", "20"));

function toSeconds(d) {
  const m = /^(\d+)(s|m)$/.exec(d);
  if (!m) throw new Error(`DEEP_DURATION 형식이 잘못됐다: ${d} (예: 60s, 2m)`);
  return m[2] === "m" ? Number(m[1]) * 60 : Number(m[1]);
}

const STEP_SECONDS = toSeconds(DURATION) + GAP_SECONDS;

// 각 구간을 순차로 실행한다. 앞 구간이 끝난 뒤 다음 구간이 시작된다.
function stage(exec, index) {
  return {
    executor: "constant-arrival-rate",
    exec,
    rate: RATE,
    timeUnit: "1s",
    duration: DURATION,
    preAllocatedVUs: 5,
    maxVUs: 30,
    startTime: `${index * STEP_SECONDS}s`,
    tags: { depth: exec },
    gracefulStop: "15s",
  };
}

export const options = {
  scenarios: {
    page1: stage("page1", 0),
    page100k: stage("page100k", 1),
    page300k: stage("page300k", 2),
    page500k: stage("page500k", 3),
    keyset: stage("keyset", 4),
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    // 구간별로 판정을 분리한다. 전역 p95 하나로는 어느 깊이에서 깨지는지 알 수 없다.
    "http_req_duration{depth:page1}": [`p(95)<${slo.listP95}`],
    "http_req_duration{depth:page100k}": [`p(95)<${slo.deepPageP95}`],
    "http_req_duration{depth:page300k}": [`p(95)<${slo.deepPageP95}`],
    "http_req_duration{depth:page500k}": [`p(95)<${slo.deepPageP95}`],
    "http_req_duration{depth:keyset}": [`p(95)<${slo.listP95}`],
    http_req_failed: [`rate<${slo.httpFailRate}`],
    checks: [`rate>${slo.checkRate}`],
  },
};

function readPage(page, name) {
  const res = get(`${urls.article}/v1/articles?boardId=${boardId}&page=${page}&pageSize=${pageSize}`, {
    name,
    endpoint: "GET /v1/articles",
    workload: TEST_TYPE,
  });
  checkJsonArray(res, "articles", name);
}

export function page1() {
  readPage(1, "deep_page_1");
}

export function page100k() {
  // 같은 페이지만 반복하면 buffer pool에 올라가 실제보다 빨라진다. 구간 내에서 흔든다.
  readPage(randomInt(99000, 101000), "deep_page_100k");
}

export function page300k() {
  readPage(randomInt(299000, 301000), "deep_page_300k");
}

export function page500k() {
  readPage(randomInt(499000, 500000), "deep_page_500k");
}

// keyset(무한 스크롤): OFFSET 없이 article_id 커서로 다음 페이지를 읽는다.
// 커서를 계속 앞으로 밀면서 연속 조회한다.
let cursor = null;

export function keyset() {
  const url =
    cursor === null
      ? `${urls.article}/v1/articles/infinite-scroll?boardId=${boardId}&pageSize=${pageSize}`
      : `${urls.article}/v1/articles/infinite-scroll?boardId=${boardId}&pageSize=${pageSize}&lastArticleId=${cursor}`;

  const res = get(url, {
    name: "deep_page_keyset",
    endpoint: "GET /v1/articles/infinite-scroll",
    workload: TEST_TYPE,
  });
  checkJsonArray(res, "", "deep_page_keyset");

  try {
    const body = res.json();
    if (Array.isArray(body) && body.length > 0) {
      cursor = body[body.length - 1].articleId;
    } else {
      cursor = null; // 끝에 도달하면 처음부터 다시 훑는다
    }
  } catch (_) {
    cursor = null;
  }
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
