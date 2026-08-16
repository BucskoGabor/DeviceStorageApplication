package hu.tanszek.device.auth;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * StubAdAuthProvider — AD/LDAP authentikáció placeholder.
 *
 * <p>Jelenleg NEM implementált — minden hívás {@link AuthenticationServiceException}-t dob. Az
 * AD/LDAP integráció a Future Work (lásd {@code agent_progress.md}).
 *
 * <p>Az {@link AuthProviderFactory} ezt a provider-t választja, ha a {@code configs.AUTH_PROVIDER}
 * értéke "AD".
 *
 * <p>Implementáció terv (Future Work):
 *
 * <ul>
 *   <li>Spring LDAP Template (spring-ldap-core)
 *   <li>AD domain controller URL konfig-ból
 *   <li>User DN lookup + bind authenticate
 *   <li>Group → Role mapping
 *   <li>Sync user-eket a local DB-be (cache)
 * </ul>
 *
 * @see AuthProvider
 */
@Slf4j
@Component
public class StubAdAuthProvider implements AuthProvider {

  private static final String PROVIDER_ID = "AD";

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public Authentication authenticate(String email, String password) throws AuthenticationException {
    log.warn("StubAdAuthProvider hit — AD integration not yet implemented");
    throw new AuthenticationServiceException("AD authentication not implemented (Future Work)");
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
