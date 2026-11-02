package ssafy.SSAju.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * 사용자 행에 짧은 쓰기 잠금을 걸고 조회한다. 분석 결과·피드백·기본 출생정보의 최종 저장과
     * 회원 탈퇴가 서로 끼어들지 않도록 순서를 정하는 용도다.
     *
     * <p>외부 API 호출 구간에서는 이 잠금을 잡지 않는다 — 네트워크 지연 동안 사용자 행을 잠그면
     * 같은 사용자의 다른 요청과 탈퇴가 모두 막힌다. 호출자는 커밋 직전의 짧은 구간에서만 쓴다.
     *
     * <p>{@code @SQLRestriction("deleted_at IS NULL")}이 적용되므로 이미 탈퇴한 계정은 조회되지
     * 않는다. 호출자는 빈 결과를 "탈퇴 완료"로 처리하고 상태가 활성인지도 함께 확인한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
