package ssafy.SSAju.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.ErrorInfo;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.ExternalApiException;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.exception.PublicDataApiException;
import ssafy.SSAju.exception.SajuResultNotFoundException;

import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.validation.FieldError;

@Slf4j
@RestControllerAdvice
public class SajuGlobalExceptionHandler {

    @ExceptionHandler(SajuResultNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSajuResultNotFound(
            SajuResultNotFoundException e, HttpServletRequest request) {
        log.warn("SajuResult not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getCode(),
                        e.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(InvalidSajuDataException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSajuData(
            InvalidSajuDataException e, HttpServletRequest request) {
        log.warn("Invalid saju data", e);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.INVALID_SAJU_DATA.getCode(),
                        ErrorMessageConstants.INVALID_SAJU_DATA.getMessage(),
                        generateRequestId())));
    }

    @ExceptionHandler(FastAPITimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleFastAPITimeout(
            FastAPITimeoutException e, HttpServletRequest request) {
        log.error("FastAPI timeout: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.FASTAPI_TIMEOUT.getCode(),
                        ErrorMessageConstants.FASTAPI_TIMEOUT.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(OpenAIApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAIApiException(
            OpenAIApiException e, HttpServletRequest request) {
        log.error("OpenAI API error: {}", e.getMessage());
        // TODO: Phase 3.2 (ConsultationService) - Differentiate OpenAIApiException types by status code:
        // 401 -> UNAUTHORIZED (인증 오류)
        // 429 -> TOO_MANY_REQUESTS (할당량 초과)
        // 5xx -> GATEWAY_TIMEOUT (서버 오류)
        // Current implementation maps all to 504 GATEWAY_TIMEOUT
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.OPENAI_API_TIMEOUT.getCode(),
                        ErrorMessageConstants.OPENAI_API_TIMEOUT.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(PublicDataApiException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicDataApiException(
            PublicDataApiException e, HttpServletRequest request) {
        log.error("Public data API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.COMPANY_NOT_FOUND.getCode(),
                        ErrorMessageConstants.COMPANY_NOT_FOUND.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalApiException(
            ExternalApiException e, HttpServletRequest request) {
        log.error("외부 API 호출 실패: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.EXTERNAL_API_ERROR.getCode(),
                        ErrorMessageConstants.EXTERNAL_API_ERROR.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(
            DataAccessException e, HttpServletRequest request) {
        log.error("Database error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.DATABASE_ERROR.getCode(),
                        ErrorMessageConstants.DATABASE_ERROR.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        String requestId = generateRequestId();
        log.warn("JSON 파싱 실패: requestId={}, exceptionType={}", requestId, e.getClass().getSimpleName());
        log.debug("JSON 파싱 상세 예외: requestId={}", requestId, e);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.VALIDATION_FAILED.getCode(),
                        "요청 본문을 파싱할 수 없습니다. 필드 값을 확인해주세요.", requestId)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fe
                        ? fe.getField() + ": " + fe.getDefaultMessage()
                        : error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.VALIDATION_FAILED.getCode(), message, generateRequestId())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception e, HttpServletRequest request) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorMessageConstants.INTERNAL_SERVER_ERROR.getMessage(), generateRequestId())));
    }

    private String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
