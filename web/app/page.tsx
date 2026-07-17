import type { Metadata } from "next";
import { ModuSquareApp } from "./ModuSquareApp";

export const metadata: Metadata = {
  title: "모두의 광장 — 주제에 경계 없이 생각이 만나는 곳",
  description: "자유게시판 1,500만 건과 이벤트 기반 아키텍처로 검증한 커뮤니티 서비스",
};

export default function Home() {
  return <ModuSquareApp />;
}
