package ssafy.SSAju.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
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

@RestControllerAdvice
public class SajuGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SajuGlobalExceptionHandler.class);

    @ExceptionHandler(InvalidSajuDataException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSajuData(InvalidSajuDataException e) {
        log.warn("Invalid saju data: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorInfo.of("INVALID_SAJU_DATA", e.getMessage())));
    }

    @ExceptionHandler(FastAPITimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleFastAPITimeout(FastAPITimeoutException e) {
        log.error("FastAPI timeout", e);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ErrorInfo.of("FASTAPI_TIMEOUT", "Failed to fetch saju data. Please try again later.")));
    }

    @ExceptionHandler(OpenAIApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAIApiException(OpenAIApiException e) {
        log.error("OpenAI API error", e);
        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(ErrorInfo.of("OPENAI_API_TIMEOUT", "OpenAI API request failed. Please try again.")));
    }

    @ExceptionHandler(PublicDataApiException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicDataApiException(PublicDataApiException e) {
        log.error("Public data API error", e);
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorInfo.of("COMPANY_NOT_FOUND", e.getMessage())));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException e) {
        log.error("Data access error", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorInfo.of("DATABASE_ERROR", "A database error occurred.")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(err -> {
                    if (err instanceof FieldError fe) {
                        return fe.getField() + ": " + fe.getDefaultMessage();
                    }
                    return err.getDefaultMessage();
                })
                .findFirst()
                .orElse("Validation failed");
        log.warn("Validation error: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorInfo.of("VALIDATION_ERROR", message)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorInfo.of("INTERNAL_SERVER_ERROR", "An unexpected error occurred.")));
    }
}
