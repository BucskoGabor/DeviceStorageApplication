package hu.tanszek.device.auth;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import hu.tanszek.device.config.entity.Config;
import hu.tanszek.device.config.repository.ConfigRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AuthProviderFactory — a {@code configs.AUTH_PROVIDER} alapján kiválasztja az aktív {@link
 * AuthProvider} implementációt.
 *
 * <p>A factory a Spring összes {@link AuthProvider} bean-jét összegyűjti (LocalAuthProvider,
 * StubAdAuthProvider, stb.) és azonosítja őket a {@link AuthProvider#getProviderId()} alapján.
 *
 * <p>Startup-kor beolvassa a {@code AUTH_PROVIDER} értéket a {@code configs} táblából. Ha az érték
 * változik runtime-ban (pl. admin updateeli az adatbázist), a {@code @RefreshScope} miatt a factory
 * újra betöltődik.
 *
 * <p>Ha az érték ismeretlen, fallback {@code LOCAL}-ra.
 *
 * <p>Használat:
 *
 * <pre>
 *   AuthProvider provider = factory.getActiveProvider();
 *   Authentication auth = provider.authenticate(email, password);
 * </pre>
 *
 * @see AuthProvider
 * @see LocalAuthProvider
 * @see StubAdAuthProvider
 */
@Slf4j
@Component
@RefreshScope
@RequiredArgsConstructor
public class AuthProviderFactory {

  /** A config key az aktív provider azonosítójához */
  private static final String CONFIG_KEY = "AUTH_PROVIDER";

  /** Fallback provider, ha a config hiányzik vagy ismeretlen értéket tartalmaz */
  private static final String FALLBACK_PROVIDER = "LOCAL";

  private final List<AuthProvider> providers;
  private final ConfigRepository configRepository;

  private Map<String, AuthProvider> providerMap;

  /**
   * A factory inicializálása — provider-ök map-be szervezése provider id alapján. A
   * {@code @PostConstruct} a Spring DI után fut le.
   */
  @PostConstruct
  public void init() {
    this.providerMap =
        providers.stream()
            .collect(
                Collectors.toMap(
                    AuthProvider::getProviderId,
                    Function.identity(),
                    (existing, replacement) -> {
                      log.warn(
                          "Duplicate provider id: {} — keeping first", existing.getProviderId());
                      return existing;
                    }));
    log.info("Registered AuthProviders: {}", providerMap.keySet());
  }

  /**
   * Az aktív {@link AuthProvider} visszaadása a {@code configs.AUTH_PROVIDER} értéke alapján.
   *
   * @return az aktív AuthProvider
   * @throws NoSuchElementException ha az aktív provider ismeretlen ÉS nincs fallback
   */
  public AuthProvider getActiveProvider() {
    String providerId = resolveProviderId();
    AuthProvider provider = providerMap.get(providerId);

    if (provider == null) {
      log.warn("Unknown provider id '{}', falling back to '{}'", providerId, FALLBACK_PROVIDER);
      provider = providerMap.get(FALLBACK_PROVIDER);
    }

    if (provider == null) {
      throw new NoSuchElementException(
          "No AuthProvider available for id '"
              + providerId
              + "' or fallback '"
              + FALLBACK_PROVIDER
              + "'");
    }

    return provider;
  }

  /** Az aktuális provider id feloldása a configs táblából. */
  private String resolveProviderId() {
    return configRepository.findByKey(CONFIG_KEY).map(Config::getValue).orElse(FALLBACK_PROVIDER);
  }

  /** Az aktuális provider id közvetlen lekérdezése (debug/logging célokra). */
  public String getActiveProviderId() {
    return resolveProviderId();
  }
}
