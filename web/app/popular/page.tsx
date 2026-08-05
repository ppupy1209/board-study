import type { Metadata } from "next";
import { PopularArticlesPage } from "./PopularArticlesPage";

export const metadata: Metadata = {
  title: "인기글 — Modu Square",
  description: "오늘 가장 많은 관심을 받은 Modu Square 인기글을 확인해 보세요.",
};

export default function PopularPage() {
  return <PopularArticlesPage />;
}
