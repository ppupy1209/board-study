"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { CommunityFooter, CommunityHeader } from "./CommunityChrome";
import {
  ARTICLE_API,
  HOT_ARTICLE_API,
  Article,
  HotArticle,
  normalizeArticle,
  normalizeArticleTitle,
  publishedAt,
} from "../lib/community-api";
const articleTags = ["일상", "취향", "질문", "동네", "여행"];

const demoArticles: Article[] = [
  {
    articleId: "9014557001",
    title: "비 오는 주말, 집에서 천천히 보기 좋은 영화가 있을까요?",
    content: "따뜻한 차 한 잔과 함께 볼 영화를 찾고 있어요. 잔잔하게 오래 여운이 남는 작품이면 더 좋겠습니다.",
    writerId: "128",
    createdAt: "2026-07-17T08:42:00",
    articleCommentCount: 32,
    articleLikeCount: 148,
    articleViewCount: 1820,
    tag: "질문",
  },
  {
    articleId: "9014557000",
    title: "여름밤, 서울에서 혼자 걷기 좋은 길을 모아봐요",
    content: "한강처럼 넓은 길도 좋지만 조용한 골목과 작은 서점이 이어지는 코스를 더 좋아합니다. 여러분의 산책길은 어디인가요?",
    writerId: "42",
    createdAt: "2026-07-17T08:18:00",
    articleCommentCount: 21,
    articleLikeCount: 96,
    articleViewCount: 934,
    tag: "동네",
  },
  {
    articleId: "9014556999",
    title: "아침을 잘 챙겨 먹게 된 나만의 작은 습관",
    content: "전날 밤 식탁에 컵과 접시를 미리 꺼내두니 바쁜 아침에도 과일 하나는 챙기게 되더라고요. 여러분만의 방법도 궁금해요.",
    writerId: "77",
    createdAt: "2026-07-17T07:51:00",
    articleCommentCount: 18,
    articleLikeCount: 87,
    articleViewCount: 1104,
    tag: "일상",
  },
  {
    articleId: "9014556998",
    title: "요즘 반복해서 듣는 앨범 한 장씩 추천해주세요",
    content: "처음부터 끝까지 순서대로 들을 때 더 좋은 앨범을 찾고 있어요. 장르는 가리지 않습니다.",
    writerId: "304",
    createdAt: "2026-07-17T07:23:00",
    articleCommentCount: 44,
    articleLikeCount: 73,
    articleViewCount: 782,
    tag: "취향",
  },
  {
    articleId: "9014556997",
    title: "여행지에서 우연히 만난 작은 가게를 오래 기억하는 이유",
    content: "유명한 명소보다 주인과 잠깐 이야기를 나눈 빵집이나 책방이 더 선명하게 남곤 해요. 여러분에게도 그런 장소가 있나요?",
    writerId: "16",
    createdAt: "2026-07-17T06:56:00",
    articleCommentCount: 29,
    articleLikeCount: 121,
    articleViewCount: 1542,
    tag: "여행",
  },
];

const categories = ["전체", ...articleTags];

