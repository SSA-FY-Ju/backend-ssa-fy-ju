package ssafy.SSAju.util;

/**
 * JWT 토큰 유형을 나타내는 enum.
 *
 * <p>ACCESS와 REFRESH 두 가지 유형을 관리하며, JWT "type" 클레임 값으로 사용됩니다.
 * JwtUtil과 JwtAuthenticationFilter 양쪽에서 공유합니다.
 */
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
