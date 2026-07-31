export const ARTICLE_API = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:9000";
export const ARTICLE_READ_API = process.env.NEXT_PUBLIC_ARTICLE_READ_API_BASE_URL ?? "http://localhost:9005";
export const COMMENT_API = process.env.NEXT_PUBLIC_COMMENT_API_BASE_URL ?? "http://localhost:9001";
export const LIKE_API = process.env.NEXT_PUBLIC_LIKE_API_BASE_URL ?? "http://localhost:9002";
export const VIEW_API = process.env.NEXT_PUBLIC_VIEW_API_BASE_URL ?? "http://localhost:9003";
export const HOT_ARTICLE_API = process.env.NEXT_PUBLIC_HOT_ARTICLE_API_BASE_URL ?? "http://localhost:9004";
export const NOTIFICATION_API = process.env.NEXT_PUBLIC_NOTIFICATION_API_BASE_URL ?? "http://localhost:9006";

export const BOARD_ID = "1";
export const LOCAL_USER_ID = "1";

export type Article = {
  articleId: string;
  title: string;
  content: string;
  writerId: string;
  boardId?: string;
  createdAt: string;
  modifiedAt?: string;
  articleCommentCount?: number;
  articleLikeCount?: number;
  articleViewCount?: number;
  tag?: string;
};

export type Comment = {
  commentId: string;
  content: string;
  articleId: string;
  writerId: string;
  deleted: boolean;
  path?: string;
  createdAt: string;
};

export type HotArticle = {
  articleId: string;
  title: string;
  createdAt?: string;
};

export type Notification = {
  notificationId: string;
  articleId: string;
  title: string;
  commentCount: number;
  likeCount: number;
  eventCount: number;
  updatedAt: string;
};

const communityStories = [
  ["퇴근 후 한 시간, 다들 어떻게 보내시나요?", "집에 도착하자마자 쉬는 날도 있고, 가볍게 산책하거나 저녁을 만들어 먹는 날도 있어요. 여러분의 평일 저녁 루틴이 궁금합니다."],
  ["우리 동네에 오래 남았으면 하는 작은 가게", "자주 가지 않아도 그 자리에 있다는 것만으로 마음이 놓이는 가게가 있나요? 동네에서 아끼는 공간을 함께 소개해 주세요."],
  ["여름이 끝나기 전에 가보고 싶은 여행지", "멀리 떠나는 여행도 좋지만 당일치기로 다녀올 수 있는 곳도 좋아요. 요즘 마음에 담아둔 여행지가 있다면 알려주세요."],
  ["요즘 새로 시작한 취미가 있나요?", "잘해야 한다는 부담 없이 천천히 즐길 수 있는 취미를 찾고 있어요. 최근 시작해서 즐겁게 이어가는 일이 있나요?"],
  ["일과 삶의 균형을 찾는 나만의 방법", "바쁜 시기에도 나를 돌보는 시간을 놓치지 않으려 합니다. 작지만 꾸준히 지키는 원칙이 있다면 나눠주세요."],
  ["최근 마음에 오래 남은 책 한 권", "책장을 덮은 뒤에도 한동안 생각나는 문장과 장면이 있더라고요. 최근에 읽은 책 중 오래 기억하고 싶은 한 권은 무엇인가요?"],
  ["오늘 나를 웃게 만든 소소한 순간", "거창한 일은 아니지만 하루를 조금 밝게 만든 순간이 있었나요? 오늘 발견한 작은 기쁨을 함께 나눠봐요."],
  ["처음부터 끝까지 듣기 좋은 앨범을 추천해요", "한 곡만 골라 듣기보다 순서대로 들을 때 더 좋은 앨범을 찾고 있습니다. 장르와 시대는 상관없어요."],
  ["혼자 걷기 좋은 서울 산책길을 나눠요", "사람이 붐비지 않고 천천히 주변을 둘러볼 수 있는 길을 좋아해요. 여러분이 아끼는 산책 코스는 어디인가요?"],
  ["아침을 덜 바쁘게 만드는 작은 습관", "전날 밤 가방과 옷을 미리 준비하니 아침이 한결 여유로워졌어요. 하루를 편안하게 시작하는 방법을 알려주세요."],
  ["비 오는 날 집에서 보기 좋은 영화", "따뜻한 차 한 잔과 함께 조용히 보기 좋은 영화를 찾고 있어요. 잔잔한 여운이 남는 작품이면 더욱 좋겠습니다."],
  ["냉장고 속 재료로 만든 의외의 한 끼", "장보기 전 남은 재료를 모아 만들었는데 생각보다 훌륭했던 메뉴가 있나요? 간단한 조합도 환영합니다."],
  ["오래 사용해도 질리지 않는 물건이 있나요?", "유행과 상관없이 손이 자주 가는 물건에는 저마다 이유가 있는 것 같아요. 오래 곁에 둔 물건을 소개해 주세요."],
  ["낯선 사람의 친절을 기억하는 순간", "잠깐 건네받은 친절이 예상보다 오래 마음에 남을 때가 있어요. 여러분에게도 문득 떠오르는 순간이 있나요?"],
  ["주말에 휴대폰 없이 보낸 오후", "알림을 잠시 꺼두고 책을 읽거나 동네를 걸었더니 시간이 다르게 흐르는 기분이었어요. 여러분은 어떻게 쉬고 있나요?"],
  ["요즘 가장 자주 하는 고민은 무엇인가요?", "답을 바로 찾지 못해도 누군가와 이야기하는 것만으로 생각이 정리되곤 합니다. 편하게 요즘의 고민을 들려주세요."],
] as const;

export function normalizeArticle<T extends Pick<Article, "articleId" | "title" | "content">>(article: T): T {
  const legacySeed = /\s#\d+$/.test(article.title) || /topic=\d+,\s*sequence=\d+/.test(article.content);
  if (!legacySeed) return article;

  let index = 0;
  try {
    index = Number(BigInt(String(article.articleId)) % BigInt(communityStories.length));
  } catch {
    index = 0;
  }
  const [title, content] = communityStories[index];
  return { ...article, title, content };
}

export function normalizeArticleTitle(articleId: string, title: string) {
  if (!/\s#\d+$/.test(title)) return title;
  try {
    return communityStories[Number(BigInt(String(articleId)) % BigInt(communityStories.length))][0];
  } catch {
    return title.replace(/\s#\d+$/, "");
  }
}

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new Error(`요청을 처리하지 못했습니다. (${response.status})`);
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (response.status === 204 || response.headers.get("content-length") === "0" || !contentType.includes("application/json")) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function publishedAt(date: string) {
  const zonedDate = /(?:Z|[+-]\d{2}:\d{2})$/.test(date) ? date : `${date}+09:00`;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Seoul",
  }).format(new Date(zonedDate));
}
