# Contract: 인증/세션 엔드포인트 (User Story 1, A2)

## `POST /api/auth/signup`

- Request body 추가 필드(#5): `birthDate`(`string`, ISO `YYYY-MM-DD`, **필수**), `birthTime`(`string`, ISO `HH:mm`, **선택**, 생략 시 `null`).
- 동작 변화 없음(기존 이메일/비밀번호 검증 유지), 응답 바디에 새 필드 추가 없음(개인정보이므로 응답에 echo하지 않음 — 필요 시 별도 프로필 조회 API에서 노출 여부는 이번 스펙 범위 밖).

## `POST /api/auth/login`

**Before**: 응답 바디에 `accessToken` + `refreshToken` 문자열 포함.

**After**:
- 응답 바디: `accessToken`만 포함(기존 필드명 유지, `refreshToken` 필드 제거).
- `Set-Cookie` 헤더 추가: `refreshToken=<value>; HttpOnly; Secure; Path=/api/auth; SameSite=Strict; Max-Age=<refresh-token-expiration seconds>`.
- 내부적으로 Redis에 `refresh-token:{jti}` 키 등록(TTL=만료시간).

## `POST /api/auth/refresh`

**Before**: 요청 헤더 `Refresh-Token`으로 토큰 전달, `TokenValidationFilter`가 모든 경로에서 이 헤더를 검사.

**After**:
- 요청: `Cookie: refreshToken=...`만 사용(헤더 방식 제거).
- `TokenValidationFilter`는 **이 엔드포인트와 `/api/auth/logout`에서만** 쿠키를 검사하도록 `shouldNotFilter` 조건 반전(그 외 경로는 필터 스킵).
- 검증 성공 시: Redis에서 기존 `refresh-token:{jti}` 삭제 + 신규 키 발급(회전), 새 Access/Refresh Token 각각 응답 바디/쿠키로 전달(로그인과 동일 방식).
- 검증 실패(만료/블랙리스트/존재하지 않음): `401`, 기존 `ApiResponse.failure` 포맷 유지.

## `POST /api/auth/logout`

**Before**: `AuthController.logout` 내부에서 직접 로직 처리(C3 위반), Refresh Token 쿠키 삭제 없음.

**After**:
- `AuthController.logout`은 인증된 사용자 식별 + `AuthService.logout(userId, ...)` 호출만 수행(로직은 Service로 이관 — C3).
- Service 내부: 현재 Access Token의 `jti`를 `access-blacklist:{jti}`에 등록(TTL=남은 유효시간), 현재 Refresh Token의 `refresh-token:{jti}` 삭제.
- 응답: `Set-Cookie: refreshToken=; Path=/api/auth; Max-Age=0`(명시적 삭제 — A2).

## 회원 탈퇴(`deleteUser` 관련 엔드포인트)

- 탈퇴 처리 트랜잭션 커밋 후, 탈퇴 시점에 유효했던 Access/Refresh Token을 로그아웃과 동일하게 무효화(블랙리스트 등록 + Redis 키 삭제).

## 보호된 일반 API(로그인/갱신/로그아웃 이외 전부)

- Refresh Token 쿠키의 존재/유효성을 **검사하지 않음**(FR-005). Access Token(Authorization 헤더, Bearer)만으로 인증하며, 인증 필터는 `access-blacklist:{jti}` `EXISTS` 확인만 추가로 수행.
