import type { Metadata } from "next";
import { ModuSquareApp } from "./ModuSquareApp";

export const metadata: Metadata = {
  title: "Modu Square — 오늘의 생각이 만나는 곳",
  description: "일상과 취향, 질문을 편안하게 나누는 열린 커뮤니티",
};

export default function Home() {
  return <ModuSquareApp />;
}
