"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { CommunityFooter, CommunityHeader } from "../CommunityChrome";
import { AuthRequestError, loginMember, registerMember } from "../../lib/auth-api";

type AuthMode = "login" | "register";

export function AuthPage() {
  const router = useRouter();
  const [mode, setMode] = useState<AuthMode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  function selectMode(nextMode: AuthMode) {
    setMode(nextMode);
    setPassword("");
    setMessage("");
    setErrorMessage("");
    setFieldErrors({});
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;

    setIsSubmitting(true);
    setMessage("");
    setErrorMessage("");
    setFieldErrors({});

    try {
      if (mode === "register") {
        await registerMember({ email, password, displayName });
        setMode("login");
        setPassword("");
        setMessage("회원가입이 완료되었습니다. 같은 이메일로 로그인해 주세요.");
        return;
      }

      await loginMember({ email, password });
      router.replace("/");
      router.refresh();
    } catch (cause) {
      if (cause instanceof AuthRequestError) {
        setErrorMessage(cause.message);
        setFieldErrors(cause.fieldErrors);
      } else {
        setErrorMessage("Auth Service에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="site-shell">
      <CommunityHeader />
      <main className="auth-page">
        <section className="auth-card" aria-labelledby="auth-title">
          <p className="auth-kicker">MEMBER ACCESS</p>
          <h1 id="auth-title">{mode === "login" ? "다시 만나 반가워요." : "새로운 이야기를 시작해요."}</h1>
          <p className="auth-copy">
            {mode === "login"
              ? "가입한 이메일로 로그인하면 세션 발급과 로그아웃 흐름을 직접 확인할 수 있어요."
              : "표시 이름과 이메일을 등록한 뒤 로그인해 보세요."}
          </p>

          <div className="auth-tabs" role="tablist" aria-label="인증 방식">
            <button type="button" role="tab" aria-selected={mode === "login"} onClick={() => selectMode("login")}>로그인</button>
            <button type="button" role="tab" aria-selected={mode === "register"} onClick={() => selectMode("register")}>회원가입</button>
          </div>

          <form className="auth-form" onSubmit={submit}>
            {mode === "register" && (
              <label>
                <span>표시 이름</span>
                <input
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  autoComplete="nickname"
                  minLength={2}
                  maxLength={40}
                  placeholder="2~40자"
                  required
                />
                {fieldErrors.displayName && <small>{fieldErrors.displayName}</small>}
              </label>
            )}
            <label>
              <span>이메일</span>
              <input
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                maxLength={320}
                placeholder="hello@example.com"
                required
              />
              {fieldErrors.email && <small>{fieldErrors.email}</small>}
            </label>
            <label>
              <span>비밀번호</span>
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete={mode === "login" ? "current-password" : "new-password"}
                minLength={mode === "register" ? 10 : undefined}
                maxLength={72}
                placeholder={mode === "register" ? "10자 이상" : "비밀번호 입력"}
                required
              />
              {fieldErrors.password && <small>{fieldErrors.password}</small>}
            </label>

            {errorMessage && <p className="auth-message error" role="alert">{errorMessage}</p>}
            {message && <p className="auth-message success" role="status">{message}</p>}

            <button className="auth-submit" type="submit" disabled={isSubmitting}>
              {isSubmitting ? "처리 중..." : mode === "login" ? "로그인" : "회원가입"}
            </button>
          </form>

          <Link className="auth-guest-link" href="/">계정 없이 Guest로 계속 둘러보기 <span>→</span></Link>
        </section>
      </main>
      <CommunityFooter />
    </div>
  );
}
