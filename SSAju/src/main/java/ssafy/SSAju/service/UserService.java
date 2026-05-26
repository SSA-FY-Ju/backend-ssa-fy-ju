package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.dto.response.UserAnalysisDto;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.AnalysisHistoryRepository;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final SajuResultRepository sajuResultRepository;
    private final CareerConsultationRepository careerConsultationRepository;
    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final ConsultationMapper consultationMapper;
    private final CompatibilityChildReadService compatibilityChildReadService;

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
            case CAREER_CONSULTATION -> buildCareerConsultationDetail(user, analysisId);
            case COMPANY_COMPATIBILITY -> buildCompatibilityDetail(user, analysisId);
        };
    }

    // ─────────────────────────────────────────
    // private: detail builders
    // ─────────────────────────────────────────

    private AnalysisDetailResponse buildSajuDetail(User user, Long analysisId) {
        SajuResult sajuResult = sajuResultRepository
                .findByIdAndUser_IdWithProfileAndFortune(analysisId, user.getId())
                .orElseThrow(() -> new SajuResultNotFoundException("사주 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = sajuResult.getUserProfile();
        CareerFortune cf = sajuResult.getCareerFortune();
        AnalysisDetailResponse.CareerFortuneDetail cfDetail = cf == null ? null :
                new AnalysisDetailResponse.CareerFortuneDetail(
                        cf.getFavoredPeriod(), cf.getConfidenceScore(), cf.getReasoning());

        return new AnalysisDetailResponse(
                AnalysisType.SAJU.name(), sajuResult.getId(), user.getName(), profile.getBirthDate(),
                sajuResult.getFetchedAt(), null, null, cfDetail, null, null);
    }

    private AnalysisDetailResponse buildCareerConsultationDetail(User user, Long analysisId) {
        CareerConsultation cc = careerConsultationRepository
                .findByIdWithSajuResultAndProfile(analysisId)
                .orElseThrow(() -> new SajuResultNotFoundException("커리어 컨설팅 결과를 찾을 수 없습니다."));

        if (!user.equals(cc.getSajuResult().getUser())) {
            throw new UnauthorizedException("접근 권한이 없습니다.");
        }

        UserProfile profile = cc.getSajuResult().getUserProfile();
        ConsultationResponse consultationDetail = consultationMapper.toResponseFromEntity(cc);

        return new AnalysisDetailResponse(
                AnalysisType.CAREER_CONSULTATION.name(), cc.getId(), user.getName(), profile.getBirthDate(),
                cc.getGeneratedAt(), null, null, null, consultationDetail, null);
    }

    private AnalysisDetailResponse buildCompatibilityDetail(User user, Long analysisId) {
        CompanyCompatibility cc = companyCompatibilityRepository.findByIdAndUser(analysisId, user)
                .orElseThrow(() -> new SajuResultNotFoundException("기업 궁합 분석 결과를 찾을 수 없습니다."));

        UserProfile profile = cc.getUserProfile();
        CompatibilityResponse compatibilityDetail = compatibilityChildReadService.buildFromExisting(cc);

        return new AnalysisDetailResponse(
                AnalysisType.COMPANY_COMPATIBILITY.name(), cc.getId(), cc.getCompanyName(), profile.getBirthDate(),
                cc.getCreatedAt(), null, null, null, null, compatibilityDetail);
    }

}
