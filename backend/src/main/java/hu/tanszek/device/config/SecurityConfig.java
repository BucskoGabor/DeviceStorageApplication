package hu.tanszek.device.config;

import hu.tanszek.device.auth.AuthProviderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.util.List;

/**
 * Biztonsági konfiguráció — AuthenticationManager és PasswordEncoder bean-ek.
 *
 * <p>TODO Task 2.3: a teljes Security Filter Chain implementálása
 * (RequestIdFilter, RateLimitFilter, CsrfFilter, JwtAuthenticationFilter, stb.).
 *
 * <p>Jelenleg csak az AuthenticationManager-t definiáljuk, ami az
 * {@link AuthProviderFactory}-ból nyeri az aktív provider-t.
 *
 * @see AuthProviderFactory
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthProviderFactory authProviderFactory;

    /**
     * Argon2PasswordEncoder bean — memóriaigényes (memory-hard),
     * GPU/ASIC-resistant, OWASP 2024+ ajánlás.
     *
     * <p>Paraméterek: memory=65536 KB, iterations=3, parallelism=1.
     */
    @Bean
    public Argon2PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    /**
     * AuthenticationManager — az AuthProviderFactory által szolgáltatott
     * aktív provider-t használja.
     *
     * <p>TODO Task 2.3: a JwtAuthenticationFilter és a SecurityContext
     * kezelés teljes implementálása.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(authProviderFactory.getActiveProvider()));
    }
}