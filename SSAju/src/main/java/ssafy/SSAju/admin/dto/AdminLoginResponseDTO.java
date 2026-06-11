package ssafy.SSAju.admin.dto;

public record AdminLoginResponseDTO(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
