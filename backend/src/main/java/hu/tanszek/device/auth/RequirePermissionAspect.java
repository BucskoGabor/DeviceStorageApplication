package hu.tanszek.device.auth;

import java.util.Collection;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import hu.tanszek.device.common.UnauthorizedActionException;

import lombok.extern.slf4j.Slf4j;

/**
 * RequirePermissionAspect — method-level permission check aspektus.
 *
 * <p>A {@link RequirePermission} annotációval ellátott controller method-ok előtt fut le.
 * Ellenőrzi, hogy a {@code SecurityContext}-ben lévő Authentication authorities tartalmazza-e a
 * megadott permission-t.
 *
 * <p>Ha nincs bejelentkezett user vagy nincs meg a permission, a method meghívása ELŐTT dobódik a
 * kivétel, így a method body nem fut le.
 *
 * <p>Authorization filter lánc:
 *
 * <ol>
 *   <li>JwtAuthenticationFilter betölti a SecurityContext-et (JWT payload-ból)
 *   <li>SecurityFilterChain hitelesíti a user-t ({@code .authenticated()})
 *   <li>RequirePermissionAspect ellenőrzi a permission-t (ez az aspektus)
 *   <li>Service-szintű row-level filter (Task 3.2-ben)
 *   <li>Controller method body végrehajtódik
 * </ol>
 */
@Slf4j
@Aspect
@Component
public class RequirePermissionAspect {

  /**
   * Around advice — a @RequirePermission annotációval ellátott method-ok előtt/után fut.
   *
   * <p>Ha a user nem rendelkezik a permission-nel, a method body NEM fut le (return előtt dobunk).
   */
  @Around("@annotation(requirePermission)")
  public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
      throws Throwable {
    String requiredPermission = requirePermission.value();

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      log.warn("Unauthenticated access attempt to {}", joinPoint.getSignature().toShortString());
      throw new UnauthorizedActionException("authRequired", "Authentication required");
    }

    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

    boolean hasPermission =
        authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(authority -> authority.equals(requiredPermission));

    if (!hasPermission) {
      log.warn(
          "Permission denied for user {} accessing {} (required: {})",
          authentication.getName(),
          joinPoint.getSignature().toShortString(),
          requiredPermission);
      throw new UnauthorizedActionException(
          "permissionDenied", "User does not have required permission: " + requiredPermission);
    }

    log.debug(
        "Permission check passed for {} (required: {})",
        joinPoint.getSignature().toShortString(),
        requiredPermission);
    return joinPoint.proceed();
  }
}
