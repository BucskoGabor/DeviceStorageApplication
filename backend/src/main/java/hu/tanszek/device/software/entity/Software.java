package hu.tanszek.device.software.entity;

import hu.tanszek.device.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Szoftver entitás (license key tárolással).
 *
 * <p>A {@code licenseKeyEncrypted} AES-GCM titkosított érték — csak {@code SOFTWARE_LICENSE_VIEW}
 * permission-nel rendelkező user látja visszafejtve. Egyébként maszkolva jelenik meg: {@code
 * ****-****-****-<utolsó 4 karakter>}.
 *
 * @see hu.tanszek.device.software.service.SoftwareService
 */
@Entity
@Table(name = "softwares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Software extends BaseEntity<Long> {

  /** A szoftver neve */
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  /** AES-GCM titkosított license key */
  @Column(name = "license_key_encrypted", nullable = false, columnDefinition = "TEXT")
  private String licenseKeyEncrypted;
}
