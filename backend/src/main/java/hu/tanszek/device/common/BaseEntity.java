package hu.tanszek.device.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * BaseEntity — minden JPA entitás ősosztálya.
 *
 * <p>A {@code @MappedSuperclass} annotáció miatt a JPA a {@code created_at} és
 * {@code updated_at} mezőket a leszármazott entitások tábláiba is beépíti.
 *
 * <p>Az {@code @EntityListeners(AuditingEntityListener.class)} aktiválja a
 * JPA Auditing-ot: a {@code @CreatedDate} és {@code @LastModifiedDate}
 * automatikusan kitöltődik {@code save()} híváskor.
 *
 * <p>A {@link org.springframework.data.jpa.domain.support.AuditingEntityListener}
 * a {@code @EnableJpaAuditing} annotációval aktiválódik a {@code DeviceStorageApplication}
 * osztályban.
 *
 * <p>A Flyway V1 migrációban minden táblán explicit definiálva van a
 * {@code created_at} és {@code updated_at} oszlop, mert a Flyway nem ismeri
 * a JPA {@code @MappedSuperclass}-t.
 *
 * @param <ID> az entitás ID-jének típusa (általában {@link Long})
 *
 * @see <a href="../../../../../implementation_plan.md">implementation_plan.md</a>
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity<ID> {

    /**
     * Az entitás elsődleges kulcsa. A JPA auto-generálja (IDENTITY stratégia).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private ID id;

    /**
     * Létrehozás időbélyege. A JPA Auditing {@code @PrePersist} callback
     * automatikusan kitölti a {@code save()} híváskor.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Utolsó módosítás időbélyege. A JPA Auditing {@code @PreUpdate} callback
     * automatikusan frissíti minden {@code save()} híváskor.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}