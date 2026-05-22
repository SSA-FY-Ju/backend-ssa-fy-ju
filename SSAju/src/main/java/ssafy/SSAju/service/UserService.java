package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.dto.response.ReanalyzeResponse;
import ssafy.SSAju.dto.response.UserAnalysisDto;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.AnalysisHistoryRepository;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final SajuResultRepository sajuResultRepository;
    private final CareerFortuneRepository careerFortuneRepository;
    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final UserSatisfactionFeedbackRepository feedbackRepository;
    private final CareerFortuneService careerFortuneService;
    private final CompanyMatchingService companyMatchingService;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPage(Long userId, AnalysisType type, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        String typeStr = type != null ? type.name() : null;
        List<UserAnalysisDto> analyses = (typeStr != null)
                ? analysisHistoryRepository.findAllByUserIdAndType(userId, typeStr, page, size)
                : analysisHistoryRepository.findAllByUserId(userId, page, size);

        long total = (typeStr != null)
                ? analysisHistoryRepository.countAllByUserIdAndType(userId, typeStr)
                : analysisHistoryRepository.countAllByUserId(userId);
        int totalPages = (int) Math.ceil((double) total / size);

        MyPageResponse.UserProfileData profileData = new MyPageResponse.UserProfileData(
                user.getId(), user.getName(), user.getEmail(),
                user.getCreatedAt(), user.getLastLoginAt());

        return new MyPageResponse(profileData, analyses,
                new MyPageResponse.PaginationInfo(page, size, total, totalPages));
    }

    @Transactional(readOnly = true)
    public AnalysisDetailResponse getAnalysisDetail(Long userId, Long analysisId, AnalysisType type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        return switch (type) {
            case SAJU -> buildSajuDetail(user, analysisId);
            case CAREER_FORTUNE -> buildCareerFortuneDetail(user, analysisId);
            case COMPANY_COMPATIBILITY -> buildCompatibilityDetail(user, analysisId);
        };
    }

    /**
     * 재분석 실행.
     *
     * COMPANY_COMPATIBILITY는 INSERT IGNORE 캐시 구조상 기존 레코드를 삭제 후 재분석해야 함.
     * 외부 API 실패 시 기존 데이터가 유실될 수 있는 설계 한계가 있으나,
     * 사용자가 에러 응답을 받고 신규 분석을 재시도할 수 있으므로 허용 범위로 판단.
     */
    @Transactional
    public ReanalyzeResponse reanalyze(Long userId, Long analysisId, AnalysisType type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        return switch (type) {
            case SAJU -> reanalyzeSaju(user, analysisId);
            case CAREER_FORTUNE -> reanalyzeCareerFortune(user, analysisId);
            case COMPANY_COMPATIBILITY -> reanalyzeCompatibility(user, analysisId);
        };
    }

    // ─────────────────────────────────────────
    // private: detail builders
    // ─────────────────────────────────────────

    private AnalysisDetailResponse buildSajuDetail(User user, Long analysisId) {
        SajuResult sajuResult = sajuResultRepository.findByIdAndUser_Id(analysisId, user.getId())
                .orElseThrow(() -> new SajuResultNotFoundException("사주 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = sajuResult.getUserProfile();
        UserSatisfactionFeedback feedback =
                feedbackRepository.findBySajuResult_IdAndUser_Id(analysisId, user.getId()).orElse(null);

        CareerFortune cf = sajuResult.getCareerFortune();
        AnalysisDetailResponse.CareerFortuneDetail cfDetail = cf == null ? null :
                new AnalysisDetailResponse.CareerFortuneDetail(
                        cf.getFavoredPeriod(), cf.getConfidenceScore(), cf.getReasoning());

        return new AnalysisDetailResponse(
                "SAJU", sajuResult.getId(), user.getName(), profile.getBirthDate(),
                sajuResult.getFetchedAt(),
                feedback != null ? feedback.getSatisfactionStatus().name() : null,
                feedback != null ? feedback.getFeedbackContent() : null,
                cfDetail, null);
    }

    private AnalysisDetailResponse buildCareerFortuneDetail(User user, Long analysisId) {
        CareerFortune cf = careerFortuneRepository.findById(analysisId)
                .orElseThrow(() -> new SajuResultNotFoundException("관운 분석 결과를 찾을 수 없습니다."));

        SajuResult sajuResult = cf.getSajuResult();
        if (!user.equals(sajuResult.getUser())) {
            throw new UnauthorizedException("접근 권한이 없습니다.");
        }

        UserProfile profile = sajuResult.getUserProfile();
        UserSatisfactionFeedback feedback =
                feedbackRepository.findBySajuResult_IdAndUser(sajuResult.getId(), user).orElse(null);

        return new AnalysisDetailResponse(
                "CAREER_FORTUNE", cf.getId(), user.getName(), profile.getBirthDate(),
                cf.getCreatedAt(),
                feedback != null ? feedback.getSatisfactionStatus().name() : null,
                feedback != null ? feedback.getFeedbackContent() : null,
                new AnalysisDetailResponse.CareerFortuneDetail(
                        cf.getFavoredPeriod(), cf.getConfidenceScore(), cf.getReasoning()),
                null);
    }

    private AnalysisDetailResponse buildCompatibilityDetail(User user, Long analysisId) {
        CompanyCompatibility cc = companyCompatibilityRepository.findByIdAndUser(analysisId, user)
                .orElseThrow(() -> new SajuResultNotFoundException("기업 궁합 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = cc.getUserProfile();

        return new AnalysisDetailResponse(
                "COMPANY_COMPATIBILITY", cc.getId(), cc.getCompanyName(), profile.getBirthDate(),
                cc.getCreatedAt(), null, null, null,
                new AnalysisDetailResponse.CompanyCompatibilityDetail(
                        cc.getCompanyName(), cc.getCompatibilityScore(), cc.getSummary()));
    }

    // ─────────────────────────────────────────
    // private: reanalyze helpers
    // ─────────────────────────────────────────

    private ReanalyzeResponse reanalyzeSaju(User user, Long analysisId) {
        SajuResult existing = sajuResultRepository.findByIdAndUser_Id(analysisId, user.getId())
                .orElseThrow(() -> new SajuResultNotFoundException("사주 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = existing.getUserProfile();
        careerFortuneService.analyzeCareerTiming(profile.getBirthDate(), profile.getBirthTime(), user.getId());

        SajuResult newResult = sajuResultRepository.findByUserProfile(profile)
                .orElseThrow(() -> new SajuResultNotFoundException("재분석 결과를 찾을 수 없습니다."));

        log.info("사주 재분석 완료: userId={}, newId={}", user.getId(), newResult.getId());
        return new ReanalyzeResponse("재분석이 완료되었습니다.", newResult.getId(), "SAJU");
    }

    private ReanalyzeResponse reanalyzeCareerFortune(User user, Long analysisId) {
        CareerFortune cf = careerFortuneRepository.findById(analysisId)
                .orElseThrow(() -> new SajuResultNotFoundException("관운 분석 결과를 찾을 수 없습니다."));

        SajuResult sr = cf.getSajuResult();
        if (!user.equals(sr.getUser())) {
            throw new UnauthorizedException("접근 권한이 없습니다.");
        }

        UserProfile profile = sr.getUserProfile();
        careerFortuneService.analyzeCareerTiming(profile.getBirthDate(), profile.getBirthTime(), user.getId());

        SajuResult newResult = sajuResultRepository.findByUserProfile(profile)
                .orElseThrow(() -> new SajuResultNotFoundException("재분석 결과를 찾을 수 없습니다."));

        CareerFortune newCf = careerFortuneRepository.findBySajuResult(newResult)
                .orElseThrow(() -> new SajuResultNotFoundException("재분석 관운 결과를 찾을 수 없습니다."));

        log.info("관운 재분석 완료: userId={}, newCfId={}", user.getId(), newCf.getId());
        return new ReanalyzeResponse("재분석이 완료되었습니다.", newCf.getId(), "CAREER_FORTUNE");
    }

    private ReanalyzeResponse reanalyzeCompatibility(User user, Long analysisId) {
        CompanyCompatibility existing = companyCompatibilityRepository.findByIdAndUser(analysisId, user)
                .orElseThrow(() -> new SajuResultNotFoundException("기업 궁합 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = existing.getUserProfile();
        String companyName = existing.getCompanyName();
        JobCategoryEnum targetRole = existing.getTargetRoleCategory();
        String detailName = existing.getTargetRoleDetailName();

        // UNIQUE(user_profile_id, company_name, target_role_category) 제약 해제 후 재분석
        // 실패 시 @Transactional(reanalyze)에 의해 rollback되어 기존 데이터 복원됨
        companyCompatibilityRepository.deleteById(existing.getId());
        companyCompatibilityRepository.flush();

        CompatibilityRequest request = new CompatibilityRequest(
                profile.getBirthDate(), profile.getBirthTime(),
                new CompatibilityRequest.TargetRoleRequest(targetRole, detailName),
                companyName, null, null);

        companyMatchingService.analyzeCompatibility(request, user.getId());

        CompanyCompatibility newCc = companyCompatibilityRepository
                .findFirstByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryOrderByVersionDesc(
                        user.getId(), profile.getId(), companyName, targetRole)
                .orElseThrow(() -> new SajuResultNotFoundException("재분석 결과를 찾을 수 없습니다."));

        log.info("기업 궁합 재분석 완료: userId={}, newId={}", user.getId(), newCc.getId());
        return new ReanalyzeResponse("재분석이 완료되었습니다.", newCc.getId(), "COMPANY_COMPATIBILITY");
    }
}
