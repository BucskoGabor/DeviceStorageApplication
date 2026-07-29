package hu.tanszek.device.assignment.entity;

import hu.tanszek.device.common.BaseEntity;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Eszköz hozzárendelés (history-szerű, egyetlen tábla).
 *
 * <p>Egy device-hoz egy időben csak egy aktív rekord tartozik
 * ({@code active = true}). A history ugyanebben a táblában van,
 * inaktív rekordok formájában.
 *
 * <p>State machine:
 * <pre>
 *   IN_STORAGE → PENDING_ASSIGNMENT → ASSIGNED → PENDING_UNASSIGNMENT → IN_STORAGE
 * </pre>
 *
 * <p>Üzleti szabályok:
 * <ul>
 *   <li>A {@code to_location_id} NEM lehet {@code LocationType.GROUP} típusú</li>
 *   <li>A {@code from_location_id} NEM lehet {@code LocationType.GROUP} típusú</li>
 *   <li>A device státusza NEM lehet {@code MAINTENANCE} vagy {@code DISPOSED}</li>
 * </ul>
 *
 * <p>Service assert: új aktív rekord mindig NULL az {@code unassign_*} mezőkön.
 *
 * @see hu.tanszek.device.assignment.repository.DeviceAssignmentRepository
 */
@Entity
@Table(name = "device_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceAssignment extends BaseEntity<Long> {

    /** Az eszköz, amihez az assignment tartozik */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** Honnen mozog az eszköz (NULL = kezdeti állapot, raktár) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id")
    private Location fromLocation;

    /** Hova mozog az eszköz (NULL = kiveszik a rendszerből) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id")
    private Location toLocation;

    /** Honnen kapja az user (NULL = kezdeti állapot) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id")
    private AppUser fromUser;

    /** Hova kapja az user (NULL = kiveszik a user-től) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id")
    private AppUser toUser;

    /** Aki az assignmentet létrehozta */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "by_user_id", nullable = false)
    private AppUser createdByUser;

    /** Aki jóváhagyta (NULL = még nem jóváhagyott) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private AppUser approvedBy;

    /** Aki az unassign-t kezdeményezte (NULL = aktív assignment) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unassigned_by_id")
    private AppUser unassignedBy;

    /** Aki az unassign-t jóváhagyta (NULL = nincs jóváhagyva) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unassign_approved_by_id")
    private AppUser unassignApprovedBy;

    /** Amikor az assignment végbement (NULL = pending) */
    @Column(name = "date_of_assignment")
    private Instant dateOfAssignment;

    /** Az assignment rekord létrehozásának időbélyege */
    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    /** Amikor az unassign végbement (NULL = aktív) */
    @Column(name = "unassign_date")
    private Instant unassignDate;

    /** Az unassign rekord létrehozásának időbélyege */
    @Column(name = "unassign_created_date")
    private Instant unassignCreatedDate;

    /** Assignment státusz (lásd {@link AssignmentStatus}) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AssignmentStatus status;

    /** true = jelenlegi aktív assignment (egy device-hoz csak egy) */
    @Column(name = "active", nullable = false)
    private boolean active;
}