package hu.tanszek.device.location.entity;

import hu.tanszek.device.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * Helyszín (hierarchikus).
 *
 * <p>A {@code parentId} self-reference — a {@code LocationService.validateNoCycle()}
 * metódus ellenőrzi a ciklusmentességet create/update előtt.
 *
 * <p>A {@link Version} mező az optimistic lock — párhuzamos módosítás ellen.
 * Ha {@code OptimisticLockException} történik move során, a service
 * 3x retry-olja külön tranzakcióban.
 *
 * <p>Üzleti szabály: a {@link LocationType#GROUP} típusú location-ra NEM lehet
 * eszközt assignolni (forrás ÉS cél is tilos) — lásd {@code DeviceService.assign()}.
 *
 * @see hu.tanszek.device.location.repository.LocationRepository
 */
@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Location extends BaseEntity<Long> {

    /** A location neve (pl. "Tanterem 101") */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Self-reference a hierarchiához (parent location) — NULL = root */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Location parent;

    /** A location típusa (lásd {@link LocationType}) */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private LocationType type;

    /**
     * Optimistic lock — párhuzamos módosítás ellen.
     * A {@code LocationService.move()} 3x retry-olja {@code OptimisticLockException} esetén.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Helper: a location children location-jai (lazy fetch).
     */
    @OneToMany(mappedBy = "parent")
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Location> children = new ArrayList<>();

    /**
     * Származtatott mező a JSON serializációhoz — a {@code parent} entitás ID-ja.
     * A {@code @JsonIgnore}-elt {@code parent} mező helyett ez kerül a wire-ba,
     * így a kliens egyszerűen használhatja a fa-struktúra megjelenítéséhez.
     *
     * <p>A {@code @JsonProperty} felülírja a {@code @JsonIgnore}-et ezen a mezőn.
     * A metódus null-t ad vissza, ha nincs parent (root node).
     */
    @com.fasterxml.jackson.annotation.JsonProperty("parentId")
    public Long getParentId() {
        return parent != null ? parent.getId() : null;
    }
}