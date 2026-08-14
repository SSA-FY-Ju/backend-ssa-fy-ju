package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.CompatibilityNarrativeRequest;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.util.AnalysisConstants;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * OpenAI 호출에 사용될 프롬프트를 생성하는 컴포넌트.
 *
 * <p>사주 데이터(일간, 천간, 지지, 오행, 지장간, 십신)를 조합하여
 * 취업 커리어 컨설팅용 한국어 프롬프트를 생성합니다.
 * ConsultationOpenAICaller에서 주입받아 사용됩니다.
 *
 * @see ssafy.SSAju.career.caller.ConsultationOpenAICaller
 */
@Component
@RequiredArgsConstructor
public class PromptProvider {

    /** KST 기준 현재 연도 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;

    /**
     * 커리어 컨설팅 요청에 사용할 프롬프트를 생성합니다.
     *
     * <p>일간, 천간/지지, 오행 분포, 지장간, 십신 분포를 포함하며
     * 현재 연도 기준 12개월 월별 운세·타임라인 형식을 지정합니다.
     *
     * @param sajuData           FastAPI에서 받은 사주 원본 데이터
     * @param tenGodDistribution 십신 분포 (계산된 값)
     * @param hiddenStems        지장간 데이터 (계산된 값)
     * @param dayMaster          일간 (예: "己")
     * @return OpenAI에 전달할 한국어 프롬프트 문자열
     */
    public String getCareerConsultationPrompt(FastAPIResponse sajuData,
                                               TenGodDistribution tenGodDistribution,
                                               HiddenStems hiddenStems,
                                               String dayMaster) {
        int currentYear = LocalDate.now(clock).getYear();
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

                [중요] careerTimeline의 모든 month 값은 반드시 정수(1~12)로 응답:
                올바른 예: months의 키 1 → {"type": "적극기", "description": "면접 기회가 많은 시기"}
                잘못된 예: "2026-01": {...}, "January": {...}, "1월": {...}
                pivotPoints의 month 필드와 warningMonths의 원소도 동일하게 정수(1~12)로 응답
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

    /**
     * 기업 궁합 분석 해설 요청에 사용할 프롬프트를 생성합니다.
     *
     * <p>점수(궁합/직군매칭/역할별)는 이미 규칙 기반으로 계산되어 있으므로, AI에게는
     * 해당 점수를 그대로 전제로 해설 텍스트만 작성하도록 지시하고 재계산을 금지한다.
     *
     * @param request      사용자/기업 사주 데이터 및 계산 완료된 점수, 직군 정보
     * @param targetMonths 월별 조언 대상 월 목록 — {@code ForecastMonthCalculator.currentTargetMonths()}로
     *                     호출자가 한 번만 계산해 프롬프트 생성과 응답 검증 양쪽에 동일하게 전달해야 한다
     *                     (이 클래스는 프롬프트 "텍스트 조립"만 담당하고 월 계산 자체는 소유하지 않는다 —
     *                     같은 값이 AI 응답 검증/월별 운세 조립에도 쓰이므로 프롬프트 생성 클래스가
     *                     아닌 별도 도메인 계산 클래스에 둔다)
     * @return OpenAI에 전달할 한국어 프롬프트 문자열
     */
    public String getCompatibilityNarrativePrompt(CompatibilityNarrativeRequest request,
                                                    List<Integer> targetMonths) {
        return """
                당신은 사주 명리학 전문가이자 기업 궁합 분석 컨설턴트입니다.
                아래 사용자와 기업의 사주 데이터, 그리고 이미 계산이 끝난 점수를 바탕으로
                궁합 분석 해설 텍스트를 한글로 작성해주세요.

                [주의] 아래 점수는 이미 규칙 기반으로 계산이 완료된 값입니다.
                점수를 다시 계산하거나 임의로 바꾸지 말고, 이 점수를 근거로 한 해설만 작성하세요.
                - 궁합 점수: %d
                - 직군 매칭 점수: %d
                - 역할별 점수(전문가): %d
                - 역할별 점수(리드): %d

                [사용자 사주 데이터]
                - 일간(日干): %s
                - 오행 분포: %s
                - 지장간(地藏干): %s

                [기업 사주 데이터]
                - 일간(日干): %s
                - 오행 분포: %s
                - 지장간(地藏干): %s

                [직군 정보]
                - 직군: %s
                - 상세 직무명: %s

                [응답 스키마] 아래 필드를 모두 포함한 JSON으로 응답하세요:
                - summary: 궁합 종합 요약 한 줄
                - roleSynergy: 직군 적합도 시너지 설명
                - roleWarning: 직군 적합도 경고/유의사항 설명
                - fiveElementsSynergyDescription: 사용자-기업 오행 상생 설명
                - weaknessDefense: 약점 방어 전략(면접 대응 문구)
                - interviewQuestions: 예상 면접 질문 목록(question, intent 포함, 최소 1개)
                - primaryRoleReason: 전문가 역할 적합 사유
                - secondaryRoleReason: 리드 역할 적합 사유
                - monthlyAdvices: {month, advice} 객체 배열, 반드시 정확히 %d개, month는 %s 각각 정확히
                  한 번씩만 포함(순서는 상관없음, month 값으로 어느 달인지 식별함)
                - cautions: 주의사항 목록(최소 1개)
                """.formatted(
                request.scores().compatibilityScore(),
                request.scores().matchScore(),
                request.scores().primaryScore(),
                request.scores().secondaryScore(),
                request.user().dayMaster(),
                request.user().fiveElements().asMap(),
                request.user().hiddenStems(),
                request.company().dayMaster(),
                request.company().fiveElements().asMap(),
                request.company().hiddenStems(),
                request.category().getDisplayName(),
                request.detailName(),
                AnalysisConstants.FORECAST_MONTH_COUNT,
                targetMonths
        );
    }
}
