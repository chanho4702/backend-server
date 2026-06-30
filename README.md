# board-service

게시글과 댓글을 관리하는 **게시판 마이크로서비스**.
모든 요청은 `gateway-server(:8000)`를 통해 라우팅된다.

> 별도 git repo: `github.com/chanho4702/backend-server` (브랜치 `main`). 우산 repo(MSA_TEMPLATE)에서는 gitignore 됨.

---

## 역할 / 아키텍처

- **게시글(Post)** CRUD — 목록 조회(페이지), 단건 조회, 생성, 수정, 삭제.
- **댓글(Comment)** CRUD — 게시글별 댓글 목록, 생성, 수정, 삭제.
- **JWT 검증** — `auth-server(:9000) /.well-known/jwks.json`의 RS256 공개키로 자체 검증(Spring Security Resource Server). 게이트웨이는 토큰을 검증하지 않는다.
- **CORS는 게이트웨이가 담당한다.** board-service는 CORS를 직접 설정하지 않는다.

## 기술 스택

Spring Boot 4.0.6 · Java 24 · Gradle · Spring Security(resource-server) · Spring Data JPA · Flyway · PostgreSQL · Lombok

---

## 빠른 시작

**전제:** Keycloak + Postgres가 먼저 떠 있어야 한다 → [`../infra/README.md`](../infra/README.md) 참고.  
auth-server(:9000)도 기동 상태여야 한다 (JWKS 엔드포인트 필요).

```powershell
# Windows PowerShell — JDK 24 필요
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
.\gradlew.bat bootRun        # :9100 기동
```

---

## 엔드포인트

모든 경로는 `gateway-server(:8000)` 를 통해 `/api/board/**` 로 접근한다.

### 게시글 (`/api/board/posts`)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/board/posts` | - | 게시글 목록 (페이지, 최신순) |
| GET | `/api/board/posts/{id}` | - | 게시글 단건 조회 |
| POST | `/api/board/posts` | Bearer(자체 JWT) | 게시글 생성 |
| PUT | `/api/board/posts/{id}` | Bearer(자체 JWT) | 게시글 수정 (작성자 본인) |
| DELETE | `/api/board/posts/{id}` | Bearer(자체 JWT) | 게시글 삭제 (작성자 본인) |

### 댓글 (`/api/board/posts/{postId}/comments`, `/api/board/comments`)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/board/posts/{postId}/comments` | - | 댓글 목록 (페이지, 오래된순) |
| POST | `/api/board/posts/{postId}/comments` | Bearer(자체 JWT) | 댓글 생성 |
| PUT | `/api/board/comments/{id}` | Bearer(자체 JWT) | 댓글 수정 (작성자 본인) |
| DELETE | `/api/board/comments/{id}` | Bearer(자체 JWT) | 댓글 삭제 (작성자 본인) |

---

## 설정 (`src/main/resources/application.yml`)

| 키 | 기본값 |
|---|---|
| `server.port` | 9100 |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/boarddb` (user/pw `keycloak`) |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `http://localhost:9000/.well-known/jwks.json` |

---

## 테스트 / 빌드

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
.\gradlew.bat test     # JUnit 전체
```

- H2 인메모리 + Hibernate 자동 DDL 사용(Flyway/Keycloak/auth-server 불필요).
- 오프라인 JWT 디코더로 Spring Security 컨텍스트를 기동한다.

---

## 디렉토리

```
src/main/java/com/platform/boardservice/
├─ BoardServiceApplication.java
├─ post/        Post · PostRepository · PostService · PostController + dto/
├─ comment/     Comment · CommentRepository · CommentService · CommentController + dto/
├─ security/    AccessGuard                              (작성자 본인 확인)
├─ common/      ApiExceptionHandler · NotFoundException
└─ config/      SecurityConfig                           (resource-server + permitAll 규칙)
src/main/resources/db/migration/                         (Flyway: posts, comments)
```
