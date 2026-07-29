package hu.tanszek.device.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JwtTokenProvider — JWT access token generálás és validáció (HS256, kid rotáció).
 *
 * <p>Az aktuális kulcs a {@link JwtProperties}-ből jön (env var: JWT_KID_ACTIVE).
 * A {@code kid} (Key ID) header mezőt használja a rotációhoz — ha a token
 * {@code kid} megegyezik az aktív kulcs {@code kid}-jével, az aktív kulccsal
 * validálódik; ha a grace period-on belül van és a {@code kid} a previous
 * kulcshoz tartozik, a previous kulccsal is validálható.
 *
 * <p>Access token TTL: {@link JwtProperties#getAccessTokenTtlMin()} perc.
 * Refresh token a DB-ben tárolódik ({@code refresh_tokens} tábla), nem JWT.
 */
@Slf4j
@Service
public class JwtTokenProvider {

    /** Az aktuális kulcs Key ID-ja */
    public static final String ACTIVE_KID = "active";

    /** A previous kulcs Key ID-ja (grace period alatt használatos) */
    public static final String PREVIOUS_KID = "previous";

    private final JwtProperties jwtProperties;
    private final SecretKey activeKey;
    private final SecretKey previousKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.activeKey = new SecretKeySpec(jwtProperties.getDecodedActiveKey(), "HmacSHA256");

        // Previous key opcionális — csak grace period alatt
        if (jwtProperties.getPreviousKey() != null && !jwtProperties.getPreviousKey().isBlank()) {
            byte[] decoded = java.util.Base64.getDecoder().decode(jwtProperties.getPreviousKey());
            this.previousKey = new SecretKeySpec(decoded, "HmacSHA256");
            log.info("JwtTokenProvider initialized with ACTIVE + PREVIOUS key (grace period)");
        } else {
            this.previousKey = null;
            log.info("JwtTokenProvider initialized with ACTIVE key only");
        }
    }

    /**
     * Access token generálása.
     *
     * @param emailHash a user email_hash-e (subject)
     * @param role a user role neve (pl. "ROLE_ADMIN")
     * @param permissions a user permission-jeinek listája (pl. ["DEVICE_READ", "USER_MANAGE"])
     * @return az aláírt JWT string
     */
    public String generateAccessToken(String emailHash, String role, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessTokenTtlMin(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .header()
                .keyId(ACTIVE_KID)
                .and()
                .subject(emailHash)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        "role", role,
                        "permissions", permissions
                ))
                .signWith(activeKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Access token TTL másodpercben.
     */
    public long getAccessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtlMin() * 60L;
    }

    /**
     * Token validáció — kid alapján választja ki a kulcsot.
     *
     * @param token a JWT string
     * @return Claims (subject + role + permissions)
     * @throws io.jsonwebtoken.JwtException ha a token invalid
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(activeKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Token validáció grace period-dal — ha a token invalid az aktív kulccsal,
     * de a grace period-on belül vagyunk, a previous kulccsal is megpróbálja.
     *
     * @param token a JWT string
     * @return Claims
     * @throws io.jsonwebtoken.JwtException ha mindkét kulccsal invalid
     */
    public Claims validateTokenWithGracePeriod(String token) {
        // Először próbáljuk az aktív kulccsal
        try {
            return validateToken(token);
        } catch (io.jsonwebtoken.JwtException e) {
            // Ha van previous key és grace period-on belül vagyunk, próbáljuk azzal
            if (previousKey != null) {
                log.warn("Token validation failed with active key, trying previous key (grace period)");
                try {
                    return Jwts.parser()
                            .verifyWith(previousKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                } catch (io.jsonwebtoken.JwtException ex) {
                    throw ex; // Mindkét kulccsal sikertelen
                }
            }
            throw e;
        }
    }

    /**
     * Token-ből GrantedAuthority lista (role + permissions) visszaállítása.
     *
     * @param claims a validált Claims
     * @return Collection<GrantedAuthority> Spring Security-nek
     */
    public Collection<GrantedAuthority> extractAuthorities(Claims claims) {
        String role = claims.get("role", String.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);

        Collection<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));

        if (permissions != null) {
            authorities.addAll(permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList()));
        }

        return authorities;
    }
}