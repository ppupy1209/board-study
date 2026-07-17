// Soak test
//
// 목적: 오래 부하를 유지했을 때 누수와 열화가 있는지 본다.
//   - JVM heap이 GC로 회수되는지, GC pause가 늘어나는지
//   - Hikari pending이 서서히 늘어나는지
//   - DB/Redis connection이 새는지
//   - Kafka lag이 시간에 따라 누적되는지
//   - 오류율과 p99가 서서히 올라가는지
//
// 계획 기본값은 안정 처리량의 60~70%로 최소 60분이다.
// DURATION은 환경변수로 조정하며, 단축해서 돌린 경우 결과 문서에 그 사실을 반드시 명시한다.
//
// 실행:
//   STABLE_RATE=400 DURATION=60m docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/soak.js

import {
  urls, boardId, pageSize, baseTags, summaryTrendStats, slo, envNum, envStr, vuPool,
} from "./config.js";
import { get, checkJsonArray, checkJsonField } from "./helpers/http.js";
import { randomArticleId, realisticPage } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "soak";

// 주의: STABLE_RATE는 "이 스크립트와 같은 워크로드"로 measured한 값이어야 한다.
//
// breakpoint.js의 안정 처리량(1,000)을 그대로 가져다 쓰면 안 된다. breakpoint는 iteration당
// 요청 1건(목록 조회)이지만, soak는 iteration당 2건이고 그중 상세 조회는 article-read를 거쳐
// 내부 호출 4건으로 번진다(조회 모델 miss 시). iteration당 비용이 5배 이상 다르다.
//
// 실제로 breakpoint의 65%인 650 iteration/s로 돌렸다가 시작부터 용량을 넘겨
// dropped iterations 324,797건이 나왔다. 누수 판정에 쓸 수 없는 데이터였다.
// 안정 처리량은 시스템의 속성이 아니라 (시스템 × 워크로드)의 속성이다.
const STABLE_RATE = envNum("STABLE_RATE", 200);
const SOAK_RATIO = envNum("SOAK_RATIO", 0.65);
const DURATION = envStr("DURATION", "60m");
const rate = Math.max(1, Math.round(STABLE_RATE * SOAK_RATIO));

export const options = {
  scenarios: {
    soak: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration: DURATION,
      ...vuPool(rate),
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    "http_req_duration{name:soak_list}": [`p(95)<${slo.listP95}`, `p(99)<${slo.listP99}`],
    http_req_failed: [`rate<${slo.httpFailRate}`],
    checks: [`rate>${slo.checkRate}`],
    dropped_iterations: ["count<1"],
  },
};

export default function () {
  const list = get(
    `${urls.article}/v1/articles?boardId=${boardId}&page=${realisticPage()}&pageSize=${pageSize}`,
    { name: "soak_list", endpoint: "GET /v1/articles", workload: TEST_TYPE }
  );
  checkJsonArray(list, "articles", "soak_list");

  const detail = get(`${urls.articleRead}/v1/articles/${randomArticleId()}`, {
    name: "soak_detail",
    endpoint: "GET /v1/articles/{articleId} (article-read)",
    workload: TEST_TYPE,
  });
  checkJsonField(detail, "articleId", "soak_detail");
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
