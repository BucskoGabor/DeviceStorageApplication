package hu.tanszek.device.config;

import hu.tanszek.device.auth.AuthProviderFactory;
import hu.tanszek.device.auth.CustomUserDetailsService;
import hu.tanszek.device.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.util.List;

/**
 * Biztonsági konfiguráció — teljes Security Filter Chain (7 lépéses).
 *
 * <p>Filter lánc rendje (implementation_plan.md §3):
 * <ol>
 *   <li>RequestIdFilter — UUID request_id generálás (MDC-be) — TODO: Task 2.9-ben</li>
 *   <li>RateLimitFilter — Bucket4j per-IP/per-email limit — TODO: Task 2.5-ben</li>
 *   <li>CsrfFilter — CSRF token check (CookieCsrfTokenRepository) — TODO: később</li>
 *   <li>JwtAuthenticationFilter — Bearer token validáció, SecurityContext</li>
 *   <li>UsernamePasswordAuthenticationFilter — csak /api/auth/login (form login)</li>
 *   <li>ExceptionTranslationFilter — Spring Security default</li>
 *   <li>AuthorizationFilter — @RequirePermission / URL-alapú permission check</li>
 * </ol>
 *
 * <p>Jelenleg csak a JwtAuthenticationFilter aktív (4. lépés). A többi
 * TODO kommentben van, és a megfelelő Fázis 2 task-okban implementálódik.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthProviderFactory authProviderFactory;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Argon2PasswordEncoder bean — memory-hard, GPU/ASIC-resistant,
     * OWASP 2024+ ajánlás. Paraméterek: memory=65536 KB, iterations=3, parallelism=1.
     */
    @Bean
    public Argon2PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    /**
     * AuthenticationManager — az AuthProviderFactory-ból nyeri az aktív provider-t.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(authProviderFactory.getActiveProvider()));
    }

    /**
     * Security Filter Chain — HTTP biztonsági konfiguráció.
     *
     * <p>Jelenlegi állapot:
     * <ul>
     *   <li>CSRF kikapcsolva (SPA + SameSite=Strict cookie miatt — TODO Task: bekapcsolni state-changing endpointokra)</li>
     *   <li>Session: STATELESS (JWT alapú)</li>
     *   <li>CORS kikapcsolva (same-origin — Vite proxy / Nginx reverse proxy)</li>
     *   <li>Public: /api/auth/** (login, refresh, logout, password-change)</li>
     *   <li>Authenticated: minden más</li>
     *   <li>JwtAuthenticationFilter a BasicAuthenticationFilter ELŐTT</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF: TODO Task később — bekapcsoláskor CookieCsrfTokenRepository.withHttpOnlyFalse()
                .csrf(AbstractHttpConfigurer::disable)

                // CORS: same-origin miatt kikapcsolva
                .cors(AbstractHttpConfigurer::disable)

                // Session: STATELESS (JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Form login és HTTP Basic kikapcsolva (JWT-t használunk)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // URL-alapú authorizáció
                .authorizeHttpRequests(auth -> auth
                        // Nyilvános auth endpoint-ok
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        // Actuator: health endpoint mindig elérhető
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        // Minden más endpoint authentikációt igényel
                        .anyRequest().authenticated())

                // JwtAuthenticationFilter beépítése a Security Filter Chain-be
                // (a BasicAuthenticationFilter ELŐTT, hogy a Bearer token-t olvassa előbb)
                .addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class)

                // UserDetailsService bean a JwtAuthenticationFilter-hez
                .userDetailsService(userDetailsService)

                .build();
    }
}