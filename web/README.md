# 모두의 광장 Web

자유게시판 중심 커뮤니티 서비스의 로컬 웹 UI입니다. Apple 디자인 원칙을 참고한 절제된 모션, 반투명 소재, 명확한 계층과 접근성 설정을 적용했습니다.

## 로컬 실행

전체 시스템은 저장소 루트에서 실행합니다.

```bash
docker compose up --build
```

- Web: <http://localhost:3000>
- Grafana: <http://localhost:3001>
- Prometheus: <http://localhost:9090>

웹만 개발할 때는 Node.js 22.13 이상에서 다음을 실행합니다.

```bash
npm ci
npm run dev
```

## 검증

```bash
npm test
npm run lint
```
