"use client";

import Link from "next/link";
import { FormEvent, ReactNode, useState } from "react";
import { useRouter } from "next/navigation";

type HeaderProps = {
  query?: string;
  onQueryChange?: (value: string) => void;
  onSearchSubmit?: (event: FormEvent<HTMLFormElement>) => void;
  active?: "home";
};

export function CommunityHeader({ query, onQueryChange, onSearchSubmit, active }: HeaderProps) {
  const router = useRouter();
  const [localQuery, setLocalQuery] = useState("");
  const searchValue = query ?? localQuery;

  function submit(event: FormEvent<HTMLFormElement>) {
    if (onSearchSubmit) {
      onSearchSubmit(event);
      return;
    }
    event.preventDefault();
    const normalized = searchValue.trim();
    router.push(normalized ? `/?q=${encodeURIComponent(normalized)}` : "/");
  }

  return (
    <header className="topbar">
      <Link className="brand" href="/" aria-label="Modu Square 홈">
        <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
        <span>Modu Square</span>
      </Link>
      <nav className="main-nav" aria-label="주요 메뉴">
        <Link className={active === "home" ? "active" : undefined} href="/">홈</Link>
        <Link href="/#popular">인기글</Link>
        <Link href="/#community">커뮤니티</Link>
      </nav>
      <form className="search" onSubmit={submit} role="search">
        <span aria-hidden="true">⌕</span>
        <input
          value={searchValue}
          onChange={(event) => onQueryChange ? onQueryChange(event.target.value) : setLocalQuery(event.target.value)}
          placeholder="관심 있는 이야기 검색"
          aria-label="게시글 검색"
        />
        <button className="search-submit" type="submit" aria-label="검색">↵</button>
      </form>
      <Link className="write-button" href="/write">새 글 쓰기</Link>
      <Link className="profile-button" href="/?q=modu_1" aria-label="내가 쓴 글">YW</Link>
    </header>
  );
}

export function CommunityFooter() {
  return (
    <footer>
      <span>© 2026 Modu Square</span>
      <span>오늘의 생각이 편안한 대화가 되는 곳</span>
      <nav><Link href="/#popular">인기글</Link><Link href="/#community">커뮤니티 약속</Link></nav>
    </footer>
  );
}

export function CommunityPage({ children, className = "community-page" }: { children: ReactNode; className?: string }) {
  return <main className={className}>{children}</main>;
}
