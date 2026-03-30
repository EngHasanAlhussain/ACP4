package uk.ac.ed.acp4.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.ac.ed.acp4.model.SqlFix;

import java.util.Optional;

@Repository
public interface SqlFixRepository extends JpaRepository<SqlFix, Long> {
    Optional<SqlFix> findByTicketId(Long ticketId);
}