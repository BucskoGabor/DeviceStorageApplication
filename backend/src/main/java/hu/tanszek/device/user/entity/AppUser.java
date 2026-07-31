package hu.tanszek.device.user.entity;

import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.common.BaseEntity;
import hu.tanszek.device.location.entity.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Felhasználó entitás.
 *
 * <p>Az email tárolása kettős formátumban:
 * <ul>
 *   <li>{@code emailEncrypted} — AES-GCM titkosított, admin megjelenítéshez visszafejthető</li>
 *   <li>{@code emailHash} — SHA-256 hash, egyediség és gyors keresés (NE használjuk titkosítatlan emailt)</li>
 * </ul>
 *
 * <p>Jelszó:
 * <ul>
 *   <li>{@code passwordHash} — Argon2id hash (memory-hard, OWASP 2024+)</li>
 *   <li>Transparent rehash a login service-ben (memory/iterations növelés)</li>
 * </ul>
 *
 * <p>Lockout mechanizmus:
 * <ul>
 *   <li>{@code failedLoginCount} — 5 próba → 15 min lockout</li>
 *   <li>{@code lockedUntil} — Timestamp, ameddig lockolva van</li>
 *   <li>{@code active} — false = deaktivált, nem tud belépni</li>
 *   <li>{@code mustChangePassword} — first-login flag, /password-change-re redirect</li>
 * </ul>
 *
 * <p>A {@code permissions} join tábla (userPermissions) user-specifikus extra
 * permission-öket ad a role permissionjeihez. A {@code Role.permissions}
 * együtt uniózva adja a végső {@code GrantedAuthority} listát a Spring Security-ben.
 *
 * @see hu.tanszek.device.user.repository.AppUserRepository
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AppUser extends BaseEntity<Long> {

    /** AES-GCM titkosított email (admin megjelenítéshez) */
    @Column(name = "email_encrypted", nullable = false, columnDefinition = "TEXT")
    private String emailEncrypted;

    /** SHA-256 hash az emailből (egyediség + gyors keresés) */
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    /** A user irodai helyszíne (NULL = nincs iroda) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_location_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "parent"})
    private Location officeLocation;

    /** Argon2id hash a jelszóból (memory-hard, OWASP 2024+) */
    @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    /** true = aktív, false = deaktivált (nem tud belépni) */
    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * true = first-login flag, a frontend a /password-change page-re redirectel.
     * A jelszócsere törli a flaget és frissíti a {@code passwordChangedAt}-et.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** A user role-ja (lazy fetch a security betöltéshez) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "permissions"})
    private Role role;

    /** Sikertelen bejelentkezési próbálkozások száma (5 → lockout) */
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    /** Lockout végének időbélyege (NULL = nincs lockout) */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Utolsó jelszócsere időbélyege (first-login tracking) */
    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    /** User-specifikus extra permission-ök (a role permissionjein felül) */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Set<Permission> permissions = new HashSet<>();
}