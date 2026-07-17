import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Modu Square",
  description: "일상과 취향, 질문을 편안하게 나누는 열린 커뮤니티",
  openGraph: {
    title: "Modu Square",
    description: "오늘의 생각을 나누고 새로운 취향을 만나는 곳",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "Modu Square community" }],
  },
  twitter: { card: "summary_large_image", images: ["/og.png"] },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
