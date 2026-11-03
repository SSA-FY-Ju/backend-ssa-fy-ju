package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.UserRepository;

/**
 * 사용자의 기본 출생정보 참조만 교체하는 짧은 쓰기 트랜잭션 경계.
 *
 * <p>프로필 조회·생성은 {@link ssafy.SSAju.career.provider.UserProfileProvider}가 트랜잭션 밖에서
 * 끝낸다 — 유일 제약 경합이 나면 실패한 쓰기 트랜잭션이 종료된 뒤 재조회해야 하는데, 바깥 트랜잭션
 * 안에서 실패하면 그 트랜잭션 전체가 롤백 대상이 되어 재조회 결과도 저장할 수 없다.
 *
 * <p>공유 프로필 행은 수정하지 않고 사용자의 FK만 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class DefaultProfileWriteService {

    private final UserRepository userRepository;

    @Transactional
    public void saveReference(Long userId, UserProfile userProfile) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessageConstants.USER_NOT_FOUND.getMessage()));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserNotFoundException(ErrorMessageConstants.USER_NOT_FOUND.getMessage());
        }
        user.changeDefaultProfile(userProfile);
    }
}
