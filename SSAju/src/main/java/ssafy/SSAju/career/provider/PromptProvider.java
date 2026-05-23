package ssafy.SSAju.career.provider;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.time.LocalDate;

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
public class PromptProvider {

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
}
