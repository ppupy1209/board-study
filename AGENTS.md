# 모두의 광장(modu-square) — AI 에이전트 공유 문서

## 개요 / 목표

이벤트 기반 커뮤니티 서비스. 대규모 게시판에서 자주 쓰이는 기능(게시글·댓글·좋아요·조회수·인기글·알림·이미지)을
멀티 모듈로 분리하고, Kafka + Outbox Pattern으로 서비스 간 이벤트를 유실 없이 전달하는 구조를 실증한다.

강의 예제에 머물지 않고 **실사용 UI · 한 번에 뜨는 로컬 인프라 · 1,500만 건 재현 데이터 · k6 부하 테스트 ·
Prometheus/Grafana 관측성**까지 갖춘 서비스형 프로젝트를 목표로 한다.

상세 배경과 실측치는 [README.md](README.md), 선택 이유와 한계는 [docs/development-log.md](docs/development-log.md)에 있다.

## 기술 스택

### 백엔드
- Java 21 (`sourceCompatibility`/`targetCompatibility` = 21, [build.gradle](build.gradle))
- Spring Boot 3.5.4 / Spring Web / Spring Data JPA / Spring Kafka / Spring Data Redis
- Lombok, JUnit 5
- Gradle 8.14.3 (wrapper), 멀티 모듈 — 루트 `allprojects` 블록에서 공통 플러그인·의존성 일괄 적용

### 프론트
- Next.js 16.2.6 + React 19.2.6 + TypeScript 5.9.3
- 빌드 러너는 `vinext` 0.0.50 (Vite 8 기반), Node >= 22.13.0
- 위치: [web/](web)

### 인프라
- MySQL 8.4, Redis 7.4, Kafka 3.8.0, MinIO(S3 호환 오브젝트 스토리지)
- Prometheus 3.2.1 + Grafana 11.5.2 + mysqld/redis/kafka exporter
- k6 0.57.0 (Prometheus remote write output — **experimental**이라 이미지 버전 고정)
- 전부 [docker-compose.yml](docker-compose.yml) 한 파일로 기동

## 실행·테스트 명령

> **중요 — 이 머신의 JDK 설정**
> 시스템 `JAVA_HOME`은 JDK 8(`C:\Program Files\AdoptOpenJDK\jdk-8.0.292.10-hotspot`)이라
> 그대로 Gradle을 돌리면 Spring Boot 플러그인 3.5.4가 "requires at least JVM runtime version 17"로 실패한다.
> `C:\Users\SAMSUNG\.gradle\gradle.properties`의 `org.gradle.java.home`으로 Gradle 데몬만 Temurin 21로 고정해 두었다.
> (JAVA_HOME은 건드리지 않음.) 확인: `./gradlew --version` 출력의 `Daemon JVM ... (from org.gradle.java.home)`.
> IntelliJ는 Gradle JVM이 `#JAVA_HOME`으로 잡혀 있으므로 Settings → Build Tools → Gradle → Gradle JVM을 `temurin-21`로 지정할 것.

### 백엔드
```bash
./gradlew build
```
```bash
./gradlew test
```
```bash
./gradlew :service:hot-article:test
```
```bash
./gradlew compileJava compileTestJava
```

### 프론트 ([web/](web) 디렉터리)
```bash
npm run dev
```
```bash
npm run build
```
```bash
npm test
```
```bash
npm run lint
```

### 전체 스택 (로컬)
```bash
docker compose up --build
```

기본 웹 포트는 3000. 충돌 시 루트 `.env`에 `WEB_PORT=3002` 지정.

| 대상 | 주소 |
|---|---|
| Web UI | http://localhost:3000 |
| Grafana | http://localhost:3001 (`admin`/`admin`, 로컬 전용) |
| Prometheus | http://localhost:9090 |
| article / comment / like / view | 9000 / 9001 / 9002 / 9003 |
| hot-article / article-read / notification / media | 9004 / 9005 / 9006 / 9007 |
| MinIO Console | http://localhost:9101 (`modu-square`/`local-development`, 로컬 전용) |
| MySQL | localhost:3307 |

### 부하 테스트
```bash
TEST_ID=smoke-1 docker compose run --rm k6 run -o experimental-prometheus-rw /scripts/smoke.js
```
전체 시나리오(average / breakpoint / deep-pagination / mixed / spike / soak / kafka-recovery / cache-penetration /
hot-article-list / media-upload)는 [load-tests/k6/](load-tests/k6)와 README의 "부하 테스트 스위트" 참고.

## 아키텍처 결정 로그

