package ssafy.SSAju.career.caller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.OpenAIApiException;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultationOpenAICaller {

    private final ChatClient chatClient;

    /**
     * OpenAI API를 호출하여 커리어 조언을 받습니다.
     *
     * 재시도 정책 (Spring Retry):
     * - ResourceAccessException (네트워크/타임아웃): 재시도
     * - HttpServerErrorException (5xx 서버 오류): 재시도
     * - OpenAIApiException (검증 실패): 재시도 안 함 (noRetryFor)
     * - HttpMessageConversionException (역직렬화/스키마 불일치): 재시도 안 함 (noRetryFor)
     * 최대 2회 재시도 (총 3회 시도)
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            noRetryFor = {OpenAIApiException.class, HttpMessageConversionException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public CareerAdviceResponse call(FastAPIResponse sajuData,
                                     TenGodDistribution tenGodDistribution,
                                     HiddenStems hiddenStems,
                                     String dayMaster) {
        String prompt = buildPrompt(sajuData, tenGodDistribution, hiddenStems, dayMaster);
        CareerAdviceResponse response;
        try {
            response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(CareerAdviceResponse.class);
        } catch (OpenAIApiException e) {
            throw e;
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // 네트워크/타임아웃/5xx → @Retryable 재시도 대상
            // TODO: 문서 정리 시 log.error("...", e) 로 예외 객체 전달하여 스택 트레이스 포함할 것
            log.error("OpenAI API 호출 실패, 재시도 예정");
            throw e;
        } catch (Exception e) {
            // 역직렬화 실패, 응답 스키마 불일치 등 비일시적 오류 → 재시도 안 함
            // TODO: 문서 정리 시 log.error("...", e) 로 예외 객체 전달하여 스택 트레이스 포함할 것
            log.error("OpenAI API 응답 처리 실패 (재시도 불가)");
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), e);
        }
        validate(response);
        return response;
    }

    /**
     * 최대 재시도 횟수 초과 시 실행: RuntimeException → OpenAIApiException으로 변환.
     */
    @Recover
    public CareerAdviceResponse recover(RuntimeException ex,
                                        FastAPIResponse sajuData,
                                        TenGodDistribution tenGodDistribution,
                                        HiddenStems hiddenStems,
                                        String dayMaster) {
        // TODO: 문서 정리 시 log.error("...", ex) 로 예외 객체 전달하여 스택 트레이스 포함할 것
        log.error("OpenAI API 재시도 후 최종 실패");
        throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), ex);
    }

    private void validate(CareerAdviceResponse response) {
        if (response == null) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_EMPTY_RESPONSE.getMessage());
        }
        if (response.industries() == null || response.industries().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_INDUSTRIES.getMessage());
        }
        for (var industry : response.industries()) {
            if (industry == null
                    || industry.name() == null || industry.name().isBlank()
                    || industry.reason() == null || industry.reason().isBlank()) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_INDUSTRY_ITEM.getMessage());
            }
        }
        if (response.interviewTips() == null || response.interviewTips().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_INTERVIEW_TIPS.getMessage());
        }
        for (var tip : response.interviewTips()) {
            if (tip == null || tip.isBlank()) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_INTERVIEW_ITEM.getMessage());
            }
        }
        if (response.strengths() == null || response.strengths().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_STRENGTHS.getMessage());
        }
        for (var strength : response.strengths()) {
            if (strength == null || strength.isBlank()) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_STRENGTH_ITEM.getMessage());
            }
        }
    }

    private String buildPrompt(FastAPIResponse sajuData,
                                TenGodDistribution tenGodDistribution,
                                HiddenStems hiddenStems,
                                String dayMaster) {
        int currentYear = LocalDate.now().getYear();
        return """
                당신은 사주 명리학 전문가이자 취업 커리어 컨설턴트입니다.
                아래 사주 데이터를 분석하여 취업 준비생에게 맞춤 커리어 조언을 한글로 제공해주세요.

                [사주 데이터]
                - 일간(日干): %s
                - 천간(天干): %s
                - 지지(地支): %s
                - 오행 분포: %s
                - 지장간(地藏干): %s
                - 십신 분포(十神): %s

                [분석 요청]
                - 취업 적합 산업군 3~5개 (name, reason, recommendedRoles 포함)
                - 면접 전략 및 직무 강점·약점 분석
                - 재물운, 장기 커리어 로드맵(0~2년, 3~5년 단계)
                - 퍼스널 브랜딩, 자소서 파워키워드(3개, 오행 기반, 해시태그 형식)
                - 멘탈 케어, 최적 근무 환경, 업무 스타일, 인간관계 전략
                - %d년 기준 12개월 월별 운세 및 전환점(pivotPoints: 점수 8 이상인 달만)
                - 일간(%s) 기반 성향 분석 및 핵심 십신 2~3개 선별

                [중요] careerTimeline.months의 각 달은 반드시 객체 형식으로 응답:
                올바른 예: "January": {"type": "적극기", "description": "면접 기회가 많은 시기"}
                잘못된 예: "January": "좋음" 또는 "January": 3
                """.formatted(
                dayMaster,
                sajuData.heavenlyStems(),
                sajuData.earthlyBranches(),
                sajuData.fiveElements(),
                hiddenStems,
                tenGodDistribution,
                currentYear,
                dayMaster
        );
    }
}
