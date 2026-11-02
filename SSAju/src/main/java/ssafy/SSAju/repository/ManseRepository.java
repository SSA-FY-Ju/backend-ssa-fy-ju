package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;

import java.util.Optional;

@Repository
public interface ManseRepository extends JpaRepository<Manse, Long> {

    Optional<Manse> findByUserProfile(UserProfile userProfile);

    Optional<Manse> findByUserProfileId(Long userProfileId);
}
