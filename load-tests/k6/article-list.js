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
