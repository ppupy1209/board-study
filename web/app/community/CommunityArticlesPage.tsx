"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { CommunityFooter, CommunityHeader, CommunityPage } from "../CommunityChrome";
import {
  Article,
  ArticleLikeStatus,
  fetchAllArticles,
  getOrCreateGuestIdentity,
  LIKE_API,
  publishedAt,
  requestJson,
} from "../../lib/community-api";
import {
  ARTICLE_CATEGORIES,
  filterArticles,
  paginateArticles,
  paginationItems,
  withTags,
} from "../../lib/article-feed";

const PAGE_SIZE = 6;

function formatCompact(value = 0) {
  return new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

export function CommunityArticlesPage() {
  const [category, setCategory] = useState("전체");
  const [query, setQuery] = useState("");
  const [articles, setArticles] = useState<Article[]>([]);
  const [notice, setNotice] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());
  const [guestUserId, setGuestUserId] = useState("");
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set());
  const [checkedLikeIds, setCheckedLikeIds] = useState<Set<string>>(new Set());
  const [busyLikeIds, setBusyLikeIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    const restoreBrowserState = window.setTimeout(() => {
      const requestedQuery = new URLSearchParams(window.location.search).get("q");
      if (requestedQuery) setQuery(requestedQuery);
      setGuestUserId(getOrCreateGuestIdentity().userId);
      try {
        setSavedIds(new Set(JSON.parse(localStorage.getItem("modu-square-saved") ?? "[]") as string[]));
      } catch {
        localStorage.removeItem("modu-square-saved");
      }
    }, 0);
    return () => window.clearTimeout(restoreBrowserState);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchAllArticles(controller.signal)
      .then((items) => setArticles(withTags(items)))
      .catch((error: Error) => {
        if (error.name !== "AbortError") {
          setArticles([]);
          setNotice("이야기를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, []);

  const filtered = useMemo(
    () => filterArticles(articles, category, query),
    [articles, category, query]
  );
  const page = useMemo(
    () => paginateArticles(filtered, currentPage, PAGE_SIZE),
    [filtered, currentPage]
  );
  const visibleArticleKey = page.items.map((article) => article.articleId).join(",");

  useEffect(() => {
    if (!guestUserId || !visibleArticleKey) return;
    let cancelled = false;
    const articleIds = visibleArticleKey.split(",");

    Promise.all(articleIds.map(async (articleId) => {
      try {
        const status = await requestJson<ArticleLikeStatus>(
          `${LIKE_API}/v1/article-likes/articles/${articleId}/users/${guestUserId}`
        );
        return { articleId, liked: status.liked };
      } catch {
        return null;
      }
    })).then((statuses) => {
      if (cancelled) return;
      setLikedIds((current) => {
        const next = new Set(current);
        statuses.forEach((status) => {
          if (!status) return;
          if (status.liked) next.add(status.articleId);
          else next.delete(status.articleId);
        });
        return next;
      });
      setCheckedLikeIds((current) => {
        const next = new Set(current);
        statuses.forEach((status) => {
          if (status) next.add(status.articleId);
        });
        return next;
      });
    });

    return () => { cancelled = true; };
  }, [guestUserId, visibleArticleKey]);

  function selectCategory(nextCategory: string) {
    setCategory(nextCategory);
    setCurrentPage(1);
    setNotice("");
  }

  function changeQuery(nextQuery: string) {
    setQuery(nextQuery);
    setCurrentPage(1);
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    if (query.trim()) setCategory("전체");
    setCurrentPage(1);
    setNotice(query.trim() ? `“${query.trim()}” 검색 결과입니다.` : "모든 이야기를 보여드리고 있어요.");
  }

  function goToPage(nextPage: number) {
    if (nextPage === page.currentPage || nextPage < 1 || nextPage > page.totalPages) return;
    setNotice("");
    setCurrentPage(nextPage);
    window.requestAnimationFrame(() => document.getElementById("community-feed")?.scrollIntoView({ behavior: "smooth", block: "start" }));
  }

  function toggleSaved(articleId: string) {
    setSavedIds((current) => {
      const next = new Set(current);
      if (next.has(articleId)) {
        next.delete(articleId);
        setNotice("저장한 글에서 제외했어요.");
      } else {
        next.add(articleId);
        setNotice("나중에 읽을 글에 저장했어요.");
      }
      localStorage.setItem("modu-square-saved", JSON.stringify([...next]));
      return next;
    });
  }

  async function toggleArticleLike(articleId: string) {
    if (!guestUserId || !checkedLikeIds.has(articleId) || busyLikeIds.has(articleId)) return;
    const nextLiked = !likedIds.has(articleId);
    const delta = nextLiked ? 1 : -1;

    setLikedIds((current) => {
      const next = new Set(current);
      if (nextLiked) next.add(articleId);
      else next.delete(articleId);
      return next;
    });
    setBusyLikeIds((current) => new Set(current).add(articleId));
    setArticles((items) => items.map((article) => article.articleId === articleId
      ? { ...article, articleLikeCount: Math.max(0, (article.articleLikeCount ?? 0) + delta) }
      : article));

    try {
      await requestJson<void>(`${LIKE_API}/v1/article-likes/articles/${articleId}/users/${guestUserId}/optimistic-lock`, {
        method: nextLiked ? "POST" : "DELETE",
      });
      setNotice(nextLiked ? "이 이야기를 좋아합니다." : "좋아요를 취소했습니다.");
    } catch (cause) {
      setLikedIds((current) => {
        const next = new Set(current);
        if (nextLiked) next.delete(articleId);
        else next.add(articleId);
        return next;
      });
      setArticles((items) => items.map((article) => article.articleId === articleId
        ? { ...article, articleLikeCount: Math.max(0, (article.articleLikeCount ?? 0) - delta) }
        : article));
      setNotice(cause instanceof Error ? cause.message : "좋아요를 반영하지 못했습니다.");
    } finally {
      setBusyLikeIds((current) => {
        const next = new Set(current);
        next.delete(articleId);
        return next;
      });
    }
  }

  return (
    <div className="site-shell">
      <CommunityHeader query={query} onQueryChange={changeQuery} onSearchSubmit={submitSearch} active="community" />
      <CommunityPage className="community-page community-browser-page">
        <section className="community-browser-intro" aria-labelledby="community-title">
          <p className="section-kicker">COMMUNITY</p>
          <h1 id="community-title">관심 있는 이야기를 둘러보세요.</h1>
          <p>일상에서 건진 작은 순간부터 취향과 질문, 동네와 여행 이야기까지 한곳에서 만날 수 있어요.</p>
        </section>

        <section className="feed-column community-browser-feed" id="community-feed" aria-labelledby="community-feed-title">
          <div className="section-heading community-browser-heading">
            <div><p className="section-kicker">ALL STORIES</p><h2 id="community-feed-title">커뮤니티 이야기</h2></div>
            <span className="section-note">{filtered.length.toLocaleString("ko-KR")}개의 이야기</span>
          </div>
          <div className="category-tabs" role="tablist" aria-label="게시글 카테고리">
            {ARTICLE_CATEGORIES.map((item) => (
              <button key={item} type="button" role="tab" aria-selected={category === item} onClick={() => selectCategory(item)}>{item}</button>
            ))}
          </div>
          {notice && <p className="notice" role="status">{notice}</p>}
          <div className="article-list" aria-busy={isLoading}>
            {isLoading && <div className="empty-state"><strong>이야기를 불러오고 있어요.</strong><span>잠시만 기다려 주세요.</span></div>}
            {!isLoading && page.items.map((article, index) => (
              <article className="article-card" key={article.articleId}>
                <button
                  type="button"
                  className={`vote-rail ${likedIds.has(article.articleId) ? "liked" : ""}`}
                  aria-label={`${article.title} ${likedIds.has(article.articleId) ? "좋아요 취소" : "좋아요"}, 현재 ${article.articleLikeCount ?? 0}개`}
                  aria-pressed={likedIds.has(article.articleId)}
                  disabled={!guestUserId || !checkedLikeIds.has(article.articleId) || busyLikeIds.has(article.articleId)}
                  onClick={() => toggleArticleLike(article.articleId)}
                >
                  <span aria-hidden="true">{likedIds.has(article.articleId) ? "♥" : "♡"}</span>
                  <strong>{formatCompact(article.articleLikeCount)}</strong>
                </button>
                <Link className="article-body article-link" href={`/articles/${article.articleId}`}>
                  <div className="article-meta"><span className={`tag tag-${index % 5}`}>{article.tag ?? "이야기"}</span><span>modu_{article.writerId}</span><time>{publishedAt(article.createdAt)}</time></div>
                  <h3>{article.title}</h3>
                  <p>{article.content}</p>
                  <div className="article-stats"><span>◌ {formatCompact(article.articleCommentCount)} 대화</span><span>◎ {formatCompact(article.articleViewCount)} 읽음</span></div>
                </Link>
                <button
                  type="button"
                  className={`save-button ${savedIds.has(article.articleId) ? "saved" : ""}`}
                  aria-label={`${article.title} ${savedIds.has(article.articleId) ? "저장 해제" : "저장"}`}
                  aria-pressed={savedIds.has(article.articleId)}
                  onClick={() => toggleSaved(article.articleId)}
                >{savedIds.has(article.articleId) ? "✓" : "＋"}</button>
              </article>
            ))}
            {!isLoading && !filtered.length && <div className="empty-state"><strong>아직 이 주제의 이야기가 없어요.</strong><span>다른 주제를 살펴보거나 첫 글을 시작해보세요.</span></div>}
          </div>
          <nav className="pagination" aria-label="게시글 페이지">
            <button type="button" onClick={() => goToPage(page.currentPage - 1)} disabled={page.currentPage === 1}>이전</button>
            {paginationItems(page.currentPage, page.totalPages).map((item, index) => item === "gap"
              ? <span className="pagination-gap" key={`gap-${index}`}>...</span>
              : <button key={item} type="button" className={item === page.currentPage ? "active" : ""} aria-current={item === page.currentPage ? "page" : undefined} onClick={() => goToPage(item)}>{item}</button>
            )}
            <button type="button" onClick={() => goToPage(page.currentPage + 1)} disabled={page.currentPage === page.totalPages}>다음</button>
          </nav>
        </section>
      </CommunityPage>
      <CommunityFooter />
    </div>
  );
}
