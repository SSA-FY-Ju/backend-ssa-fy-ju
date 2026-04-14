# SSAju Git Workflow & Commit Rules

## Test-Then-Commit 프로세스

Claude는 코드를 작성하거나 수정할 때 반드시 아래 프로세스를 엄격하게 준수합니다.

### 1단계: 테스트 필수 실행

기능 구현 또는 리팩토링 후, 코드를 커밋하기 전에 반드시 `SSAju/` 디렉토리에서 테스트 실행:

```bash
cd SSAju/
./gradlew test
```

해당 기능에 대한 테스트 코드가 없다면 **먼저 작성**합니다.

### 2단계: 자가 치유 (Self-Healing)

테스트가 실패(FAILED)할 경우:
1. 에러 로그 분석
2. 코드 수정
3. 테스트 재실행 (통과할 때까지 반복)

### 3단계: 커밋 조건

**오직 모든 테스트가 통과(BUILD SUCCESSFUL)했을 때만** `git commit` 실행:
- 테스트를 우회하거나 무시하고 커밋 금지
- 커밋 이전에 테스트 결과 확인 필수

## 커밋 메시지 규칙 (Conventional Commits)

### 접두사 (Prefix)

커밋 메시지는 반드시 다음 접두사 중 하나로 시작:

| 접두사 | 사용 시점 | 예시 |
|--------|---------|------|
| `feat:` | 새로운 기능 추가 (클래스, 메서드, API 등) | `feat: 사주 결과 저장을 위한 SajuResult 엔티티 추가` |
| `fix:` | 버그 수정 | `fix: FastAPI 타임아웃 예외 처리 개선` |
| `docs:` | 문서 수정 (README, spec.md, CLAUDE.md 등) | `docs: Phase 1 API 명세 업데이트` |
| `refactor:` | 코드 리팩토링 (기능 변화 없음) | `refactor: CareerFortuneService 메서드 분리` |
| `test:` | 테스트 코드 추가/수정/삭제 | `test: 관운 분석 단위 테스트 추가` |
| `chore:` | 설정 파일, 의존성 수정 등 | `chore: Spring AI 의존성 추가` |

### 커밋 메시지 형식

```
<prefix>: <제목>

<본문 설명 (선택사항)>

[Test Passed]
```

### 예시

```
feat: 관운 분석 API 엔드포인트 추가

- POST /api/career/timing 구현
- H1/H2 판정 로직 구현
- CareerTimingResponse DTO 추가

[Test Passed]
```

## 브랜치 및 PR 규칙

### 브랜치 생성

새로운 기능 개발이나 버그 수정을 시작하기 전에 **반드시 `main` 브랜치로부터** 새로운 브랜치 생성:

```bash
git checkout main
git pull origin main
git checkout -b <prefix>/<feature-name>
```

### 브랜치 네이밍 규칙

형식: `<prefix>/<feature-name>`

| 예시 | 설명 |
|------|------|
| `feat/saju-api` | 사주 API 구현 |
| `feat/career-consultation` | AI 커리어 컨설팅 기능 |
| `fix/login-error` | 로그인 오류 수정 |
| `docs/update-spec` | 명세 업데이트 |
| `refactor/service-layer` | Service 계층 리팩토링 |

### 직접 머지 금지

**절대 `main` 브랜치로 직접 병합(Merge)하지 마세요.**

작업 완료 후:
1. 모든 테스트 통과 확인
2. 커밋 완료
3. 원격 저장소에 브랜치 `push`:
   ```bash
   git push origin <prefix>/<feature-name>
   ```
4. 작업 완료 보고
5. **관리자(사용자)가 PR 생성 및 머지 수행**

## 일반적인 워크플로우

```bash
# 1. main에서 새 브랜치 생성
git checkout main
git pull origin main
git checkout -b feat/new-feature

# 2. 코드 작성/수정
# ... 개발 ...

# 3. 테스트 실행 (SSAju 디렉토리에서)
cd SSAju/
./gradlew test
# BUILD SUCCESSFUL 확인

# 4. 커밋
git add <files>
git commit -m "feat: 새로운 기능 추가

설명

[Test Passed]"

# 5. Push
git push origin feat/new-feature

# 6. 관리자 대기 (PR 생성/머지는 관리자가 담당)
```
