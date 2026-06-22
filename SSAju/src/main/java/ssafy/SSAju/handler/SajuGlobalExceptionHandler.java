package ssafy.SSAju.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.ErrorInfo;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.exception.ConsentRequiredException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.FeedbackNotAllowedException;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.exception.DailyLimitExceededException;
import ssafy.SSAju.exception.DuplicateEmailException;
import ssafy.SSAju.exception.ExternalApiException;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.InvalidPasswordException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.exception.PublicDataApiException;
import ssafy.SSAju.exception.AnalyticsNotFoundException;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;

import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.validation.FieldError;

@Slf4j
@RestControllerAdvice
public class SajuGlobalExceptionHandler {

    /**
     * 이메일 중복 예외를 처리합니다.
     *
     * <p>회원가입 시 이미 등록된 이메일로 가입 시도할 경우 발생합니다.
     *
     * @param e DuplicateEmailException
     * @param request HTTP 요청
     * @return 409 Conflict, 에러 코드: DUPLICATE_EMAIL
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(
            DuplicateEmailException e, HttpServletRequest request) {
        log.warn("중복 이메일: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.DUPLICATE_EMAIL.getCode(),
                        ErrorMessageConstants.DUPLICATE_EMAIL.getMessage(), generateRequestId())));
    }

    /**
     * 토큰 유효성 예외를 처리합니다.
     *
     * <p>RefreshToken 갱신 시 토큰이 유효하지 않거나 만료된 경우 발생합니다.
     *
     * @param e InvalidTokenException
     * @param request HTTP 요청
     * @return 401 Unauthorized, 에러 코드: INVALID_TOKEN
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(
            InvalidTokenException e, HttpServletRequest request) {
        log.warn("유효하지 않은 토큰: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.INVALID_TOKEN.getCode(),
                        ErrorMessageConstants.INVALID_TOKEN.getMessage(), generateRequestId())));
    }

    /**
     * 약관 미동의 예외를 처리합니다.
     *
     * <p>회원가입 시 이용약관 또는 개인정보 수집에 미동의한 경우 발생합니다.
     *
     * @param e ConsentRequiredException
     * @param request HTTP 요청
     * @return 403 Forbidden, 에러 코드: TERMS_AGREEMENT_REQUIRED
     */
    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleConsentRequired(
            ConsentRequiredException e, HttpServletRequest request) {
        log.warn("약관 미동의: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.TERMS_AGREEMENT_REQUIRED.getCode(),
                        ErrorMessageConstants.TERMS_AGREEMENT_REQUIRED.getMessage(), generateRequestId())));
    }

    /**
     * 접근 권한 예외를 처리합니다.
     *
     * <p>본인 소유가 아닌 분석 결과에 접근하려는 경우 등 권한이 없는 접근에서 발생합니다.
     * AuthException 보다 먼저 등록하여 구체적인 핸들러가 우선 적용되도록 합니다.
     *
     * @param e UnauthorizedException
     * @param request HTTP 요청
     * @return 403 Forbidden, 에러 코드: ACCESS_DENIED
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException e, HttpServletRequest request) {
        log.warn("접근 권한 없음: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.ACCESS_DENIED.getCode(),
                        ErrorMessageConstants.ACCESS_DENIED.getMessage(), generateRequestId())));
    }

    /**
     * 인증 예외를 처리합니다.
     *
     * <p>로그인 실패(이메일 미존재, 비밀번호 불일치) 등의 인증 관련 오류입니다.
     * User Enumeration 공격 방지를 위해 로그인 실패 시 구체적인 실패 원인을 공개하지 않습니다.
     *
     * @param e AuthException
     * @param request HTTP 요청
     * @return 401 Unauthorized, 에러 코드: INVALID_CREDENTIALS
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(
            AuthException e, HttpServletRequest request) {
        log.warn("인증 예외: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.INVALID_CREDENTIALS.getCode(),
                        ErrorMessageConstants.INVALID_CREDENTIALS.getMessage(), generateRequestId())));
    }

    /**
     * 사용자 미발견 예외를 처리합니다.
     *
     * <p>마이페이지 접근, 사용자 정보 조회 시 해당 사용자를 찾을 수 없는 경우 발생합니다.
     *
     * @param e UserNotFoundException
     * @param request HTTP 요청
     * @return 404 Not Found, 에러 코드: USER_NOT_FOUND
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(
            UserNotFoundException e, HttpServletRequest request) {
        log.warn("사용자 미발견: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.USER_NOT_FOUND.getCode(),
                        ErrorMessageConstants.USER_NOT_FOUND.getMessage(), generateRequestId())));
    }

    /**
     * 비밀번호 오류 예외를 처리합니다.
     *
     * <p>로그인 시 비밀번호가 일치하지 않는 경우 발생합니다.
     * 사용자 열거 공격 방지를 위해 "이메일 또는 비밀번호가 일치하지 않습니다"로 응답합니다.
     *
     * @param e InvalidPasswordException
     * @param request HTTP 요청
     * @return 401 Unauthorized, 에러 코드: INVALID_CREDENTIALS
     */
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPassword(
            InvalidPasswordException e, HttpServletRequest request) {
        log.warn("비밀번호 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.INVALID_CREDENTIALS.getCode(),
                        ErrorMessageConstants.INVALID_CREDENTIALS.getMessage(), generateRequestId())));
    }

    /**
     * 일일 제한 초과 예외를 처리합니다.
     *
     * <p>사용자가 일일 API 사용 한도(3회 사주 분석)를 초과한 경우 발생합니다.
     *
     * @param e DailyLimitExceededException
     * @param request HTTP 요청
     * @return 429 Too Many Requests, 에러 코드: DAILY_LIMIT_EXCEEDED
     */
    @ExceptionHandler(DailyLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleDailyLimitExceeded(
            DailyLimitExceededException e, HttpServletRequest request) {
        log.warn("일일 API 사용 한도 초과: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.DAILY_LIMIT_EXCEEDED.getCode(),
                        ErrorMessageConstants.DAILY_LIMIT_EXCEEDED.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(FeedbackNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeedbackNotAllowed(
            FeedbackNotAllowedException e, HttpServletRequest request) {
        log.warn("피드백 불가 요청: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo(
                        "FEEDBACK_NOT_ALLOWED", e.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(SajuResultNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSajuResultNotFound(
            SajuResultNotFoundException e, HttpServletRequest request) {
        log.warn("SajuResult not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getCode(),
                        e.getMessage(), generateRequestId())));
    }

    @ExceptionHandler(AnalyticsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAnalyticsNotFound(
            AnalyticsNotFoundException e, HttpServletRequest request) {
        log.warn("Analytics not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.ANALYTICS_NOT_FOUND.getCode(),
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
        log.error("OpenAI API error: statusCode={}, message={}", e.getStatusCode(), e.getMessage());
        if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
            // 서버의 API Key 설정 문제 → 클라이언트에 인증 오류를 노출하지 않음
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(new ErrorInfo(
                            ErrorMessageConstants.OPENAI_UNAUTHORIZED.getCode(),
                            "서버 설정 오류로 요청을 처리할 수 없습니다.", generateRequestId())));
        }
        if (e.getStatusCode() == 429) {
            // OpenAI rate limit → 일시적 서비스 불가 (클라이언트 잘못 아님)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure(new ErrorInfo(
                            ErrorMessageConstants.OPENAI_RATE_LIMIT.getCode(),
                            ErrorMessageConstants.OPENAI_RATE_LIMIT.getMessage(), generateRequestId())));
        }
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        String requestId = generateRequestId();
        log.warn("잘못된 요청 파라미터: requestId={}", requestId);
        log.debug("IllegalArgumentException 상세: requestId={}", requestId, e);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.VALIDATION_FAILED.getCode(),
                        "요청 파라미터가 유효하지 않습니다.", requestId)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String requestId = generateRequestId();
        log.warn("요청 파라미터 타입 오류: requestId={}", requestId);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(new ErrorInfo(
                        ErrorMessageConstants.VALIDATION_FAILED.getCode(),
                        "요청 파라미터 값이 올바르지 않습니다.", requestId)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
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
