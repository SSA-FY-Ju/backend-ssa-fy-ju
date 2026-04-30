package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ChatClient chatClient;
    private final UserProfileRepository userProfileRepository;
    private final SajuResultRepository sajuResultRepository;
    private final CareerConsultationRepository careerConsultationRepository;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelVersion;

    @Transactional
    public ConsultationResponse getCareerConsultation(ConsultationRequest request) {
        log.info("커리어 컨설팅 시작: birthDate={}, birthTime={}", request.birthDate(), request.birthTime());

        UserProfile userProfile = userProfileRepository
                .findByBirthDateAndBirthTime(request.birthDate(), request.birthTime())
                .orElseThrow(() -> new InvalidSajuDataException(
                        "해당 생년월일시의 사주 데이터가 없습니다. 먼저 관운 분석(/api/career/timing)을 진행해주세요."));

        SajuResult sajuResult = sajuResultRepository
                .findByUserProfile(userProfile)
                .orElseThrow(() -> new InvalidSajuDataException(
                        "사주 분석 결과가 없습니다. 먼저 관운 분석(/api/career/timing)을 진행해주세요."));

        CareerAdviceResponse advice = callOpenAI(request);

        CareerConsultation consultation = CareerConsultation.builder()
                .sajuResult(sajuResult)
                .industries(advice.industries())
                .interviewTips(advice.interviewTips())
                .strengths(advice.strengths())
                .openaiModelVersion(modelVersion)
                .build();
        careerConsultationRepository.save(consultation);

        log.info("커리어 컨설팅 완료: sajuResultId={}", sajuResult.getId());
        return new ConsultationResponse(
                advice.industries(),
                advice.interviewTips(),
                advice.strengths(),
                modelVersion
        );
    }

    private CareerAdviceResponse callOpenAI(ConsultationRequest request) {
        String prompt = buildPrompt(request);
        try {
            CareerAdviceResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(CareerAdviceResponse.class);
            if (response == null) {
                throw new OpenAIApiException("OpenAI 응답이 비어있습니다");
            }
            return response;
        } catch (OpenAIApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI API 호출 실패: {}", e.getMessage());
            throw new OpenAIApiException("OpenAI API 호출 실패: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(ConsultationRequest request) {
        return """
                당신은 사주 명리학 전문가입니다. 아래 사주 데이터를 분석하여 취업 준비생에게 맞춤 커리어 조언을 제공해주세요.

                [사주 데이터]
                - 천간(天干): %s
                - 지지(地支): %s
                - 오행 분포: %s
                - 지장간(地藏干): %s
                - 십신 분포(十神): %s

                아래 JSON 형식으로 정확히 응답해주세요:
                {
                  "industries": [{"name": "산업명", "reason": "사주에 기반한 이유"}],
                  "interviewTips": ["팁1", "팁2", "팁3"],
                  "strengths": ["강점1", "강점2", "강점3"]
                }

                규칙:
                - industries: 추천 산업군 3-5개 (각각 name과 reason 포함)
                - interviewTips: 면접 준비 팁 3개 (사주 특성 기반)
                - strengths: 직무 강점 3개 (십신과 지장간 분석 기반)
                """.formatted(
                request.heavenlyStems(),
                request.earthlyBranches(),
                request.fiveElements(),
                request.hiddenStems(),
                request.tenGodDistribution()
        );
    }
}
