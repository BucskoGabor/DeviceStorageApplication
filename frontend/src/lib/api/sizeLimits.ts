/**
 * Frontend méretkorlát konstansok (tükrözik a backend SizeLimits-ot).
 *
 * <p>A backend és a frontend méretkorlátok szinkronban vannak — ha a backend
 * SizeLimits változik, ezt is frissíteni kell.
 */
export const SizeLimits = {
  SHORT_TEXT_MAX: 100,
  MEDIUM_TEXT_MAX: 255,
  LONG_TEXT_MAX: 500,
  VERY_LONG_TEXT_MAX: 10000,
  INVENTORY_NUMBER_MAX: 50,
  EMAIL_MAX: 255,
  PASSWORD_HASH_MAX: 255,
  URL_MAX: 2048,
  ENDPOINT_MAX: 500,
  MESSAGE_KEY_MAX: 100,
  FILE_NAME_MAX: 255,
  MIME_TYPE_MAX: 100,
  STORAGE_PATH_MAX: 500,
} as const
