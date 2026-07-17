# board-service

게시글과 댓글을 관리하는 **게시판 마이크로서비스** (`:9100`).
모든 요청은 `gateway-server(:8000)`를 통해 `/api/board/**`로 라우팅된다.

> 별도 git repo: `github.com/chanho4702/backend-server` (브랜치 `main`). 우산 repo(MSA_TEMPLATE)에서는 gitignore 됨.

이 서비스는 **도메인 서비스의 표준 패턴 예시**다. 유레카 자기등록 → 게이트웨이 뒤 라우팅 → auth-server JWT 자체 검증(JWKS) → JPA/Flyway 영속화로 이어지는 한 벌을 갖췄고, 새 도메인 서비스를 만들 때 이 구조를 복제한다. (맨 아래 [체크리스트](#복제해-새-도메인-서비스를-만들-때-바꿀-것) 참고.)

---

## 역할 / 아키텍처

- **게시글(Post)** CRUD — 목록 조회(페이지, 최신순), 단건 조회, 생성, 수정, 삭제.
- **댓글(Comment)** CRUD — 게시글별 댓글 목록(페이지, 오래된순), 생성, 수정, 삭제.
- **유레카 자기등록** — `eureka-server(:8761)`에 `board-service`로 등록. 게이트웨이가 `lb://board-service`로 이 등록을 해석해 라우팅한다. `prefer-ip-address: true`로 IP 등록(Windows/사설망 호스트명 DNS 해석 실패 회피).
- **JWT 자체 검증** — `auth-server(:9000)`의 JWKS(RS256 공개키)로 서명을 검증하고, issuer·audience까지 추가 검증한다. 게이트웨이는 토큰을 검증하지 않는다(각 서비스 책임).
- **인가** — 조회(GET)는 공개, 생성/수정/삭제는 인증 필요. 수정·삭제는 **작성자 본인 또는 ADMIN**만 가능(서비스 계층 `AccessGuard`).
- **CORS는 게이트웨이가 담당한다.** board-service는 CORS를 직접 설정하지 않는다.

---

## 기술 스택 (실측)

| 항목 | 값 |
|---|---|
| 언어 / JDK | Java 24 (Gradle toolchain `languageVersion 24`) |
| 프레임워크 | Spring Boot 4.0.6 |
| Spring Cloud | 2025.1.2 (Netflix Eureka Client) |
| 보안 | Spring Security · OAuth2 Resource Server (JWT) |
| 영속화 | Spring Data JPA · Hibernate (`ddl-auto: validate`) |
| 마이그레이션 | Flyway (core + postgresql) |
| DB | PostgreSQL (런타임), H2 (테스트) |
| 빌드 | Gradle Wrapper 9.5.1, `bootJar` → `app.jar` |
| 기타 | Lombok 1.18.46, Bean Validation |

---

## API 엔드포인트

모든 경로는 `gateway-server(:8000)`를 통해 접근한다. 인증이 필요한 요청은 `Authorization: Bearer <자체 JWT>` 헤더를 붙인다(auth-server가 발급한 AT).

### 게시글 — `PostController` (`/api/board/posts`)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/board/posts` | - | 게시글 목록. 페이지(기본 size 20, `createdAt` DESC). 요약 필드만 반환 |
| GET | `/api/board/posts/{id}` | - | 게시글 단건 조회. 본문 포함 |
| POST | `/api/board/posts` | Bearer | 게시글 생성 (201 Created) |
| PUT | `/api/board/posts/{id}` | Bearer | 게시글 수정 (작성자 본인 또는 ADMIN) |
| DELETE | `/api/board/posts/{id}` | Bearer | 게시글 삭제 (작성자 본인 또는 ADMIN, 204). 댓글 함께 삭제 |

### 댓글 — `CommentController` (`/api/board`)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/board/posts/{postId}/comments` | - | 댓글 목록. 페이지(기본 size 50, `createdAt` ASC) |
| POST | `/api/board/posts/{postId}/comments` | Bearer | 댓글 생성 (201 Created) |
| PUT | `/api/board/comments/{id}` | Bearer | 댓글 수정 (작성자 본인 또는 ADMIN) |
| DELETE | `/api/board/comments/{id}` | Bearer | 댓글 삭제 (작성자 본인 또는 ADMIN, 204) |

인가 규칙은 `SecurityConfig`에서 `GET /api/board/posts/**`만 `permitAll`, 나머지는 `authenticated`로 강제한다. 게시글 요약 목록(`PostSummaryResponse`)은 `id·title·authorName·createdAt`만, 단건(`PostResponse`)은 본문·작성자·수정시각까지 반환한다.

### 에러 응답 (`ApiExceptionHandler`)

| 상황 | 상태 | 바디 `error` |
|---|---|---|
| 리소스 없음 | 404 | `not_found` |
| 권한 없음(본인/ADMIN 아님, subject 형식 오류) | 403 | `forbidden` |
| 검증 실패(`@NotBlank` 등) | 400 | `validation_failed` |

---

## JWT 검증 방식 (JWKS — 코드 실측)

`SecurityConfig`는 Resource Server로 동작하며, 다음 3단계로 토큰을 검증한다.

1. **서명 검증** — `NimbusJwtDecoder.withJwkSetUri(jwkSetUri)`로 auth-server JWKS(`/.well-known/jwks.json`)의 RS256 공개키로 서명 확인.
2. **issuer + audience 검증** — `DelegatingOAuth2TokenValidator`로 기본 검증(만료·issuer, `JwtValidators.createDefaultWithIssuer`)에 더해 `AudienceValidator`가 `aud` 클레임에 `platform-api`가 포함됐는지 확인. 다른 발급자·다른 대상의 토큰은 거부.
3. **권한 매핑** — `roles` 클레임 → `ROLE_*` 권한(`JwtGrantedAuthoritiesConverter`, prefix `ROLE_`). auth-server와 동일 규약.

세션은 `STATELESS`, CSRF는 비활성(순수 토큰 API).

작성자 판정은 `AccessGuard`(서비스 계층)가 담당한다.
- `currentUserId(auth)` — JWT `sub`를 `Long`으로 파싱(=userId). 숫자가 아니면 500이 아니라 **403**.
- `currentUserName(auth)` — JWT `name` 클레임(표시용, 없으면 `sub`).
- `requireOwnerOrAdmin(authorId, auth)` — `ROLE_ADMIN`이면 통과, 아니면 `authorId == currentUserId`일 때만 통과, 그 외 403.

---

## 데이터 모델 (엔티티 실측)

`GenerationType.IDENTITY`(DB auto-increment). 작성자 정보(`author_id`, `author_name`)는 JWT에서 추출해 저장하며, 외부 사용자 서비스로 조인하지 않는다.

### `post` — `Post`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `title` | VARCHAR(255) NOT NULL | |
| `content` | TEXT NOT NULL | |
| `author_id` | BIGINT NOT NULL | JWT `sub` |
| `author_name` | VARCHAR(255) NOT NULL | JWT `name` |
| `created_at` / `updated_at` | TIMESTAMP NOT NULL | 엔티티에서 `Instant.now()` 세팅 |

### `comment` — `Comment`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `post_id` | BIGINT NOT NULL | `REFERENCES post(id) ON DELETE CASCADE`, `idx_comment_post` |
| `content` | TEXT NOT NULL | |
| `author_id` | BIGINT NOT NULL | JWT `sub` |
| `author_name` | VARCHAR(255) NOT NULL | JWT `name` |
| `created_at` / `updated_at` | TIMESTAMP NOT NULL | |

> 게시글 삭제 시 서비스가 `commentRepository.deleteByPostId(id)`를 먼저 호출해 댓글을 정리한다(H2/PG 모두 보장). DB의 `ON DELETE CASCADE`는 이중 안전장치.

스키마는 **Flyway로 관리**하고 JPA는 `ddl-auto: validate`로 엔티티와 스키마 일치만 검증한다(스키마 자동생성 안 함).

| 버전 | 파일 | 내용 |
|---|---|---|
| V1 | `db/migration/V1__init.sql` | `post` 테이블 |
| V2 | `db/migration/V2__add_comment.sql` | `comment` 테이블 + FK + 인덱스 |

---

## DB 연결 · 환경변수

`application.yml`의 모든 자격증명/URL은 env로 오버라이드 가능. 기본값은 로컬 dev 전용이며 **운영에서는 반드시 주입**한다.

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `BOARD_DB_URL` | `jdbc:postgresql://localhost:5433/boarddb` | 데이터소스 URL |
| `BOARD_DB_USERNAME` | `keycloak` | DB 사용자 |
| `BOARD_DB_PASSWORD` | `keycloak` | DB 비밀번호 |
| `AUTH_JWKS_URI` | `http://localhost:9000/.well-known/jwks.json` | JWKS 엔드포인트 |
| `EUREKA_URI` | `http://localhost:8761/eureka` | 유레카 등록 주소 |
| `PLATFORM_ISSUER` | `http://localhost:9000` | 기대 issuer |
| `PLATFORM_AUDIENCE` | `platform-api` | 기대 audience |

고정 설정: `server.port=9100`, `jpa.open-in-view=false`, `eureka.instance.prefer-ip-address=true`.

---

## 실행 방법

**전제:** Keycloak + Postgres가 먼저 떠 있어야 한다 → [`../infra/README.md`](../infra/README.md). auth-server(:9000)도 기동 상태여야 한다(JWKS 필요). 유레카(:8761)는 등록 대상 — 없어도 기동은 되나 게이트웨이 라우팅은 등록 후 활성화.

### gradlew bootRun

```powershell
# Windows PowerShell — JDK 24 필요
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
.\gradlew.bat bootRun        # :9100 기동
```

### IntelliJ (.run 공유 설정)

repo에 커밋된 `.run/bootRun.run.xml`(Gradle `bootRun` 태스크)이 실행 버튼에 자동으로 나타난다. IDE에서 재시작·디버깅한다. 우산 repo의 `scripts\dev-up.ps1`은 **백엔드를 IntelliJ에 맡기고** 인프라·프론트만 올린다.

### 테스트 / 빌드

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
.\gradlew.bat test           # JUnit 전체
.\gradlew.bat bootJar        # build/libs/app.jar
```

테스트는 `application-test.yml`로 **H2 인메모리(PostgreSQL 모드) + `ddl-auto: create-drop`**, Flyway·유레카 비활성으로 돈다(Keycloak/auth-server/DB 불필요). JWKS URI는 값만 있으면 되고 오프라인에서 컨텍스트가 뜬다.

### Docker

`Dockerfile`은 **런타임 전용**이다. `gradlew bootJar` 산출물(`build/libs/app.jar`)을 먼저 만든 뒤 이미지를 빌드한다.

```dockerfile
FROM eclipse-temurin:24-jre   # JRE 24
COPY build/libs/app.jar app.jar
EXPOSE 9100
```

---

## 프로젝트 구조

```
board-service/
├─ build.gradle                         Spring Boot 4.0.6 · Java 24 · bootJar→app.jar
├─ Dockerfile                           런타임 전용(temurin 24-jre, app.jar 복사)
├─ .run/bootRun.run.xml                 IntelliJ 공유 Run Config
└─ src/
   ├─ main/java/com/platform/boardservice/
   │  ├─ BoardServiceApplication.java
   │  ├─ post/        Post · PostRepository · PostService · PostController + dto/
   │  ├─ comment/     Comment · CommentRepository · CommentService · CommentController + dto/
   │  ├─ security/    AccessGuard(본인/ADMIN 판정) · AudienceValidator(aud 검증)
   │  ├─ common/      ApiExceptionHandler · NotFoundException
   │  └─ config/      SecurityConfig(resource-server · JWKS · roles→ROLE_*)
   ├─ main/resources/
   │  ├─ application.yml                포트·DB·JWKS·유레카·issuer/audience
   │  └─ db/migration/                  Flyway: V1(post) · V2(comment)
   └─ test/
      ├─ java/.../                      Controller·Service·Repository·AccessGuard·AudienceValidator 테스트
      └─ resources/application-test.yml H2 + Flyway/유레카 off
```

---

## 복제해 새 도메인 서비스를 만들 때 바꿀 것

이 서비스를 그대로 복사해 예: `order-service`를 만든다고 할 때 손댈 지점(실측 기반):

1. **`settings.gradle` / `build.gradle`** — `rootProject.name`, `group` 유지, 필요 시 의존성 가감.
2. **패키지명** — `com.platform.boardservice` → `com.platform.orderservice`, `BoardServiceApplication` 클래스명.
3. **`application.yml`**
   - `spring.application.name`: `board-service` → 새 서비스명(유레카 등록 ID = 게이트웨이 `lb://` 대상).
   - `server.port`: `9100` → 새 포트.
   - `BOARD_DB_*` 환경변수 접두어와 기본 DB(`boarddb`) → 새 DB.
   - `platform.jwt.issuer/audience`는 **그대로 유지**(같은 auth-server·같은 플랫폼 토큰).
4. **`SecurityConfig`** — `permitAll` 경로(`/api/board/posts/**`)를 새 도메인 공개 규칙으로. issuer/audience·roles 매핑 규약은 **유지**.
5. **도메인 코드** — `post`/`comment` 패키지를 새 엔티티·DTO·컨트롤러·서비스로 교체. `AccessGuard`(본인/ADMIN 판정)와 `AudienceValidator`, `ApiExceptionHandler`는 재사용.
6. **`db/migration`** — `V1`부터 새 스키마로. `ddl-auto: validate`이므로 엔티티와 반드시 일치시킬 것.
7. **`Dockerfile`의 `EXPOSE`** 포트, **게이트웨이 라우트**(`gateway-server`에 `/api/<new>/**` → `lb://<new>-service` 추가).
8. **컨트롤러 경로 접두어** — `/api/board` → `/api/<new>`(게이트웨이 라우트와 일치시킬 것).
