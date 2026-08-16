package hu.tanszek.device.auth;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hu.tanszek.device.config.entity.Config;
import hu.tanszek.device.config.repository.ConfigRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link AuthProviderFactory}-hoz.
 *
 * <p>A provider-ek mockolva, a ConfigRepository mockolva van.
 */
class AuthProviderFactoryTest {

  private AuthProvider localProvider;
  private AuthProvider adProvider;
  private ConfigRepository configRepository;
  private AuthProviderFactory factory;

  @BeforeEach
  void setUp() {
    localProvider = mock(AuthProvider.class);
    adProvider = mock(AuthProvider.class);
    when(localProvider.getProviderId()).thenReturn("LOCAL");
    when(adProvider.getProviderId()).thenReturn("AD");
    configRepository = mock(ConfigRepository.class);
    factory = new AuthProviderFactory(List.of(localProvider, adProvider), configRepository);
    factory.init();
  }

  @Test
  void getActiveProvider_returnsProviderByConfigValue() {
    when(configRepository.findByKey("AUTH_PROVIDER"))
        .thenReturn(Optional.of(new Config("AUTH_PROVIDER", "AD")));

    AuthProvider active = factory.getActiveProvider();

    assertThat(active).isSameAs(adProvider);
  }

  @Test
  void getActiveProvider_fallsBackToLocalWhenConfigMissing() {
    when(configRepository.findByKey("AUTH_PROVIDER")).thenReturn(Optional.empty());

    AuthProvider active = factory.getActiveProvider();

    assertThat(active).isSameAs(localProvider);
  }

  @Test
  void getActiveProvider_fallsBackToLocalWhenUnknownId() {
    when(configRepository.findByKey("AUTH_PROVIDER"))
        .thenReturn(Optional.of(new Config("AUTH_PROVIDER", "OAUTH2")));

    AuthProvider active = factory.getActiveProvider();

    assertThat(active).isSameAs(localProvider);
  }

  @Test
  void getActiveProvider_throwsWhenNoFallbackAvailable() {
    AuthProvider onlyProvider = mock(AuthProvider.class);
    when(onlyProvider.getProviderId()).thenReturn("OAUTH2");
    when(configRepository.findByKey("AUTH_PROVIDER"))
        .thenReturn(Optional.of(new Config("AUTH_PROVIDER", "OAUTH2")));
    AuthProviderFactory isolatedFactory =
        new AuthProviderFactory(List.of(onlyProvider), configRepository);
    isolatedFactory.init();

    assertThatThrownBy(isolatedFactory::getActiveProvider)
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void getActiveProviderId_returnsConfiguredValue() {
    when(configRepository.findByKey("AUTH_PROVIDER"))
        .thenReturn(Optional.of(new Config("AUTH_PROVIDER", "AD")));

    assertThat(factory.getActiveProviderId()).isEqualTo("AD");
  }

  @Test
  void getActiveProviderId_returnsFallbackWhenConfigMissing() {
    when(configRepository.findByKey("AUTH_PROVIDER")).thenReturn(Optional.empty());

    assertThat(factory.getActiveProviderId()).isEqualTo("LOCAL");
  }

  @Test
  void init_warnsAndKeepsFirstOnDuplicateProviderId() {
    AuthProvider duplicateProvider = mock(AuthProvider.class);
    when(duplicateProvider.getProviderId()).thenReturn("LOCAL");
    AuthProviderFactory dupFactory =
        new AuthProviderFactory(List.of(localProvider, duplicateProvider), configRepository);

    dupFactory.init();

    when(configRepository.findByKey("AUTH_PROVIDER")).thenReturn(Optional.empty());
    assertThat(dupFactory.getActiveProvider()).isSameAs(localProvider);
  }
}
