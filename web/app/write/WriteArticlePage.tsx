"use client";

import Link from "next/link";
import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CommunityFooter, CommunityHeader, CommunityPage } from "../CommunityChrome";
import {
  ARTICLE_API,
  Article,
  BOARD_ID,
  LOCAL_USER_ID,
  MEDIA_API,
  MediaAsset,
  UploadTicket,
  requestJson,
} from "../../lib/community-api";

type AttachmentStatus = "ready" | "uploading" | "processing" | "failed";

type Attachment = {
  localId: string;
  file: File;
  previewUrl: string;
  status: AttachmentStatus;
};

const MAX_IMAGES = 5;
const MAX_FILE_SIZE = 10 * 1024 * 1024;
const ALLOWED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

export function WriteArticlePage() {
  const router = useRouter();
  const attachmentRef = useRef<Attachment[]>([]);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => () => {
    attachmentRef.current.forEach((attachment) => URL.revokeObjectURL(attachment.previewUrl));
  }, []);

  function replaceAttachments(next: Attachment[]) {
    attachmentRef.current = next;
    setAttachments(next);
  }

  function selectImages(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    event.target.value = "";
    if (!files.length) return;

    if (attachments.length + files.length > MAX_IMAGES) {
      setError("이미지는 최대 5개까지 첨부할 수 있습니다.");
      return;
    }

    const invalid = files.find((file) => !ALLOWED_TYPES.has(file.type) || file.size > MAX_FILE_SIZE);
    if (invalid) {
      setError("JPEG, PNG, WebP 이미지를 파일당 10MB 이하로 첨부해 주세요.");
      return;
    }

    const next = [
      ...attachments,
      ...files.map((file) => ({
        localId: crypto.randomUUID(),
        file,
        previewUrl: URL.createObjectURL(file),
        status: "ready" as const,
      })),
    ];
    replaceAttachments(next);
    setError("");
  }

  function removeImage(localId: string) {
    const target = attachments.find((attachment) => attachment.localId === localId);
    if (target) URL.revokeObjectURL(target.previewUrl);
    replaceAttachments(attachments.filter((attachment) => attachment.localId !== localId));
  }

  function updateStatus(localId: string, status: AttachmentStatus) {
    replaceAttachments(
      attachmentRef.current.map((attachment) =>
        attachment.localId === localId ? { ...attachment, status } : attachment
      )
    );
  }

  async function uploadImage(attachment: Attachment) {
    updateStatus(attachment.localId, "uploading");
    const ticket = await requestJson<UploadTicket>(`${MEDIA_API}/v1/media/uploads/presign`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fileName: attachment.file.name,
        contentType: attachment.file.type,
        sizeBytes: attachment.file.size,
      }),
    });

    const uploadResponse = await fetch(ticket.uploadUrl, {
      method: "PUT",
      headers: ticket.headers,
      body: attachment.file,
    });
    if (!uploadResponse.ok) {
      throw new Error(`이미지를 저장소로 전송하지 못했습니다. (${uploadResponse.status})`);
    }

    const media = await requestJson<MediaAsset>(
      `${MEDIA_API}/v1/media/uploads/${ticket.mediaId}/complete`,
      { method: "POST" }
    );
    updateStatus(attachment.localId, "processing");
    return media;
  }

  async function discardMedia(mediaIds: string[]) {
    await Promise.allSettled(
      mediaIds.map((mediaId) =>
        fetch(`${MEDIA_API}/v1/media/${mediaId}`, { method: "DELETE" })
      )
    );
  }

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
    const uploadedMediaIds: string[] = [];
    let createdArticle: Article | null = null;

    try {
      for (const attachment of attachmentRef.current) {
        const media = await uploadImage(attachment);
        uploadedMediaIds.push(media.mediaId);
      }

      createdArticle = await requestJson<Article>(`${ARTICLE_API}/v1/articles`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: normalizedTitle,
          content: normalizedContent,
          writerId: LOCAL_USER_ID,
          boardId: BOARD_ID,
        }),
      });

      if (uploadedMediaIds.length) {
        await requestJson<MediaAsset[]>(
          `${MEDIA_API}/v1/media/articles/${createdArticle.articleId}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ mediaIds: uploadedMediaIds }),
          }
        );
      }

      attachmentRef.current.forEach((attachment) => URL.revokeObjectURL(attachment.previewUrl));
      router.push(`/articles/${createdArticle.articleId}`);
    } catch (cause) {
      attachmentRef.current
        .filter((attachment) => attachment.status === "uploading")
        .forEach((attachment) => updateStatus(attachment.localId, "failed"));

      if (createdArticle) {
        await fetch(`${ARTICLE_API}/v1/articles/${createdArticle.articleId}`, { method: "DELETE" });
      }
      await discardMedia(uploadedMediaIds);
      setError(cause instanceof Error ? cause.message : "글을 등록하지 못했습니다.");
      setIsSubmitting(false);
    }
  }

  const statusLabel: Record<AttachmentStatus, string> = {
    ready: "업로드 준비",
    uploading: "저장소로 전송 중",
    processing: "썸네일 처리 중",
    failed: "업로드 실패",
  };

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

            <fieldset className="image-attachment-field">
              <legend>사진</legend>
              <div className="image-upload-row">
                <label className="image-upload-button">
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    onChange={selectImages}
                    disabled={isSubmitting || attachments.length >= MAX_IMAGES}
                  />
                  <span aria-hidden="true">＋</span>
                  사진 선택
                </label>
                <p>JPEG · PNG · WebP, 파일당 10MB 이하, 최대 5장</p>
              </div>

              {attachments.length > 0 && (
                <ul className="image-preview-list" aria-label="첨부할 사진">
                  {attachments.map((attachment) => (
                    <li key={attachment.localId}>
                      <img src={attachment.previewUrl} alt="" />
                      <div>
                        <strong>{attachment.file.name}</strong>
                        <span data-status={attachment.status}>{statusLabel[attachment.status]}</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => removeImage(attachment.localId)}
                        disabled={isSubmitting}
                        aria-label={`${attachment.file.name} 첨부 취소`}
                      >
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </fieldset>

            {error && <p className="form-error" role="alert">{error}</p>}
            <div className="editor-actions">
              <Link className="secondary-button" href="/">취소</Link>
              <button className="primary-button" type="submit" disabled={isSubmitting}>
                {isSubmitting ? "이미지와 이야기를 등록하는 중…" : "이야기 등록하기"}
              </button>
            </div>
          </form>
        </section>
      </CommunityPage>
      <CommunityFooter />
    </div>
  );
}
