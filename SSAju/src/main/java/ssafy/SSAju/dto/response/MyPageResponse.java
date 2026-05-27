package ssafy.SSAju.dto.response;

import java.time.Instant;
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
            Instant createdAt,
            Instant lastLoginAt
    ) {}

    public record PaginationInfo(
            int page,
            int size,
            long total,
            int totalPages
    ) {}
}
