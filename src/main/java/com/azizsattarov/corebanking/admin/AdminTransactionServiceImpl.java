package com.azizsattarov.corebanking.admin;

import com.azizsattarov.corebanking.admin.dto.AdminTransactionView;
import com.azizsattarov.corebanking.admin.dto.UpdateBlockchainRequest;
import com.azizsattarov.corebanking.admin.event.BlockchainSubmitFailedEvent;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import com.azizsattarov.corebanking.transaction.ChainStatus;
import com.azizsattarov.corebanking.transaction.Transaction;
import com.azizsattarov.corebanking.transaction.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminTransactionServiceImpl implements AdminTransactionService {

    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxSubmitAttempts;

    public AdminTransactionServiceImpl(TransactionRepository transactionRepository,
                                       ApplicationEventPublisher eventPublisher,
                                       @Value("${app.blockchain.max-submit-attempts:8}") int maxSubmitAttempts) {
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.maxSubmitAttempts = maxSubmitAttempts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTransactionView> findAll(int limit) {
        return transactionRepository
                .findAllOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(AdminTransactionServiceImpl::toView)
                .toList();
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
    public List<AdminTransactionView> findFailedSubmit(int limit) {
        return transactionRepository
                .findFailedSubmit(PageRequest.of(0, limit))
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

        ChainStatus previousStatus = t.getChainStatus();

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
            if (t.getSubmitAttempts() >= maxSubmitAttempts) {
                t.setChainStatus(ChainStatus.FAILED_SUBMIT);
            } else {
                t.setChainStatus(ChainStatus.PENDING_SUBMIT);
            }
        }

        Transaction saved = transactionRepository.save(t);
        if (saved.getChainStatus() == ChainStatus.FAILED_SUBMIT
                && previousStatus != ChainStatus.FAILED_SUBMIT) {
            eventPublisher.publishEvent(new BlockchainSubmitFailedEvent(
                    saved.getTransactionId(),
                    saved.getAccount() != null ? saved.getAccount().getAccountNumber() : null,
                    saved.getLastSubmitError(),
                    saved.getSubmitAttempts()
            ));
        }
        return toView(saved);
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
    @Transactional
    public AdminTransactionView retryBlockchainSubmit(Long transactionId) {
        Transaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));
        if (t.getChainStatus() != ChainStatus.FAILED_SUBMIT) {
            throw new BadRequestException(
                    "Only FAILED_SUBMIT transactions can be retried; current status: " + t.getChainStatus());
        }
        t.setSubmitAttempts(0);
        t.setChainStatus(ChainStatus.PENDING_SUBMIT);
        t.setLastSubmitError(null);
        t.setBlockchainTx(null);
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
