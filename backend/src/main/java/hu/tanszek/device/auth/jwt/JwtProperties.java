package hu.tanszek.device.auth.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * JWT konfigurációs beállítások a {@code jwt.*} properties-ből.
 *
 * <p>A {@code @ConfigurationProperties} annotációval a Spring Boot automatikusan
 * bind-eli az application.yml-ből az értékeket:
 * <pre>
 * jwt:
 *   access-token-ttl-min: 15
 *   refresh-token-ttl-days: 30
 *   grace-period-sec: 3600
 *   kids:
 *     active: <base64-secret>
 *     previous: <base64-secret> (opcionális)
 * </pre>
 *
 * <p>A {@link #setActiveKey(String)} setter dekódolja a base64 secret-et
 * és beállítja a {@link #decodedActiveKey}-et. Ha a kulcs hossza nem
 * 32+ byte (HS256 minimum 256 bit), a setter IllegalStateException-t dob
 * fail-fast indításkor.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Access token TTL percben (alapértelmezetten 15) */
    private int accessTokenTtlMin = 15;

    /** Refresh token TTL napokban (alapértelmezetten 30) */
    private int refreshTokenTtlDays = 30;

    /** Grace period a kid rotációhoz másodpercben (alapértelmezetten 3600 = 1 óra) */
    private int gracePeriodSec = 3600;

    /** Key ID configuráció (active + opcionális previous) */
    private Kids kids = new Kids();

    /** A dekódolt aktív kulcs (32+ byte, HS256 minimum) */
    private byte[] decodedActiveKey;

    /**
     * Visszaadja a dekódolt aktív HMAC kulcsot (Base64-ből byte tömbbé konvertálva).
     *
     * @return 32+ byte-os aktív HMAC kulcs
     */
    public byte[] getDecodedActiveKey() {
        if (decodedActiveKey != null) {
            return decodedActiveKey;
        }
        String activeKeyBase64 = getActiveKey();
        if (activeKeyBase64 == null || activeKeyBase64.isBlank()) {
            throw new IllegalStateException("jwt.kids.active is missing");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(activeKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.kids.active is not valid base64", e);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "jwt.kids.active must be at least 32 bytes (256 bits), got " + decoded.length
            );
        }
        this.decodedActiveKey = decoded;
        return decoded;
    }

    public String getActiveKey() {
        return kids.active;
    }

    /**
     * A previous kulcs setter hasonló a setActiveKey-hez, de opcionális.
     */
    public void setPreviousKey(String previousKeyBase64) {
        this.kids.previous = previousKeyBase64;
    }

    public String getPreviousKey() {
        return kids.previous;
    }

    /**
     * Inner class a nested jwt.kids.* properties-hez.
     */
    @Getter
    @Setter
    public static class Kids {
        private String active;
        private String previous;
    }
}