"use client";

import Link from "next/link";
import { FormEvent, ReactNode, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { LOCAL_USER_ID, NOTIFICATION_API, Notification as NotificationItem } from "../lib/community-api";

type HeaderProps = {
  query?: string;
  onQueryChange?: (value: string) => void;
  onSearchSubmit?: (event: FormEvent<HTMLFormElement>) => void;
  active?: "home" | "popular";
};

function notificationSummary(notification: NotificationItem) {
  const parts: string[] = [];
  if (notification.commentCount) parts.push(`\uB313\uAE00 ${notification.commentCount}\uAC1C`);
  if (notification.likeCount) parts.push(`\uC88B\uC544\uC694 ${notification.likeCount}\uAC1C`);
  return parts.join(" \u00B7 ");
}

export function CommunityHeader({ query, onQueryChange, onSearchSubmit, active }: HeaderProps) {
  const router = useRouter();
  const [localQuery, setLocalQuery] = useState("");
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const searchValue = query ?? localQuery;

  useEffect(() => {
    const controller = new AbortController();

    async function loadNotifications() {
      try {
        const response = await fetch(`${NOTIFICATION_API}/v1/notifications/users/${LOCAL_USER_ID}?limit=10`, {
          signal: controller.signal,
        });
        if (!response.ok) return;
        const data = await response.json() as NotificationItem[];
        setNotifications(data);
      } catch {
        // Keep the community available while the notification service is starting.
      }
    }

    loadNotifications();
    const intervalId = window.setInterval(loadNotifications, 30_000);
    return () => {
      window.clearInterval(intervalId);
      controller.abort();
    };
  }, []);

  const totalEventCount = notifications.reduce((sum, notification) => sum + notification.eventCount, 0);
  const notificationBadge = totalEventCount > 99 ? "99+" : String(totalEventCount);

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
        <Link className={active === "popular" ? "active" : undefined} href="/popular">인기글</Link>
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
      <div className="notification-menu">
        <button
          className="notification-button"
          type="button"
          aria-expanded={isNotificationOpen}
          aria-label={`\uCD5C\uADFC \uC54C\uB9BC ${totalEventCount}\uAC74`}
          onClick={() => setIsNotificationOpen((open) => !open)}
        >
          <span aria-hidden="true">{"\uC54C\uB9BC"}</span>
          {totalEventCount > 0 && <b>{notificationBadge}</b>}
        </button>
        {isNotificationOpen && (
          <section className="notification-panel" aria-label={"\uCD5C\uADFC \uC54C\uB9BC"}>
            <div className="notification-panel-heading">
              <strong>{"\uCD5C\uADFC \uC54C\uB9BC"}</strong>
              <span>{"5\uBD84 \uB2E8\uC704 \uBB36\uC74C"}</span>
            </div>
            {notifications.length ? (
              <ul>
                {notifications.map((notification) => (
                  <li key={notification.notificationId}>
                    <Link href={`/articles/${notification.articleId}`} onClick={() => setIsNotificationOpen(false)}>
                      <strong>{notification.title}</strong>
                      <span>{notificationSummary(notification)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="notification-empty">{"\uC0C8\uB85C\uC6B4 \uC54C\uB9BC\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."}</p>
            )}
          </section>
        )}
      </div>
      <Link className="profile-button" href="/?q=modu_1" aria-label="내가 쓴 글">YW</Link>
    </header>
  );
}

export function CommunityFooter() {
  return (
    <footer>
      <span>© 2026 Modu Square</span>
      <span>오늘의 생각이 편안한 대화가 되는 곳</span>
      <nav><Link href="/popular">인기글</Link><Link href="/#community">커뮤니티 약속</Link></nav>
    </footer>
  );
}

export function CommunityPage({ children, className = "community-page" }: { children: ReactNode; className?: string }) {
  return <main className={className}>{children}</main>;
}
