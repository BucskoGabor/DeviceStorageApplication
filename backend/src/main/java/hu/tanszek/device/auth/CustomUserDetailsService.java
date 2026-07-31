package hu.tanszek.device.auth;

import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CustomUserDetailsService — Spring Security UserDetailsService implementáció.
 *
 * <p>Betölti az AppUser-t az email_hash alapján (a JwtTokenProvider subject
 * mezője email_hash, NEM email), és visszaadja a UserDetails-t a role + permission
 * authorities-kkal.
 *
 * <p>A security kontextusba betöltött authorities:
 * <ul>
 *   <li>ROLE_ADMIN / ROLE_TEACHER / ROLE_STUDENT — a user role-ja</li>
 *   <li>DEVICE_READ / USER_MANAGE / stb. — a role.permission-ök + user.permission-ök uniója</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String emailHash) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email_hash: " + emailHash));

        return User.withUsername(user.getEmailHash())
                .password(user.getPasswordHash())
                .authorities(getAuthorities(user))
                .accountExpired(false)
                .accountLocked(user.getLockedUntil() != null && user.getLockedUntil().isAfter(java.time.Instant.now()))
                .disabled(!user.isActive())
                .credentialsExpired(false)
                .build();
    }

    /**
     * Authorities összeállítása: role neve + role.permission-ök + user.permission-ök.
     */
    private Collection<GrantedAuthority> getAuthorities(AppUser user) {
        // Role neve (ROLE_ prefix-szel)
        String roleName = user.getRole().getName();
        SimpleGrantedAuthority roleAuthority = new SimpleGrantedAuthority(roleName);

        // Role permission-ök
        Stream<GrantedAuthority> rolePerms = user.getRole().getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getName()));

        // User-specifikus permission-ök
        Stream<GrantedAuthority> userPerms = user.getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getName()));

        return Stream.concat(Stream.of(roleAuthority), Stream.concat(rolePerms, userPerms))
                .collect(Collectors.toSet());
    }
}