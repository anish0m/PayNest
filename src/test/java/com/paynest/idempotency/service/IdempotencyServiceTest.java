package com.paynest.idempotency.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyServiceTest {

    private final IdempotencyService service =
            new IdempotencyService(null);

    @Test
    void fingerprintRejectsAnAmountWithNonZeroPrecisionBeyondCents() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.fingerprint(
                        1L, 2L, new BigDecimal("10.255")));

        assertEquals(
                "Amount must have at most 2 decimal places",
                exception.getMessage());
    }
}
