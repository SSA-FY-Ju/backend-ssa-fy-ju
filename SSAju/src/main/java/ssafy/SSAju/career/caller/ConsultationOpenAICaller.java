package ssafy.SSAju.career.caller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.OpenAIApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultationOpenAICaller {

    private final ChatClient chatClient;

    public CareerAdviceResponse call(FastAPIResponse sajuData,
                                     Map<String, Integer> tenGodDistribution,
                                     Map<String, List<String>> hiddenStems,
                                     String dayMaster) {
        try {
            // buildPrompt를 try 안에서 호출: 내부 NPE도 OpenAIApiException으로 래핑
            String prompt = buildPrompt(sajuData, tenGodDistribution, hiddenStems, dayMaster);
            CareerAdviceResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(CareerAdviceResponse.class);
            validate(response);
            return response;
        } catch (OpenAIApiException e) {
            throw e;
        } catch (Exception e) {
            // 스택트레이스만 로깅 (e.getMessage()는 민감 내부 정보 포함 가능)
            log.error("OpenAI API 호출 실패", e);
            throw new OpenAIApiException("OpenAI API 호출 실패", e);
        }
    }

    private void validate(CareerAdviceResponse response) {
        if (response == null) {
            throw new OpenAIApiException("OpenAI 응답이 비어있습니다");
        }
        if (response.industries() == null || response.industries().isEmpty()) {
            throw new OpenAIApiException("산업 추천 정보가 누락되었습니다");
        }
        if (response.interviewTips() == null || response.interviewTips().isEmpty()) {
            throw new OpenAIApiException("면접 팁 정보가 누락되었습니다");
        }
        if (response.strengths() == null || response.strengths().isEmpty()) {
            throw new OpenAIApiException("강점 분석 정보가 누락되었습니다");
        }
    }

    private String buildPrompt(FastAPIResponse sajuData,
                                Map<String, Integer> tenGodDistribution,
                                Map<String, List<String>> hiddenStems,
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
