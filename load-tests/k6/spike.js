// Spike test
//
// 목적: 짧은 급증 트래픽에서 오류율과 p99가 어떻게 변하고, 급증이 끝난 뒤 정상 p95로
//       돌아오기까지 얼마나 걸리는지 본다.
//
// 형태 (breakpoint에서 얻은 안정 처리량 STABLE_RATE 기준):
//   STABLE의 30%로 3분 -> STABLE의 100~120%로 1분 -> 다시 30%로 5분
//
// 실행 (STABLE_RATE는 breakpoint 실측값으로 넣는다):
//   STABLE_RATE=400 docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/spike.js

import { urls, boardId, pageSize, baseTags, summaryTrendStats, slo, envNum, envStr, vuPool } from "./config.js";
import { get, checkJsonArray } from "./helpers/http.js";
import { realisticPage } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "spike";

// breakpoint 실측 안정 처리량. 기본값은 임의값이 아니라 반드시 실측으로 덮어써야 한다.
const STABLE_RATE = envNum("STABLE_RATE", 200);
const SPIKE_RATIO = envNum("SPIKE_RATIO", 1.1);
const BASE_DURATION = envStr("BASE_DURATION", "3m");
const SPIKE_DURATION = envStr("SPIKE_DURATION", "1m");
const RECOVER_DURATION = envStr("RECOVER_DURATION", "5m");

const baseRate = Math.max(1, Math.round(STABLE_RATE * 0.3));
const spikeRate = Math.max(1, Math.round(STABLE_RATE * SPIKE_RATIO));

export const options = {
  scenarios: {
    spike: {
      executor: "ramping-arrival-rate",
      startRate: baseRate,
      timeUnit: "1s",
      ...vuPool(spikeRate),
      stages: [
        // 평상시
        { target: baseRate, duration: BASE_DURATION },
        // 급증 (거의 즉시 올린다)
        { target: spikeRate, duration: "10s" },
        { target: spikeRate, duration: SPIKE_DURATION },
        // 급감 후 회복 관찰
        { target: baseRate, duration: "10s" },
        { target: baseRate, duration: RECOVER_DURATION },
      ],
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    // spike 구간에서 SLO가 깨지는 것 자체는 관찰 대상이므로 abort하지 않는다.
    http_req_failed: [`rate<${slo.httpFailRate}`],
    checks: [`rate>${slo.checkRate}`],
  },
};

export default function () {
  const res = get(
    `${urls.article}/v1/articles?boardId=${boardId}&page=${realisticPage()}&pageSize=${pageSize}`,
    { name: "spike_list", endpoint: "GET /v1/articles", workload: TEST_TYPE }
  );
  checkJsonArray(res, "articles", "spike_list");
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
