# Modu Square

Modu Square는 오늘의 생각을 나누고, 다른 사람의 취향과 질문을 발견하는 커뮤니티입니다.

![Modu Square 메인 화면](docs/images/modu-square-home.png)

## 주요 기능

### 주제별 커뮤니티

![주제별 커뮤니티 화면](docs/images/modu-square-community.png)

전체, 일상, 취향, 질문, 동네, 여행 중 관심 있는 주제를 골라 둘러보세요. 

### 글과 대화

![게시글 상세 화면](docs/images/modu-square-article.png)

게시글을 열면 내용과 조회수, 좋아요, 댓글을 한곳에서 확인할 수 있습니다.

### 오늘의 인기글

![오늘의 인기글 Top 10 화면](docs/images/modu-square-popular.png)

좋아요, 댓글, 조회수를 모아 오늘의 인기글 Top 10을 보여줍니다. 

## 로컬 실행

Git과 Docker Desktop이 필요합니다. Docker Desktop을 먼저 실행한 뒤 아래 순서대로 진행하세요.

### 1. 저장소 받기

```bash
git clone https://github.com/ppupy1209/modu-square.git
cd modu-square
```

### 2. 서비스 시작하기

```bash
docker compose up --build -d
```

처음에는 이미지를 내려받고 서비스를 빌드하느라 몇 분 정도 걸릴 수 있습니다. 준비 상태는 다음 명령으로 확인합니다.

```bash
docker compose ps
```

준비가 끝나면 브라우저에서 `http://localhost:3000`을 여세요. 첫 실행에는 주제별 게시글과 댓글, 좋아요, 조회수, 인기글 데이터가 함께 들어갑니다. 가입하거나 데이터를 직접 만들지 않아도 주요 기능을 바로 둘러볼 수 있습니다.

### 3. 서비스 종료하기

```bash
docker compose down
```

게시글과 첨부 이미지를 비롯한 로컬 데이터는 Docker 볼륨에 남습니다. 데이터를 모두 지우고 처음 상태로 되돌릴 때만 아래 명령을 사용하세요.

```bash
docker compose down -v
docker compose up --build -d
```
