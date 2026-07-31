"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { CommunityFooter, CommunityHeader, CommunityPage } from "../../CommunityChrome";
import {
  ARTICLE_API,
  ARTICLE_READ_API,
  COMMENT_API,
  LIKE_API,
  VIEW_API,
  Article,
  Comment,
  LOCAL_USER_ID,
  normalizeArticle,
  publishedAt,
  requestJson,
} from "../../../lib/community-api";

type CommentPageResponse = { comments: Comment[]; commentCount: number };

export function ArticleDetailPage() {
  const params = useParams<{ articleId: string }>();
  const router = useRouter();
  const articleId = params.articleId;
  const [article, setArticle] = useState<Article | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentCount, setCommentCount] = useState(0);
  const [likeCount, setLikeCount] = useState(0);
  const [viewCount, setViewCount] = useState(0);
  const [liked, setLiked] = useState(false);
  const [comment, setComment] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isWorking, setIsWorking] = useState(false);

  const loadComments = useCallback(async () => {
    const data = await requestJson<CommentPageResponse>(`${COMMENT_API}/v2/comments?articleId=${articleId}&page=1&pageSize=50`);
    setComments(data.comments.map((item) => ({ ...item, commentId: String(item.commentId), articleId: String(item.articleId), writerId: String(item.writerId) })));
    setCommentCount(data.commentCount);
  }, [articleId]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setIsLoading(true);
      try {
        const articleData = await requestJson<Article>(`${ARTICLE_READ_API}/v1/articles/${articleId}`);
        if (cancelled) return;
        setArticle(normalizeArticle({ ...articleData, articleId: String(articleData.articleId), writerId: String(articleData.writerId) }));
        setCommentCount(articleData.articleCommentCount ?? 0);
        setLikeCount(articleData.articleLikeCount ?? 0);
        setViewCount(articleData.articleViewCount ?? 0);

        const [commentsResult, viewResult, likedResult] = await Promise.allSettled([
          loadComments(),
          requestJson<number>(`${VIEW_API}/v1/article-views/articles/${articleId}/users/${LOCAL_USER_ID}`, { method: "POST" }),
          fetch(`${LIKE_API}/v1/article-likes/articles/${articleId}/users/${LOCAL_USER_ID}`),
        ]);
        if (cancelled) return;
        if (viewResult.status === "fulfilled") setViewCount(viewResult.value);
        if (likedResult.status === "fulfilled") setLiked(likedResult.value.ok);
        if (commentsResult.status === "rejected") setComments([]);
      } catch (cause) {
        if (!cancelled) setError(cause instanceof Error ? cause.message : "게시글을 불러오지 못했습니다.");
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [articleId, loadComments]);

  async function toggleLike() {
    if (isWorking) return;
    const nextLiked = !liked;
    setLiked(nextLiked);
    setLikeCount((count) => Math.max(0, count + (nextLiked ? 1 : -1)));
    setIsWorking(true);
    try {
      await requestJson<void>(`${LIKE_API}/v1/article-likes/articles/${articleId}/users/${LOCAL_USER_ID}/optimistic-lock`, {
        method: nextLiked ? "POST" : "DELETE",
      });
      setNotice(nextLiked ? "이 이야기를 좋아합니다." : "좋아요를 취소했습니다.");
    } catch (cause) {
      setLiked(!nextLiked);
      setLikeCount((count) => Math.max(0, count + (nextLiked ? -1 : 1)));
      setNotice(cause instanceof Error ? cause.message : "좋아요를 반영하지 못했습니다.");
    } finally {
      setIsWorking(false);
    }
  }

  async function submitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = comment.trim();
    if (content.length < 2) {
      setNotice("댓글을 두 글자 이상 입력해 주세요.");
      return;
    }
    setIsWorking(true);
    try {
      await requestJson<Comment>(`${COMMENT_API}/v2/comments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ articleId, content, writerId: LOCAL_USER_ID }),
      });
      setComment("");
      await loadComments();
      setNotice("댓글을 등록했어요.");
    } catch (cause) {
      setNotice(cause instanceof Error ? cause.message : "댓글을 등록하지 못했습니다.");
    } finally {
      setIsWorking(false);
    }
  }

  async function deleteComment(commentId: string) {
    setIsWorking(true);
    try {
      await requestJson<void>(`${COMMENT_API}/v2/comments/${commentId}`, { method: "DELETE" });
      await loadComments();
      setNotice("댓글을 삭제했어요.");
    } catch (cause) {
      setNotice(cause instanceof Error ? cause.message : "댓글을 삭제하지 못했습니다.");
    } finally {
      setIsWorking(false);
    }
  }

  async function deleteArticle() {
    if (!window.confirm("이 이야기를 삭제할까요? 삭제 후에는 되돌릴 수 없습니다.")) return;
    setIsWorking(true);
    try {
      await requestJson<void>(`${ARTICLE_API}/v1/articles/${articleId}`, { method: "DELETE" });
      router.push("/");
    } catch (cause) {
      setNotice(cause instanceof Error ? cause.message : "게시글을 삭제하지 못했습니다.");
      setIsWorking(false);
    }
  }

  return (
    <div className="site-shell">
      <CommunityHeader />
      <CommunityPage className="detail-page">
        <div className="page-breadcrumb"><Link href="/">← 새로운 이야기</Link><span>자유게시판</span></div>
        {isLoading && <div className="page-state" role="status"><strong>이야기를 불러오고 있어요.</strong><span>잠시만 기다려 주세요.</span></div>}
        {!isLoading && error && <div className="page-state error-state" role="alert"><strong>이야기를 열 수 없어요.</strong><span>{error}</span><Link href="/">홈으로 돌아가기</Link></div>}
        {!isLoading && article && (
          <>
            <article className="detail-card">
              <div className="detail-meta"><span>자유게시판</span><span>modu_{article.writerId}</span><span>·</span><time>{publishedAt(article.createdAt)}</time></div>
              <h1>{article.title}</h1>
              <p className="detail-content">{article.content}</p>
              <div className="detail-actions">
                <button className={`reaction-button ${liked ? "liked" : ""}`} type="button" onClick={toggleLike} disabled={isWorking} aria-pressed={liked}>
                  <span>{liked ? "♥" : "♡"}</span> 좋아요 {likeCount.toLocaleString("ko-KR")}
                </button>
                <span>◌ 댓글 {commentCount.toLocaleString("ko-KR")}</span>
                <span>◎ 읽음 {viewCount.toLocaleString("ko-KR")}</span>
                {article.writerId === LOCAL_USER_ID && <button className="delete-link" type="button" onClick={deleteArticle} disabled={isWorking}>글 삭제</button>}
              </div>
            </article>

            <section className="comments-card" aria-labelledby="comments-title">
              <div className="comments-heading"><div><p className="section-kicker">CONVERSATION</p><h2 id="comments-title">댓글 {commentCount}</h2></div></div>
              <form className="comment-form" onSubmit={submitComment}>
                <span className="comment-avatar" aria-hidden="true">YW</span>
                <label>
                  <span className="sr-only">댓글 내용</span>
                  <textarea value={comment} onChange={(event) => setComment(event.target.value)} rows={3} maxLength={1000} placeholder="이야기에 따뜻한 댓글을 남겨보세요" />
                </label>
                <button type="submit" disabled={isWorking || !comment.trim()}>댓글 등록</button>
              </form>
              {notice && <p className="notice" role="status">{notice}</p>}
              <div className="comment-list">
                {comments.map((item) => (
                  <article className="comment-item" key={item.commentId}>
                    <span className="comment-avatar" aria-hidden="true">{item.writerId === LOCAL_USER_ID ? "YW" : `M${item.writerId.slice(-2)}`}</span>
                    <div><div className="comment-meta"><strong>modu_{item.writerId}</strong><time>{publishedAt(item.createdAt)}</time></div><p>{item.deleted ? "삭제된 댓글입니다." : item.content}</p></div>
                    {!item.deleted && item.writerId === LOCAL_USER_ID && <button type="button" onClick={() => deleteComment(item.commentId)} disabled={isWorking}>삭제</button>}
                  </article>
                ))}
                {!comments.length && <div className="empty-comments"><strong>첫 댓글을 기다리고 있어요.</strong><span>이 이야기에 가장 먼저 마음을 건네보세요.</span></div>}
              </div>
            </section>
          </>
        )}
      </CommunityPage>
      <CommunityFooter />
    </div>
  );
}
