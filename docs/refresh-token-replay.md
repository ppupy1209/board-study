# 로그아웃했는데 공격자의 세션은 끝나지 않았다

## 문제 발생

Guest로 둘러보던 서비스에 글쓰기 인증을 추가하려면 Access Token만으로는 충분하지 않았다. 수명을 길게 두면 탈취 피해가 오래가고, 짧게 두면 사용자가 몇 분마다 다시 로그인해야 했다. 그래서 Access Token은 5분, Refresh Token은 14일로 나누는 세션 갱신을 설계했다.

그 과정에서 로그아웃을 브라우저 쿠키 삭제로만 처리하면 생기는 빈틈을 발견했다. 브라우저에서 쿠키가 사라져도 공격자가 미리 복사한 Refresh Token은 없어지지 않는다. 서버가 해당 Token의 상태를 기억하지 않는다면 사용자가 로그아웃한 뒤에도 공격자는 최대 14일 동안 새 Access Token을 발급받을 수 있다.

네트워크 재시도와 탈취 요청이 같은 Refresh Token을 거의 동시에 제출하는 상황도 문제였다. 두 요청을 모두 정상 갱신으로 받아들이면 유효한 Token이 두 갈래로 갈라지고, 이후에는 어느 쪽이 사용자 것인지 구분할 수 없다.

이 프로젝트에는 실사용자가 없으므로 실제 침해 사고로 표현하지 않았다. Docker 환경에서 동일 Refresh Token을 동시에 두 번 제출하는 공격 시나리오를 만들고, 이를 실패시키는 것을 완료 조건으로 삼았다.

## 비교한 선택지

| 선택지 | 장점 | 포기한 이유 |
|---|---|---|
| 긴 수명의 JWT 하나만 사용 | DB 조회 없이 단순함 | 탈취·로그아웃 뒤 서버에서 즉시 폐기하기 어려움 |
| 고정 Refresh Token을 서버에 저장 | 로그아웃 폐기는 가능 | 로그아웃 전 복제되거나 갱신 요청이 겹치면 재사용을 탐지할 수 없음 |
| 모든 Access Token 요청마다 세션 조회 | 즉시 차단 가능 | 각 서비스의 모든 요청이 Auth DB 상태에 결합됨 |
| Refresh Token Rotation + Token Family | 이전 Token 재등장을 침해 신호로 식별 가능 | 상태 저장과 동시성 제어가 필요함 |
| DPoP·mTLS로 Token을 클라이언트 키에 결속 | 탈취 Token 단독 사용을 강하게 제한 | 브라우저 기반 개인 프로젝트에 키 수명주기 운영 복잡도가 큼 |

OAuth 2.0 보안 모범 사례인 [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)도 공개 클라이언트의 Refresh Token에 대해 sender-constrained 방식 또는 Rotation을 요구한다. 현재 브라우저 구조에서는 Rotation이 재현 가능성과 운영 복잡도의 균형이 가장 좋다고 판단했다.

## 해결

### 1. Token 값이 아니라 상태와 계보를 저장

- Refresh Token은 `SecureRandom` 256비트 난수로 발급한다.
- DB에는 원문 대신 SHA-256 해시만 저장한다. DB가 노출돼도 저장값만으로 Token을 제출할 수 없다.
- 로그인마다 `family_id`를 만들고, 회전된 Token을 `replaced_by_token_id`로 연결한다.
- 상태를 `ACTIVE → ROTATED → REVOKED`로 구분한다.
- Access Token에는 세션 계보를 식별하는 `sid` claim을 넣는다.

### 2. 회전은 한 트랜잭션에서 한 요청만 성공

같은 Refresh Token에 대한 동시 요청은 대상 행을 비관적 잠금으로 조회한다. 첫 요청만 기존 Token을 `ROTATED`로 바꾸고 다음 Token을 만든다. 잠금을 기다린 두 번째 요청은 이미 회전된 Token을 보게 되므로 정상 재시도가 아니라 재사용으로 판단한다.

낙관적 락 재시도도 검토했지만, 이 충돌은 재시도해 성공시킬 일이 아니라 세션 탈취 가능성을 알리고 차단해야 하는 사건이다. Refresh 호출 빈도도 일반 API보다 낮아 짧은 행 잠금을 선택했다.

### 3. 이전 Token이 돌아오면 방금 발급한 Token도 폐기

재사용이 감지되면 같은 `family_id`에 속한 Token을 한 번의 UPDATE로 모두 `REVOKED` 처리한다. 공격자와 정상 사용자 중 누가 이전 Token을 보냈는지 서버는 알 수 없으므로 양쪽 세션을 모두 끊고 다시 로그인하게 하는 쪽을 택했다.

