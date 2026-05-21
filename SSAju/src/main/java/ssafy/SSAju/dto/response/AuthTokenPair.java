package ssafy.SSAju.dto.response;

public record AuthTokenPair(String accessToken, String refreshTokenValue, long expiresIn) {
}
