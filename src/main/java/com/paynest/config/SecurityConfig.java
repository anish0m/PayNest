package com.paynest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Supplies the one security-related bean Day-05 needs: a password encoder.
 *
 * <p>This class does not enable Spring Security. Only {@code spring-security-crypto}
 * is on the classpath — a plain library, no filters, no auto-configuration, no
 * effect on any endpoint. Verified in step 1 by checking {@code dependency:list}
 * for the absence of {@code spring-security-web}/{@code -config}.
 *
 * <p>A {@code @Configuration} bean rather than {@code new BCryptPasswordEncoder()}
 * inside {@code UserService}: the cost factor is decided in one place, the
 * service depends on the {@link PasswordEncoder} interface (not the concrete
 * class), and a test can substitute a different encoder without a Spring context.
 *
 * <h2>Why BCrypt is deliberately slow</h2>
 *
 * <p>An attacker who steals the {@code users} table does not attack the
 * algorithm — they guess. SHA-256 runs ~10 billion hashes/sec on a consumer
 * GPU; BCrypt at cost 10 runs ~10,000/sec. SHA-256 is not a bad hash, it is a
 * bad <em>password</em> hash: it was built to be fast, which is right for
 * checksums and wrong here. Each +1 of cost doubles the work, and that dial is
 * the feature.
 *
 * <pre>
 *   $2a$10$N9qo8uLOickgx2ZMRZoMye IjZAgcfl7p92ldGxad68LJZdL17lhWy
 *    |   |  |                      |
 *    |   |  +- salt, 22 chars      +- hash, 31 chars
 *    |   +---- cost factor
 *    +-------- algorithm version
 * </pre>
 *
 * <p>One string, always exactly 60 characters — the source of V3's
 * {@code VARCHAR(60)}. The salt is stored in plaintext inside the hash; its
 * only job is to stop work being shared between rows, which it does whether
 * or not an attacker can read it. The cost factor is stored there too, which
 * is what lets it be raised later without invalidating existing hashes.
 *
 * <p>{@code encode(x).equals(encode(x))} is always false — a new random salt
 * every call. Comparison is always {@code matches(raw, stored)}, never
 * {@code equals()}.
 *
 * <p><b>Dated debt (24 Sep 2026):</b> cost 10 costs ~50-100ms per hash, which
 * the test suite pays on every {@code register()} call. Left at the default
 * deliberately, so tests exercise the configuration that ships. If the suite
 * becomes slow enough to discourage running it, the fix is a
 * {@code @Profile("test")} bean at cost 4, with at least one test still
 * running at the production cost.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
