package ssafy.SSAju.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ssafy.SSAju.career.util.JobCategoryEnum;

import java.time.LocalDate;
import java.time.LocalTime;

public record CompatibilityRequest(
        @NotNull LocalDate userBirthDate,
        LocalTime userBirthTime,
        @NotNull @Valid TargetRoleRequest targetRole,
        @NotBlank String companyName,
        LocalDate companyFoundingDate,
        LocalTime companyFoundingTime
) {
    public record TargetRoleRequest(
            @NotNull JobCategoryEnum category,
            String detailName
    ) {}
}
