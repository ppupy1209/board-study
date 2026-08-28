package board.common.outboxmessagerelay;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findAllByCreatedAtLessThanEqualOrderByCreatedAtAsc(
            LocalDateTime from,
            Pageable pageable
    );

    Optional<Outbox> findFirstByOrderByCreatedAtAsc();
}
