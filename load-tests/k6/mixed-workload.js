// 실제 사용자 혼합 트래픽
//
// 목적: 조회와 쓰기가 섞였을 때의 안정 처리량과 API별 지연, 그리고 이벤트 파이프라인
//       (Outbox -> Kafka -> Query Model)의 반영 지연을 함께 본다.
//
// 트래픽 비율 (계획 기준):
//   목록/무한 스크롤 55% / 상세 20% / 조회수 10% / 좋아요 7% / 댓글 5% / 인기글 3%
//
// 깊은 OFFSET 조회는 목적이 달라 여기 넣지 않고 deep-pagination.js로 분리했다.
//
// 실행:
//   RATE=100 DURATION=5m docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/mixed-workload.js

import {
  urls, boardId, pageSize, baseTags, summaryTrendStats, slo, envNum, envStr, vuPool,
} from "./config.js";
import { get, post, del, checkJsonArray, checkJsonField, checkStatus } from "./helpers/http.js";
import { randomArticleId, realisticPage, uniqueUserId, contendedUserId } from "./helpers/data.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "mixed-workload";
const RATE = envNum("RATE", 100);
const DURATION = envStr("DURATION", "5m");

export const options = {
  scenarios: {
    mixed: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      ...vuPool(RATE),
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
  thresholds: {
    // API별로 판정을 분리한다.
    "http_req_duration{name:mx_list}": [`p(95)<${slo.listP95}`, `p(99)<${slo.listP99}`],
    "http_req_duration{name:mx_scroll}": [`p(95)<${slo.listP95}`],
    "http_req_duration{name:mx_detail}": [`p(95)<${slo.listP95}`],
    "http_req_duration{name:mx_view_increase}": [`p(95)<${slo.writeP95}`],
    "http_req_duration{name:mx_like_create}": [`p(95)<${slo.writeP95}`],
    "http_req_duration{name:mx_comment_create}": [`p(95)<${slo.writeP95}`],
    "http_req_duration{name:mx_hot}": [`p(95)<${slo.listP95}`],
    http_req_failed: [`rate<${slo.httpFailRate}`],
    checks: [`rate>${slo.checkRate}`],
    dropped_iterations: ["count<1"],
  },
};

export default function () {
  const r = Math.random();
  const workload = TEST_TYPE;

  if (r < 0.55) {
    // 55% 목록/무한 스크롤
    if (Math.random() < 0.5) {
      const res = get(
        `${urls.article}/v1/articles?boardId=${boardId}&page=${realisticPage()}&pageSize=${pageSize}`,
        { name: "mx_list", endpoint: "GET /v1/articles", workload }
      );
      checkJsonArray(res, "articles", "mx_list");
    } else {
      const res = get(
        `${urls.article}/v1/articles/infinite-scroll?boardId=${boardId}&pageSize=${pageSize}`,
        { name: "mx_scroll", endpoint: "GET /v1/articles/infinite-scroll", workload }
      );
      checkJsonArray(res, "", "mx_scroll");
    }
  } else if (r < 0.75) {
    // 20% 상세 조회 (조회 모델)
    const res = get(`${urls.articleRead}/v1/articles/${randomArticleId()}`, {
      name: "mx_detail",
      endpoint: "GET /v1/articles/{articleId} (article-read)",
      workload,
    });
    checkJsonField(res, "articleId", "mx_detail");
  } else if (r < 0.85) {
    // 10% 조회수 증가
    const res = post(
      `${urls.view}/v1/article-views/articles/${randomArticleId()}/users/${uniqueUserId()}`,
      null,
      { name: "mx_view_increase", endpoint: "POST /v1/article-views/.../users/{userId}", workload }
    );
    checkStatus(res, "mx_view_increase");
  } else if (r < 0.92) {
    // 7% 좋아요 등록/취소
    // 같은 사용자가 같은 글에 중복으로 좋아요를 누르는 상황을 만들기 위해 좁은 user 범위를 공유한다.
    const articleId = randomArticleId();
    const userId = contendedUserId();
    const res = post(
      `${urls.like}/v1/article-likes/articles/${articleId}/users/${userId}/pessimistic-lock-1`,
      null,
      { name: "mx_like_create", endpoint: "POST /v1/article-likes/.../pessimistic-lock-1", workload }
    );
    checkStatus(res, "mx_like_create");

    // 절반은 다시 취소해서 좋아요 수가 무한히 늘지 않게 한다.
    if (Math.random() < 0.5) {
      const unlike = del(
        `${urls.like}/v1/article-likes/articles/${articleId}/users/${userId}/pessimistic-lock-1`,
        { name: "mx_like_delete", endpoint: "DELETE /v1/article-likes/.../pessimistic-lock-1", workload }
      );
      checkStatus(unlike, "mx_like_delete");
    }
  } else if (r < 0.97) {
    // 5% 댓글 등록/조회
    //
    // 댓글은 article DB가 아니라 comment DB에 쌓이므로 자유게시판 게시글 자체는 변경되지 않는다.
    // unique key를 (article_id, path)로 고친 뒤부터 게시글마다 독립적으로 채번되므로,
    // 실제 사용자처럼 여러 게시글에 분산해서 단다.
    const articleId = randomArticleId();
    if (Math.random() < 0.5) {
      const res = post(
        `${urls.comment}/v2/comments`,
        {
          articleId: articleId,
          content: "mixed workload comment",
          writerId: uniqueUserId(),
        },
        { name: "mx_comment_create", endpoint: "POST /v2/comments", workload }
      );
      checkStatus(res, "mx_comment_create");
    } else {
      const res = get(`${urls.comment}/v2/comments?articleId=${articleId}&page=1&pageSize=10`, {
        name: "mx_comment_list",
        endpoint: "GET /v2/comments",
        workload,
      });
      checkStatus(res, "mx_comment_list");
    }
  } else {
    // 3% 인기글 조회
    const today = new Date().toISOString().slice(0, 10).replaceAll("-", "");
    const res = get(`${urls.hotArticle}/v1/hot-articles/articles/date/${today}`, {
      name: "mx_hot",
      endpoint: "GET /v1/hot-articles/articles/date/{dateStr}",
      workload,
    });
    checkJsonArray(res, "", "mx_hot");
  }
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
