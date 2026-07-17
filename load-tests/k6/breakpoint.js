// Stress / Breakpoint test
//
// 목적: SLO를 만족하는 최대 안정 처리량과, 처음 SLO가 깨지는 임계 처리량을 찾는다.
//
// 단계별로 도착률을 올리며 각 단계를 유지한다. 단계 경계는 Grafana에서 stage 태그로 구분한다.
// iteration당 요청 수가 1이 아니면 RPS와 iteration/s가 달라지므로, 이 스크립트는
// iteration당 요청 1건으로 맞춰 두 값이 같아지게 했다.
//
// 주의: 오류율이 급증하거나 머신이 응답하지 않으면 즉시 중단하고 원인을 먼저 본다.
//
// 실행:
//   docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/breakpoint.js
//   STAGES=50,100,200,400 STAGE_DURATION=2m docker compose run --rm k6 run ... /scripts/breakpoint.js

import { urls, boardId, pageSize, baseTags, summaryTrendStats, slo, envStr, vuPool } from "./config.js";
import { get, checkJsonArray } from "./helpers/http.js";
import { realisticPage } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "breakpoint";

const STAGES = envStr("STAGES", "50,100,200,400,600,800,1000")
  .split(",")
  .map((s) => Number(s.trim()))
  .filter((n) => n > 0);

const STAGE_DURATION = envStr("STAGE_DURATION", "3m");
const WARMUP = envStr("WARMUP", "30s");

// 각 단계를 별도 시나리오로 만들어 startTime으로 순차 실행한다.
// 이렇게 해야 단계별 p95/p99를 태그로 분리해 판정할 수 있다.
function buildScenarios() {
  const scenarios = {};
  const stageSeconds = parseDuration(STAGE_DURATION);
  const warmupSeconds = parseDuration(WARMUP);
  let offset = 0;

  for (const rate of STAGES) {
    scenarios[`rate_${rate}`] = {
      executor: "constant-arrival-rate",
      exec: "browse",
      rate,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      ...vuPool(rate),
      startTime: `${offset}s`,
      // 단계 태그. 워밍업 구간과 유지 구간을 구분하지 않고 단계 전체를 하나로 본다.
      tags: { stage: String(rate) },
      gracefulStop: "10s",
    };
    offset += stageSeconds + warmupSeconds;
  }
  return scenarios;
}

function parseDuration(d) {
  const m = /^(\d+)(s|m)$/.exec(d);
  if (!m) return 180;
  return m[2] === "m" ? Number(m[1]) * 60 : Number(m[1]);
}

// 단계별 threshold. abortOnFail은 쓰지 않는다.
// 어느 단계에서 처음 깨지는지가 이 테스트의 결과물이므로, 깨져도 끝까지 돌려 기록한다.
function buildThresholds() {
  const t = {
    http_req_failed: [`rate<${slo.httpFailRate}`],
  };
  for (const rate of STAGES) {
    t[`http_req_duration{stage:${rate}}`] = [`p(95)<${slo.listP95}`, `p(99)<${slo.listP99}`];
    t[`http_req_failed{stage:${rate}}`] = [`rate<${slo.httpFailRate}`];
  }
  return t;
}

export const options = {
  scenarios: buildScenarios(),
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: buildThresholds(),
};

export function browse() {
  const res = get(
    `${urls.article}/v1/articles?boardId=${boardId}&page=${realisticPage()}&pageSize=${pageSize}`,
    { name: "bp_list", endpoint: "GET /v1/articles", workload: TEST_TYPE }
  );
  checkJsonArray(res, "articles", "bp_list");
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
