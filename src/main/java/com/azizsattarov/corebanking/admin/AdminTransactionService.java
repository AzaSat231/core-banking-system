package com.azizsattarov.corebanking.admin;

import com.azizsattarov.corebanking.admin.dto.AdminTransactionView;
import com.azizsattarov.corebanking.admin.dto.UpdateBlockchainRequest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service-side reconciliation API consumed by the in-middleware blockchain worker.
 * All methods assume the caller has already passed ServiceTokenAuthFilter
 * and is therefore authenticated as ROLE_SERVICE.
 */
public interface AdminTransactionService {

    List<AdminTransactionView> findPendingSubmit(int limit, int maxAttempts);

    List<AdminTransactionView> findSubmitted(int limit);

    List<AdminTransactionView> findForTamperCheck(LocalDateTime since, int limit);

    AdminTransactionView updateBlockchain(Long transactionId, UpdateBlockchainRequest req);

    AdminTransactionView markConfirmed(Long transactionId);

    AdminTransactionView markTampered(Long transactionId, String reason);

    AdminTransactionView findById(Long transactionId);
}
