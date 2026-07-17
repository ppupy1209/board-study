"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";

type Article = {
  articleId: number;
  title: string;
  content: string;
  writerId: number;
  createdAt: string;
  articleCommentCount?: number;
  articleLikeCount?: number;
  articleViewCount?: number;
  tag?: string;
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:9000";

const demoArticles: Article[] = [
  {
    articleId: 9014557001,
    title: "좋은 시스템은 빠른 시스템보다 오래 설명할 수 있는 시스템이다",
    content: "이번 주 팀 회고에서 장애 대응보다 더 오래 이야기한 건 선택의 이유였습니다. 기록이 남아 있으니 다음 결정은 훨씬 빨랐습니다.",
    writerId: 128,
    createdAt: "2026-07-17T08:42:00",
    articleCommentCount: 32,
    articleLikeCount: 148,
    articleViewCount: 1820,
    tag: "개발",
  },
  {
    articleId: 9014557000,
    title: "여름밤, 서울에서 혼자 걷기 좋은 길을 모아봐요",
    content: "한강처럼 넓은 길도 좋지만 조용한 골목과 작은 서점이 이어지는 코스를 더 좋아합니다. 여러분의 산책 루트는 어디인가요?",
    writerId: 42,
    createdAt: "2026-07-17T08:18:00",
    articleCommentCount: 21,
    articleLikeCount: 96,
    articleViewCount: 934,
    tag: "일상",
  },
  {
    articleId: 9014556999,
    title: "커서 기반 페이지네이션을 적용하며 놓쳤던 한 가지",
    content: "속도만 보고 바꿨다가 임의 페이지 이동이라는 제품 요구를 놓쳤습니다. 결국 목록마다 사용자의 탐색 방식을 먼저 정의했습니다.",
    writerId: 77,
    createdAt: "2026-07-17T07:51:00",
    articleCommentCount: 18,
    articleLikeCount: 87,
    articleViewCount: 1104,
    tag: "테크",
  },
  {
    articleId: 9014556998,
    title: "요즘 반복해서 듣는 앨범 한 장씩 추천해주세요",
    content: "처음부터 끝까지 순서대로 들을 때 더 좋은 앨범을 찾고 있어요. 장르는 가리지 않습니다.",
    writerId: 304,
    createdAt: "2026-07-17T07:23:00",
    articleCommentCount: 44,
    articleLikeCount: 73,
    articleViewCount: 782,
    tag: "취향",
  },
  {
    articleId: 9014556997,
    title: "Kafka 이벤트를 믿을 수 있게 만든 건 화려한 기능이 아니었다",
    content: "Outbox에 남은 실패를 다시 읽고, 소비자는 같은 이벤트를 두 번 받아도 같은 결과를 만들도록 했습니다.",
    writerId: 16,
    createdAt: "2026-07-17T06:56:00",
    articleCommentCount: 29,
    articleLikeCount: 121,
    articleViewCount: 1542,
    tag: "테크",
  },
];

const categories = ["전체", "테크", "일상", "취향", "질문"];

function formatCompact(value = 0) {
  return new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

function publishedAt(date: string) {
  const zonedDate = /(?:Z|[+-]\d{2}:\d{2})$/.test(date) ? date : `${date}+09:00`;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Seoul",
  }).format(new Date(zonedDate));
}

