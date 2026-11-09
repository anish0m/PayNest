package com.paynest.transfer.dto;

import com.paynest.transfer.model.TransferStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
/**
 * Optional constraints for a wallet's transfer history; null means that field
 * is unrestricted. The lower date bound is inclusive and the upper bound is
 * exclusive, forming the interval [from, to). Both date and amount ranges
 * must have a lower bound no greater than their upper bound.
 */
public record TransferFilter(
        TransferStatus status,
        Instant from,
        Instant to,
        @PositiveOrZero BigDecimal minAmount,
        @PositiveOrZero BigDecimal maxAmount) {

    @AssertTrue(message = "from must not be after to, and minAmount must not be greater than maxAmount")
    public boolean hasValidRanges() {
        boolean validDateRange = from == null
                || to == null
                || !from.isAfter(to);

        boolean validAmountRange = minAmount == null
                || maxAmount == null
                || minAmount.compareTo(maxAmount) <= 0;

        return validDateRange && validAmountRange;
    }
}