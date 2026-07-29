package hu.tanszek.device.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter — minden request-en kiolvassa a Bearer tokent,
 * validálja, és betölti a SecurityContext-be.
 *
 * <p>Ha a token invalid vagy lejárt, a filter NEM dob kivételt — csak
 * {@code log.debug()} bejegyzést ír, és a lánc folytatódik. A Spring Security
 * később a védett endpointokon {@code 401 Unauthorized}-t ad vissza, és
 * a frontend silent refresh-t indít.
 *
 * <p>A filter a {@code UsernamePasswordAuthenticationToken}-t állítja be a
 * SecurityContext-be, ahol a principal a user email_hash, és az authorities
 * a role + permissions a JWT payload-ból.
 *
 * <p>Filter pozíció: Security Filter Chain 4. lépése (a CsrfFilter után).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtTokenProvider.validateTokenWithGracePeriod(token);

                // UserDetails betöltése email_hash alapján
                UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        jwtTokenProvider.extractAuthorities(claims)
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user {} for {}", userDetails.getUsername(), request.getRequestURI());
            } catch (JwtException ex) {
                // Invalid token — nem dobunk, csak log
                log.debug("Invalid JWT for {}: {}", request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Bearer token kiolvasása az Authorization headerből.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}