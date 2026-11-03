package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.request.DefaultProfileRequest;
import ssafy.SSAju.dto.response.DefaultProfileResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.UserRepository;

/**
 * 분석 요청 화면에서 먼저 제안할 기본 출생정보를 조회·저장한다.
 *
 * <p>분석 실행과 완전히 분리된 경로다 — FastAPI·OpenAI 호출, 만세력·분석 생성, 쿼터 차감이 모두
 * 없다. 반대로 분석을 수행해도 기본값은 자동으로 바뀌지 않는다.
 *
 * <p>기본값을 바꿀 때 공유 프로필 행의 출생정보를 수정하지 않는다. 같은 행을 다른 사용자와 과거
 * 분석이 함께 가리키고 있어, 값을 바꾸면 남의 기본값과 이미 저장된 분석의 의미까지 달라진다.
 * 대신 해당 출생정보의 프로필을 조회·생성해 사용자 FK만 교체한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProfileService {

    private final UserRepository userRepository;
    private final UserProfileProvider userProfileProvider;
    private final DefaultProfileWriteService defaultProfileWriteService;
    private final RequestValidator requestValidator;

    @Transactional(readOnly = true)
    public DefaultProfileResponse getDefaultProfile(Long userId) {
        User user = findActiveUser(userId);
        UserProfile userProfile = user.getUserProfile();
        return userProfile == null ? null : DefaultProfileResponse.from(userProfile);
    }

    /**
     * 기본 출생정보를 저장하거나 교체한다.
     *
     * <p>{@code @Transactional}이 없다 — 프로필 생성 경합을 재조회로 수렴시키려면 실패한 쓰기
     * 트랜잭션이 먼저 끝나야 하고, 사용자 참조 저장은 짧은 별도 트랜잭션이어야 한다.
     */
    public DefaultProfileResponse changeDefaultProfile(Long userId, DefaultProfileRequest request) {
        if (userId == null) {
            throw new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요.");
        }
        requestValidator.validateBirthInfo(request.birthDate(), request.birthTime());

        UserProfile userProfile = userProfileProvider.findOrCreate(request.birthDate(), request.birthTime());
        defaultProfileWriteService.saveReference(userId, userProfile);

        log.info("기본 출생정보 저장 완료: userId={}", userId);
        return DefaultProfileResponse.from(userProfile);
    }

    private User findActiveUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessageConstants.USER_NOT_FOUND.getMessage()));
    }
}
