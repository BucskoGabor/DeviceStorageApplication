package hu.tanszek.device.auth;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import hu.tanszek.device.config.repository.ConfigRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LocalAuthProvider — Argon2 + DB alapú authentikáció.
 *
 * <p>Authentikáció flow:
 *
 * <ol>
 *   <li>SHA-256 hash az emailből ({@code emailHash})
 *   <li>User lookup az AppUserRepository.findByEmailHash metódussal
 *   <li>User.active = true check (különben DisabledException)
 *   <li>User.lockedUntil > now check (különben LockedException)
 *   <li>Argon2PasswordEncoder.matches(rawPassword, user.passwordHash) check (különben
 *       BadCredentialsException, és failedLoginCount növelés)
 *   <li>Sikeres authentikáció: UsernamePasswordAuthenticationToken a role + permission
 *       authorities-kkal
 *   <li>Argon2PasswordEncoder.upgradeEncoding() check — ha a hash paraméterei elavultak,
 *       transparent rehash és DB update (egy tranzakcióban)
 * </ol>
 *
 * <p>Lockout mechanizmus: 5 hibás próba → 15 perces lockout ({@code lockedUntil = now + 15min},
 * {@code failedLoginCount >= 5}).
 *
 * @see AuthProvider
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAuthProvider implements AuthProvider {

  private static final String PROVIDER_ID = "LOCAL";

  private static final int MAX_LOGIN_ATTEMPTS = 5;
  private static final long LOCKOUT_DURATION_MIN = 15;

  private final AppUserRepository appUserRepository;
  private final ConfigRepository configRepository;
  private final Argon2PasswordEncoder passwordEncoder;

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public Authentication authenticate(String email, String password) throws AuthenticationException {
    log.debug("Authentication attempt for email: {}", email);

    // 1. Email → SHA-256 hash
    String emailHash = sha256(email);
    AppUser user =
        appUserRepository
            .findByEmailHash(emailHash)
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

    // 2. Aktív check
    if (!user.isActive()) {
      throw new DisabledException("User account is disabled");
    }

    // 3. Lockout check
    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
      throw new LockedException("User account is locked until " + user.getLockedUntil());
    }

    // 4. Password verify
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      handleFailedLogin(user);
      throw new BadCredentialsException("Invalid email or password");
    }

    // 5. Successful auth: reset failedLoginCount, rehash if needed, return Authentication
    resetFailedLoginCount(user, password);
    return buildAuthentication(user);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  /** SHA-256 hash az email címből. */
  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Failed login kezelése: failedLoginCount növelés, lockout threshold check. */
  private void handleFailedLogin(AppUser user) {
    int failedCount = user.getFailedLoginCount() + 1;
    user.setFailedLoginCount(failedCount);

    if (failedCount >= MAX_LOGIN_ATTEMPTS) {
      user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_DURATION_MIN * 60));
      log.warn("User {} locked after {} failed attempts", user.getEmailHash(), failedCount);
    }

    appUserRepository.save(user);
  }

  /**
   * Sikeres login: failedLoginCount és lockedUntil reset + Argon2 rehash check.
   *
   * <p>Ha a jelszó hash paraméterei (memory, iterations, parallelism) elmaradnak az aktuális
   * policy-től, transparent módon újrahasheli és DB-be írja.
   *
   * @param user a bejelentkezett user
   * @param rawPassword a nyers jelszó (rehash-hez szükséges)
   */
  private void resetFailedLoginCount(AppUser user, String rawPassword) {
    boolean changed = false;

    if (user.getFailedLoginCount() > 0 || user.getLockedUntil() != null) {
      user.setFailedLoginCount(0);
      user.setLockedUntil(null);
      changed = true;
    }

    // Argon2 upgrade check: ha a hash paraméterei elavultak, rehash
    if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
      String newHash = passwordEncoder.encode(rawPassword);
      user.setPasswordHash(newHash);
      user.setPasswordChangedAt(Instant.now());
      log.info("Argon2 rehash applied for user {}", user.getEmailHash());
      changed = true;
    }

    if (changed) {
      appUserRepository.save(user);
    }
  }

  /**
   * UsernamePasswordAuthenticationToken összeállítása a role + permission authorities-kkal.
   *
   * <p>A role-t ELŐRE tesszük a collection-be, és LinkedHashSet-et használunk, hogy az iterációs
   * sorrend determinisztikus legyen — így az AuthController_login azonosítja a role-t (ne egy
   * ROLE_* prefixű permissiont, pl. "ROLE_READ").
   */
  private Authentication buildAuthentication(AppUser user) {
    Set<hu.tanszek.device.auth.entity.Permission> userPermissions = user.getPermissions();
    List<GrantedAuthority> roleAuthorities =
        user.getRole().getPermissions().stream()
            .map(p -> new SimpleGrantedAuthority(p.getName()))
            .collect(Collectors.toList());
    List<GrantedAuthority> userSpecificAuthorities =
        userPermissions.stream()
            .map(p -> new SimpleGrantedAuthority(p.getName()))
            .collect(Collectors.toList());

    Collection<GrantedAuthority> allAuthorities =
        Stream.concat(
                Stream.concat(
                    Stream.of(new SimpleGrantedAuthority(user.getRole().getName())),
                    roleAuthorities.stream()),
                userSpecificAuthorities.stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return new UsernamePasswordAuthenticationToken(user.getEmailHash(), null, allAuthorities);
  }
}
