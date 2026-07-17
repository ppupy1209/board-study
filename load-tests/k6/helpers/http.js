import http from "k6/http";
import { check } from "k6";

// 요청 태그 규칙:
// - name:     Grafana에서 API를 구분하는 저카디널리티 이름. URL 전체나 article ID를 넣지 않는다.
// - endpoint: 경로 패턴(경로 변수는 치환하지 않은 형태).
// - workload: 시나리오 이름.
//
// k6는 태그하지 않으면 URL 전체를 name으로 쓰기 때문에, article ID가 들어간 URL이 그대로
// 지표 label이 되어 카디널리티가 폭발한다. 그래서 모든 요청에 명시적으로 name을 붙인다.

export function get(url, { name, endpoint, workload }) {
  return http.get(url, { tags: { name, endpoint, workload } });
}

export function post(url, body, { name, endpoint, workload }) {
  return http.post(url, JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    tags: { name, endpoint, workload },
  });
}

export function del(url, { name, endpoint, workload }) {
  return http.del(url, null, { tags: { name, endpoint, workload } });
}

/** 응답 코드뿐 아니라 최소한의 응답 schema까지 확인한다. */
export function checkJsonArray(response, field, name) {
  return check(
    response,
    {
      [`${name}: status 200`]: (r) => r.status === 200,
      [`${name}: ${field} is array`]: (r) => {
        try {
          return Array.isArray(field === "" ? r.json() : r.json(field));
        } catch (_) {
          return false;
        }
      },
    },
    { name }
  );
}

export function checkJsonField(response, field, name) {
  return check(
    response,
    {
      [`${name}: status 200`]: (r) => r.status === 200,
      [`${name}: has ${field}`]: (r) => {
        try {
          return r.json(field) !== undefined && r.json(field) !== null;
        } catch (_) {
          return false;
        }
      },
    },
    { name }
  );
}

export function checkStatus(response, name, expected = 200) {
  return check(response, { [`${name}: status ${expected}`]: (r) => r.status === expected }, { name });
}
