// 브라우저에 남아 있던 이전 Refresh Token이 탈취·재사용되는 상황을 재현한다.
// 같은 Token으로 동시에 갱신하면 한 요청이 먼저 회전한 뒤, 뒤늦은 요청이
// 재사용을 감지해 방금 발급된 Token까지 같은 패밀리 전체를 폐기해야 한다.
//
// 실행 예시:
// TEST_ID=auth-replay-local \
// docker compose run --rm k6 run /scripts/auth-refresh-replay.js

import http from "k6/http";
import { check, fail } from "k6";
import { urls, baseTags, summaryTrendStats } from "./config.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "auth-refresh-replay";
const REFRESH_COOKIE = "MODU_REFRESH";
const PASSWORD = "replay-scenario-password";

export const options = {
  scenarios: {
    replayAttack: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      maxDuration: "30s",
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    checks: ["rate==1"],
  },
};

export function setup() {
  const email = `replay-${Date.now()}@modusquare.test`;
  const request = JSON.stringify({
    email,
    password: PASSWORD,
    displayName: "replay-scenario",
  });
  const headers = { "Content-Type": "application/json" };

  const register = http.post(`${urls.auth}/v1/auth/members`, request, {
    headers,
    tags: { name: "auth_register" },
  });
  if (register.status !== 201) {
    fail(`회원 생성 실패: status=${register.status}, body=${register.body}`);
  }

  const login = http.post(
    `${urls.auth}/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers, tags: { name: "auth_login" } }
  );
  const originalRefreshToken = refreshTokenFrom(login);
  if (login.status !== 200 || !originalRefreshToken) {
    fail(`로그인 실패: status=${login.status}, body=${login.body}`);
  }

  return { originalRefreshToken };
}

export default function (data) {
  const request = refreshRequest(data.originalRefreshToken, "auth_refresh_race");

  // 동일 Token을 거의 동시에 두 번 제출해 정상적인 클라이언트 갱신과
  // 탈취 Token 재사용을 서버가 요청 순서만으로 구분할 수 없는 상황을 만든다.
  const responses = http.batch([request, request]);
  const success = responses.find((response) => response.status === 200);
  const reuseDetected = responses.find(
    (response) => response.status === 401 && errorCode(response) === "refresh_token_reuse_detected"
  );

  check(responses, {
    "동시 갱신 중 한 건만 성공": () => responses.filter((response) => response.status === 200).length === 1,
    "이전 Token 재사용 탐지": () => Boolean(reuseDetected),
  });

  const successorRefreshToken = success ? refreshTokenFrom(success) : null;
  if (!successorRefreshToken) {
    fail("성공 응답에서 회전된 Refresh Token 쿠키를 찾지 못했습니다.");
  }

  const afterDetection = http.post(
    `${urls.auth}/v1/auth/refresh`,
    null,
    requestOptions(successorRefreshToken, "auth_refresh_after_replay")
  );
  check(afterDetection, {
    "재사용 탐지 후 신규 Token도 폐기":
      (response) => response.status === 401 && errorCode(response) === "refresh_token_revoked",
    "실패 응답에서 Refresh Token 쿠키 제거":
      (response) => (response.headers["Set-Cookie"] || "").includes("Max-Age=0"),
  });
}

function refreshRequest(refreshToken, name) {
  return [
    "POST",
    `${urls.auth}/v1/auth/refresh`,
    null,
    requestOptions(refreshToken, name),
  ];
}

function requestOptions(refreshToken, name) {
  return {
    headers: { Cookie: `${REFRESH_COOKIE}=${refreshToken}` },
    responseCallback: http.expectedStatuses(200, 401),
    tags: {
      name,
      endpoint: "POST /v1/auth/refresh",
      workload: TEST_TYPE,
    },
  };
}

function refreshTokenFrom(response) {
  const cookies = response.cookies[REFRESH_COOKIE] || [];
  return cookies.length > 0 ? cookies[0].value : null;
}

function errorCode(response) {
  try {
    return response.json("code");
  } catch (_) {
    return null;
  }
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
