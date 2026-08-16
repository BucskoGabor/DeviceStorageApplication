package hu.tanszek.device.config.entity;

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
 * Rendszer konfigurációs kulcs-érték pár.
 *
 * <p>A Flyway V2__seed.sql 12 alapértelmezett értéket tölt be (lásd {@code implementation_plan.md}
 * §2.1).
 *
 * @see hu.tanszek.device.config.repository.ConfigRepository
 */
@Entity
@Table(name = "configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Config extends BaseEntity<Long> {

  /** A konfigurációs kulcs (egyedi) */
  @Column(name = "key", nullable = false, unique = true, length = 255)
  private String key;

  /** A konfigurációs érték */
  @Column(name = "value", nullable = false, columnDefinition = "TEXT")
  private String value;
}
