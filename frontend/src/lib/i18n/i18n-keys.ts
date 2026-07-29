/**
 * i18n keys — generated from backend messages bundles
 * Run `mvn generate-i18n-keys` to regenerate.
 * Source: messages_hu.properties + messages_en.properties
 */

export const i18nKeys = [
    'assignmentNotApproved',
    'authRequired',
    'authenticationFailed',
    'created',
    'deleted',
    'deviceNotFound',
    'error',
    'internalError',
    'invalidCredentials',
    'loginFailed',
    'loginSuccess',
    'logoutSuccess',
    'passwordChangeInvalidCurrent',
    'passwordChangeSameAsOld',
    'passwordChangeTooShort',
    'permissionDenied',
    'rateLimitExceeded',
    'refreshTokenExpired',
    'refreshTokenInvalid',
    'refreshTokenMissing',
    'resourceNotFound',
    'success',
    'updated',
    'userDisabled',
    'userEmailDuplicate',
    'userLocked',
    'validation.email',
    'validation.maxLength',
    'validation.minLength',
    'validation.notBlank',
    'validation.size.max',
    'validationError'
] as const;

export type MessageKey = (typeof i18nKeys)[number];

/**
 * Default messages (Hungarian fallback).
 */
export const defaultMessages: Record<MessageKey, string> = {
    assignmentNotApproved: "A hozzárendelés még nincs jóváhagyva",
    authRequired: "Bejelentkezés szükséges",
    authenticationFailed: "Hitelesítés sikertelen",
    created: "Sikeresen létrehozva",
    deleted: "Sikeresen törölve",
    deviceNotFound: "Eszköz nem található",
    error: "Hiba",
    internalError: "Váratlan hiba történt",
    invalidCredentials: "Hibás email vagy jelszó",
    loginFailed: "Bejelentkezés sikertelen",
    loginSuccess: "Bejelentkezés sikeres",
    logoutSuccess: "Kijelentkezés sikeres",
    passwordChangeInvalidCurrent: "A jelenlegi jelszó hibás",
    passwordChangeSameAsOld: "Az új jelszó nem egyezhet a régivel",
    passwordChangeTooShort: "Az új jelszónak legalább 12 karakternek kell lennie",
    permissionDenied: "Nincs megfelelő jogosultsága ehhez a művelethez",
    rateLimitExceeded: "Túl sok próbálkozás, kérjük várjon",
    refreshTokenExpired: "Refresh token lejárt",
    refreshTokenInvalid: "Refresh token érvénytelen vagy lejárt",
    refreshTokenMissing: "Refresh token hiányzik",
    resourceNotFound: "Erőforrás nem található",
    success: "Sikeres",
    updated: "Sikeresen frissítve",
    userDisabled: "A felhasználó fiók inaktív",
    userEmailDuplicate: "Ez az email cím már használatban van",
    userLocked: "A felhasználó fiók zárolva van",
    validation.email: "Érvényes email címet adjon meg",
    validation.maxLength: "Maximum {{max}} karakter",
    validation.minLength: "Minimum {{min}} karakter",
    validation.notBlank: "A mező nem lehet üres",
    validation.size.max: "A mező túl hosszú (max {{max}} karakter)",
    validationError: "Validációs hiba",
};
