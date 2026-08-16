package hu.tanszek.device.auth.dto;

import java.util.List;

/**
 * Login response DTO.
 *
 * <p>A sikeres bejelentkezés után a backend ezt a JSON-t adja vissza:
 *
 * <pre>
 *   {
 *     "accessToken": "eyJ...",
 *     "expiresIn": 900,
 *     "role": "ROLE_ADMIN",
 *     "permissions": ["DEVICE_READ", "USER_MANAGE", ...],
 *     "mustChangePassword": true
 *   }
 * </pre>
 *
 * <p>A refresh token a HttpOnly cookie-ban jön (lásd {@code AuthController.login}). A
 * role/permissions a frontend useAuthStore.setAuth hívásához kell, hogy a RequireRole és
 * RequirePermission wrapper komponensek működjenek.
 *
 * @param accessToken az aláírt JWT access token
 * @param expiresIn access token TTL másodpercben
 * @param role a user role neve (pl. "ROLE_ADMIN")
 * @param permissions a user permission-jeinek listája
 * @param mustChangePassword true ha first-login jelszócsere szükséges
 */
public record LoginResponse(
    String accessToken,
    long expiresIn,
    String role,
    List<String> permissions,
    boolean mustChangePassword) {}
