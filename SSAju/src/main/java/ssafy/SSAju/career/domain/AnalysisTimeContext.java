package ssafy.SSAju.career.domain;

import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.util.AnalysisConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 한 번의 분석 요청이 사용하는 시간 문맥. 요청당 한 번만 확정하고 이후 프롬프트 조립·외부 호출
 * 재시도·응답 검증·요약 생성까지 그대로 전달한다.
 *
 * <p>요청 처리 도중 자정이나 연·월 경계를 넘어도 재시도와 요약이 서로 다른 기준을 쓰지 않도록
 * {@code LocalDate.now(clock)}를 각 단계에서 다시 호출하지 않는 것이 이 record의 목적이다.
 *
 * <p>일일 쿼터의 {@code usageDate}는 "실제로 차감한 날짜"이며 보상 복원의 기준이 되므로 이
 * 문맥으로 대체하지 않는다.
 */
public record AnalysisTimeContext(
        LocalDate analysisDate,
        int year,
        int month,
        List<Integer> targetMonths
) {

    /** {@code year * 100 + month} 형태의 기존 월 식별자(예: 202611) 변환 계수. */
    private static final int YEAR_MONTH_RADIX = 100;

    private static final int MIN_MONTH = 1;
    private static final int MAX_MONTH = 12;

    public AnalysisTimeContext {
        if (analysisDate == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TIME_CONTEXT_INVALID.getMessage());
        }
        if (year != analysisDate.getYear() || month != analysisDate.getMonthValue()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TIME_CONTEXT_INVALID.getMessage());
        }
        if (targetMonths == null || targetMonths.size() != AnalysisConstants.FORECAST_MONTH_COUNT) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TARGET_MONTHS_INVALID.getMessage());
        }
        for (Integer targetMonth : targetMonths) {
            if (targetMonth == null || targetMonth < MIN_MONTH || targetMonth > MAX_MONTH) {
                throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TARGET_MONTHS_INVALID.getMessage());
            }
        }
        targetMonths = List.copyOf(targetMonths);
    }

    /**
     * 주입된 {@link Clock}(서비스 기준 타임존)으로 이번 요청의 시간 문맥을 확정한다.
     * 대상 월은 현재 월부터 {@link AnalysisConstants#FORECAST_MONTH_COUNT}개월이며 12월을 순환한다.
     */
    public static AnalysisTimeContext from(Clock clock) {
        LocalDate analysisDate = LocalDate.now(clock);
        int month = analysisDate.getMonthValue();
        List<Integer> targetMonths = new ArrayList<>();
        for (int i = 0; i < AnalysisConstants.FORECAST_MONTH_COUNT; i++) {
            targetMonths.add(((month - 1 + i) % MAX_MONTH) + 1);
        }
        return new AnalysisTimeContext(analysisDate, analysisDate.getYear(), month, targetMonths);
    }

    /** 기존 컨설팅·궁합 월 식별자와 같은 {@code yyyyMM} 정수. */
    public int yearMonth() {
        return year * YEAR_MONTH_RADIX + month;
    }
}
