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
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.time.LocalDate;
import java.time.LocalTime;
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
    private final UserProfileRepository userProfileRepository;
    private final UserSatisfactionFeedbackRepository feedbackRepository;
    private final CareerFortuneService careerFortuneService;
    private final CompanyMatchingService companyMatchingService;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPage(Long userId, String type, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        List<UserAnalysisDto> analyses = (type != null && !type.isBlank())
                ? analysisHistoryRepository.findAllByUserIdAndType(userId, type, page, size)
                : analysisHistoryRepository.findAllByUserId(userId, page, size);

        long total = analysisHistoryRepository.countAllByUserId(userId);
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;

        MyPageResponse.UserProfileData profileData = new MyPageResponse.UserProfileData(
                user.getId(), user.getName(), user.getEmail(),
                user.getCreatedAt(), user.getLastLoginAt());

        return new MyPageResponse(profileData, analyses,
                new MyPageResponse.PaginationInfo(page, size, total, totalPages));
    }

    @Transactional(readOnly = true)
    public AnalysisDetailResponse getAnalysisDetail(Long userId, Long analysisId, String type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        return switch (type) {
            case "SAJU" -> buildSajuDetail(user, analysisId);
            case "CAREER_FORTUNE" -> buildCareerFortuneDetail(user, analysisId);
            case "COMPANY_COMPATIBILITY" -> buildCompatibilityDetail(user, analysisId);
            default -> throw new IllegalArgumentException("지원하지 않는 분석 타입: " + type);
        };
    }

    /**
     * 재분석 실행.
     * @Transactional 없음: 내부에서 careerFortuneService/companyMatchingService가
     * 각자의 트랜잭션으로 커밋하므로, 재분석 후 findBy 조회 시 커밋된 데이터를 볼 수 있음.
     */
    public ReanalyzeResponse reanalyze(Long userId, Long analysisId, String type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        return switch (type) {
            case "SAJU" -> reanalyzeSaju(user, analysisId);
            case "CAREER_FORTUNE" -> reanalyzeCareerFortune(user, analysisId);
            case "COMPANY_COMPATIBILITY" -> reanalyzeCompatibility(user, analysisId);
            default -> throw new IllegalArgumentException("지원하지 않는 분석 타입: " + type);
        };
    }

    @Transactional
    public void associateSajuResultWithUser(Long userId, LocalDate birthDate, LocalTime birthTime) {
        userProfileRepository.findByBirthDateAndBirthTime(birthDate, birthTime)
                .flatMap(sajuResultRepository::findByUserProfile)
                .ifPresent(sr -> {
                    if (sr.getUser() == null) {
                        userRepository.findById(userId).ifPresent(user -> {
                            sr.assignUser(user);
                            sajuResultRepository.save(sr);
                        });
                    }
                });
    }

    @Transactional
    public void associateCompatibilityWithUser(Long userId, LocalDate birthDate, LocalTime birthTime,
                                               String companyName, JobCategoryEnum targetRoleCategory) {
        userProfileRepository.findByBirthDateAndBirthTime(birthDate, birthTime)
                .ifPresent(profile ->
                        companyCompatibilityRepository.findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
                                        profile.getId(), companyName, targetRoleCategory)
                                .ifPresent(cc -> {
                                    if (cc.getUser() == null) {
                                        userRepository.findById(userId).ifPresent(user -> {
                                            cc.assignUser(user);
                                            companyCompatibilityRepository.save(cc);
                                        });
                                    }
                                }));
    }

    // ─────────────────────────────────────────
    // private: detail builders
    // ─────────────────────────────────────────

    private AnalysisDetailResponse buildSajuDetail(User user, Long analysisId) {
        SajuResult sajuResult = sajuResultRepository.findByIdAndUser(analysisId, user)
                .orElseThrow(() -> new SajuResultNotFoundException("사주 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = sajuResult.getUserProfile();
        UserSatisfactionFeedback feedback =
                feedbackRepository.findBySajuResult_IdAndUser(analysisId, user).orElse(null);

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
        SajuResult existing = sajuResultRepository.findByIdAndUser(analysisId, user)
                .orElseThrow(() -> new SajuResultNotFoundException("사주 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = existing.getUserProfile();
        careerFortuneService.analyzeCareerTiming(profile.getBirthDate(), profile.getBirthTime());

        SajuResult newResult = sajuResultRepository.findByUserProfile(profile)
                .orElseThrow(() -> new SajuResultNotFoundException("재분석 결과를 찾을 수 없습니다."));
        associateSajuResultWithUser(user.getId(), profile.getBirthDate(), profile.getBirthTime());

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
        careerFortuneService.analyzeCareerTiming(profile.getBirthDate(), profile.getBirthTime());

        SajuResult newResult = sajuResultRepository.findByUserProfile(profile)
                .orElseThrow(() -> new SajuResultNotFoundException("재분석 결과를 찾을 수 없습니다."));
        associateSajuResultWithUser(user.getId(), profile.getBirthDate(), profile.getBirthTime());

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

        companyCompatibilityRepository.deleteById(existing.getId());

        CompatibilityRequest request = new CompatibilityRequest(
                profile.getBirthDate(), profile.getBirthTime(),
                new CompatibilityRequest.TargetRoleRequest(targetRole, detailName),
                companyName, null, null);

        companyMatchingService.analyzeCompatibility(request);

        CompanyCompatibility newCc = companyCompatibilityRepository
                .findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
                        profile.getId(), companyName, targetRole)
                .orElseThrow(() -> new SajuResultNotFoundException("재분석 결과를 찾을 수 없습니다."));

        associateCompatibilityWithUser(user.getId(), profile.getBirthDate(), profile.getBirthTime(),
                companyName, targetRole);

        log.info("기업 궁합 재분석 완료: userId={}, newId={}", user.getId(), newCc.getId());
        return new ReanalyzeResponse("재분석이 완료되었습니다.", newCc.getId(), "COMPANY_COMPATIBILITY");
    }
}
