package hu.tanszek.device.auth;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import hu.tanszek.device.common.UnauthorizedActionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tesztek a {@link RequirePermissionAspect}-hez.
 *
 * <p>Az aspektus metódus-meghívásait mock metódusokkal helyettesítjük, és a SecurityContext-be
 * helyezett Authentication-t állítunk be.
 */
class RequirePermissionAspectTest {

  private RequirePermissionAspect aspect;

  @BeforeEach
  void setUp() {
    aspect = new RequirePermissionAspect();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allowsMethodWhenUserHasPermission() {
    // SecurityContext: user ROLE_ADMIN + DEVICE_READ permission
    setAuthenticatedUser(
        "hash123",
        List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("DEVICE_READ")));

    // @RequirePermission("DEVICE_READ") method mock
    RequirePermission annotation = mockAnnotation("DEVICE_READ");
    Object result = invokeAspect(annotation);

    // Mock method "success" string-et ad vissza
    assertThat(result).isEqualTo("success");
  }

  @Test
  void throwsExceptionWhenUserMissingPermission() {
    // User csak ROLE_STUDENT, nincs USER_MANAGE
    setAuthenticatedUser(
        "hash123",
        List.of(
            new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("DEVICE_READ")));

    RequirePermission annotation = mockAnnotation("USER_MANAGE");

    assertThatThrownBy(() -> invokeAspect(annotation))
        .isInstanceOf(UnauthorizedActionException.class)
        .extracting("messageKey")
        .isEqualTo("permissionDenied");
  }

  @Test
  void throwsExceptionWhenUnauthenticated() {
    // Nincs SecurityContext
    RequirePermission annotation = mockAnnotation("DEVICE_READ");

    assertThatThrownBy(() -> invokeAspect(annotation))
        .isInstanceOf(UnauthorizedActionException.class)
        .extracting("messageKey")
        .isEqualTo("authRequired");
  }

  // ===== Helpers =====

  private void setAuthenticatedUser(String principal, List<GrantedAuthority> authorities) {
    Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private RequirePermission mockAnnotation(String value) {
    return new RequirePermission() {
      @Override
      public String value() {
        return value;
      }

      @Override
      public Class<? extends java.lang.annotation.Annotation> annotationType() {
        return RequirePermission.class;
      }
    };
  }

  private Object invokeAspect(RequirePermission annotation) {
    // Mock ProceedingJoinPoint — a method body "success" string-et ad vissza
    org.aspectj.lang.ProceedingJoinPoint pjp =
        org.mockito.Mockito.mock(org.aspectj.lang.ProceedingJoinPoint.class);
    org.aspectj.lang.Signature signature =
        org.mockito.Mockito.mock(org.aspectj.lang.Signature.class);
    org.mockito.Mockito.when(signature.toShortString()).thenReturn("testMethod()");
    org.mockito.Mockito.when(pjp.getSignature()).thenReturn(signature);
    try {
      org.mockito.Mockito.when(pjp.proceed()).thenReturn("success");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
    try {
      return aspect.checkPermission(pjp, annotation);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
