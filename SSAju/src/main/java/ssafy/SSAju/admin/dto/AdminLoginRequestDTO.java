package ssafy.SSAju.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequestDTO(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
