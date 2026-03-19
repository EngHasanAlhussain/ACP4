package uk.ac.ed.acp4.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp4.model.AuditLog;
import uk.ac.ed.acp4.model.CodeFix;
import uk.ac.ed.acp4.model.Ticket;
import uk.ac.ed.acp4.ticket.TicketService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<List<Ticket>> getTickets(
            @RequestParam(required = false) String status) {
        List<Ticket> tickets = (status != null)
                ? ticketService.getTicketsByStatus(status.toUpperCase())
                : ticketService.getAllTickets();
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getTicket(@PathVariable Long id) {
        return ticketService.getTicketById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/fix")
    public ResponseEntity<CodeFix> getCodeFix(@PathVariable Long id) {
        return ticketService.getCodeFixByTicketId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLog>> getAuditLog(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getAuditLog(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Ticket> approveTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String engineer = (body != null) ? body.getOrDefault("engineer", "anonymous") : "anonymous";
        try {
            Ticket ticket = ticketService.approveTicket(id, engineer);
            return ResponseEntity.ok(ticket);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Ticket> rejectTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String engineer = (body != null) ? body.getOrDefault("engineer", "anonymous") : "anonymous";
        String reason   = (body != null) ? body.getOrDefault("reason", "No reason provided") : "No reason provided";
        try {
            Ticket ticket = ticketService.rejectTicket(id, engineer, reason);
            return ResponseEntity.ok(ticket);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}