여기서 예외 처리 자체가 또 다른 함정이었다. 재사용을 감지한 뒤 일반적인 RuntimeException을 던지면 Spring 트랜잭션이 롤백되어, 방금 수행한 패밀리 폐기까지 되돌아갈 수 있다. 인증 오류 응답은 401로 반환하되 보안 상태 변경은 반드시 커밋되도록 해당 예외를 `noRollbackFor`로 분리했다.

### 4. 브라우저와 서버 양쪽에서 노출 범위 제한

- Refresh Token은 응답 JSON에 넣지 않고 `HttpOnly`, `SameSite=Strict`, 경로 `/v1/auth` 쿠키로만 전달한다.
- Access Token은 5분 뒤 만료된다.
- 로그아웃은 쿠키 삭제와 서버의 Token Family 폐기를 함께 수행한다.
- 재사용·만료·폐기 응답에서도 Refresh 쿠키를 즉시 제거한다.
- 원문 Token은 로그에 남기지 않는다.

## 운영에서 보이게 만들기

재사용을 자동 차단해도 관찰하지 못하면 계정 탈취 징후를 놓친다. 결과값을 제한한 고정 cardinality 지표를 추가했다.

```text
auth_refresh_attempts_total{result="success|invalid|expired|revoked|reuse_detected"}
auth_refresh_reuse_detected_total
auth_token_family_revocations_total{reason="logout|reuse_detected|member_not_found"}
```

Prometheus에는 최근 5분간 재사용이 한 번이라도 감지되면 `RefreshTokenReuseDetected` 경보가 활성화되는 규칙을 등록했다. 현재 로컬 구성에는 Alertmanager가 없으므로 Prometheus UI에서 경보 상태를 확인하는 범위이며, 외부 알림 채널 연동은 배포 환경의 과제로 남겼다.

## 검증

MySQL 8.4 Testcontainers 통합 테스트에서 다음 세 경우를 검증했다.

1. 정상 회전 뒤 이전 Token을 다시 제출하면 신규 Token까지 패밀리 전체가 폐기된다.
2. 로그아웃 뒤 탈취된 Token으로는 새 Access Token을 발급할 수 없다.
3. 같은 Token을 두 요청이 동시에 갱신하면 한 건만 먼저 성공하고, 재사용 탐지 직후 그 성공 응답의 신규 Token도 폐기된다.

실제 Docker 네트워크에서도 k6 `http.batch`로 같은 Token을 동시에 제출했다.

```bash
docker compose up -d --build mysql media-migrate auth
docker compose run --rm --no-deps k6 run /scripts/auth-refresh-replay.js
```

2026-09-01 로컬 실행 결과, 회원가입·로그인·동시 갱신 2건·신규 Token 재시도까지 총 5개 HTTP 요청의 check가 100% 통과했다. 서비스 지표도 `success=1`, `reuse_detected=1`, `revoked=1`, `family_revocations{reason="reuse_detected"}=1`로 시나리오와 일치했다. 이 테스트의 목적은 최대 처리량 측정이 아니라 요청 순서와 DB 상태 전이가 실제 HTTP에서도 일치하는지 확인하는 것이다.

## Guest와 OAuth의 경계

이번 변경은 기존 게시글·댓글·좋아요 API에 인증을 강제하지 않는다. 따라서 Docker Compose로 실행한 뒤 가입 없이 둘러보는 Guest 흐름은 그대로 유지된다. 향후에는 조회는 Guest로 두고 쓰기 요청만 인증하도록 단계적으로 연결할 수 있다.

OAuth2/OIDC를 추가할 때도 공급자 callback 이후의 세션 발급을 동일한 Token Family 로직으로 합칠 수 있다. 공급자 연동 테스트는 가짜 OIDC 서버로 `authorization code → callback → 내부 회원 매핑 → 세션 발급`을 자동 검증하고, Google·Kakao 같은 실제 공급자는 환경변수로 주입한 개발용 Client ID/Secret과 redirect URI로 별도 확인할 계획이다. 공급자 비밀값이 없어도 핵심 Token 회전·재사용 방어 테스트는 독립적으로 실행된다.

## 남은 한계

- 재사용 탐지 뒤에도 이미 발급된 Access Token은 최대 5분간 유효하다. 즉시 차단하려면 각 서비스가 `sid` 폐기 상태를 조회하거나 짧은 TTL의 denylist를 공유해야 하지만, 모든 요청의 Auth 의존성과 가용성 비용이 생긴다.
- SameSite 정책은 실제 OAuth 공급자의 redirect 방식에 맞춰 재검토해야 한다. 현재 일반 로그인에는 `Strict`를 적용했다.
- 비정상 로그인 시도 제한, 계정 복구, 키 교체와 다중 서명 키 운영은 이번 범위에 포함하지 않았다.
- Token 이력 정리 배치는 아직 없다. 만료된 패밀리를 보존 기간 뒤 삭제하는 운영 정책이 필요하다.