export function ModuSquareApp() {
  const [category, setCategory] = useState("전체");
  const [query, setQuery] = useState("");
  const [articles, setArticles] = useState(demoArticles);
  const [dataMode, setDataMode] = useState<"demo" | "live">("demo");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    fetch(`${API_BASE}/v1/articles?boardId=1&page=1&pageSize=12`, {
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) throw new Error("api unavailable");
        return response.json();
      })
      .then((data: { articles?: Article[] }) => {
        if (!data.articles?.length) return;
        setArticles(data.articles.map((article, index) => ({
          ...article,
          tag: ["일상", "테크", "취향", "질문"][index % 4],
          articleCommentCount: article.articleCommentCount ?? 8 + index * 3,
          articleLikeCount: article.articleLikeCount ?? 24 + index * 7,
          articleViewCount: article.articleViewCount ?? 320 + index * 91,
        })));
        setDataMode("live");
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return articles.filter((article) => {
      const inCategory = category === "전체" || article.tag === category;
      const inQuery = !normalized || `${article.title} ${article.content}`.toLowerCase().includes(normalized);
      return inCategory && inQuery;
    });
  }, [articles, category, query]);

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setNotice(query.trim() ? `“${query.trim()}” 검색 결과입니다.` : "최신 글을 보여드리고 있어요.");
  }

  return (
    <div className="site-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="모두의 광장 홈">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <span>모두의 광장</span>
        </a>
        <nav className="main-nav" aria-label="주요 메뉴">
          <a className="active" href="#feed">피드</a>
          <a href="#popular">인기</a>
          <a href="#system">시스템</a>
        </nav>
        <form className="search" onSubmit={submitSearch} role="search">
          <span aria-hidden="true">⌕</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="이야기 검색" aria-label="게시글 검색" />
          <kbd>↵</kbd>
        </form>
        <button className="write-button" type="button" onClick={() => setNotice("데모에서는 글쓰기 흐름만 미리 보여드려요.")}>새 글 쓰기</button>
        <button className="profile-button" type="button" aria-label="내 프로필">YW</button>
      </header>

      <main id="top">
        <section className="signal-hero" aria-labelledby="hero-title">
          <div>
            <p className="eyebrow"><span /> 일상부터 질문까지, 어떤 주제든 좋아요</p>
            <h1 id="hero-title">주제에 경계 없이,<br /><em>생각이 만나는 광장.</em></h1>
            <p className="hero-copy">가볍게 꺼낸 한마디가 새로운 대화의 시작이 됩니다.<br />자유게시판에서 관심사를 발견하고 경험을 나눠보세요.</p>
          </div>
          <div className="hero-metrics" aria-label="모두의 광장 주요 지표">
            <div><strong>15M+</strong><span>자유게시판 글</span></div>
            <div><strong>0.3s</strong><span>깊은 페이지 조회</span></div>
            <div><strong>6</strong><span>독립 서비스</span></div>
          </div>
        </section>

        <div className="content-grid">
          <section className="feed-column" id="feed" aria-labelledby="feed-title">
            <div className="section-heading">
              <div><p className="section-kicker">FREE BOARD</p><h2 id="feed-title">자유게시판</h2></div>
              <span className={`mode-badge ${dataMode}`}><i /> {dataMode === "live" ? "LIVE DATA" : "DEMO DATA"}</span>
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
                    <span>⌃</span><strong>{formatCompact(article.articleLikeCount)}</strong>
                  </div>
                  <div className="article-body">
                    <div className="article-meta"><span className={`tag tag-${index % 4}`}>{article.tag ?? "이야기"}</span><span>modu_{article.writerId}</span><span>·</span><time>{publishedAt(article.createdAt)}</time></div>
                    <h3>{article.title}</h3>
                    <p>{article.content}</p>
                    <div className="article-stats"><span>◌ {formatCompact(article.articleCommentCount)} 대화</span><span>◎ {formatCompact(article.articleViewCount)} 읽음</span></div>
                  </div>
                  <button type="button" className="save-button" aria-label={`${article.title} 저장`} onClick={() => setNotice("나중에 읽을 글에 저장했어요.")}>＋</button>
                </article>
              ))}
              {!filtered.length && <div className="empty-state"><strong>아직 이 주제의 이야기가 없어요.</strong><span>다른 키워드로 찾아보거나 첫 글을 시작해보세요.</span></div>}
            </div>
            <button className="more-button" type="button" onClick={() => setNotice("다음 커서는 준비 중입니다. 현재 페이지는 30개 단위로 조회해요.")}>이야기 더 보기 <span>↓</span></button>
          </section>

          <aside className="side-column">
            <section className="side-card popular-card" id="popular">
              <div className="side-title"><div><p>TRENDING NOW</p><h2>오늘의 인기 흐름</h2></div><span className="live-dot">LIVE</span></div>
              <ol>
                {[
                  ["01", "개발자의 기록은 어디까지 남겨야 할까", "테크 · 3.2K 읽음"],
                  ["02", "이번 여름, 꼭 다시 가고 싶은 도시", "여행 · 2.8K 읽음"],
                  ["03", "작은 팀에서 코드 리뷰를 지키는 법", "개발 · 2.1K 읽음"],
                  ["04", "취향이 선명한 사람들의 책상", "라이프 · 1.7K 읽음"],
                  ["05", "커피 한 잔으로 시작된 동네 모임", "일상 · 1.4K 읽음"],
                ].map(([rank, title, meta]) => <li key={rank}><strong>{rank}</strong><div><span>{title}</span><small>{meta}</small></div></li>)}
              </ol>
            </section>

            <section className="side-card system-card" id="system">
              <div className="side-title"><div><p>SYSTEM PULSE</p><h2>서비스 상태</h2></div><span className="healthy">정상</span></div>
              <div className="system-map">
                <span>API</span><i /><span>Kafka</span><i /><span>Redis</span><i /><span>Read</span>
              </div>
              <dl>
                <div><dt>이벤트 전달</dt><dd><span className="status-dot" /> 정상</dd></div>
                <div><dt>조회 캐시</dt><dd>93.8% hit</dd></div>
                <div><dt>자유게시판 글</dt><dd>15,000,100</dd></div>
              </dl>
              <a href="http://localhost:3001" target="_blank" rel="noreferrer">Grafana 대시보드 열기 <span>↗</span></a>
            </section>

            <section className="quote-card">
              <span className="quote-mark">“</span>
              <blockquote>좋은 커뮤니티는 답보다<br />더 나은 질문을 남깁니다.</blockquote>
              <p>모두의 광장 community principle</p>
            </section>
          </aside>
        </div>
      </main>
      <footer><span>© 2026 모두의 광장</span><span>15M+ free-board event-driven community</span><nav><a href="#system">상태</a><a href="https://github.com/ppupy1209/modu-square">GitHub</a></nav></footer>
    </div>
  );
}
