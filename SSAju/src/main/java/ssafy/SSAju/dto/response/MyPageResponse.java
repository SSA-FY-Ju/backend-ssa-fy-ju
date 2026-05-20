package ssafy.SSAju.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record MyPageResponse(
        UserProfileData profile,
        List<UserAnalysisDto> analyses,
        PaginationInfo pagination
) {
    public record UserProfileData(
            Long id,
            String name,
            String email,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt
    ) {}

    public record PaginationInfo(
            int page,
            int size,
            long total,
            int totalPages
    ) {}
}
