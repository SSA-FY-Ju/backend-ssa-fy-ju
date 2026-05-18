package ssafy.SSAju.dto.response;

/**
 * 회원가입 응답 DTO.
 *
 * 회원가입 성공 후 클라이언트에 반환되는 응답 정보입니다.
 *
 * @param message 회원가입 완료 메시지
 * @param redirectUrl 회원가입 후 리다이렉트할 URL (예: /login)
 */
public record SignupResponse(
        String message,
        String redirectUrl
) {}
