package uk.ac.ed.acp4.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.ac.ed.acp4.model.CodeFix;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeFixRepository extends JpaRepository<CodeFix, Long> {
    Optional<CodeFix> findByTicketId(Long ticketId);
    List<CodeFix> findByStatus(String status);
}