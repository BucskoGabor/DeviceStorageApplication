package hu.tanszek.device.device.entity;

import java.util.HashSet;
import java.util.Set;

import hu.tanszek.device.common.BaseEntity;
import hu.tanszek.device.software.entity.Software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Eszköz entitás.
 *
 * <p>{@code type} String (nem ENUM), mert egyedi tanszéki eszköztípusok támogatása szükséges.
 * Service-réteg validálja regex-szel ({@code [a-zA-Z0-9\-_]+}, max 50 karakter).
 *
 * <p>{@code status} ENUM — {@code MAINTENANCE} és {@code DISPOSED} állapotú eszközökre NEM lehet
 * assignolni vagy törölni (lásd {@code DeviceService}).
 *
 * <p>A {@code softwares} kapcsolat many-to-many, cascade nélkül — a szoftver törlésekor manuálisan
 * tisztítandó a join tábla service-szinten.
 *
 * @see hu.tanszek.device.device.repository.DeviceRepository
 */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Device extends BaseEntity<Long> {

  /** Eszköz típusa (pl. "laptop", "monitor") — service validálja */
  @Column(name = "type", nullable = false, length = 50)
  private String type;

  /** Egyedi leltári szám (max 50 karakter) */
  @Column(name = "inventory_number", nullable = false, unique = true, length = 50)
  private String inventoryNumber;

  /** Eszköz státusz (lásd {@link DeviceStatus}) */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DeviceStatus status;

  /** Telepített szoftverek (lazy fetch, nincs cascade) */
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "device_softwares",
      joinColumns = @JoinColumn(name = "device_id"),
      inverseJoinColumns = @JoinColumn(name = "software_id"))
  @Builder.Default
  private Set<Software> softwares = new HashSet<>();
}
