# SSAju API 명세서

**버전**: 0.0.2 · **최종 업데이트**: 2026-05-25 · **기본 URL**: `http://localhost:8080`

---

## 📋 목차
1. [인증 API](#인증-api)
2. [커리어 상담 API](#커리어-상담-api)
3. [관운 분석 API](#관운-분석-api)
4. [기업 궁합 API](#기업-궁합-api)
5. [사용자 API](#사용자-api)
6. [피드백 API](#피드백-api)
7. [공통 응답 포맷](#공통-응답-포맷)
8. [에러 처리](#에러-처리)
9. [인증 방식](#인증-방식)

---

## 인증 API

### 1. 회원가입

**엔드포인트**: `POST /api/auth/signup`

**설명**: 이메일과 비밀번호로 새로운 사용자를 등록합니다.

**요청 본문**:
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "김순미",
  "termsAgreed": true,
  "privacyAgreed": true
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `email` | String | ✓ | 로그인 이메일 (중복 불가, 유효한 이메일 형식) |
| `password` | String | ✓ | 비밀번호 (8자 이상) |
| `name` | String | ✓ | 사용자 이름 |
| `termsAgreed` | Boolean | ✓ | 이용약관 동의 여부 (true 필수) |
| `privacyAgreed` | Boolean | ✓ | 개인정보 수집 동의 여부 (true 필수) |

**응답 (201 Created)**:
```json
{
  "success": true,
  "data": {
    "message": "회원가입이 완료되었습니다.",
    "redirectUrl": "/login"
  },
  "error": null,
  "timestamp": 1716201600000
}
```

**에러 응답**:
- `409 Conflict`: 이미 등록된 이메일 (`DUPLICATE_EMAIL`)
- `403 Forbidden`: 약관 미동의 (`TERMS_AGREEMENT_REQUIRED`)
- `400 Bad Request`: 입력값 검증 실패 (`VALIDATION_FAILED`)

---

### 2. 로그인

**엔드포인트**: `POST /api/auth/login`

**설명**: 이메일과 비밀번호로 인증하고 토큰을 발급합니다.

**요청 본문**:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `email` | String | ✓ | 로그인 이메일 |
| `password` | String | ✓ | 비밀번호 |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessTokenExpiresIn": 3600
  },
  "error": null,
  "timestamp": 1716201600000
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `accessTokenExpiresIn` | Long | 액세스 토큰 만료 시간 (초 단위) |

**응답 헤더**:
- `Authorization`: `Bearer {accessToken}` (1시간 유효)
- `Refresh-Token`: `{refreshToken}` (7일 유효)

**보안 특성**:
- 이메일 미존재 및 비밀번호 오류 시 동일한 에러 메시지 반환 (User Enumeration 방지)
- 실제 실패 원인은 LoginAttempt 기록에 저장 (감사 추적)
- 클라이언트 IP는 프록시 헤더를 통해 추출

**에러 응답**:
- `401 Unauthorized`: 이메일 또는 비밀번호 오류 (`INVALID_CREDENTIALS`)

---

### 3. 로그아웃

**엔드포인트**: `POST /api/auth/logout`

**설명**: 현재 세션을 종료하고 RefreshToken을 무효화합니다.

**요청 헤더**:
```
Authorization: Bearer {accessToken}
Refresh-Token: {refreshToken}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": 1716201600000
}
```

**토큰 처리**:
- 서버 내 RefreshToken을 무효화(revoked) 상태로 변경

**에러 응답**:
- `401 Unauthorized`: Refresh-Token 헤더 누락 또는 인증 정보 없음 (`INVALID_CREDENTIALS`)

---

### 4. AccessToken 재발급

**엔드포인트**: `POST /api/auth/refresh`

**설명**: 유효한 RefreshToken을 사용하여 새로운 AccessToken을 발급받습니다.

**요청 헤더**:
```
Refresh-Token: {refreshToken}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessTokenExpiresIn": 3600
  },
  "error": null,
  "timestamp": 1716201600000
}
```

**응답 헤더**:
- `Authorization`: `Bearer {새로운 accessToken}`
- `Refresh-Token`: `{새로운 refreshToken}`

**보안 특성**:
- RefreshToken은 TokenValidationFilter에서 사전 검증
- 유효하지 않거나 만료된 RefreshToken은 거부

**에러 응답**:
- `401 Unauthorized`: RefreshToken 누락, 만료, 또는 무효 (`INVALID_TOKEN`)

---

### 5. 이메일 중복 확인

**엔드포인트**: `POST /api/auth/check-email`

**설명**: 회원가입 폼에서 이메일 사용 가능 여부를 실시간 확인합니다.

**요청 본문**:
```json
{
  "email": "user@example.com"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `email` | String | ✓ | 확인할 이메일 |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": 1716201600000
}
```

**에러 응답**:
- `409 Conflict`: 이미 등록된 이메일 (`DUPLICATE_EMAIL`)

---

## 커리어 상담 API

### 6. AI 커리어 상담

**엔드포인트**: `POST /api/career/consultation`

**설명**: 생년월일시를 기반으로 사주 분석 데이터(십신+지장간)와 OpenAI를 활용하여 23개 필드의 상세한 커리어 조언을 반환합니다.

**요청 헤더**:
```
Authorization: Bearer {accessToken}
```

**요청 본문**:
```json
{
  "birthDate": "1995-06-15",
  "birthTime": "14:30"
}
```

| 필드 | 타입 | 필수 | 형식 | 설명 |
|------|------|------|------|------|
| `birthDate` | String | ✓ | YYYY-MM-DD | 생년월일 |
| `birthTime` | String | ✓ | HH:mm | 출생 시간 (24시간 형식) |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "consultationId": 42,
    "openaiModelVersion": "gpt-4o-mini",
    "industries": [
      {
        "name": "소프트웨어 개발",
        "reason": "일간(己土)의 안정성과 수용적 성향이 체계적인 프로젝트 관리에 적합",
        "recommendedRoles": ["백엔드 엔지니어", "DevOps", "기술 리더"]
      }
    ],
    "interviewTips": [
      "체계성과 신뢰성을 강조하는 사례 준비",
      "팀 협업 경험 강조"
    ],
    "strengths": ["신뢰성", "책임감", "세밀함"],
    "cautions": ["지나친 신중함으로 결정 지연 위험"],
    "favoredPeriod": "H1",
    "confidenceScore": 82,
    "reasoning": "정관(正官) 기운이 명확하여 직업운이 상반기에 집중",
    "sajuProfile": {
      "dayMaster": "己",
      "dayMasterDescription": "己土(기토) - 수용적이고 꼼꼼한 성향, 신뢰성 높음",
      "fiveElements": {"木": 1, "火": 2, "土": 2, "金": 2, "水": 1},
      "fiveElementsAnalysis": "토(土) 기운이 강해 안정성 및 신뢰성 우수, 수(水) 부족으로 창의성 보강 필요",
      "tenGodDistribution": {"正官": 1, "偏官": 1, "正財": 2, "偏財": 1},
      "keyTenGods": ["正官", "偏官"],
      "tenGodCharacteristics": {
        "正官": "체계적인 규율과 책임감, 조직 내 신뢰 구축"
      }
    },
    "wealthStyle": {
      "incomeSource": "정규직 및 프로젝트 보너스",
      "financialAdvice": "안정적 자산 증식 추천",
      "investmentTendency": "보수적이나 꾸준함",
      "additionalIncome": "부수 스킬 개발로 부가 수입 창출 가능"
    },
    "longTermRoadmap": {
      "phase0to2years": {
        "goal": "커리어 기초 구축",
        "focus": "핵심 업무 역량 강화",
        "action": "현 회사에서 깊이 있는 경험 쌓기"
      },
      "phase3to5years": {
        "goal": "리더십 역량 개발",
        "focus": "팀 관리 경험",
        "action": "시니어 직급으로 승진 추진"
      },
      "ultimateGoal": "기술 리더십 확보",
      "goalDescription": "자신의 분야에서 신뢰받는 리더 위치"
    },
    "personalBranding": {
      "suitColor": "검정색/남색",
      "impression": "신뢰감 있고 안정적인 이미지",
      "hairAndMakeup": "깔끔하고 정돈된 스타일",
      "brandingKeyword": "안정성, 신뢰, 정확성",
      "taglineForResume": "신뢰받는 엔지니어, 정확한 완성도"
    },
    "powerKeywords": {
      "keywords": [
        {
          "keyword": "책임감 있는",
          "element": "土",
          "description": "주어진 일을 끝까지 완수하는 능력",
          "usageExample": "책임감 있게 프로젝트 마일스톤 달성",
          "context": "이력서, 자기소개서, 면접"
        }
      ],
      "selectionGuide": "본인의 강점을 대표할 3개 키워드 선정",
      "usageTips": ["면접 자기소개에 자연스럽게 녹여내기"],
      "avoidanceTip": "과장하지 않기, 실제 사례와 함께 제시"
    },
    "mentalCare": {
      "stressVulnerability": ["과도한 책임감", "변화에 대한 불안"],
      "rechargeMethod": ["규칙적인 루틴", "자연과의 시간"],
      "mindsetMantra": "느리지만 확실하게",
      "emergencyTactic": "신뢰할 수 있는 멘토와 대화"
    },
    "environmentFit": {
      "workVibe": "안정적이고 체계적인 조직",
      "companySize": "중견 이상 기업",
      "colleagueType": "신뢰할 수 있는 동료",
      "conflictApproach": "차분한 대화를 통한 해결",
      "physicalEnv": "조용하고 집중할 수 있는 환경",
      "culturalFit": "투명하고 명확한 가치관의 조직"
    },
    "workStyle": {
      "preferredCompanyType": "기술 기업 또는 금융회사",
      "leadershipType": "따뜻하고 체계적인 리더십",
      "decisionMaking": "충분한 정보 수집 후 신중한 결정",
      "conflictResolution": "직접 대화와 합의점 모색"
    },
    "relationshipStrategy": {
      "socialStyle": "소수 친밀한 관계 선호",
      "networkingApproach": "신뢰 기반의 점진적 관계 구축",
      "teamPosition": "신뢰받는 서포터 역할",
      "conflictResolution": "차분한 커뮤니케이션",
      "careerNetworking": "업계 선배 멘토링 활용"
    },
    "careerTimeline": {
      "year": 2026,
      "months": {
        "3": {
          "type": "opportunity",
          "description": "새로운 프로젝트 기회 또는 직책 제안"
        },
        "6": {
          "type": "stable",
          "description": "안정적인 성과 창출 기간"
        }
      },
      "pivotPoints": [
        {
          "month": 3,
          "type": "positive",
          "score": 8,
          "description": "주요 기회 또는 의사결정 시점"
        }
      ],
      "warningMonths": [9],
      "warningDescription": "신중함 필요, 급격한 변화 피할 것"
    },
    "analysisSummary": "己土 일간, H1 유리, 정관(正官) 기운 강세"
  },
  "error": null,
  "timestamp": 1716201600000
}
```

**응답 필드 상세**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `consultationId` | Long | 저장된 커리어 상담 결과 ID |
| `openaiModelVersion` | String | 분석에 사용된 OpenAI 모델 버전 |
| `industries[]` | Array | 추천 산업 3~5개 (name, reason, recommendedRoles) |
| `interviewTips[]` | Array[String] | 면접 팁 (3~5개) |
| `strengths[]` | Array[String] | 강점 분석 (3~5개) |
| `cautions[]` | Array[String] | 주의사항 (2~3개) |
| `favoredPeriod` | String | "H1"(상반기) 또는 "H2"(하반기) |
| `confidenceScore` | Integer | 신뢰도 점수 (0~100) |
| `reasoning` | String | 상세 근거 |
| `sajuProfile` | Object | 사주 프로필 (dayMaster, fiveElements, tenGodDistribution, tenGodCharacteristics 등) |
| `wealthStyle` | Object | 재무 스타일 (incomeSource, financialAdvice 등) |
| `longTermRoadmap` | Object | 경력 로드맵 (phase0to2years, phase3to5years 등) |
| `personalBranding` | Object | 개인 브랜딩 (suitColor, impression 등) |
| `powerKeywords` | Object | 파워 키워드 3개 + 사용 가이드 |
| `mentalCare` | Object | 정신건강 관리 (stressVulnerability, rechargeMethod 등) |
| `environmentFit` | Object | 근무환경 적합도 |
| `workStyle` | Object | 업무 스타일 |
| `relationshipStrategy` | Object | 인간관계 전략 |
| `careerTimeline` | Object | 연간 월별 운세 + 전환점 (`months`의 키: Integer 1~12) |
| `analysisSummary` | String | 분석에 사용된 사주 데이터 한 줄 요약 |

**타임아웃**: 8초 (OpenAI API 응답 대기)

**에러 응답**:
- `400 Bad Request`: birthDate 또는 birthTime 누락/형식 오류 (`VALIDATION_FAILED`)
- `401 Unauthorized`: 인증 정보 없음
- `429 Too Many Requests`: 일일 분석 한도 초과 (`DAILY_LIMIT_EXCEEDED`)
- `503 Service Unavailable`: FastAPI 타임아웃 (`FASTAPI_TIMEOUT`)
- `504 Gateway Timeout`: OpenAI API 타임아웃 (`OPENAI_API_TIMEOUT`)

---

## 관운 분석 API

### 7. 관운 분석

**엔드포인트**: `POST /api/career/timing`

**설명**: 생년월일시로부터 직업 관련 운(官運)을 분석하여 상반기(H1) 또는 하반기(H2)의 유리함을 판정합니다.

**요청 헤더**:
```
Authorization: Bearer {accessToken}
```

**요청 본문**:
```json
{
  "birthDate": "1995-06-15",
  "birthTime": "14:30"
}
```

| 필드 | 타입 | 필수 | 형식 | 설명 |
|------|------|------|------|------|
| `birthDate` | String | ✓ | YYYY-MM-DD | 생년월일 |
| `birthTime` | String | ✓ | HH:mm | 출생 시간 (24시간 형식) |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "sajuResultId": 123,
    "favoredPeriod": "H1",
    "confidenceScore": 82,
    "reasoning": "정관(正官) 기운이 강하여 상반기 운세 유리. 월간 천간이 일간을 생(生)하는 관계로 직업 기회 증대."
  },
  "error": null,
  "timestamp": 1716201600000
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `sajuResultId` | Long | 저장된 사주 분석 결과 ID |
| `favoredPeriod` | String | "H1"(상반기 유리) 또는 "H2"(하반기 유리) |
| `confidenceScore` | Integer | 신뢰도 점수 (0~100) |
| `reasoning` | String | 분석 근거 (십신, 지장간 기반) |

**처리 흐름**:
1. FastAPI 호출 → 천간/지지/오행 데이터 수신
2. Spring에서 십神 계산 (TenGodCalculator)
3. Spring에서 地藏干 계산 (HiddenStemCalculator)
4. CareerFortuneAnalyzer로 관운 분석 → H1/H2 판정

**타임아웃**: 3초 (FastAPI 응답 대기)

**에러 응답**:
- `400 Bad Request`: birthDate 또는 birthTime 형식 오류 (`VALIDATION_FAILED`)
- `401 Unauthorized`: 인증 정보 없음
- `429 Too Many Requests`: 일일 분석 한도 초과 (`DAILY_LIMIT_EXCEEDED`)
- `503 Service Unavailable`: FastAPI 타임아웃 (`FASTAPI_TIMEOUT`)

---

## 기업 궁합 API

### 8. 기업 궁합 분석

**엔드포인트**: `POST /api/company/compatibility`

**설명**: 사용자 사주와 기업 설립일의 십신+지장간 분석을 통해 호환성 점수와 직군 적합도를 평가합니다.

**요청 헤더**:
```
Authorization: Bearer {accessToken}
```

**요청 본문**:
```json
{
  "userBirthDate": "1995-06-15",
  "userBirthTime": "14:30",
  "companyName": "삼성전자",
  "companyFoundingDate": "1938-03-01",
  "companyFoundingTime": "12:00",
  "targetRole": {
    "category": "TECH_BACKEND",
    "detailName": "백엔드 엔지니어"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `userBirthDate` | String | ✓ | 사용자 생년월일 (YYYY-MM-DD) |
| `userBirthTime` | String | × | 사용자 출생 시간 (HH:mm, 미지정 시 12:00) |
| `companyName` | String | ✓ | 기업명 |
| `companyFoundingDate` | String | × | 기업 설립일 (YYYY-MM-DD, 공공데이터 API 조회 실패 시 필요) |
| `companyFoundingTime` | String | × | 기업 설립 시간 (HH:mm, 미지정 시 12:00) |
| `targetRole.category` | Enum | ✓ | 직군 코드 (하단 JobCategoryEnum 참고) |
| `targetRole.detailName` | String | × | 구체적 직무명 |

**JobCategoryEnum 값**:

| 값 | 설명 |
|----|------|
| `TECH_BACKEND` | 백엔드 개발 |
| `TECH_FRONTEND` | 프론트엔드 개발 |
| `TECH_MOBILE` | 모바일 개발 |
| `TECH_DATA` | 데이터/AI |
| `TECH_INFRA` | 인프라/DevOps |
| `FINANCE` | 금융/회계 |
| `MARKETING` | 마케팅 |
| `HR` | 인사/조직 |
| `OPERATIONS` | 운영/기획 |
| `SALES` | 영업 |
| `STRATEGY` | 전략/경영 |
| `RESEARCH` | 연구개발 |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "compatibilityId": 55,
    "requestContext": {
      "companyName": "삼성전자",
      "targetRole": {
        "category": "TECH_BACKEND",
        "detailName": "백엔드 엔지니어"
      }
    },
    "compatibilityScore": 78,
    "summary": "사용자의 안정적 기운(土)과 기업의 체계적 문화가 높은 시너지를 형성합니다.",
    "targetRoleAnalysis": {
      "matchScore": 85,
      "synergy": "백엔드 개발의 금(金) 기운과 사용자의 수(水) 기운이 상생 관계로 최적 조합",
      "warning": "지나친 체계화로 창의성 제약 가능성 있음"
    },
    "fiveElements": {
      "userDistribution": {"木": 1, "火": 2, "土": 2, "金": 2, "水": 1},
      "companyDistribution": {"木": 0, "火": 1, "土": 3, "金": 2, "水": 2},
      "synergyDescription": "기업의 안정적 기운(土)과 사용자의 신뢰성(土)이 일치하여 문화 적응성 우수"
    },
    "analysisBreakdown": {
      "characterMatch": 82,
      "potentialSynergy": 75,
      "longTermStability": 78
    },
    "actionableStrategy": {
      "interviewKeywords": ["책임감", "체계적 설계", "안정성"],
      "weaknessDefense": "창의성 부족 우려를 프로젝트 완성도로 반박",
      "bestTiming": {
        "luckyDays": ["화요일", "금요일"],
        "preferredTime": "오전 10시~12시"
      }
    },
    "expectedInterviewQuestions": [
      {
        "question": "팀 문화에 어떻게 적응하시겠습니까?",
        "intent": "조직 적응력 확인"
      },
      {
        "question": "체계적인 업무 프로세스를 어떻게 평가하시나요?",
        "intent": "업무 스타일 파악"
      }
    ],
    "roleCompatibility": [
      {
        "roleName": "백엔드 엔지니어",
        "score": 85,
        "reason": "기술력과 책임감 강화",
        "tag": "BEST_FIT"
      }
    ],
    "monthlyForecast": [
      {
        "month": 3,
        "score": 8,
        "status": "LUCKY",
        "advice": "입사 시기로 최적"
      }
    ],
    "cautions": [
      "조직 문화의 급격한 변화에 유의",
      "자율성 부족 시 스트레스 가능성"
    ]
  },
  "error": null,
  "timestamp": 1716201600000
}
```

**응답 필드 상세**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `compatibilityId` | Long | 저장된 기업 궁합 분석 결과 ID |
| `requestContext` | Object | 분석 요청 컨텍스트 (companyName, targetRole) |
| `compatibilityScore` | Integer | 기업 궁합점 (0~100) |
| `summary` | String | 전체 궁합 한 줄 요약 |
| `targetRoleAnalysis` | Object | 직군별 적합도 분석 (matchScore, synergy, warning) |
| `fiveElements` | Object | 오행 기반 분석 (userDistribution, companyDistribution, synergyDescription) |
| `analysisBreakdown` | Object | 세부 점수 (characterMatch, potentialSynergy, longTermStability) |
| `actionableStrategy` | Object | 실천 전략 (interviewKeywords, weaknessDefense, bestTiming) |
| `expectedInterviewQuestions[]` | Array | 예상 면접 질문 (question, intent) |
| `roleCompatibility[]` | Array | 직군별 점수 (roleName, score, reason, tag) |
| `monthlyForecast[]` | Array | 월별 예측 (month: 1~12, score, status: LUCKY\|NORMAL\|CAUTION, advice) |
| `cautions[]` | Array[String] | 주의사항 |

**처리 흐름**:
1. 공공데이터 API로 기업 설립년도 조회 (없으면 사용자 입력)
2. FastAPI로 기업/사용자 사주 데이터 계산
3. JobRoleAnalyzer로 직군 오행 분석
4. CompanyMatchingService로 종합 분석

**타임아웃**: 3초 (FastAPI) + 5초 (공공데이터 API)

**에러 응답**:
- `400 Bad Request`: userBirthDate, companyName 또는 targetRole.category 누락 (`VALIDATION_FAILED`)
- `401 Unauthorized`: 인증 정보 없음
- `404 Not Found`: 기업 정보 미발견 (`COMPANY_NOT_FOUND`)
- `503 Service Unavailable`: FastAPI 타임아웃 (`FASTAPI_TIMEOUT`)
- `502 Bad Gateway`: 외부 API 호출 실패 (`EXTERNAL_API_ERROR`)

---

## 사용자 API

### 9. 회원 탈퇴

**엔드포인트**: `DELETE /api/users/me`

**설명**: 사용자 계정을 삭제합니다. 비밀번호 확인이 필수입니다.

**요청 헤더**:
```
Authorization: Bearer {accessToken}
```

**요청 본문**:
```json
{
  "password": "password123"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `password` | String | ✓ | 현재 비밀번호 |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": 1716201600000
}
```

**처리 사항**:
- 사용자 계정 상태를 DELETED로 변경
- RefreshToken 쿠키 제거

**에러 응답**:
- `401 Unauthorized`: 인증 정보 없음
- `401 Unauthorized`: 비밀번호 불일치 (`INVALID_CREDENTIALS`)

---

### 10. 마이페이지 조회

**엔드포인트**: `GET /api/mypage`

**설명**: 사용자의 모든 분석 이력을 조회합니다. 타입별, 페이지별 필터링 지원.

**요청 헤더**:
```
Authorization: Bearer {accessToken}
```

**요청 파라미터**:
```
GET /api/mypage?type=CAREER_CONSULTATION&page=0&size=10
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `type` | Enum | × | (전체) | 분석 타입: `SAJU`, `CAREER_CONSULTATION`, `COMPANY_COMPATIBILITY` |
| `page` | Integer | × | 0 | 페이지 번호 (0부터 시작) |
| `size` | Integer | × | 10 | 페이지당 아이템 수 (1 이상) |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "profile": {
      "id": 1,
      "name": "김순미",
      "email": "user@example.com",
      "createdAt": "2026-05-01T10:00:00",
      "lastLoginAt": "2026-05-25T09:00:00"
    },
    "analyses": [
      {
        "type": "CAREER_CONSULTATION",
        "analysisId": 42,
        "targetName": "김순미",
        "birthDate": "1995-06-15",
        "createdAt": "2026-05-20T14:30:00"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 10,
      "total": 15,
      "totalPages": 2
    }
  },
  "error": null,
  "timestamp": 1716201600000
}
```

**응답 필드 상세**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `profile.id` | Long | 사용자 ID |
| `profile.name` | String | 사용자 이름 |
| `profile.email` | String | 사용자 이메일 |
| `profile.createdAt` | String | 가입일시 (ISO 8601) |
| `profile.lastLoginAt` | String | 마지막 로그인 일시 (ISO 8601) |
| `analyses[].type` | String | 분석 타입 (`SAJU`, `CAREER_CONSULTATION`, `COMPANY_COMPATIBILITY`) |
| `analyses[].analysisId` | Long | 분석 결과 ID |
| `analyses[].targetName` | String | 분석 대상 이름 (SAJU·CAREER_CONSULTATION: 사용자 이름, COMPANY_COMPATIBILITY: 기업명) |
| `analyses[].birthDate` | String | 생년월일 (YYYY-MM-DD) |
| `analyses[].createdAt` | String | 분석 일시 (ISO 8601) |
| `pagination.page` | Integer | 현재 페이지 (0부터 시작) |
| `pagination.size` | Integer | 페이지당 아이템 수 |
| `pagination.total` | Long | 전체 아이템 수 |
| `pagination.totalPages` | Integer | 전체 페이지 수 |

**에러 응답**:
- `401 Unauthorized`: 인증 정보 없음

---

### 11. 분석 결과 상세 조회

**엔드포인트**: `GET /api/mypage/analyses/{analysisId}`

**설명**: 특정 분석 결과의 상세 정보를 조회합니다.

**요청 헤더**:
```
Authorization: Bearer {accessToken}
```

**경로 파라미터**:
- `analysisId` (Long): 분석 결과 ID

**요청 파라미터**:
```
GET /api/mypage/analyses/42?type=CAREER_CONSULTATION
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `type` | Enum | ✓ | 분석 타입: `SAJU`, `CAREER_CONSULTATION`, `COMPANY_COMPATIBILITY` |

**응답 (200 OK) — type=SAJU**:
```json
{
  "success": true,
  "data": {
    "type": "SAJU",
    "analysisId": 10,
    "targetName": "김순미",
    "birthDate": "1995-06-15",
    "createdAt": "2026-05-20T14:30:00",
    "satisfactionStatus": "SATISFIED",
    "feedbackContent": "매우 도움이 되었습니다.",
    "careerFortuneDetail": {
      "favoredPeriod": "H1",
      "confidenceScore": 82,
      "reasoning": "정관(正官) 기운이 강하여 상반기 운세 유리."
    },
    "consultationDetail": null,
    "compatibilityDetail": null
  },
  "error": null,
  "timestamp": 1716201600000
}
```

**응답 (200 OK) — type=CAREER_CONSULTATION**:
- `consultationDetail`에 ConsultationResponse 전체 구조 반환 (API 6 응답 참고)
- `careerFortuneDetail`: null
- `compatibilityDetail`: null

**응답 (200 OK) — type=COMPANY_COMPATIBILITY**:
- `compatibilityDetail`에 CompatibilityResponse 전체 구조 반환 (API 8 응답 참고)
- `careerFortuneDetail`: null
- `consultationDetail`: null

**응답 필드 상세**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `type` | String | 분석 타입 (`SAJU`, `CAREER_CONSULTATION`, `COMPANY_COMPATIBILITY`) |
| `analysisId` | Long | 분석 결과 ID |
| `targetName` | String | 분석 대상 이름 |
| `birthDate` | String | 생년월일 (YYYY-MM-DD) |
| `createdAt` | String | 분석 일시 (ISO 8601) |
| `satisfactionStatus` | String | 피드백 만족도 (`SATISFIED`, `DISSATISFIED`, null) |
| `feedbackContent` | String | 피드백 내용 (null 가능) |
| `careerFortuneDetail` | Object | SAJU 타입일 때만 반환 (favoredPeriod, confidenceScore, reasoning) |
| `consultationDetail` | Object | CAREER_CONSULTATION 타입일 때만 반환 |
| `compatibilityDetail` | Object | COMPANY_COMPATIBILITY 타입일 때만 반환 |

**에러 응답**:
- `401 Unauthorized`: 인증 정보 없음
- `403 Forbidden`: 다른 사용자의 분석 결과 조회 시도 (`UNAUTHORIZED`)
- `404 Not Found`: 존재하지 않는 분석 결과 (`SAJU_RESULT_NOT_FOUND`)

---

## 피드백 API

### 12. 만족도 피드백 저장

**엔드포인트**: `POST /api/feedback/satisfaction`

**설명**: 분석 결과에 대한 사용자 만족도 및 피드백을 저장합니다. 인증 불필요.

**요청 본문**:
```json
{
  "analysisId": 42,
  "feedbackType": "CONSULTATION",
  "satisfactionStatus": "SATISFIED",
  "feedbackContent": "매우 도움이 되었습니다."
}
```

| 필드 | 타입 | 필수 | 값 | 설명 |
|------|------|------|------|------|
| `analysisId` | Long | ✓ | - | 분석 결과 ID |
| `feedbackType` | Enum | ✓ | `CAREER_TIMING`, `CONSULTATION`, `COMPATIBILITY` | 피드백 대상 분석 타입 |
| `satisfactionStatus` | Enum | ✓ | `SATISFIED`, `DISSATISFIED` | 만족도 |
| `feedbackContent` | String | × | (max 500 chars) | 추가 피드백 의견 |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "feedbackId": 456,
    "createdAt": "2026-05-20T15:30:00",
    "feedbackContent": "매우 도움이 되었습니다."
  },
  "error": null,
  "timestamp": 1716201600000
}
```

**에러 응답**:
- `400 Bad Request`: 필수 필드 누락 또는 피드백 불가 상태 (`VALIDATION_FAILED`, `FEEDBACK_NOT_ALLOWED`)
- `404 Not Found`: 존재하지 않는 분석 결과 (`SAJU_RESULT_NOT_FOUND`)

---

## 공통 응답 포맷

모든 API 응답은 다음 포맷을 따릅니다:

**성공 응답**:
```json
{
  "success": true,
  "data": {...},
  "error": null,
  "timestamp": 1716201600000
}
```

**실패 응답**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "DUPLICATE_EMAIL",
    "message": "이미 사용 중인 이메일입니다.",
    "requestId": "req-a1b2c3d4"
  },
  "timestamp": 1716201600000
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `success` | Boolean | 요청 성공 여부 |
| `data` | Object/Array/Null | 응답 데이터 (실패 시 null) |
| `error` | Object/Null | 에러 정보 (성공 시 null) |
| `error.code` | String | 에러 코드 |
| `error.message` | String | 에러 메시지 |
| `error.requestId` | String | 요청 추적 ID (형식: `req-{8자리}`) |
| `timestamp` | Long | 응답 시간 (Unix epoch milliseconds) |

---

## 에러 처리

### HTTP 상태 코드

| 상태 | 코드 | 설명 |
|------|------|------|
| 성공 | 200 | 요청 성공 |
| 성공 | 201 | 리소스 생성 성공 |
| 클라이언트 오류 | 400 | 요청 형식/값 오류 |
| 인증 오류 | 401 | 인증 정보 없음 또는 토큰 만료/무효 |
| 권한 오류 | 403 | 요청 권한 없음 (약관 미동의, 타인 리소스 접근 등) |
| 리소스 오류 | 404 | 리소스 미발견 |
| 충돌 | 409 | 중복된 리소스 (이메일 등) |
| 한도 초과 | 429 | 일일 API 사용 한도 초과 |
| 서버 오류 | 500 | 예상 불가능한 서버 오류 |
| 외부 연동 오류 | 502 | 외부 API 호출 실패 |
| 서비스 불가 | 503 | FastAPI 또는 OpenAI 일시 불가 (rate limit 등) |
| 타임아웃 | 504 | OpenAI API 타임아웃 |

### 주요 에러 코드

| 에러 코드 | HTTP | 설명 |
|-----------|------|------|
| `DUPLICATE_EMAIL` | 409 | 이미 등록된 이메일 |
| `TERMS_AGREEMENT_REQUIRED` | 403 | 이용약관 또는 개인정보 수집 미동의 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 오류 (로그인 실패) |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰 |
| `TOKEN_EXPIRED` | 401 | 만료된 토큰 |
| `UNAUTHORIZED` | 403 | 타인 리소스 접근 시도 |
| `USER_NOT_FOUND` | 404 | 존재하지 않는 사용자 |
| `SAJU_RESULT_NOT_FOUND` | 404 | 존재하지 않는 분석 결과 |
| `COMPANY_NOT_FOUND` | 404 | 공공데이터 API에서 기업 정보 미발견 |
| `VALIDATION_FAILED` | 400 | 필수 필드 누락 또는 형식 오류 |
| `INVALID_SAJU_DATA` | 400 | 사주 데이터 유효성 검증 실패 |
| `DAILY_LIMIT_EXCEEDED` | 429 | 일일 분석 한도 초과 (3회) |
| `FEEDBACK_NOT_ALLOWED` | 400 | 해당 분석에 피드백 불가 상태 |
| `FASTAPI_TIMEOUT` | 503 | FastAPI 응답 시간 초과 |
| `OPENAI_API_TIMEOUT` | 504 | OpenAI API 응답 시간 초과 |
| `OPENAI_RATE_LIMIT` | 503 | OpenAI API 사용량 초과 |
| `EXTERNAL_API_ERROR` | 502 | 외부 API 호출 실패 |
| `DATABASE_ERROR` | 500 | 데이터베이스 접근 오류 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상치 못한 서버 오류 |

---

## 인증 방식

### AccessToken 사용

모든 보호된 엔드포인트는 Authorization 헤더에 AccessToken을 포함해야 합니다:

```
Authorization: Bearer {accessToken}
```

### RefreshToken 처리

AccessToken 만료 시 Refresh-Token 헤더에 RefreshToken을 포함하여 새로운 AccessToken을 발급받을 수 있습니다. (`POST /api/auth/refresh` 참고)

```
Refresh-Token: {refreshToken}
```

### 토큰 만료 시간

- **AccessToken**: 1시간 (응답 헤더 `Authorization`으로 전달)
- **RefreshToken**: 7일 (응답 헤더 `Refresh-Token`으로 전달)

---

**마지막 수정**: 2026-05-25
**버전**: 0.0.2
**상태**: 코드베이스 기준 동기화 완료
