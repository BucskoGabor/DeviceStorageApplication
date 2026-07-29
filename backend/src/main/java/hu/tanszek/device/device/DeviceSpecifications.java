package hu.tanszek.device.device;

import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.user.entity.AppUser;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/**
 * DeviceSpecifications — Specification builder a Device row-level filterhez.
 *
 * <p>A Specification interface használata (lásd {@link Specification})
 * lehetővé teszi a dinamikus Predicate összeállítást a JpaSpecificationExecutor-ral.
 *
 * <p>A row-level filter három role-t különböztet meg:
 * <ul>
 *   <li>ADMIN: minden device (nincs szűrés, a spec üres)</li>
 *   <li>TEACHER: saját + irodai device-ok</li>
 *   <li>STUDENT: csak saját device-ok</li>
 * </ul>
 */
public final class DeviceSpecifications {

    private DeviceSpecifications() {}

    /**
     * Egyetlen Specification, amely a currentUser role-ja alapján alkalmazza
     * a row-level filtert.
     *
     * <p>TODO Task 3.3: implementálni a pontos logikát a role + user alapján.
     * Most a placeholder visszaadja az összes device-ot (ADMIN-nak megfelelő).
     *
     * @param currentUserId a bejelentkezett user ID-ja (null = ADMIN)
     * @return Specification ami kombinálja a row-level szűrést
     */
    public static Specification<Device> hasAccess(Long currentUserId) {
        // ADMIN (currentUserId null) esetén nincs szűrés
        if (currentUserId == null) {
            return (root, query, cb) -> cb.conjunction();
        }

        // TODO Task 3.3: STUDENT és TEACHER szűrés implementálása
        // A role információ itt még nem elérhető, mert a Specification nem kap
        // AppUser-t. A role-alapú döntés a service-szinten kell történjen, ahol
        // az AppUser repository-ból lekérhető.
        //
        // Addig is: STUDENT/TEACHER esetén csak a saját device-okat adjuk vissza
        // (active assignment to currentUser).
        return (root, query, cb) -> {
            // Subquery: van-e aktív assignment, ahol a device a currentUser-hez van rendelve
            var subquery = query.subquery(Long.class);
            var subRoot = subquery.from(DeviceAssignment.class);
            subquery.select(subRoot.get("device_id"))
                    .where(
                            cb.and(
                                    cb.equal(subRoot.get("to_user_id"), currentUserId),
                                    cb.equal(subRoot.get("active"), true),
                                    cb.equal(subRoot.get("device_id"), root.get("id"))
                            )
                    );

            return cb.exists(subquery);
        };
    }

    /**
     * TEACHER-specifikus Specification: saját + irodai device-ok.
     *
     * <p>TODO Task 3.3: implementálni az irodai szűrést (location_id = user.office_location_id).
     */
    public static Specification<Device> teacherAccess(Long currentUserId, AppUser currentUser) {
        return Specification.allOf(
                hasAccess(currentUserId),
                // TODO: irodai szűrés hozzáadása (device.location_id = currentUser.officeLocation.id)
                (root, query, cb) -> cb.conjunction()
        );
    }
}