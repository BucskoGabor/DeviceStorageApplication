package hu.tanszek.device.device;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.user.entity.AppUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

class DeviceSpecificationsTest {

  @Test
  void hasAccess_nullUser_returnsConjunction() {
    Specification<Device> spec = DeviceSpecifications.hasAccess(null);
    assertThat(spec).isNotNull();

    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Predicate pred = mock(Predicate.class);
    when(cb.conjunction()).thenReturn(pred);

    Predicate result = spec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), cb);
    assertThat(result).isEqualTo(pred);
  }

  @Test
  @SuppressWarnings("unchecked")
  void hasAccess_withUser_buildsSubquery() {
    Specification<Device> spec = DeviceSpecifications.hasAccess(10L);
    assertThat(spec).isNotNull();

    Root<Device> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Subquery<Long> subquery = mock(Subquery.class);
    Root subRoot = mock(Root.class);
    Path idPath = mock(Path.class);
    Path toUserPath = mock(Path.class);
    Path toUserIdPath = mock(Path.class);
    Path activePath = mock(Path.class);
    Path devicePath = mock(Path.class);
    Path deviceIdPath = mock(Path.class);
    Predicate existsPredicate = mock(Predicate.class);

    when(query.subquery(Long.class)).thenReturn(subquery);
    when(subquery.from(any(Class.class))).thenReturn(subRoot);
    when(subquery.select(any(jakarta.persistence.criteria.Expression.class))).thenReturn(subquery);
    when(subquery.where(any(Predicate.class))).thenReturn(subquery);
    when(subRoot.get("id")).thenReturn(idPath);
    when(subRoot.get("toUser")).thenReturn(toUserPath);
    when(toUserPath.get("id")).thenReturn(toUserIdPath);
    when(subRoot.get("active")).thenReturn(activePath);
    when(subRoot.get("device")).thenReturn(devicePath);
    when(devicePath.get("id")).thenReturn(deviceIdPath);
    when(root.get("id")).thenReturn(idPath);
    when(cb.exists(subquery)).thenReturn(existsPredicate);

    Predicate result = spec.toPredicate(root, query, cb);
    assertThat(result).isEqualTo(existsPredicate);
  }

  @Test
  @SuppressWarnings("unchecked")
  void teacherAccess_withoutOfficeLocation_usesDisjunction() {
    AppUser user = AppUser.builder().id(10L).officeLocation(null).build();
    Specification<Device> spec = DeviceSpecifications.teacherAccess(10L, user);
    assertThat(spec).isNotNull();

    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    Root<Device> root = mock(Root.class);
    Subquery<Long> subquery = mock(Subquery.class);
    Root subRoot = mock(Root.class);
    Path idPath = mock(Path.class);
    Path toUserPath = mock(Path.class);
    Path toUserIdPath = mock(Path.class);
    Path activePath = mock(Path.class);
    Path devicePath = mock(Path.class);
    Path deviceIdPath = mock(Path.class);
    Predicate existsPredicate = mock(Predicate.class);
    Predicate disjunction = mock(Predicate.class);
    Predicate orPredicate = mock(Predicate.class);

    when(query.subquery(Long.class)).thenReturn(subquery);
    when(subquery.from(any(Class.class))).thenReturn(subRoot);
    when(subquery.select(any(jakarta.persistence.criteria.Expression.class))).thenReturn(subquery);
    when(subquery.where(any(Predicate.class))).thenReturn(subquery);
    when(subRoot.get("id")).thenReturn(idPath);
    when(subRoot.get("toUser")).thenReturn(toUserPath);
    when(toUserPath.get("id")).thenReturn(toUserIdPath);
    when(subRoot.get("active")).thenReturn(activePath);
    when(subRoot.get("device")).thenReturn(devicePath);
    when(devicePath.get("id")).thenReturn(deviceIdPath);
    when(root.get("id")).thenReturn(idPath);
    when(cb.exists(subquery)).thenReturn(existsPredicate);
    when(cb.disjunction()).thenReturn(disjunction);
    when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(orPredicate);
    when(cb.or(any(Predicate[].class))).thenReturn(orPredicate);

    Predicate result = spec.toPredicate(root, query, cb);
    assertThat(result).isNotNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void teacherAccess_withOfficeLocation_buildsSpecification() {
    Location office = Location.builder().id(5L).name("Room 101").build();
    AppUser user = AppUser.builder().id(10L).officeLocation(office).build();
    Specification<Device> spec = DeviceSpecifications.teacherAccess(10L, user);
    assertThat(spec).isNotNull();

    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Root<Device> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    Subquery<Long> subquery = mock(Subquery.class);
    Root subRoot = mock(Root.class);
    Path idPath = mock(Path.class);
    Path toLocPath = mock(Path.class);
    Path toLocIdPath = mock(Path.class);
    Path activePath = mock(Path.class);
    Path devicePath = mock(Path.class);
    Path deviceIdPath = mock(Path.class);
    Predicate existsPredicate = mock(Predicate.class);
    Predicate orPredicate = mock(Predicate.class);

    when(query.subquery(Long.class)).thenReturn(subquery);
    when(subquery.from(any(Class.class))).thenReturn(subRoot);
    when(subquery.select(any(jakarta.persistence.criteria.Expression.class))).thenReturn(subquery);
    when(subquery.where(any(Predicate.class))).thenReturn(subquery);
    when(subRoot.get("id")).thenReturn(idPath);
    when(subRoot.get("toLocation")).thenReturn(toLocPath);
    when(toLocPath.get("id")).thenReturn(toLocIdPath);
    when(subRoot.get("toUser")).thenReturn(toLocPath);
    when(subRoot.get("active")).thenReturn(activePath);
    when(subRoot.get("device")).thenReturn(devicePath);
    when(devicePath.get("id")).thenReturn(deviceIdPath);
    when(root.get("id")).thenReturn(idPath);
    when(cb.exists(subquery)).thenReturn(existsPredicate);
    when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(orPredicate);
    when(cb.or(any(Predicate[].class))).thenReturn(orPredicate);

    Predicate result = spec.toPredicate(root, query, cb);
    assertThat(result).isNotNull();
  }
}
