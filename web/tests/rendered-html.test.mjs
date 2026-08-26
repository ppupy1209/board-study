import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import ts from "../node_modules/typescript/lib/typescript.js";

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
  assert.match(html, />Guest</);
  assert.doesNotMatch(html, /함께 만드는 광장/);
  assert.doesNotMatch(html, /깊은 페이지|독립 서비스|SYSTEM PULSE|Kafka|Redis|Grafana|15M\+/i);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape|react-loading-skeleton/);
});

test("server-renders writing, article detail, community, and popular routes", async () => {
  const [writeResponse, detailResponse, communityResponse, popularResponse] = await Promise.all([
    render("/write"),
    render("/articles/8000000000000000099"),
    render("/community"),
    render("/popular"),
  ]);

  assert.equal(writeResponse.status, 200);
  assert.match(await writeResponse.text(), /<title>새 글 쓰기 — Modu Square<\/title>/);
  assert.equal(detailResponse.status, 200);
  assert.match(await detailResponse.text(), /<title>이야기 — Modu Square<\/title>/);
  assert.equal(communityResponse.status, 200);
  const communityHtml = await communityResponse.text();
  assert.match(communityHtml, /<title>커뮤니티 — Modu Square<\/title>/);
  assert.equal(popularResponse.status, 200);
  assert.match(await popularResponse.text(), /<title>인기글 — Modu Square<\/title>/);
});

test("paginates each topic from its own filtered articles", async () => {
  const source = await readFile(new URL("../lib/article-feed.ts", import.meta.url), "utf8");
  const javascript = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const feed = await import(`data:text/javascript;base64,${Buffer.from(javascript).toString("base64")}`);
  const makeArticle = (articleId, title, content = "오늘 있었던 일을 기록합니다") => ({
    articleId: String(articleId),
    writerId: String(articleId),
    title,
    content,
    createdAt: "2026-08-26T00:00:00",
  });
  const articles = feed.withTags([
    ...Array.from({ length: 8 }, (_, index) => makeArticle(index + 1, `평범한 하루 ${index + 1}`)),
    ...Array.from({ length: 3 }, (_, index) => makeArticle(index + 20, `주말 여행 ${index + 1}`)),
    ...Array.from({ length: 2 }, (_, index) => makeArticle(index + 30, `좋아하는 음악 ${index + 1}`)),
  ]);

  const allPage = feed.paginateArticles(feed.filterArticles(articles, "전체", ""), 1, 6);
  assert.equal(allPage.totalPages, 3);
  assert.equal(allPage.items.length, 6);

  const travelArticles = feed.filterArticles(articles, "여행", "");
  const travelPage = feed.paginateArticles(travelArticles, 1, 6);
  assert.equal(travelPage.totalPages, 1);
  assert.equal(travelPage.items.length, 3);

  const invalidTravelPage = feed.paginateArticles(travelArticles, 2, 6);
  assert.equal(invalidTravelPage.currentPage, 1);
  assert.equal(invalidTravelPage.items.length, 3);
});

test("keeps accessibility, navigation, and social preview contracts", async () => {
  const [page, community, chrome, detail, write, layout, css, packageJson, smallSeed, communityData, likeConsistency, largeSeed] = await Promise.all([
    readFile(new URL("../app/ModuSquareApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/community/CommunityArticlesPage.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/CommunityChrome.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/articles/[articleId]/ArticleDetailPage.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/write/WriteArticlePage.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/globals.css", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../../infra/mysql/init/01-schema.sql", import.meta.url), "utf8"),
    readFile(new URL("../../infra/mysql/migrations/03-community-data.sql", import.meta.url), "utf8"),
    readFile(new URL("../../infra/mysql/migrations/06-community-like-consistency.sql", import.meta.url), "utf8"),
    readFile(new URL("../../infra/mysql/seed/seed-articles.sh", import.meta.url), "utf8"),
  ]);

  assert.match(chrome, /aria-label="게시글 검색"/);
  assert.match(chrome, /href="\/write"/);
  assert.match(chrome, /href="\/community"/);
  assert.match(chrome, /className="main-nav"[\s\S]*href="\/popular">인기글[\s\S]*href="\/community">커뮤니티/);
  assert.match(chrome, /getOrCreateGuestIdentity/);
  assert.match(chrome, />Guest</);
  assert.doesNotMatch(chrome, /YW|\/#community/);
  assert.match(page, /href=\{`\/articles\/\$\{article\.articleId\}`\}/);
  assert.match(page, /popularArticles\.map/);
  assert.match(page, /fetchAllArticles/);
  assert.match(page, /paginateArticles/);
  assert.match(page, /setCurrentPage\(1\)/);
  assert.match(page, /toggleArticleLike/);
  assert.match(page, /aria-pressed=\{likedIds\.has\(article\.articleId\)\}/);
  assert.match(page, /optimistic-lock/);
  assert.match(page, /className="pagination"/);
  assert.doesNotMatch(page, /demoArticles|loadMore|함께 만드는 광장/);
  assert.match(page, /인기글 전체 보기/);
  assert.match(community, /ARTICLE_CATEGORIES\.map/);
  assert.match(community, /filterArticles/);
  assert.match(community, /paginateArticles/);
  assert.match(community, /toggleArticleLike/);
  assert.match(community, /active="community"/);
  assert.match(community, /관심 있는 이야기를 둘러보세요/);
  assert.match(css, /community-browser-intro h1[^}]*white-space:\s*nowrap/);
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
  assert.doesNotMatch(smallSeed, /8000000000000000/);
  assert.match(communityData, /article_like\.article_like/);
  assert.match(communityData, /comment\.comment_v2/);
  assert.match(communityData, /article_view\.article_view_count/);
  assert.match(communityData, /SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5\) tens/);
  assert.match(likeConsistency, /SELECT 51 n UNION ALL SELECT 52/);
  assert.match(likeConsistency, /COUNT\(likes\.article_like_id\)/);
  assert.doesNotMatch(largeSeed, /topic=|sequence=|THEN '[^']* #'/);
});
