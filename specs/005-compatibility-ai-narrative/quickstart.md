# Quickstart: 기업 궁합 분석 AI 해설 전환 검증

## 사전 준비

- `SSAju/application-local.yaml`에 `OPENAI_API_KEY` 설정(기존 커리어 컨설팅 기능과 동일 키 재사용)
- 로컬 MySQL 8 실행 중, `cd SSAju && ./gradlew bootRun`
- 테스트용 사용자 계정(로그인 후 access token 확보)

## US1: 개인화된 해설 텍스트 확인

1. 서로 다른 두 사용자(또는 동일 사용자, 서로 다른 기업명 2건)로 `POST /api/compatibility` 요청
2. 응답의 `summary`, `targetRoleAnalysis.synergy/warning`, `fiveElements.synergyDescription`, `actionableStrategy.weaknessDefense`, `expectedInterviewQuestions[]`, `roleCompatibility[].reason`, `monthlyForecast[].advice`, `cautions[]` 를 두 응답 간 비교

**기대 결과**: 8개 필드 모두 두 응답에서 문구가 다르게 나온다(완전히 동일한 템플릿 문자열이 반복되지 않음). [contracts/compatibility-narrative-contract.md](contracts/compatibility-narrative-contract.md) 참고.

3. 동일한 사용자·기업·직군으로 같은 달에 재요청

**기대 결과**: 첫 요청과 동일한 텍스트가 그대로 반환된다(AI 재호출 없음, DB 캐시 재사용, FR-006).

## US2: 점수 산정 일관성 확인

1. 같은 오행 분포를 가진 요청에 대해 응답의 `targetRoleAnalysis.matchScore`와 `roleCompatibility[0].score`(전문가 역할)를 비교

**기대 결과**: `roleCompatibility[0].score == targetRoleAnalysis.matchScore` (data-model.md "산식 통합에 따른 계산 흐름 변화" 참고). `roleCompatibility[1].score`(리드 역할)는 `matchScore - 15`.

2. 단위 테스트로 검증: `FiveElementMatchScoreCalculatorTest`(신규) / `RoleCompatibilityCalculatorTest`(수정) — 다양한 `primaryCount`/`secondaryCount` 조합에서 두 지표가 항상 일관된 관계를 유지하는지 확인

## US3: AI 실패 시 쿼터 보상 확인

1. `application-local.yaml`의 OpenAI 관련 설정을 일시적으로 잘못된 값(예: 잘못된 base URL)으로 바꾸거나, 테스트 코드에서 `ChatClient`를 mock으로 대체해 `ResourceAccessException`을 강제 발생
2. 요청 전 사용자의 일일 사용 횟수를 기록해두고, 궁합 분석 요청 실행
3. 요청 실패(3회 재시도 소진) 후 사용자의 일일 사용 횟수를 다시 조회

**기대 결과**: 요청 전후 사용 횟수가 동일하다(FR-004). 응답은 명확한 오류(`OpenAIApiException` → 전역 핸들러의 표준 에러 응답)이며 부분 데이터가 저장되지 않는다(FR-005).

## 회귀 검증

```bash
cd SSAju
./gradlew test --tests "*CompanyMatching*"
./gradlew test --tests "*JobRoleAnalyzer*"
./gradlew test --tests "*RoleCompatibilityCalculator*"
./gradlew clean test
```

`BUILD SUCCESSFUL` 확인 및 [SSAju/CLAUDE.md](../../../SSAju/CLAUDE.md) 기준 커밋 전 필수 테스트 통과 확인.
