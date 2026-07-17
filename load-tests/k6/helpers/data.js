import { totalArticles, pageSize, userIdRange, envNum, envStr } from "../config.js";

// article_id 생성 방식
//
// 자유게시판 대용량 seed(infra/mysql/seed/seed-articles.sh)는 article_id를
// 7000000000000000000 + n (n = 0 .. 14,999,999) 으로 연속 채번한다. 실측으로 확인한 사실이다.
//   - 7000000000000000000 ~ 7000000000014999999 구간에 정확히 15,000,000건
//   - 8000000000000000000 이상 구간에 데모 게시글 100건 (Snowflake 채번이라 예측 불가)
//
// 이 연속 구간을 쓰면 1,500만 건 전체에 균일하게 접근할 수 있다.
// 반면 목록 응답에서 얻은 ID만 재사용하면 첫 페이지에 보이는 소수 게시글에만 요청이 몰려
// Redis cache hit ratio가 실제보다 좋게 나오고 MySQL I/O 부하도 과소평가된다.
// 그래서 기본은 seeded 모드를 쓰고, 데모 데이터만 있는 환경을 위해 pool 모드를 남겨 둔다.

const SEED_ID_BASE = envNum("SEED_ID_BASE", 7000000000000000000);
const SEED_ID_COUNT = envNum("SEED_ID_COUNT", 15000000);

// seeded: 연속 구간에서 균일 랜덤 (1,500만 건 적재 환경)
// pool:   목록 응답에서 수집한 실제 ID 재사용 (데모 데이터 환경)
const ID_MODE = envStr("ARTICLE_ID_MODE", "seeded");

const idPool = [];
const MAX_POOL = 200;

export function rememberArticleIds(ids) {
  if (ID_MODE !== "pool") {
    return;
  }
  for (const id of ids) {
    if (idPool.length >= MAX_POOL) {
      idPool[Math.floor(Math.random() * MAX_POOL)] = id;
    } else {
      idPool.push(id);
    }
  }
}

export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/** 1,500만 건 전체에 균일하게 분포하는 article ID. 없으면 null. */
export function randomArticleId() {
  if (ID_MODE === "seeded") {
    // JS Number는 2^53까지만 정확하다. 7e18은 이를 넘으므로 BigInt로 계산해 문자열로 넘긴다.
    const offset = BigInt(randomInt(0, SEED_ID_COUNT - 1));
    return (BigInt(SEED_ID_BASE) + offset).toString();
  }
  if (idPool.length === 0) {
    return null;
  }
  return idPool[Math.floor(Math.random() * idPool.length)];
}

/** 충돌을 의도하지 않는 일반 쓰기용 user ID. */
export function uniqueUserId() {
  return randomInt(userIdRange.unique.min, userIdRange.unique.max);
}

/** 좋아요 중복 방지를 검증하기 위해 의도적으로 좁은 범위를 공유하는 user ID. */
export function contendedUserId() {
  return randomInt(userIdRange.contended.min, userIdRange.contended.max);
}

/** 마지막 페이지 번호. pageSize 기준. */
export function lastPage() {
  return Math.floor(totalArticles / pageSize);
}

/**
 * 실제 사용자 분포를 흉내 낸 페이지 번호.
 * 대부분의 사용자는 앞쪽 페이지만 본다. 깊은 페이지는 deep-pagination 시나리오에서 따로 다룬다.
 */
export function realisticPage() {
  const r = Math.random();
  if (r < 0.7) return randomInt(1, 5);
  if (r < 0.9) return randomInt(6, 30);
  return randomInt(31, 200);
}
