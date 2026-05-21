package ssafy.SSAju.dto.response;

/**
 * 인증 토큰 응답 DTO.
 *
 * 로그인 성공 후 클라이언트에 반환되는 토큰 정보입니다.
 * 액세스 토큰은 Authorization 응답 헤더(Bearer)로 전달되고,
 * 리프레시 토큰은 HttpOnly 쿠키에 저장됩니다.
 *
 * @param accessTokenExpiresIn 액세스 토큰 만료 시간 (초 단위)
 */
public record AuthTokenResponse(
        long accessTokenExpiresIn
) {}
