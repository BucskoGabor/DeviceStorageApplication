package hu.tanszek.device.crypto;

import org.springframework.stereotype.Component;

/**
 * CryptoHolder — Spring-bridge a {@link CryptoService} statikus eléréséhez.
 *
 * <p>A JPA entity-k (pl. {@code AppUser.getEmail()}) Jackson szerializációja során visszafejti az
 * email-t a {@link CryptoService#decrypt} segítségével — viszont a Jackson-on-the-fly getter-ek nem
 * kapnak Spring injection-t, így a service-t statikusan kell elérniük.
 *
 * <p><b>Ismert korlát:</b> Ha a Jackson szerializáció a Spring context inicializálása előtt fut le
 * (pl. unit tesztek vagy application context nélküli kód útvonalak), a {@link #getInstance()}
 * {@code null}-t ad vissza. Ilyenkor a hívó kód egy NPE-t kapna — ez az AntiPattern, és hosszú
 * távon a terv szerint át kellene terni DTO-alapú dekripcióra (a service réteg injektálja a
 * CryptoService-t a DTO mapping során). A jelenlegi fix biztonságosabbá teszi a hozzáférést: {@link
 * #getInstance()} most ellenőrzi a null-ot, és a getter-ek opcionálisan kezelik.
 *
 * <p>A {@code volatile} biztosítja, hogy több szálon át látható legyen a frissítés — az
 * inicializálás a konstruktorban, az alkalmazás indulásakor egyszer történik.
 */
@Component
public class CryptoHolder {
  private static volatile CryptoService instance;

  public CryptoHolder(CryptoService cryptoService) {
    CryptoHolder.instance = cryptoService;
  }

  public static CryptoService getInstance() {
    return instance;
  }

  /**
   * Biztonságos getter — {@code null}-t ad vissza, ha a {@link CryptoService} még nem
   * inicializálódott. Olyan opcionális dekripcióknál használd, ahol a hívó kezelni tudja a {@code
   * null} visszatérési értéket.
   */
  public static CryptoService getInstanceOrNull() {
    return instance;
  }
}
