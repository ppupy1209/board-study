package board.auth.repository;

import board.auth.domain.AuthMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthMemberRepository extends JpaRepository<AuthMember, Long> {
    Optional<AuthMember> findByEmail(String email);

    boolean existsByEmail(String email);
}
