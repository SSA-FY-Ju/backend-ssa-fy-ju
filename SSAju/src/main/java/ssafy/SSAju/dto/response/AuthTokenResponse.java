package ssafy.SSAju.dto.response;

public record AuthTokenResponse(
        String accessToken,
        long accessTokenExpiresIn
) {}
