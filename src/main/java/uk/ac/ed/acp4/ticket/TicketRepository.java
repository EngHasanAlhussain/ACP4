package uk.ac.ed.acp4.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.ac.ed.acp4.model.Ticket;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatus(String status);
    Optional<Ticket> findFirstByServiceNameAndStatusAndFixTypeOrderByCreatedAtDesc(
            String serviceName, String status, String fixType);
    List<Ticket> findByServiceName(String serviceName);
    Optional<Ticket> findFirstByServiceNameAndStatusOrderByCreatedAtDesc(String serviceName, String status);
}

