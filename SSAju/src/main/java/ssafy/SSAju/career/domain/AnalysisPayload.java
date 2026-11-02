package ssafy.SSAju.career.domain;

import ssafy.SSAju.career.enums.CompanyFoundingDateSource;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * {@code saju.input_json}·{@code saju.result_json}에 저장되는 유형별 입력·결과 스냅샷.
 *
 * <p>결과 스냅샷에는 분석 ID를 담지 않는다 — ID는 {@code saju.id} 컬럼이 유일한 출처이고 조회 시
 * 컬럼 값을 응답에 주입한다. 저장된 스냅샷은 조회 시 그대로 반환하며 현재 연도·모델·회사 정보로
 * 재생성하지 않는다.
 *
 * <p>중첩 구조는 기존 응답 DTO의 record를 그대로 재사용한다. 같은 모양을 한 벌 더 정의하면
 * 응답 필드가 바뀔 때 두 곳을 맞춰야 하고, 저장 스냅샷이 응답과 달라졌는지 컴파일러가
 * 잡아주지 못한다.
 */
public final class AnalysisPayload {

    /** 현재 지원하는 입력·결과 스냅샷 스키마 버전. */
    public static final int SCHEMA_VERSION = 1;

    private AnalysisPayload() {
    }

    /** 유형별 요청·실제 계산 입력 스냅샷. */
    public sealed interface Input permits SajuInput, ConsultationInput, CompatibilityInput {
        int schemaVersion();
    }

    /** 유형별 완성 결과 스냅샷(분석 ID 제외). */
    public sealed interface Result permits SajuResultData, ConsultationResultData, CompatibilityResultData {
        int schemaVersion();
    }

    // ───────────────────────────── SAJU (관운) ─────────────────────────────

    public record SajuInput(
            int schemaVersion,
            LocalDate birthDate,
            LocalTime birthTime,
            int analysisYear,
            int analysisMonth
    ) implements Input {
        public SajuInput {
            requireVersion(schemaVersion);
            requireBirthInfo(birthDate, birthTime);
        }

        public static SajuInput of(LocalDate birthDate, LocalTime birthTime, AnalysisTimeContext time) {
            return new SajuInput(SCHEMA_VERSION, birthDate, birthTime, time.year(), time.month());
        }
    }

    public record SajuResultData(
            int schemaVersion,
            String favoredPeriod,
            int confidenceScore,
            String reasoning
    ) implements Result {
        public SajuResultData {
            requireVersion(schemaVersion);
        }

        public static SajuResultData of(String favoredPeriod, int confidenceScore, String reasoning) {
            return new SajuResultData(SCHEMA_VERSION, favoredPeriod, confidenceScore, reasoning);
        }
    }

    // ──────────────────── CAREER_CONSULTATION (커리어 컨설팅) ────────────────────

    public record ConsultationInput(
            int schemaVersion,
            LocalDate birthDate,
            LocalTime birthTime,
            int consultationMonth,
            int analysisYear
    ) implements Input {
        public ConsultationInput {
            requireVersion(schemaVersion);
            requireBirthInfo(birthDate, birthTime);
        }

        public static ConsultationInput of(LocalDate birthDate, LocalTime birthTime, AnalysisTimeContext time) {
            return new ConsultationInput(SCHEMA_VERSION, birthDate, birthTime, time.yearMonth(), time.year());
        }
    }

    /** {@link ConsultationResponse}에서 consultationId만 제외한 완성 결과. */
    public record ConsultationResultData(
            int schemaVersion,
            List<CareerAdviceResponse.IndustryRecommendation> industries,
            List<String> interviewTips,
            List<String> strengths,
            String openaiModelVersion,
            String favoredPeriod,
            int confidenceScore,
            String reasoning,
            ConsultationResponse.SajuProfile sajuProfile,
            List<String> cautions,
            CareerAdviceResponse.WealthStyle wealthStyle,
            CareerAdviceResponse.LongTermRoadmap longTermRoadmap,
            CareerAdviceResponse.PersonalBranding personalBranding,
            CareerAdviceResponse.PowerKeywords powerKeywords,
            CareerAdviceResponse.MentalCare mentalCare,
            CareerAdviceResponse.EnvironmentFit environmentFit,
            CareerAdviceResponse.WorkStyle workStyle,
            CareerAdviceResponse.RelationshipStrategy relationshipStrategy,
            CareerAdviceResponse.CareerTimeline careerTimeline,
            String analysisSummary
    ) implements Result {
        public ConsultationResultData {
            requireVersion(schemaVersion);
        }
    }

    // ───────────────── COMPANY_COMPATIBILITY (기업/직무 궁합) ─────────────────

    public record CompatibilityInput(
            int schemaVersion,
            LocalDate requestedUserBirthDate,
            LocalTime requestedUserBirthTime,
            LocalTime resolvedUserBirthTime,
            String companyName,
            JobCategoryEnum targetRoleCategory,
            String targetRoleDetailName,
            LocalDate requestedCompanyFoundingDate,
            LocalTime requestedCompanyFoundingTime,
            LocalDate resolvedCompanyFoundingDate,
            LocalTime resolvedCompanyFoundingTime,
            CompanyFoundingDateSource companyFoundingDateSource,
            int compatibilityMonth,
            int analysisYear
    ) implements Input {
        public CompatibilityInput {
            requireVersion(schemaVersion);
            if (requestedUserBirthDate == null || resolvedUserBirthTime == null) {
                throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_SNAPSHOT_REQUIRED.getMessage());
            }
            if (resolvedCompanyFoundingDate == null || resolvedCompanyFoundingTime == null
                    || companyFoundingDateSource == null) {
                throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_SNAPSHOT_REQUIRED.getMessage());
            }
        }
    }

    /** {@link CompatibilityResponse}에서 compatibilityId만 제외한 완성 결과. */
    public record CompatibilityResultData(
            int schemaVersion,
            CompatibilityResponse.RequestContext requestContext,
            Integer compatibilityScore,
            String summary,
            CompatibilityResponse.TargetRoleAnalysis targetRoleAnalysis,
            CompatibilityResponse.FiveElements fiveElements,
            CompatibilityResponse.AnalysisBreakdown analysisBreakdown,
            CompatibilityResponse.ActionableStrategy actionableStrategy,
            List<CompatibilityResponse.InterviewQuestion> expectedInterviewQuestions,
            List<CompatibilityResponse.RoleCompatibility> roleCompatibility,
            List<CompatibilityResponse.MonthlyForecast> monthlyForecast,
            List<String> cautions
    ) implements Result {
        public CompatibilityResultData {
            requireVersion(schemaVersion);
        }
    }

    private static void requireVersion(int schemaVersion) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new InvalidSajuDataException(
                    ErrorMessageConstants.ANALYSIS_SNAPSHOT_VERSION_UNSUPPORTED.getMessage());
        }
    }

    private static void requireBirthInfo(LocalDate birthDate, LocalTime birthTime) {
        if (birthDate == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.BIRTH_DATE_REQUIRED.getMessage());
        }
        if (birthTime == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.BIRTH_TIME_REQUIRED.getMessage());
        }
    }
}
