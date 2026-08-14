package ssafy.SSAju.career.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 월별 운세 대상 월 목록을 계산합니다.
 *
 * <p>이 계산은 "AI에게 뭘 물어볼지"(프롬프트 텍스트 조립, {@code PromptProvider})와는
 * 별개의 순수 도메인 계산이다 — 프롬프트 본문에 포함되기도 하지만, AI 응답이 정확히
 * 이 월들을 커버하는지 검증({@code CompanyMatchingOpenAICaller.validate})하고 월별 운세를
 * 조립({@code AnalysisResponseBuilder.buildMonthlyForecasts})하는 데도 동일하게 쓰인다.
 * 검증/조립 로직이 프롬프트 생성 클래스에 의존하지 않도록 이 클래스로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class ForecastMonthCalculator {

    /** KST 기준 현재 날짜 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;

    /**
     * 현재 월부터 {@link AnalysisConstants#FORECAST_MONTH_COUNT}개월의 대상 월 목록을 계산합니다.
     * 12월 경계는 순환합니다(예: 11월 → [11, 12, 1, 2, 3]).
     */
    public List<Integer> currentTargetMonths() {
        int currentMonth = LocalDate.now(clock).getMonthValue();
        List<Integer> targetMonths = new ArrayList<>();
        for (int i = 0; i < AnalysisConstants.FORECAST_MONTH_COUNT; i++) {
            targetMonths.add(((currentMonth - 1 + i) % 12) + 1);
        }
        return targetMonths;
    }
}
