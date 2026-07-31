package hu.tanszek.device.auth.entity;

import hu.tanszek.device.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.HashSet;
import java.util.Set;

/**
 * Felhasználói role.
 *
 * <p>3 role a Flyway V2__seed.sql-ben definiálva:
 * <ul>
 *   <li>{@code ROLE_ADMIN} — minden permission</li>
 *   <li>{@code ROLE_TEACHER} — DEVICE_READ + DEVICE_ASSIGN/UNASSIGN + USER_READ + LOCATION_READ + AUDIT_READ</li>
 *   <li>{@code ROLE_STUDENT} — DEVICE_READ + USER_READ + LOCATION_READ</li>
 * </ul>
 *
 * <p>A {@code permissions} kapcsolat lazy fetch-csel töltődik
 * (a role permissionjei a {@link hu.tanszek.device.user.entity.AppUser}
 * entitáson keresztül is elérhetők a {@code userPermissions} join-on át).
 *
 * @see hu.tanszek.device.auth.repository.RoleRepository
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Role extends BaseEntity<Long> {

    /** A role egyedi neve (pl. "ROLE_ADMIN") */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /** A role-hoz tartozó permission-ök (lazy fetch) */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}