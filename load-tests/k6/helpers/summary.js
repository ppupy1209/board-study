import { testId, dataset } from "../config.js";

// 각 실행 결과를 /results 아래에 남긴다. Prometheus remote write와 별개로,
// 실행 직후 숫자를 그대로 문서에 옮길 수 있게 하기 위한 것이다.

function pick(metric) {
  if (!metric || !metric.values) return null;
  const v = metric.values;
  return {
    count: v.count,
    rate: v.rate,
    avg: v.avg,
    med: v.med,
    min: v.min,
    max: v.max,
    "p(95)": v["p(95)"],
    "p(99)": v["p(99)"],
  };
}

/**
 * k6 기본 요약과 함께, 문서화에 필요한 핵심 지표만 추린 JSON을 저장한다.
 * dropped_iterations는 목표 처리량을 만들지 못했다는 뜻이므로 반드시 남긴다.
 */
export function summarize(testType, data) {
  const metrics = data.metrics || {};
  const digest = {
    testid: testId,
    test_type: testType,
    dataset: dataset,
    generated_at: new Date().toISOString(),
    http_reqs: pick(metrics.http_reqs),
    http_req_duration: pick(metrics.http_req_duration),
    http_req_failed: metrics.http_req_failed ? metrics.http_req_failed.values : null,
    iterations: pick(metrics.iterations),
    dropped_iterations: metrics.dropped_iterations ? metrics.dropped_iterations.values : { count: 0 },
    vus_max: metrics.vus_max ? metrics.vus_max.values.max : null,
    checks: metrics.checks ? metrics.checks.values : null,
    thresholds: {},
  };

  // API/시나리오별 판정 결과를 그대로 남긴다.
  for (const [name, metric] of Object.entries(metrics)) {
    if (metric.thresholds) {
      for (const [expr, result] of Object.entries(metric.thresholds)) {
        digest.thresholds[`${name} ${expr}`] = result.ok === false ? "FAIL" : "PASS";
      }
    }
  }

  const out = {};
  out["stdout"] = textSummary(digest);
  out[`/results/${testType}-${testId}.json`] = JSON.stringify(data, null, 2);
  out[`/results/${testType}-${testId}-digest.json`] = JSON.stringify(digest, null, 2);
  return out;
}

function fmt(n, unit = "") {
  if (n === undefined || n === null) return "n/a";
  return `${Math.round(n * 100) / 100}${unit}`;
}

function textSummary(d) {
  const lines = [];
  lines.push("");
  lines.push("================ 실측 요약 ================");
  lines.push(`testid      : ${d.testid}`);
  lines.push(`test_type   : ${d.test_type}`);
  lines.push(`dataset     : ${d.dataset}`);
  if (d.http_reqs) {
    lines.push(`요청 수      : ${d.http_reqs.count}`);
    lines.push(`실제 RPS     : ${fmt(d.http_reqs.rate, " req/s")}`);
  }
  if (d.http_req_duration) {
    lines.push(`p95         : ${fmt(d.http_req_duration["p(95)"], " ms")}`);
    lines.push(`p99         : ${fmt(d.http_req_duration["p(99)"], " ms")}`);
    lines.push(`max         : ${fmt(d.http_req_duration.max, " ms")}`);
  }
  if (d.http_req_failed) {
    lines.push(`HTTP 실패율  : ${fmt(d.http_req_failed.rate * 100, " %")}`);
  }
  if (d.checks) {
    lines.push(`check 성공률 : ${fmt(d.checks.rate * 100, " %")}`);
  }
  lines.push(`dropped_iterations : ${d.dropped_iterations.count || 0}`);
  lines.push(`vus_max     : ${d.vus_max}`);
  lines.push("---- threshold 판정 ----");
  for (const [k, v] of Object.entries(d.thresholds)) {
    lines.push(`  ${v === "FAIL" ? "✗" : "✓"} ${k}`);
  }
  lines.push("==========================================");
  lines.push("");
  return lines.join("\n");
}
