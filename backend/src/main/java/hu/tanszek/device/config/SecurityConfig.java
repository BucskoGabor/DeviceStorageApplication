package hu.tanszek.device.config;

import hu.tanszek.device.auth.AuthProviderFactory;
import hu.tanszek.device.auth.CustomUserDetailsService;
import hu.tanszek.device.auth.RateLimitFilter;
import hu.tanszek.device.auth.jwt.JwtAuthenticationFilter;
import hu.tanszek.device.common.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

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
    private final RateLimitFilter rateLimitFilter;
    private final RequestIdFilter requestIdFilter;

    /**
     * Argon2PasswordEncoder bean — memory-hard, GPU/ASIC-resistant,
     * OWASP 2024+ ajánlás. Paraméterek: memory=65536 KB, iterations=3, parallelism=1.
     */
    @Bean
    public Argon2PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    /**
     * RoleHierarchy — ROLE_ADMIN > ROLE_TEACHER > ROLE_STUDENT hierarchia.
     *
     * <p>A {@link RoleHierarchyImpl} a Spring Security-ben egy ROLE_ADMIN
     * user örökli a ROLE_TEACHER és ROLE_STUDENT összes permission-jét
     * (ahol a RoleHierarchy bean definiálja a "tartalmazza" relációt).
     *
     * <p>A {@code RoleHierarchyVoter} ezt használja a permission check-eknél.
     *
     * <p>Ha új role jön a rendszerbe (admin UI-ból), itt kell frissíteni és
     * újraindítani a backend-et.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
                "ROLE_ADMIN > ROLE_TEACHER \n" +
                "ROLE_TEACHER > ROLE_STUDENT"
        );
    }

    /**
     * AuthenticationManager — az AuthProviderFactory-ból nyeri az aktív provider-t.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        ProviderManager manager = new ProviderManager(List.of(authProviderFactory.getActiveProvider()));
        // RoleHierarchy bean elérhető a provider-en belül a voter-eken keresztül
        return manager;
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
                // CSRF aktívan a state-changing endpointokon, CookieCsrfTokenRepository.withHttpOnlyFalse()
                // A /api/auth/login és /api/auth/refresh kivételek (még nincs session)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh"))

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

                // Filter lánc (terv §3):
                // 1) RequestIdFilter — UUID request_id (MDC-be) a lánc legelején
                // 2) RateLimitFilter — Bucket4j per-IP/per-email a CsrfFilter ELŐTT
                // 3) [CsrfFilter] — Spring Security default filter
                // 4) JwtAuthenticationFilter — Bearer token validáció
                // 5) [UsernamePasswordAuthenticationFilter] — default, /api/auth/login
                // 6) [ExceptionTranslationFilter] — default
                // 7) [AuthorizationFilter] — @RequirePermission aspektus
                // A SecurityConfig lánc a 'CsrfFilter' előtti pozíciót használja
                // a RateLimitFilternek (terv §3: brute-force botok ne kapjanak CSRF tokent).
                .addFilterBefore(requestIdFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class)

                // UserDetailsService bean a JwtAuthenticationFilter-hez
                .userDetailsService(userDetailsService)

                .build();
    }
}