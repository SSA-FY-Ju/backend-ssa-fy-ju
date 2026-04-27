package ssafy.SSAju.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.ErrorInfo;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.exception.PublicDataApiException;

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class SajuGlobalExceptionHandler {

    @ExceptionHandler(InvalidSajuDataException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSajuData(
            InvalidSajuDataException e, HttpServletRequest request) {
        log.warn("Invalid saju data: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo("INVALID_SAJU_DATA", e.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(FastAPITimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleFastAPITimeout(
            FastAPITimeoutException e, HttpServletRequest request) {
        log.error("FastAPI timeout: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(new ErrorInfo("FASTAPI_TIMEOUT",
                        "Failed to fetch saju data after retries. Please try again later.", generateRequestId())));
    }

    @ExceptionHandler(OpenAIApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAIApiException(
            OpenAIApiException e, HttpServletRequest request) {
        log.error("OpenAI API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.failure(new ErrorInfo("OPENAI_API_TIMEOUT",
                        "OpenAI API request failed. Please try again.", generateRequestId())));
    }

    @ExceptionHandler(PublicDataApiException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicDataApiException(
            PublicDataApiException e, HttpServletRequest request) {
        log.error("Public data API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ErrorInfo("COMPANY_NOT_FOUND",
                        "Company not found in public database. Please provide founding date.", generateRequestId())));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(
            DataAccessException e, HttpServletRequest request) {
        log.error("Database error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(new ErrorInfo("DATABASE_ERROR",
                        "A database error occurred. Please try again.", generateRequestId())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo("VALIDATION_FAILED", message, generateRequestId())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception e, HttpServletRequest request) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(new ErrorInfo("INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred.", generateRequestId())));
    }

    private String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
