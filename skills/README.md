# SSAju 개발 규칙 가이드

SSAju 프로젝트 개발 시 따라야 할 **일반적인 규칙과 패턴**을 모아놓은 폴더입니다.

**중요**: 이 폴더는 **SSAju 프로젝트의 구체적인 구현** (엔티티, API 구조, 기능 명세)을 담지 않습니다. 구체적인 내용은 spec.md와 plan.md에서 확인하세요.

## 📚 가이드 문서

### 1. [코드 스타일 가이드](./code-style-guide.md)

Java 코드 작성 시 따라야 할 **일반적인 스타일 규칙**입니다.

**포함 내용**:
- Lombok 어노테이션 사용 규칙 (@Getter, @NoArgsConstructor, @Builder)
- DTO는 record 타입으로 작성하는 이유
- JPA 관계에서 FetchType.LAZY를 명시해야 하는 이유 (N+1 문제)
- 계층형 분리의 책임 (Controller, Service, Repository)
- 예외 처리 원칙 (try-catch 금지, GlobalExceptionHandler 사용)
- 네이밍 컨벤션 (PascalCase, camelCase, UPPER_SNAKE_CASE)
- Null 처리, 트랜잭션, 로깅, 테스트, 보안 관행

### 2. [Git 워크플로우](./git-workflow.md)

**프로젝트 전체에 적용되는** Git 사용 규칙입니다.

**포함 내용**:
- Test-Then-Commit 프로세스 (필수)
- Conventional Commits 규칙 (feat:, fix:, docs:, refactor:, test:, chore:)
- 커밋 메시지 형식 및 [Test Passed] 필수 포함
- 브랜치 네이밍 규칙 (prefix/feature-name)
- PR 전략 (직접 머지 금지, 관리자가 PR 처리)
- 일반적인 워크플로우 예시

### 3. [아키텍처 가이드](./architecture-guide.md)

**모든 Spring Boot 프로젝트에 적용되는** 아키텍처 원칙과 패턴입니다.

**포함 내용**:
- 계층형 아키텍처 패턴 (Controller → Service → Repository → DB)
- 각 계층의 책임과 규칙
- 의존성 관리 원칙 (어떤 라이브러리를 선택할지)
- 예외 처리 원칙 (커스텀 예외 계층, GlobalExceptionHandler)
- 외부 API 호출 패턴 (재시도, 타임아웃, 예외 변환)
- 로깅 전략 (로그 레벨, 민감 정보 보호)
- 테스트 원칙 (Given-When-Then 패턴)
- 데이터 접근 패턴 (JPA, N+1 쿼리 방지)
- 보안 원칙 (환경 변수, 입력 검증, 에러 메시지)
- Phase 1 제약사항

## 🎯 사용 예

### 코드 작성 시
```java
// ❓ "Entity에 @Data를 쓰면 안 되는 이유가 뭐지?"
// → code-style-guide.md의 "Lombok 사용 규칙" 섹션 참조
```

### 커밋 시
```bash
# ❓ "어떤 형식으로 커밋 메시지를 작성해야 하지?"
# → git-workflow.md의 "커밋 메시지 규칙" 섹션 참조
```

### 아키텍처 설계 시
```java
// ❓ "외부 API 호출할 때 어떻게 예외를 처리해야 하지?"
// → architecture-guide.md의 "외부 API 호출 패턴" 섹션 참조
```

## 📋 체크리스트

**코드 리뷰 시** (자신의 코드 또는 다른 개발자의 코드):

- [ ] Entity: @Getter, @NoArgsConstructor, @Builder 사용 (code-style-guide.md)
- [ ] DTO: record 타입 사용 (code-style-guide.md)
- [ ] JPA 관계: FetchType.LAZY 명시 (code-style-guide.md)
- [ ] 계층 분리: Controller는 얇게, Service에 비즈니스 로직 (architecture-guide.md)
- [ ] 예외: try-catch 없고, GlobalExceptionHandler 사용 (architecture-guide.md)
- [ ] 외부 API: 재시도 로직, 타임아웃, 예외 변환 (architecture-guide.md)
- [ ] 로깅: DEBUG, INFO, WARN, ERROR 올바르게 사용 (architecture-guide.md)
- [ ] 테스트: Given-When-Then 패턴 사용 (architecture-guide.md)
- [ ] 커밋: [Test Passed] 포함, Conventional Commits 형식 (git-workflow.md)
- [ ] 브랜치: prefix/feature-name 형식 (git-workflow.md)

## 🔗 관련 문서

**spec.md와의 관계**:
- skills/: **"어떻게"** (규칙, 패턴, 가이드)
- specs/001-career-fortune-api/spec.md: **"무엇을"** (기능, API, 엔티티)
- plan.md: **"언제 구현할"** (우선순위, 태스크 분해)

## 🚀 빠른 참조

### 테스트 실행 (항상 커밋 전에)
```bash
cd SSAju/
./gradlew test
```

### 브랜치 생성 및 작업 시작
```bash
git checkout -b feat/feature-name
# ... 개발 ...
./gradlew test  # 테스트 통과 확인
git commit -m "feat: 설명 [Test Passed]"
git push origin feat/feature-name
```

### 코드 스타일 확인
```
Entity 작성 중? → code-style-guide.md
DTO 작성 중? → code-style-guide.md
외부 API 호출? → architecture-guide.md
예외 처리? → architecture-guide.md
```

---

**Last Updated**: 2026-04-10
