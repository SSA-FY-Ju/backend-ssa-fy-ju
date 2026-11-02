package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.repository.ManseRepository;

import java.time.Instant;

/**
 * 만세력 한 행을 저장하는 짧은 쓰기 트랜잭션 경계.
 *
 * <p>FastAPI 호출과 십성·지장간 계산은 {@link ssafy.SSAju.career.provider.ManseProvider}가
 * 트랜잭션 밖에서 끝낸 뒤, 실제 INSERT만 이 서비스에서 수행한다. 외부 I/O 동안 DB 커넥션을
 * 점유하지 않기 위해서다.
 *
 * <p>프로필당 만세력은 하나이므로 동시 최초 요청 중 하나는 유일 제약 위반으로 실패한다. 이
 * 트랜잭션이 독립적이라서 실패해도 호출자의 재조회가 정상 동작한다.
 */
@Service
@RequiredArgsConstructor
public class ManseWriteService {

    private final ManseRepository manseRepository;

    @Transactional
    public Manse save(UserProfile userProfile, ManseData data, Instant fetchedAt) {
        return manseRepository.saveAndFlush(Manse.builder()
                .userProfile(userProfile)
                .data(data)
                .fetchedAt(fetchedAt)
                .build());
    }
}
