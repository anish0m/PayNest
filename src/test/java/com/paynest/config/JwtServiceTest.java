package com.paynest.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long FIFTEEN_MINUTES = 900_000L;

    private JwtService newService() {
        return new JwtService(TEST_SECRET, FIFTEEN_MINUTES);
    }

    // TEST 1 — a token round-trips.
    @Test
    void issueToken_roundTripsEmail() {
        JwtService service = newService();

        String email = "ada@example.com";
        String token = service.issueToken(email);

        assertEquals(email, service.extractEmail(token));
    }

    //  TEST 2 — a freshly issued token is valid.
    @Test
    void freshTokenIsValid() {
        JwtService service = newService();

        String token = service.issueToken("ada@example.com");

        assertTrue(service.isValid(token));
    }

    //  ⚠️ TEST 3 — THE ONE THAT ACTUALLY TESTS SECURITY. A tampered token is rejected.
    @Test
    void tamperedTokenIsRejected() {
        JwtService service = newService();

        String token = service.issueToken("ada@example.com");
        String[] parts = token.split("\\.");

        String mutatedPayload = parts[1].replaceFirst("e", "f");
        String tampered = parts[0] + "." + mutatedPayload + "." + parts[2];

        assertNotEquals(token, tampered);
        assertFalse(service.isValid(tampered));
    }

    //  ⚠️ TEST 4 — TWO TOKENS ISSUED BACK-TO-BACK DIFFER. The jti assertion.
    @Test
    void twoBackToBackTokensAreDifferent() {
        JwtService service = newService();

        String first = service.issueToken("ada@example.com");
        String second = service.issueToken("ada@example.com");

        assertNotEquals(first, second);
    }

    //  OPTIONAL TEST 5 — a weak key is refused.
    @Test
    void weakKeyIsRejected() {
        assertThrows(io.jsonwebtoken.security.WeakKeyException.class,
                () -> new JwtService("abcd", FIFTEEN_MINUTES));
    }

}