function formatCompact(value = 0) {
  return new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

function enrichArticles(items: Article[], offset = 0) {
  return items.map((article, index) => {
    const normalized = normalizeArticle({
      ...article,
      articleId: String(article.articleId),
      writerId: String(article.writerId),
    });
    return {
      ...normalized,
      tag: normalized.tag ?? articleTags[(index + offset) % articleTags.length],
      articleCommentCount: normalized.articleCommentCount ?? 8 + index * 3,
      articleLikeCount: normalized.articleLikeCount ?? 24 + index * 7,
      articleViewCount: normalized.articleViewCount ?? 320 + index * 91,
    };
  });
}

export function ModuSquareApp() {
  const [category, setCategory] = useState("전체");
  const [query, setQuery] = useState("");
  const [articles, setArticles] = useState(demoArticles);
  const [notice, setNotice] = useState("");
  const [nextPage, setNextPage] = useState(2);
  const [hasLiveArticles, setHasLiveArticles] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());
  const [hotArticles, setHotArticles] = useState<HotArticle[]>([]);

  useEffect(() => {
    const restoreBrowserState = window.setTimeout(() => {
      const requestedQuery = new URLSearchParams(window.location.search).get("q");
      if (requestedQuery) setQuery(requestedQuery);
      try {
        setSavedIds(new Set(JSON.parse(localStorage.getItem("modu-square-saved") ?? "[]") as string[]));
      } catch {
        localStorage.removeItem("modu-square-saved");
      }
    }, 0);

    const controller = new AbortController();
    fetch(`${ARTICLE_API}/v1/articles?boardId=1&page=1&pageSize=12`, {
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) throw new Error("api unavailable");
        return response.json();
      })
      .then((data: { articles?: Article[] }) => {
        if (!data.articles?.length) return;
        setArticles(enrichArticles(data.articles));
        setHasLiveArticles(true);
      })
      .catch(() => undefined);

    const today = new Intl.DateTimeFormat("en-CA", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      timeZone: "Asia/Seoul",
    }).format(new Date()).replaceAll("-", "");
    fetch(`${HOT_ARTICLE_API}/v1/hot-articles/articles/date/${today}`, { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error("hot article api unavailable");
        return response.json();
      })
      .then((items: HotArticle[]) => setHotArticles(items.slice(0, 5).map((item) => ({
        ...item,
        articleId: String(item.articleId),
        title: normalizeArticleTitle(String(item.articleId), item.title),
      }))))
      .catch(() => undefined);
    return () => {
      window.clearTimeout(restoreBrowserState);
      controller.abort();
    };
  }, []);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return articles.filter((article) => {
      const inCategory = category === "전체" || article.tag === category;
      const inQuery = !normalized || `${article.title} ${article.content} modu_${article.writerId}`.toLowerCase().includes(normalized);
      return inCategory && inQuery;
    });
  }, [articles, category, query]);

  const popularArticles = useMemo<HotArticle[]>(() => {
    if (hotArticles.length) return hotArticles;
    return articles.slice(0, 5).map((article) => ({
      articleId: article.articleId,
      title: article.title,
      createdAt: article.createdAt,
    }));
  }, [articles, hotArticles]);

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    if (query.trim()) setCategory("전체");
    setNotice(query.trim() ? `“${query.trim()}” 검색 결과입니다.` : "새로운 이야기부터 보여드리고 있어요.");
  }

  async function loadMore() {
    if (!hasLiveArticles) {
      setNotice("지금은 여기까지예요. 잠시 후 새로운 이야기를 다시 확인해 주세요.");
      return;
    }

    setIsLoadingMore(true);
    try {
      const response = await fetch(`${ARTICLE_API}/v1/articles?boardId=1&page=${nextPage}&pageSize=12`);
      if (!response.ok) throw new Error("api unavailable");
      const data: { articles?: Article[] } = await response.json();
      if (!data.articles?.length) {
        setNotice("모든 이야기를 다 읽었어요. 새로운 글이 올라오면 다시 찾아와 주세요.");
        return;
      }
      const nextArticles = enrichArticles(data.articles, articles.length);
      setArticles((current) => {
        const knownIds = new Set(current.map((article) => article.articleId));
        return [...current, ...nextArticles.filter((article) => !knownIds.has(article.articleId))];
      });
      setNextPage((page) => page + 1);
      setNotice(`${nextArticles.length}개의 이야기를 더 불러왔어요.`);
    } catch {
      setNotice("새로운 이야기를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setIsLoadingMore(false);
    }
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

  return (
    <div className="site-shell">
      <CommunityHeader query={query} onQueryChange={setQuery} onSearchSubmit={submitSearch} active="home" />

      <main id="top">
        <section className="signal-hero" aria-labelledby="hero-title">
          <div>
            <p className="eyebrow"><span /> 누구나 편하게 머무는 열린 커뮤니티</p>
            <h1 id="hero-title">오늘의 생각을 나누고,<br /><em>새로운 취향을 만나요.</em></h1>
            <p className="hero-copy">소소한 일상부터 오래 품어온 질문까지.<br />가볍게 이야기를 시작하고 서로의 세계를 발견해보세요.</p>
          </div>
          <div className="hero-highlights" aria-label="Modu Square에서 나누는 이야기">
            <div><strong>일상</strong><span>오늘 있었던 작은 순간</span></div>
            <div><strong>취향</strong><span>좋아하는 것을 나누는 기쁨</span></div>
            <div><strong>질문</strong><span>서로의 경험에서 찾는 답</span></div>
          </div>
        </section>

        <div className="content-grid">
          <section className="feed-column" id="feed" aria-labelledby="feed-title">
            <div className="section-heading">
              <div><p className="section-kicker">LATEST STORIES</p><h2 id="feed-title">새로운 이야기</h2></div>
              <span className="section-note">방금 올라온 글부터 만나보세요</span>
            </div>
            <div className="category-tabs" role="tablist" aria-label="게시글 카테고리">
              {categories.map((item) => (
                <button key={item} type="button" role="tab" aria-selected={category === item} onClick={() => setCategory(item)}>{item}</button>
              ))}
            </div>
            {notice && <p className="notice" role="status">{notice}</p>}
            <div className="article-list">
              {filtered.map((article, index) => (
                <article className="article-card" key={`${article.articleId}-${index}`}>
                  <div className="vote-rail" aria-label={`좋아요 ${article.articleLikeCount ?? 0}개`}>
                    <span>♡</span><strong>{formatCompact(article.articleLikeCount)}</strong>
                  </div>
                  <Link className="article-body article-link" href={`/articles/${article.articleId}`}>
                    <div className="article-meta"><span className={`tag tag-${index % 5}`}>{article.tag ?? "이야기"}</span><span>modu_{article.writerId}</span><span>·</span><time>{publishedAt(article.createdAt)}</time></div>
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
              {!filtered.length && <div className="empty-state"><strong>아직 이 주제의 이야기가 없어요.</strong><span>다른 키워드로 찾아보거나 첫 글을 시작해보세요.</span></div>}
            </div>
            <button className="more-button" type="button" onClick={loadMore} disabled={isLoadingMore}>{isLoadingMore ? "불러오는 중…" : "이야기 더 보기"} <span>↓</span></button>
          </section>

          <aside className="side-column">
            <section className="side-card popular-card" id="popular">
              <div className="side-title"><div><p>TRENDING NOW</p><h2>지금 인기 있는 이야기</h2></div><span className="live-dot">NOW</span></div>
              <ol>
                {popularArticles.map((article, index) => (
                  <li key={article.articleId}>
                    <strong>{String(index + 1).padStart(2, "0")}</strong>
                    <Link href={`/articles/${article.articleId}`}>
                      <span>{article.title}</span>
                      <small>{hotArticles.length ? "지금 대화가 활발한 이야기" : "새롭게 주목받는 이야기"} <b>→</b></small>
                    </Link>
                  </li>
                ))}
              </ol>
              <Link className="popular-more-link" href="/popular">인기글 전체 보기 <span>→</span></Link>
            </section>

            <section className="side-card community-card" id="community">
              <div className="side-title"><div><p>WELCOME TO MODU SQUARE</p><h2>함께 만드는 광장</h2></div><span className="welcome-badge">환영해요</span></div>
              <p className="community-copy">편안한 대화는 서로를 한 사람으로 존중하는 마음에서 시작됩니다.</p>
              <ul>
                <li><span>01</span><div><strong>다름을 존중해요</strong><small>생각보다 사람을 먼저 바라봐요.</small></div></li>
                <li><span>02</span><div><strong>경험을 솔직하게 나눠요</strong><small>직접 겪은 일과 들은 이야기를 구분해요.</small></div></li>
                <li><span>03</span><div><strong>함께 안전하게 지켜요</strong><small>불편한 글은 반응 대신 신고로 알려주세요.</small></div></li>
              </ul>
              <a href="#feed">첫 이야기 둘러보기 <span>→</span></a>
            </section>

            <section className="quote-card">
              <span className="quote-mark">“</span>
              <blockquote>좋은 커뮤니티는 답보다<br />더 나은 질문을 남깁니다.</blockquote>
              <p>Modu Square community principle</p>
            </section>
          </aside>
        </div>
      </main>
      <CommunityFooter />
    </div>
  );
}
