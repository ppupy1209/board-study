import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/ssr/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the 모두의 광장 product page", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>모두의 광장/);
  assert.match(html, /주제에 경계 없이/);
  assert.match(html, /15M\+/);
  assert.match(html, /SYSTEM PULSE/);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape|react-loading-skeleton/);
});

test("keeps accessibility and social preview contracts", async () => {
  const [page, layout, css, packageJson] = await Promise.all([
    readFile(new URL("../app/ModuSquareApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/globals.css", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
  ]);

  assert.match(page, /aria-label="게시글 검색"/);
  assert.match(page, /role="status"/);
  assert.match(layout, /\/og\.png/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
  assert.match(css, /prefers-reduced-transparency:\s*reduce/);
  assert.match(css, /prefers-contrast:\s*more/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
});
