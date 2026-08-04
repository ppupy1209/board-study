import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const MEDIA_URL = __ENV.MEDIA_URL || "http://media:9007";
const MODE = (__ENV.UPLOAD_MODE || "direct").toLowerCase();
const RATE = Number(__ENV.RATE || 3);
const DURATION = __ENV.DURATION || "30s";
const TEST_ID = __ENV.TEST_ID || ("media-" + MODE + "-" + Date.now());
const IMAGE = open("/scripts/fixtures/community-photo.png", "b");

const flowDuration = new Trend("media_upload_flow_duration", true);
const uploadedBytes = new Counter("media_uploaded_bytes");
const failures = new Rate("media_upload_failures");

export const options = {
  scenarios: {
    imageUploadBurst: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: Math.max(10, RATE * 3),
      maxVUs: Math.max(30, RATE * 8),
      tags: { testid: TEST_ID, mode: MODE },
    },
  },
  thresholds: {
    media_upload_failures: ["rate<0.01"],
    media_upload_flow_duration: ["p(95)<5000"],
  },
};

function directUpload() {
  const ticketResponse = http.post(
    MEDIA_URL + "/v1/media/uploads/presign",
    JSON.stringify({
      fileName: "community-photo.png",
      contentType: "image/png",
      sizeBytes: IMAGE.byteLength,
    }),
    { headers: { "Content-Type": "application/json" }, tags: { step: "presign" } },
  );
  if (!check(ticketResponse, { "presign 201": (response) => response.status === 201 })) {
    return false;
  }

  const ticket = ticketResponse.json();
  const internalUrl = ticket.uploadUrl.replace("http://localhost:9100", "http://minio:9000");
  const uploadResponse = http.put(internalUrl, IMAGE, {
    headers: { ...ticket.headers, Host: "localhost:9100" },
    tags: { step: "storage-put" },
  });
  if (!check(uploadResponse, { "storage PUT 200": (response) => response.status === 200 })) {
    return false;
  }

  const completeResponse = http.post(
    MEDIA_URL + "/v1/media/uploads/" + ticket.mediaId + "/complete",
    null,
    { tags: { step: "complete" } },
  );
  return check(completeResponse, { "complete 200": (response) => response.status === 200 });
}

function proxyUpload() {
  const response = http.post(
    MEDIA_URL + "/v1/media/uploads/proxy",
    { file: http.file(IMAGE, "community-photo.png", "image/png") },
    { tags: { step: "application-upload" } },
  );
  return check(response, { "proxy upload 201": (result) => result.status === 201 });
}

export default function () {
  const startedAt = Date.now();
  const success = MODE === "proxy" ? proxyUpload() : directUpload();
  flowDuration.add(Date.now() - startedAt, { mode: MODE, testid: TEST_ID });
  failures.add(!success, { mode: MODE, testid: TEST_ID });
  if (success) uploadedBytes.add(IMAGE.byteLength, { mode: MODE, testid: TEST_ID });
  sleep(0.05);
}

export function handleSummary(data) {
  const summary = {
    stdout: JSON.stringify(
      {
        testId: TEST_ID,
        mode: MODE,
        sourceImageBytes: IMAGE.byteLength,
        iterations: data.metrics.iterations?.values?.count ?? 0,
        uploadFailures: data.metrics.media_upload_failures?.values?.rate ?? null,
        flowP95Ms: data.metrics.media_upload_flow_duration?.values?.["p(95)"] ?? null,
        flowAverageMs: data.metrics.media_upload_flow_duration?.values?.avg ?? null,
      },
      null,
      2,
    ) + "\n",
  };
  summary["/results/" + TEST_ID + ".json"] = JSON.stringify(data, null, 2);
  return summary;
}
