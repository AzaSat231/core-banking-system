package com.azizsattarov.corebanking.admin;

import com.azizsattarov.corebanking.admin.dto.AdminTransactionView;
import com.azizsattarov.corebanking.admin.dto.UpdateBlockchainRequest;
import com.azizsattarov.corebanking.exception.NotFoundException;
import com.azizsattarov.corebanking.transaction.ChainStatus;
import com.azizsattarov.corebanking.transaction.Transaction;
import com.azizsattarov.corebanking.transaction.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminTransactionServiceImpl implements AdminTransactionService {

    private final TransactionRepository transactionRepository;

    public AdminTransactionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTransactionView> findPendingSubmit(int limit, int maxAttempts) {
        return transactionRepository
                .findPendingSubmit(maxAttempts, PageRequest.of(0, limit))
                .stream()
                .map(AdminTransactionServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTransactionView> findSubmitted(int limit) {
        return transactionRepository
                .findSubmitted(PageRequest.of(0, limit))
                .stream()
                .map(AdminTransactionServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTransactionView> findForTamperCheck(LocalDateTime since, int limit) {
        return transactionRepository
                .findConfirmedSince(since, PageRequest.of(0, limit))
                .stream()
                .map(AdminTransactionServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional
    public AdminTransactionView updateBlockchain(Long transactionId, UpdateBlockchainRequest req) {
        Transaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));

        if (req.canonicalHash() != null && !req.canonicalHash().isBlank()) {
            t.setCanonicalHash(req.canonicalHash());
        }
        t.setSubmitAttempts(t.getSubmitAttempts() + 1);

        if (req.blockchainTx() != null && !req.blockchainTx().isBlank()) {
            t.setBlockchainTx(req.blockchainTx());
            t.setChainStatus(ChainStatus.SUBMITTED);
            t.setLastSubmitError(null);
        } else {
            // Submission failed — keep status as PENDING_SUBMIT until max attempts,
            // then escalate to FAILED_SUBMIT for manual review.
            t.setLastSubmitError(req.submitError());
            if (t.getSubmitAttempts() >= 8) {
                t.setChainStatus(ChainStatus.FAILED_SUBMIT);
            } else {
                t.setChainStatus(ChainStatus.PENDING_SUBMIT);
            }
        }

        return toView(transactionRepository.save(t));
    }

    @Override
    @Transactional
    public AdminTransactionView markConfirmed(Long transactionId) {
        Transaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));
        t.setChainStatus(ChainStatus.CONFIRMED);
        return toView(transactionRepository.save(t));
    }

    @Override
    @Transactional
    public AdminTransactionView markTampered(Long transactionId, String reason) {
        Transaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));
        t.setChainStatus(ChainStatus.TAMPERED);
        t.setLastSubmitError(reason);
        return toView(transactionRepository.save(t));
    }

    @Override
    public AdminTransactionView findById(Long transactionId) {
        Transaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));
        return toView(t);
    }

    private static AdminTransactionView toView(Transaction t) {
        return new AdminTransactionView(
                t.getTransactionId(),
                t.getReferenceId(),
                t.getAccount() != null ? t.getAccount().getAccountNumber() : null,
                t.getTransactionType(),
                t.getAmount(),
                t.getBalanceAfter(),
                t.getCreatedAt(),
                t.getCanonicalHash(),
                t.getBlockchainTx(),
                t.getChainStatus(),
                t.getSubmitAttempts(),
                t.getLastSubmitError()
        );
    }
}
