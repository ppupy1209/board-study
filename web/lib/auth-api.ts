export const AUTH_API = process.env.NEXT_PUBLIC_AUTH_API_BASE_URL ?? "http://localhost:9008";

const ACCESS_TOKEN_STORAGE_KEY = "modu-square-access-token";
const SESSION_HINT_STORAGE_KEY = "modu-square-has-session";

export type AuthMember = {
  memberId: number;
  email: string;
  displayName: string;
  sessionId: string;
};

export type RegisterMemberInput = {
  email: string;
  password: string;
  displayName: string;
};

export type LoginMemberInput = {
  email: string;
  password: string;
};

type AccessTokenResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
};

type AuthErrorPayload = {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
};

export class AuthRequestError extends Error {
  readonly code?: string;
  readonly fieldErrors: Record<string, string>;
  readonly status: number;

  constructor(payload: AuthErrorPayload, status: number) {
    super(payload.message ?? `인증 요청을 처리하지 못했습니다. (${status})`);
    this.name = "AuthRequestError";
    this.code = payload.code;
    this.fieldErrors = payload.fieldErrors ?? {};
    this.status = status;
  }
}

async function authRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  const response = await fetch(`${AUTH_API}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  if (!response.ok) {
    let payload: AuthErrorPayload = {};
    try {
      payload = await response.json() as AuthErrorPayload;
    } catch {
      // The status code is enough when the response has no JSON body.
    }
    throw new AuthRequestError(payload, response.status);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

function storeAccessToken(accessToken: string) {
  if (typeof window === "undefined") return;
  window.sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken);
  window.localStorage.setItem(SESSION_HINT_STORAGE_KEY, "true");
}

function storedAccessToken() {
  if (typeof window === "undefined") return null;
  return window.sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
}

function removeAccessToken() {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
}

function clearStoredSession() {
  if (typeof window === "undefined") return;
  removeAccessToken();
  window.localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
}

async function currentMember(accessToken: string) {
  return authRequest<AuthMember>("/v1/auth/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

async function refreshAccessToken() {
  return authRequest<AccessTokenResponse>("/v1/auth/refresh", { method: "POST" });
}

export async function registerMember(input: RegisterMemberInput) {
  return authRequest<Omit<AuthMember, "sessionId">>("/v1/auth/members", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function loginMember(input: LoginMemberInput) {
  const session = await authRequest<AccessTokenResponse>("/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
  storeAccessToken(session.accessToken);
  return currentMember(session.accessToken);
}

export async function restoreAuthMember(): Promise<AuthMember | null> {
  const accessToken = storedAccessToken();
  if (accessToken) {
    try {
      return await currentMember(accessToken);
    } catch (cause) {
      if (!(cause instanceof AuthRequestError) || cause.status !== 401) return null;
      removeAccessToken();
    }
  }

  if (typeof window === "undefined" || window.localStorage.getItem(SESSION_HINT_STORAGE_KEY) !== "true") {
    return null;
  }

  try {
    const session = await refreshAccessToken();
    storeAccessToken(session.accessToken);
    return await currentMember(session.accessToken);
  } catch (cause) {
    if (cause instanceof AuthRequestError) clearStoredSession();
    return null;
  }
}

export async function logoutMember() {
  await authRequest<void>("/v1/auth/logout", { method: "POST" });
  clearStoredSession();
}
