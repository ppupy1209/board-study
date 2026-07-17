// [보관용 - 변경 전 baseline 스크립트]
//
// 이 스크립트는 변경 전 기준선을 다시 재현할 때만 쓴다. 새 테스트는 아래 스위트를 사용한다.
//   smoke.js / average-load.js / breakpoint.js / deep-pagination.js
//   mixed-workload.js / spike.js / soak.js / kafka-recovery.js
//
// 이 스크립트의 한계 (그래서 스위트를 새로 만들었다):
//   - 도착률이 아닌 VU 기반이라 목표 처리량을 지정할 수 없다
//   - p99, endpoint별 태그, dropped_iterations 판정이 없다
//   - 조회 전용이라 쓰기/이벤트 경로를 검증하지 못한다

import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:9000";

export const options = {
  scenarios: {
    regular_list: {
      executor: "ramping-vus",
      exec: "regularList",
      startVUs: 0,
      stages: [
        { duration: "10s", target: 25 },
        { duration: "30s", target: 100 },
        { duration: "10s", target: 0 },
      ],
    },
    deep_page: {
      executor: "constant-arrival-rate",
      exec: "deepPage",
      rate: 5,
      timeUnit: "1s",
      duration: "40s",
      preAllocatedVUs: 10,
      maxVUs: 50,
      startTime: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{scenario:regular_list}": ["p(95)<500"],
    "http_req_duration{scenario:deep_page}": ["p(95)<1000"],
    checks: ["rate>0.99"],
  },
};

function verify(response) {
  check(response, {
    "status is 200": (result) => result.status === 200,
    "response has articles": (result) => {
      try {
        return Array.isArray(result.json("articles"));
      } catch (_) {
        return false;
      }
    },
  });
}

export function regularList() {
  verify(http.get(`${baseUrl}/v1/articles?boardId=1&page=1&pageSize=30`));
  sleep(0.2);
}

export function deepPage() {
  const page = 90000 + (__ITER % 10000);
  verify(http.get(`${baseUrl}/v1/articles?boardId=1&page=${page}&pageSize=30`));
}
