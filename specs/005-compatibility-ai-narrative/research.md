# Research: 기업 궁합 분석 AI 해설 전환 및 점수 산정 일원화

## 1. AI 해설 호출 방식: 1-call vs 필드별 개별 호출

**Decision**: `ConsultationOpenAICaller`와 동일한 1-call JSON 모드 — 8개 해설 텍스트 필드를 하나의 `ChatClient` 호출로 한 번에 받는다(`CompatibilityNarrativeResponse` record로 매핑).

**Rationale**: 필드별로 나눠 호출하면 요청당 레이턴시가 8배로 늘어나 SC-004(15초 이내) 달성이 어려워지고, 실패 지점이 8곳으로 늘어나 쿼터 보상 로직(FR-004)이 복잡해진다. 이미 커리어 컨설팅(`ConsultationOpenAICaller`)이 23개 필드를 1-call로 처리하는 검증된 패턴이 있어 그대로 재사용하면 리스크가 낮다.

**Alternatives considered**: 필드별 개별 호출(레이턴시·비용·실패 지점 증가로 기각), 2단계 호출(요약 먼저, 상세 나중 — 이번 8개 필드 규모에서는 불필요한 복잡성으로 기각).

## 2. 점수 산식 통합 방향

**Decision**: `RoleCompatibilityCalculator.calculatePrimary`가 자체 산식(`주오행×30+40`)을 버리고, `JobRoleAnalyzer`가 이미 계산한 `matchScore`(`주오행×40 + 부오행×20`)를 그대로 전달받아 재사용한다. `RoleCompatibilityCalculator.calculateSecondary`는 기존처럼 그 값에서 페널티(-15)만 차감.

**Rationale**: 두 계산기 모두 "사용자 오행 분포가 이 직군과 얼마나 맞는가"라는 동일한 질문에 답하고 있어 별도 산식을 유지할 이유가 없다. 이미 계산되어 있는 `matchScore`를 재사용하면 코드 중복도 없어지고, 새 3번째 공식을 설계하는 것보다 리스크가 작다(기존 계산 결과의 신뢰도가 이미 검증됨).

**Alternatives considered**:
- 완전히 새로운 통합 공식 설계 — 가중치를 처음부터 다시 정해야 해서 회귀 테스트 범위가 커지고, 기존 `matchScore` 대비 무엇이 달라지는지 설명하기 어려워 기각.
- `RoleCompatibilityCalculator`의 기존 공식을 유지하고 `JobRoleAnalyzer` 쪽을 거기에 맞추기 — 반대 방향도 가능하지만, `matchScore`는 이미 `targetRoleAnalysis`(직군 자체와의 매칭)라는 더 상위 개념이고 `RoleCompatibility`(전문가/리드)는 그 하위 세부 역할이므로, 상위 개념의 점수를 하위가 이어받는 방향이 의미상 자연스러워 채택.

## 3. AI 실패 시 쿼터 보상 범위

**Decision**: 기존 `CompanyMatchingService.analyzeCompatibility`의 `try { calculateSajuData(...) } catch (RuntimeException e) { restoreDailyUsage(...); throw e; }` 블록을 AI 해설 호출까지 확장한다 — 사주 계산이 성공한 뒤 AI 호출이 실패해도 동일하게 쿼터를 복원한다.

**Rationale**: FR-004/FR-005 및 User Story 3 acceptance scenario가 "AI 해설 생성 실패 시에도 쿼터가 소진된 채 남지 않아야 한다"를 명시. 기존 `restoreDailyUsage`(보상 트랜잭션, `specs/004-redis-hardening-refactor` US2)를 그대로 재사용하면 신규 보상 로직을 만들 필요가 없다.

**Alternatives considered**: AI 호출 실패는 보상하지 않고 사용자가 재시도하도록 안내 — 기존 프로젝트 전반의 "쿼터 증발 방지" 원칙(US2)과 상충해 기각.

## 4. 응답 캐싱 정책

**Decision**: 기존 월별 캐시(`CompanyCompatibility` unique constraint + `completed` 플래그) 정책을 그대로 유지한다. AI가 생성한 텍스트도 기존 자식 엔티티(`TargetRoleAnalysis` 등)에 저장되므로, 캐시 히트 시 저장된 텍스트를 재사용하고 AI를 재호출하지 않는다.

**Rationale**: FR-006. 스키마 변경이 없으므로 기존 `childReadService.buildFromExisting` 경로가 그대로 작동한다.

**Alternatives considered**: AI 해설만 별도 TTL로 재생성 — 스키마/캐시 정책 이원화로 복잡도만 늘어나 기각.

## 5. 프론트엔드 계약(JSON 구조) 영향

**Decision**: `CompatibilityResponse` 및 모든 하위 record의 필드명·타입을 변경하지 않는다. 변경 대상은 필드 값(텍스트 문구, 통합된 점수 수치)뿐이다.

**Rationale**: FR-007, SC-005, 사용자 확인(대화 기록). 프론트엔드 파싱 코드 수정 불필요.

**Alternatives considered**: AI 생성 여부를 알리는 메타데이터 필드(`generatedBy` 등) 추가 — 이번 스펙 범위에서는 요구되지 않아 기각(필요 시 별도 스펙에서 논의).

## 6. 동시성/분산락과의 관계

**Decision**: 이번 계획에서는 `analyzeCompatibility`에 분산락을 도입하지 않는다. 기존 INSERT IGNORE 기반 경합 처리(`companyCompatibilityJdbcRepository.insertOrIgnore`)를 그대로 유지한다.

**Rationale**: 분산락은 `specs/004-redis-hardening-refactor` US5(T035)에서 별도로 다루기로 이미 합의됨(대화 기록). 두 작업을 한 커밋에서 동시에 건드리면 동시성 테스트 실패의 원인(락 vs AI 지연)을 구분하기 어려워진다.

**Alternatives considered**: 이번 스펙에 분산락까지 포함 — 사용자가 명시적으로 범위 밖으로 결정(대화 기록), 기각.

---

모든 `NEEDS CLARIFICATION` 항목 없음(spec.md 작성 시 Assumptions로 해결됨).
