// Smoke test
//
// 목적: 스크립트, 인증 없는 API 경로, article ID 생성 방식, Prometheus 전송이 모두 동작하는지 확인한다.
// 성능을 판정하기 위한 테스트가 아니다. 1~5 RPS로 모든 주요 API를 최소 한 번씩 호출한다.
//
// 이 테스트가 실패하면 더 큰 부하 테스트를 실행하지 않는다.
//
// 실행:
//   docker compose run --rm k6 run /scripts/smoke.js

import { sleep } from "k6";
import { urls, boardId, pageSize, baseTags, testBoardId, summaryTrendStats } from "./config.js";
import { get, post, checkJsonArray, checkJsonField, checkStatus } from "./helpers/http.js";
import { randomArticleId, uniqueUserId } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "smoke";

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-arrival-rate",
      rate: 2,
      timeUnit: "1s",
      duration: "1m",
      preAllocatedVUs: 5,
      maxVUs: 10,
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    // smoke는 배선 확인이 목적이므로 기능이 동작하는지만 본다.
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
  },
};

export default function () {
  const workload = TEST_TYPE;
  const articleId = randomArticleId();
  const userId = uniqueUserId();

  // 1. 목록 조회 (article-service)
  const list = get(`${urls.article}/v1/articles?boardId=${boardId}&page=1&pageSize=${pageSize}`, {
    name: "article_list",
    endpoint: "GET /v1/articles",
    workload,
  });
  checkJsonArray(list, "articles", "article_list");

  // 2. 무한 스크롤 (article-service)
  const scroll = get(
    `${urls.article}/v1/articles/infinite-scroll?boardId=${boardId}&pageSize=${pageSize}`,
    { name: "article_scroll", endpoint: "GET /v1/articles/infinite-scroll", workload }
  );
  checkJsonArray(scroll, "", "article_scroll");

  // 3. 게시글 수 (article-service)
  const count = get(`${urls.article}/v1/articles/boards/${boardId}/count`, {
    name: "article_count",
    endpoint: "GET /v1/articles/boards/{boardId}/count",
    workload,
  });
  checkStatus(count, "article_count");

  // 4. 상세 조회 (article-service)
  const detail = get(`${urls.article}/v1/articles/${articleId}`, {
    name: "article_detail",
    endpoint: "GET /v1/articles/{articleId}",
    workload,
  });
  checkJsonField(detail, "articleId", "article_detail");

  // 5. 조회 모델 조회 (article-read-service)
  const readList = get(
    `${urls.articleRead}/v1/articles?boardId=${boardId}&page=1&pageSize=${pageSize}`,
    { name: "read_list", endpoint: "GET /v1/articles (article-read)", workload }
  );
  checkJsonArray(readList, "articles", "read_list");

  // 6. 조회수 증가 (view-service) -> ARTICLE_VIEWED 이벤트 발생
  const view = post(`${urls.view}/v1/article-views/articles/${articleId}/users/${userId}`, null, {
    name: "view_increase",
    endpoint: "POST /v1/article-views/articles/{articleId}/users/{userId}",
    workload,
  });
  checkStatus(view, "view_increase");

  // 7. 조회수 조회 (view-service)
  const viewCount = get(`${urls.view}/v1/article-views/articles/${articleId}/count`, {
    name: "view_count",
    endpoint: "GET /v1/article-views/articles/{articleId}/count",
    workload,
  });
  checkStatus(viewCount, "view_count");

  // 8. 좋아요 등록/취소 (like-service) -> ARTICLE_LIKED / ARTICLE_UNLIKED 이벤트 발생
  const like = post(
    `${urls.like}/v1/article-likes/articles/${articleId}/users/${userId}/pessimistic-lock-1`,
    null,
    { name: "like_create", endpoint: "POST /v1/article-likes/.../pessimistic-lock-1", workload }
  );
  checkStatus(like, "like_create");

  const likeCount = get(`${urls.like}/v1/article-likes/articles/${articleId}/count`, {
    name: "like_count",
    endpoint: "GET /v1/article-likes/articles/{articleId}/count",
    workload,
  });
  checkStatus(likeCount, "like_count");

  // 9. 댓글 조회 (comment-service)
  const comments = get(
    `${urls.comment}/v2/comments?articleId=${articleId}&page=1&pageSize=10`,
    { name: "comment_list", endpoint: "GET /v2/comments", workload }
  );
  checkStatus(comments, "comment_list");

  // 10. 인기글 조회 (hot-article-service)
  const today = new Date().toISOString().slice(0, 10).replaceAll("-", "");
  const hot = get(`${urls.hotArticle}/v1/hot-articles/articles/date/${today}`, {
    name: "hot_articles",
    endpoint: "GET /v1/hot-articles/articles/date/{dateStr}",
    workload,
  });
  checkJsonArray(hot, "", "hot_articles");

  // 11. 게시글 생성 (article-service) -> ARTICLE_CREATED 이벤트 발생
  //     자유게시판 15,000,100건을 오염시키지 않도록 별도 test board에 넣는다.
  const created = post(
    `${urls.article}/v1/articles`,
    {
      title: `smoke test article`,
      content: "smoke test content",
      writerId: userId,
      boardId: testBoardId,
    },
    { name: "article_create", endpoint: "POST /v1/articles", workload }
  );
  checkJsonField(created, "articleId", "article_create");

  sleep(1);
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
