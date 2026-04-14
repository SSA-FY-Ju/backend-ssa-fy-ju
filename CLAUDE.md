# SSAju Project - Backend Entry

SSAju는 SSAFY 사주 기반 커리어 상담 서비스의 백엔드입니다. (Java 21 / Spring Boot 4.0.5)

## 🛠 Commands (Root: SSAju/)
- Build: `./gradlew build`
- Run: `./gradlew bootRun`
- Test: `./gradlew test` (성공 확인 필수)

## 🚀 Workflow: Test-Then-Commit
1. **구현 & 테스트**: 기능 작성 후 반드시 `./gradlew test` 실행.
2. **커밋**: 테스트 성공 시에만 `git commit` (Prefix: `feat:`, `fix:`, `docs:` 등).
3. **Push**: 브랜치(`prefix/name`) 푸시 후 대기 (직접 머지 금지).

## 📚 상세 지침 (반드시 숙지)
| 문서 | 경로 |
| :--- | :--- |
| **코드 스타일 & JPA** | `/skills/code-style-guide.md` |
| **Git & 커밋 규칙** | `/skills/git-workflow.md` |
| **아키텍처 & 예외** | `/skills/architecture-guide.md` |
| **기능 명세(Spec)** | `/specs/001-career-fortune-api/spec.md` 하위 문서 |

**Last Updated**: 2026-04-10