package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.AnalysisHistoryRepository;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService 마이페이지 분석 상세 조회 테스트")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AnalysisHistoryRepository analysisHistoryRepository;
    @Mock private SajuResultRepository sajuResultRepository;
    @Mock private CareerConsultationRepository careerConsultationRepository;
    @Mock private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Mock private ConsultationMapper consultationMapper;
    @Mock private CompatibilityChildReadService compatibilityChildReadService;

    private UserService service;

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 10L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 5, 20);

    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트유저")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(LocalDateTime.now())
            .privacyAgreedAt(LocalDateTime.now())
            .build();

    @BeforeEach
    void setUp() {
        service = new UserService(
                userRepository, analysisHistoryRepository, sajuResultRepository,
                careerConsultationRepository, companyCompatibilityRepository,
                consultationMapper, compatibilityChildReadService);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
    }

    // ─────────────────────────────────────────
    // SAJU 상세 조회
    // ─────────────────────────────────────────

    @Test
    @DisplayName("SAJU 타입 상세 조회 → type이 AnalysisType.SAJU.name()으로 반환")
    void shouldReturnSajuDetail_WithCorrectTypeName() {
        var userProfile = mock(UserProfile.class);
        var careerFortune = mock(CareerFortune.class);
        var sajuResult = mock(SajuResult.class);

        given(userProfile.getBirthDate()).willReturn(BIRTH_DATE);
        given(careerFortune.getFavoredPeriod()).willReturn("H1");
        given(careerFortune.getConfidenceScore()).willReturn(80);
        given(careerFortune.getReasoning()).willReturn("상반기 유리");
        given(sajuResult.getId()).willReturn(ANALYSIS_ID);
        given(sajuResult.getUserProfile()).willReturn(userProfile);
        given(sajuResult.getCareerFortune()).willReturn(careerFortune);
        given(sajuResult.getFetchedAt()).willReturn(LocalDateTime.now());
        given(sajuResultRepository.findByIdAndUser_Id(eq(ANALYSIS_ID), any())).willReturn(Optional.of(sajuResult));

        AnalysisDetailResponse result = service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.SAJU);

        assertThat(result.type()).isEqualTo(AnalysisType.SAJU.name());
        assertThat(result.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(result.targetName()).isEqualTo("테스트유저");
        assertThat(result.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(result.careerFortuneDetail()).isNotNull();
        assertThat(result.careerFortuneDetail().favoredPeriod()).isEqualTo("H1");
        assertThat(result.careerFortuneDetail().confidenceScore()).isEqualTo(80);
        assertThat(result.consultationDetail()).isNull();
        assertThat(result.compatibilityDetail()).isNull();
    }

    @Test
    @DisplayName("SAJU 상세 조회 — SajuResult 없음 → SajuResultNotFoundException")
    void shouldThrow_WhenSajuResultNotFound() {
        given(sajuResultRepository.findByIdAndUser_Id(eq(ANALYSIS_ID), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.SAJU))
                .isInstanceOf(SajuResultNotFoundException.class);
    }

    // ─────────────────────────────────────────
    // CAREER_CONSULTATION 상세 조회
    // ─────────────────────────────────────────

    @Test
    @DisplayName("CAREER_CONSULTATION 타입 상세 조회 → type이 AnalysisType.CAREER_CONSULTATION.name()으로 반환")
    void shouldReturnCareerConsultationDetail_WithCorrectTypeName() {
        var userProfile = mock(UserProfile.class);
        var sajuResult = mock(SajuResult.class);
        var cc = mock(CareerConsultation.class);
        var mockResponse = mock(ConsultationResponse.class);

        given(userProfile.getBirthDate()).willReturn(BIRTH_DATE);
        given(sajuResult.getUser()).willReturn(MOCK_USER);
        given(sajuResult.getUserProfile()).willReturn(userProfile);
        given(cc.getId()).willReturn(ANALYSIS_ID);
        given(cc.getSajuResult()).willReturn(sajuResult);
        given(cc.getGeneratedAt()).willReturn(LocalDateTime.now());
        given(careerConsultationRepository.findById(ANALYSIS_ID)).willReturn(Optional.of(cc));
        given(consultationMapper.toResponseFromEntity(cc)).willReturn(mockResponse);

        AnalysisDetailResponse result = service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.CAREER_CONSULTATION);

        assertThat(result.type()).isEqualTo(AnalysisType.CAREER_CONSULTATION.name());
        assertThat(result.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(result.consultationDetail()).isSameAs(mockResponse);
        assertThat(result.careerFortuneDetail()).isNull();
        assertThat(result.compatibilityDetail()).isNull();
    }

    @Test
    @DisplayName("CAREER_CONSULTATION 상세 조회 — 다른 유저의 데이터 접근 → UnauthorizedException")
    void shouldThrow_WhenCareerConsultationBelongsToOtherUser() {
        var otherUser = User.builder()
                .email("other@test.com")
                .passwordHash("hash")
                .name("다른유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .build();

        var sajuResult = mock(SajuResult.class);
        var cc = mock(CareerConsultation.class);

        given(sajuResult.getUser()).willReturn(otherUser);
        given(cc.getSajuResult()).willReturn(sajuResult);
        given(careerConsultationRepository.findById(ANALYSIS_ID)).willReturn(Optional.of(cc));

        assertThatThrownBy(() -> service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.CAREER_CONSULTATION))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─────────────────────────────────────────
    // COMPANY_COMPATIBILITY 상세 조회
    // ─────────────────────────────────────────

    @Test
    @DisplayName("COMPANY_COMPATIBILITY 타입 상세 조회 → type이 AnalysisType.COMPANY_COMPATIBILITY.name()으로 반환")
    void shouldReturnCompatibilityDetail_WithCorrectTypeName() {
        var userProfile = mock(UserProfile.class);
        var cc = mock(CompanyCompatibility.class);
        var mockResponse = mock(CompatibilityResponse.class);

        given(userProfile.getBirthDate()).willReturn(BIRTH_DATE);
        given(cc.getId()).willReturn(ANALYSIS_ID);
        given(cc.getCompanyName()).willReturn("삼성전자");
        given(cc.getUserProfile()).willReturn(userProfile);
        given(cc.getCreatedAt()).willReturn(LocalDateTime.now());
        given(companyCompatibilityRepository.findByIdAndUser(ANALYSIS_ID, MOCK_USER)).willReturn(Optional.of(cc));
        given(compatibilityChildReadService.buildFromExisting(cc)).willReturn(mockResponse);

        AnalysisDetailResponse result = service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.COMPANY_COMPATIBILITY);

        assertThat(result.type()).isEqualTo(AnalysisType.COMPANY_COMPATIBILITY.name());
        assertThat(result.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(result.targetName()).isEqualTo("삼성전자");
        assertThat(result.compatibilityDetail()).isSameAs(mockResponse);
        assertThat(result.careerFortuneDetail()).isNull();
        assertThat(result.consultationDetail()).isNull();
    }

    @Test
    @DisplayName("COMPANY_COMPATIBILITY 상세 조회 — 존재하지 않는 ID → SajuResultNotFoundException")
    void shouldThrow_WhenCompatibilityNotFound() {
        given(companyCompatibilityRepository.findByIdAndUser(ANALYSIS_ID, MOCK_USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.COMPANY_COMPATIBILITY))
                .isInstanceOf(SajuResultNotFoundException.class);
    }

    // ─────────────────────────────────────────
    // 공통 — 사용자 없음
    // ─────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 userId → UserNotFoundException")
    void shouldThrow_WhenUserNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnalysisDetail(99L, ANALYSIS_ID, AnalysisType.SAJU))
                .isInstanceOf(UserNotFoundException.class);
    }
}