| 날짜 | 결정 | 이유 |
|---|---|---|
| 2026-08-07 | Gradle 데몬 JDK를 사용자 홈 `gradle.properties`의 `org.gradle.java.home`으로 21 고정 | 머신 기본 JAVA_HOME이 JDK 8. toolchain으로는 해결 불가 — Spring Boot 플러그인 classpath 해석 단계에서 데몬 JVM 자체가 17+ 여야 함. JAVA_HOME을 바꾸면 레거시 Java 8 프로젝트가 영향받으므로 Gradle 범위로 한정 |
| 2026-08-06 | 인기글: 자정 직후 전날 목록 유지 + 유예 중 전날 변경 반영 | 자정에 목록이 빈 채로 노출되는 단절을 없애되, 유예 구간의 변경도 놓치지 않기 위해 |
| 2026-08-04 | article-read: 404를 `MISSING`으로 60~70초 부재 캐시, Redis 잠금으로 콜드 미스 원본 조회 단일화 | 캐시 관통 방지. 원본 조회 55,688건 → 1건, p95 859ms → 1.26ms. 원본 장애를 부재로 오인하지 않도록 404와 통신 오류를 분리 |
| 2026-08-04 | media: Presigned URL 직접 업로드 + Kafka 비동기 WebP 변환 | 애플리케이션을 이미지 바이트 경로에서 제거해 전송 비용 절감 |
| 2026-07-31 | notification: 인기 게시글 묶음 알림 | 이벤트당 개별 알림의 증폭을 억제 |
| 2026-07-31 | hot-article 조회 모델 분리 + 전용 부하 테스트 | Top 10 조회의 호출 증폭(1:4) 확인 후 CQRS 조회 모델과 비교하기 위해 |
| (이전) | Transactional Outbox — **전송 확인 후에만 삭제** + 미발행 재처리 | Kafka 60초 장애에서 이벤트 유실 0건 달성 |
| (이전) | 이벤트 멱등성: 절대값 반영 | 중복 소비에도 결과가 같도록 |
| (이전) | count 행 원자적 upsert | 동시성 하에서 deadlock 제거 |
| (이전) | 목록 조회: Covering Index + ID 선조회 JOIN, 무한 스크롤은 Keyset | OFFSET 깊은 페이지 9.42초 → Keyset 0.0055초 (약 1,700배) |
| (이전) | Snowflake 분산 ID | 서비스 분리 환경에서 충돌 없는 ID 생성 |

## 컨벤션

- **커밋 메시지**: Conventional Commits + **한글 본문**. `feat(hot-article): 자정 직후 전날 인기글 유지`,
  `fix(web): ...`, `docs(infra): ...`. 파괴적 변경은 `feat(scope)!:`.
- **스코프**는 모듈명을 그대로 쓴다 (`article`, `article-read`, `hot-article`, `media`, `notification`, `web`, `infra`).
- **문서도 산출물로 취급**한다. 성능·장애 실험은 수치와 함께 `docs/`에 기록하고 README의 실측 요약과 어긋나지 않게 유지한다.
- **k6 결과물**(`load-tests/results/`)은 gitignore 대상. 문서화된 수치만 `docs/performance-results/`에 남긴다.
- ⚠️ **`src/test` 아래 `DataInitializer` 계열은 테스트가 아니라 수동 데이터 적재 도구다.**
  `@Test`가 붙어 있어 `./gradlew test`에 딸려 돌면 자유게시판에 1,200만 건을 추가해 데이터셋을 오염시킨다.
  현재 `@Disabled`로 막아 두었으니 **절대 풀지 말 것**.
- ⚠️ **`docker compose down -v` 금지.** `mysql-data` 볼륨의 1,500만 건이 사라진다. `stop`/`restart`/`down`은 안전.
- 대용량 시드는 최초 1회만: `docker compose --profile large-data run --rm seed-large` (중단해도 마지막 10만 건 배치부터 이어짐).

## 현재 진행 상태

- **완료**
  - 8개 서비스 모듈 + 4개 common 모듈 구성, Docker Compose 단일 명령 기동
  - Outbox 기반 이벤트 전달, 멱등 소비, Snowflake ID
  - article-read CQRS 조회 모델 + Request Collapsing + 캐시 관통 방지(부재 캐시 + Redis 잠금)
  - hot-article 인기글 점수 계산과 자정 만료/유예 정책 (2026-08-06)
  - media 이미지 첨부(Presigned URL + 비동기 WebP)
  - notification 묶음 알림
  - Prometheus/Grafana 관측성, k6 부하 테스트 스위트 10종
  - Java 21 Gradle 빌드 환경 정리 (2026-08-07) — `compileJava compileTestJava` 전 모듈 통과 확인

- **진행 중**
  - (없음 — 마지막 작업인 hot-article 자정 정책이 041e510으로 마무리됨)

- **다음 작업** (docs/development-log.md "11. 다음 개선" 기준)
  - Query Model 전체 재구축과 체크포인트
  - Kafka 파티션 확대 시 key ordering 검증
  - Testcontainers 기반 통합 테스트
  - k6 결과를 CI artifact로 저장하고 이전 기준선과 비교
  - UI의 글쓰기·댓글·좋아요 흐름을 API와 완전 연결
