import type { Metadata } from "next";
import { ArticleDetailPage } from "./ArticleDetailPage";

export const metadata: Metadata = {
  title: "이야기 — Modu Square",
  description: "Modu Square의 이야기와 댓글을 확인해 보세요.",
};

export default function ArticlePage() {
  return <ArticleDetailPage />;
}
