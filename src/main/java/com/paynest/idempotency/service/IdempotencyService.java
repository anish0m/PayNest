package com.paynest.idempotency.service;

import com.paynest.idempotency.exception.IdempotencyConflictException;
import com.paynest.idempotency.exception.IdempotencyKeyReusedException;
import com.paynest.idempotency.model.IdempotencyRecord;
import com.paynest.idempotency.model.IdempotencyStatus;
import com.paynest.idempotency.repository.IdempotencyRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Logger logger =
            LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository repository;

    private IdempotencyService self;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
        this.self = this;
    }

    @Autowired(required = false)
    public void setSelf(@Lazy IdempotencyService self) {
        this.self = self;
    }

    // Public so Spring can proxy these transaction boundaries.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyRecord> claimInNewTransaction(
            IdempotencyRecord attempt) {

        return repository.claim(attempt);
    }

    // Public so Spring can proxy these transaction boundaries.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int settleInNewTransaction(
            String scope,
            String idempotencyKey,
            UUID transferId) {

        return repository.settle(scope, idempotencyKey, transferId);
    }

    // Public so Spring can proxy these transaction boundaries.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int releaseInNewTransaction(
            String scope,
            String idempotencyKey) {

        return repository.release(scope, idempotencyKey);
    }

    public String fingerprint(
            Long senderWalletId,
            Long receiverWalletId,
            BigDecimal amount) {

        BigDecimal amountInCents;
        try {
            amountInCents = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Amount must have at most 2 decimal places", exception);
        }

        // ':' cannot occur in numeric wallet IDs or a plain decimal amount.
        return senderWalletId + ":"
                + receiverWalletId + ":"
                + amountInCents.toPlainString();
    }

    public Optional<IdempotencyRecord> begin(
            String scope,
            String idempotencyKey,
            String requestFingerprint) {

        IdempotencyRecord attempt = new IdempotencyRecord(
                scope,
                idempotencyKey,
                IdempotencyStatus.IN_PROGRESS,
                null,
                requestFingerprint,
                Instant.now());

        Optional<IdempotencyRecord> existing =
                self.claimInNewTransaction(attempt);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord record = existing.get();

        if (!record.requestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyKeyReusedException(
                    scope, idempotencyKey);
        }

        if (record.status() == IdempotencyStatus.COMPLETED) {
            return Optional.of(record);
        }

        throw new IdempotencyConflictException(scope, idempotencyKey);
    }

    public void succeed(
            String scope,
            String idempotencyKey,
            UUID transferId) {

        int rows = self.settleInNewTransaction(
                scope, idempotencyKey, transferId);

        if (rows == 0) {
            logger.warn(
                    "Idempotency settle found no in-progress claim: "
                            + "scope={}, key={}",
                    scope,
                    idempotencyKey);
        }
    }

    public void fail(String scope, String idempotencyKey) {
        int rows = self.releaseInNewTransaction(
                scope, idempotencyKey);

        if (rows == 0) {
            logger.warn(
                    "Idempotency release found no in-progress claim: "
                            + "scope={}, key={}",
                    scope,
                    idempotencyKey);
        }
    }
}
