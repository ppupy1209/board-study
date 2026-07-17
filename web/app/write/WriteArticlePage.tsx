"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { CommunityFooter, CommunityHeader, CommunityPage } from "../CommunityChrome";
import { ARTICLE_API, Article, BOARD_ID, LOCAL_USER_ID, requestJson } from "../../lib/community-api";

export function WriteArticlePage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedTitle = title.trim();
    const normalizedContent = content.trim();
    if (normalizedTitle.length < 2 || normalizedContent.length < 2) {
      setError("제목과 내용을 두 글자 이상 입력해 주세요.");
      return;
    }

    setIsSubmitting(true);
    setError("");
    try {
      const article = await requestJson<Article>(`${ARTICLE_API}/v1/articles`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: normalizedTitle,
          content: normalizedContent,
          writerId: LOCAL_USER_ID,
          boardId: BOARD_ID,
        }),
      });
      router.push(`/articles/${article.articleId}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "글을 등록하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="site-shell">
      <CommunityHeader />
      <CommunityPage className="editor-page">
        <div className="page-breadcrumb"><Link href="/">← 홈으로</Link><span>새 글 쓰기</span></div>
        <section className="editor-card" aria-labelledby="editor-title">
          <div className="editor-heading">
            <p className="section-kicker">NEW STORY</p>
            <h1 id="editor-title">어떤 이야기를 나누고 싶나요?</h1>
            <p>완벽하게 다듬지 않아도 괜찮아요. 지금의 생각을 편안하게 들려주세요.</p>
          </div>
          <form className="editor-form" onSubmit={submit}>
            <label>
              <span>제목</span>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                maxLength={120}
                placeholder="이야기의 제목을 적어주세요"
                autoFocus
                required
              />
              <small>{title.length}/120</small>
            </label>
            <label>
              <span>내용</span>
              <textarea
                value={content}
                onChange={(event) => setContent(event.target.value)}
                maxLength={5000}
                placeholder="경험과 생각을 자유롭게 나눠주세요"
                rows={12}
                required
              />
              <small>{content.length}/5,000</small>
            </label>
            {error && <p className="form-error" role="alert">{error}</p>}
            <div className="editor-actions">
              <Link className="secondary-button" href="/">취소</Link>
              <button className="primary-button" type="submit" disabled={isSubmitting}>
                {isSubmitting ? "등록하는 중…" : "이야기 등록하기"}
              </button>
            </div>
          </form>
        </section>
      </CommunityPage>
      <CommunityFooter />
    </div>
  );
}
