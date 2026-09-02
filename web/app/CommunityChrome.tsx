"use client";

import Link from "next/link";
import { FormEvent, ReactNode, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getOrCreateGuestIdentity, NOTIFICATION_API, Notification as NotificationItem } from "../lib/community-api";
import { AuthMember, logoutMember, restoreAuthMember } from "../lib/auth-api";

type HeaderProps = {
  query?: string;
  onQueryChange?: (value: string) => void;
  onSearchSubmit?: (event: FormEvent<HTMLFormElement>) => void;
  active?: "home" | "community" | "popular";
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
  const [guestUserId, setGuestUserId] = useState("");
  const [authMember, setAuthMember] = useState<AuthMember | null>(null);
  const [isAuthResolved, setIsAuthResolved] = useState(false);
  const [isAccountOpen, setIsAccountOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [accountError, setAccountError] = useState("");
  const searchValue = query ?? localQuery;

  useEffect(() => {
    const identity = getOrCreateGuestIdentity();
    const restoreGuest = window.setTimeout(() => setGuestUserId(identity.userId), 0);
    return () => {
      window.clearTimeout(restoreGuest);
    };
  }, []);

  useEffect(() => {
    let active = true;
    restoreAuthMember().then((member) => {
      if (active) {
        setAuthMember(member);
        setIsAuthResolved(true);
      }
    });
    return () => { active = false; };
  }, []);

  const notificationUserId = authMember
    ? String(authMember.memberId)
    : isAuthResolved ? guestUserId : "";

  useEffect(() => {
    if (!notificationUserId) return;
    const controller = new AbortController();

    async function loadNotifications() {
      try {
        const response = await fetch(`${NOTIFICATION_API}/v1/notifications/users/${notificationUserId}?limit=10`, {
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
  }, [notificationUserId]);

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

  async function logout() {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    setAccountError("");
    try {
      await logoutMember();
      setAuthMember(null);
      setNotifications([]);
      setIsAccountOpen(false);
    } catch {
      setAccountError("로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setIsLoggingOut(false);
    }
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
        <Link className={active === "community" ? "active" : undefined} href="/community">커뮤니티</Link>
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
          onClick={() => {
            setIsNotificationOpen((open) => !open);
            setIsAccountOpen(false);
          }}
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
      <div className="account-menu">
        <button
          className={`profile-button ${authMember ? "member-profile" : "guest-profile"}`}
          type="button"
          aria-expanded={isAccountOpen}
          aria-label={authMember ? `${authMember.displayName} 계정 메뉴` : "Guest 계정 메뉴"}
          onClick={() => {
            setIsAccountOpen((open) => !open);
            setIsNotificationOpen(false);
            setAccountError("");
          }}
        >
          {authMember?.displayName ?? "Guest"}
        </button>
        {isAccountOpen && (
          <section className="account-panel" aria-label={authMember ? "회원 계정" : "Guest 계정"}>
            {authMember ? (
              <>
                <div className="account-summary">
                  <strong>{authMember.displayName}</strong>
                  <span>{authMember.email}</span>
                </div>
                {accountError && <p className="account-error" role="alert">{accountError}</p>}
                <button className="account-logout" type="button" onClick={logout} disabled={isLoggingOut}>
                  {isLoggingOut ? "로그아웃 중..." : "로그아웃"}
                </button>
              </>
            ) : (
              <>
                <div className="account-summary">
                  <strong>Guest</strong>
                  <span>가입 없이 둘러보고 있어요.</span>
                </div>
                <Link className="account-primary" href="/auth" onClick={() => setIsAccountOpen(false)}>로그인 · 회원가입</Link>
                {guestUserId && (
                  <Link className="account-secondary" href={`/?q=modu_${guestUserId}`} onClick={() => setIsAccountOpen(false)}>Guest로 쓴 글</Link>
                )}
              </>
            )}
          </section>
        )}
      </div>
    </header>
  );
}

export function CommunityFooter() {
  return (
    <footer>
      <span>© 2026 Modu Square</span>
      <span>오늘의 생각이 편안한 대화가 되는 곳</span>
      <nav><Link href="/popular">인기글</Link><Link href="/community">커뮤니티</Link><Link href="/write">새 글 쓰기</Link></nav>
    </footer>
  );
}

export function CommunityPage({ children, className = "community-page" }: { children: ReactNode; className?: string }) {
  return <main className={className}>{children}</main>;
}
