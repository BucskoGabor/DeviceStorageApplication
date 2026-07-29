package hu.tanszek.device.auth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * AuthProvider interface — Spring Security {@link AuthenticationProvider} wrapper.
 *
 * <p>Az interface célja, hogy a rendszer több auth provider-t támogathasson
 * (LOCAL: Argon2 + DB; AD: LDAP; OAuth2: stb.) anélkül, hogy a hívó kód
 * változna. Az {@link AuthProviderFactory} választja ki az aktív implementációt
 * a {@code configs.AUTH_PROVIDER} értéke alapján.
 *
 * <p>Használat:
 * <pre>
 *   AuthProvider provider = factory.getActiveProvider();
 *   Authentication auth = provider.authenticate(email, password);
 * </pre>
 *
 * @see AuthProviderFactory
 * @see LocalAuthProvider
 * @see StubAdAuthProvider
 */
public interface AuthProvider extends AuthenticationProvider {

    /**
     * A provider azonosítója (pl. "LOCAL", "AD", "OAUTH2").
     *
     * @return az azonosító string
     */
    String getProviderId();

    /**
     * Authentikáció email + password alapján.
     *
     * @param email a user email címe (plain text, login input)
     * @param password a user plain text jelszava
     * @return Spring Security {@link Authentication} object a sikeres
     *         hitelesítés után (username + role/permission authorities)
     * @throws AuthenticationException ha a hitelesítés sikertelen
     *         (hibás jelszó, locked account, inaktív user, stb.)
     */
    Authentication authenticate(String email, String password) throws AuthenticationException;
}