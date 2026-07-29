package hu.tanszek.device.auth.dto;

/**
 * Login response DTO.
 *
 * <p>A sikeres bejelentkezés után a backend ezt a JSON-t adja vissza:
 * <pre>
 *   {
 *     "accessToken": "eyJ...",
 *     "expiresIn": 900  (másodperc, 15 perc = 900s)
 *   }
 * </pre>
 *
 * <p>A refresh token a HttpOnly cookie-ban jön (lásd {@code AuthController.login}).
 *
 * @param accessToken az aláírt JWT access token
 * @param expiresIn access token TTL másodpercben
 */
public record LoginResponse(String accessToken, long expiresIn) {}