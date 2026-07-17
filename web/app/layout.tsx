import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "모두의 광장",
  description: "자유게시판 1,500만 건과 이벤트 기반 아키텍처로 검증한 커뮤니티 서비스",
  openGraph: {
    title: "모두의 광장",
    description: "15M+ FREE BOARD · EVENT-DRIVEN COMMUNITY",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "모두의 광장 social preview" }],
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
