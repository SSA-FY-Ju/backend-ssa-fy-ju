package ssafy.SSAju.admin.repository;

import ssafy.SSAju.entity.User;

import java.util.Optional;

public interface AdminUserAuthQueryRepository extends org.springframework.data.repository.Repository<User, Long> {

    Optional<User> findByEmail(String email);
}
