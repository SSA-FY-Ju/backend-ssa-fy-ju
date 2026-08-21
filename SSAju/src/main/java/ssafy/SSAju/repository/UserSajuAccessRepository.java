package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.UserSajuAccess;

public interface UserSajuAccessRepository extends JpaRepository<UserSajuAccess, Long> {

    boolean existsByUserIdAndSajuResultId(Long userId, Long sajuResultId);
}
