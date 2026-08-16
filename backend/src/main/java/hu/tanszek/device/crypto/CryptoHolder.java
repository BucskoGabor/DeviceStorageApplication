package hu.tanszek.device.crypto;

import org.springframework.stereotype.Component;

/**
 * CryptoHolder — static holder for CryptoService to allow JPA entities to access email decryption
 * for Jackson JSON serialization.
 */
@Component
public class CryptoHolder {
  private static CryptoService instance;

  public CryptoHolder(CryptoService cryptoService) {
    CryptoHolder.instance = cryptoService;
  }

  public static CryptoService getInstance() {
    return instance;
  }
}
