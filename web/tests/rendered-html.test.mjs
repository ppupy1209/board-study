import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render(path = "/") {
  const workerUrl = new URL("../dist/server/ssr/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request(`http://localhost${path}`, { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the Modu Square community page", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Modu Square/);
  assert.match(html, /오늘의 생각을 나누고/);
  assert.match(html, /함께 만드는 광장/);
  assert.doesNotMatch(html, /깊은 페이지|독립 서비스|SYSTEM PULSE|Kafka|Redis|Grafana|15M\+/i);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape|react-loading-skeleton/);
});

test("server-renders writing, article detail, and popular routes", async () => {
  const [writeResponse, detailResponse, popularResponse] = await Promise.all([
    render("/write"),
    render("/articles/8000000000000000099"),
    render("/popular"),
  ]);

  assert.equal(writeResponse.status, 200);
  assert.match(await writeResponse.text(), /<title>새 글 쓰기 — Modu Square<\/title>/);
  assert.equal(detailResponse.status, 200);
  assert.match(await detailResponse.text(), /<title>이야기 — Modu Square<\/title>/);
  assert.equal(popularResponse.status, 200);
  assert.match(await popularResponse.text(), /<title>인기글 — Modu Square<\/title>/);
});

test("keeps accessibility, navigation, and social preview contracts", async () => {
  const [page, chrome, detail, write, layout, css, packageJson, smallSeed, largeSeed] = await Promise.all([
    readFile(new URL("../app/ModuSquareApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/CommunityChrome.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/articles/[articleId]/ArticleDetailPage.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/write/WriteArticlePage.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/globals.css", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../../infra/mysql/init/01-schema.sql", import.meta.url), "utf8"),
    readFile(new URL("../../infra/mysql/seed/seed-articles.sh", import.meta.url), "utf8"),
  ]);

  assert.match(chrome, /aria-label="게시글 검색"/);
  assert.match(chrome, /href="\/write"/);
  assert.match(page, /href=\{`\/articles\/\$\{article\.articleId\}`\}/);
  assert.match(page, /popularArticles\.map/);
  assert.match(page, /HOT_ARTICLE_API/);
  assert.match(page, /인기글 전체 보기/);
  assert.match(detail, /aria-pressed=\{liked\}/);
  assert.match(detail, /이야기를 불러오고 있어요/);
  assert.match(write, /<form className="editor-form"/);
  assert.match(write, /어떤 이야기를 나누고 싶나요/);
  assert.match(page, /role="status"/);
  assert.match(layout, /\/og\.png/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
  assert.match(css, /prefers-reduced-transparency:\s*reduce/);
  assert.match(css, /prefers-contrast:\s*more/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
  assert.doesNotMatch(smallSeed, /topic=|sequence=|THEN '[^']* #'/);
  assert.doesNotMatch(largeSeed, /topic=|sequence=|THEN '[^']* #'/);
});
