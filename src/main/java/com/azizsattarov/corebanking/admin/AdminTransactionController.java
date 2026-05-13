package com.azizsattarov.corebanking.admin;

import com.azizsattarov.corebanking.admin.dto.AdminTransactionView;
import com.azizsattarov.corebanking.admin.dto.UpdateBlockchainRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service-only endpoints used by the in-middleware blockchain reconciliation worker.
 * Authentication: ROLE_SERVICE, granted by ServiceTokenAuthFilter when the
 * X-Service-Token header matches app.middleware.service-token.
 */
@RestController
@RequestMapping("/admin/transactions")
public class AdminTransactionController {

    private final AdminTransactionService adminService;

    public AdminTransactionController(AdminTransactionService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/pending-submit")
    public List<AdminTransactionView> pendingSubmit(
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "8")  int maxAttempts) {
        return adminService.findPendingSubmit(limit, maxAttempts);
    }

    @GetMapping("/submitted")
    public List<AdminTransactionView> submitted(
            @RequestParam(defaultValue = "25") int limit) {
        return adminService.findSubmitted(limit);
    }

    @GetMapping("/for-tamper-check")
    public List<AdminTransactionView> forTamperCheck(
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "100") int limit) {
        LocalDateTime sinceTs = (since == null || since.isBlank())
                ? LocalDateTime.now().minusDays(1)
                : LocalDateTime.parse(since);
        return adminService.findForTamperCheck(sinceTs, limit);
    }

    @GetMapping("/{transactionId}")
    public AdminTransactionView getById(@PathVariable Long transactionId) {
        return adminService.findById(transactionId);
    }

    @PatchMapping("/{transactionId}/blockchain")
    public AdminTransactionView updateBlockchain(
            @PathVariable Long transactionId,
            @RequestBody UpdateBlockchainRequest req) {
        return adminService.updateBlockchain(transactionId, req);
    }

    @PatchMapping("/{transactionId}/confirm")
    public AdminTransactionView confirm(@PathVariable Long transactionId) {
        return adminService.markConfirmed(transactionId);
    }

    @PatchMapping("/{transactionId}/tampered")
    public AdminTransactionView tampered(
            @PathVariable Long transactionId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return adminService.markTampered(transactionId, reason);
    }
}
