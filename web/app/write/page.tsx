import type { Metadata } from "next";
import { WriteArticlePage } from "./WriteArticlePage";

export const metadata: Metadata = {
  title: "새 글 쓰기 — Modu Square",
  description: "Modu Square에 새로운 이야기를 남겨보세요.",
};

export default function WritePage() {
  return <WriteArticlePage />;
}
