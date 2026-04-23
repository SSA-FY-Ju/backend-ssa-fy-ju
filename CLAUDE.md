# SSAju Project - Backend Entry

SSAju는 SSAFY 사주 기반 커리어 상담 서비스의 백엔드입니다. (Java 21 / Spring Boot 4.0.5)

## 🛠 Commands (Root: SSAju/)
- Build: `./gradlew build`
- Run: `./gradlew bootRun`
- Test: `./gradlew test` (성공 확인 필수)

## 🚀 Workflow: Develop → Test → Commit → Push → PR
1. **브랜치 생성 & 개발**: 적절한 이름으로 브랜치 생성 후 기능 개발.
2. **테스트 & 커밋 & 푸시**: 테스트 통과 시 커밋 및 푸시, PR 생성.
3. **검토 & 수정**: 코드 리뷰 결과 반영 후 완료 또는 추가 수정.

## 📚 상세 지침 (반드시 숙지)
| 문서 | 경로 |
| :--- | :--- |
| **코드 스타일 & JPA** | `/skills/code-style-guide.md` |
| **Git & 커밋 규칙** | `/skills/git-workflow.md` |
| **아키텍처 & 예외** | `/skills/architecture-guide.md` |
| **기능 명세(Spec)** | `/specs/001-career-fortune-api/spec.md` 하위 문서 |

## 🔒 보안 & 환경 설정

### 민감 정보 관리 (필수)

⚠️ **소스 코드에 절대 포함하면 안 됨**:
- API Key, 토큰 (OpenAI, FastAPI, 공공데이터 API)
- 데이터베이스 비밀번호
- 사용자 개인정보

✅ **올바른 방식**:
- 모든 민감 정보는 환경변수 사용: `${OPENAI_API_KEY}`, `${DB_PASSWORD}`
- `.gitignore`에 포함: `application-local.yaml`, `.env`, `*.properties`

### 로컬 개발 환경

**로컬 설정**은 `application-local.yaml`에서 관리:
- 이 파일은 Git에 커밋하지 말 것 (`.gitignore` 설정됨)
- 각 개발자가 로컬에서 자신의 정보로 설정

**Last Updated**: 2026-04-23