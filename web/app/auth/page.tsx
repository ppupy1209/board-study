import type { Metadata } from "next";
import { AuthPage } from "./AuthPage";

export const metadata: Metadata = {
  title: "로그인 · 회원가입 — Modu Square",
  description: "Modu Square 회원가입과 로그인",
};

export default function AuthRoute() {
  return <AuthPage />;
}
