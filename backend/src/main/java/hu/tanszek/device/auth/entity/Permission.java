package hu.tanszek.device.auth.entity;

import hu.tanszek.device.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Granularitású jogosultság.
 *
 * <p>14 permission a Flyway V2__seed.sql-ben definiálva:
 * <ul>
 *   <li>{@code DEVICE_CREATE}, {@code DEVICE_READ}, {@code DEVICE_UPDATE}, {@code DEVICE_DELETE}</li>
 *   <li>{@code DEVICE_ASSIGN}, {@code DEVICE_UNASSIGN}</li>
 *   <li>{@code USER_MANAGE}, {@code USER_READ}</li>
 *   <li>{@code LOCATION_MANAGE}, {@code LOCATION_READ}</li>
 *   <li>{@code AUDIT_READ}, {@code AUDIT_ROLLBACK}</li>
 *   <li>{@code SOFTWARE_MANAGE}, {@code SOFTWARE_LICENSE_VIEW}</li>
 * </ul>
 *
 * @see hu.tanszek.device.auth.repository.PermissionRepository
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends BaseEntity<Long> {

    /** A permission egyedi neve (pl. "DEVICE_CREATE") */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
}