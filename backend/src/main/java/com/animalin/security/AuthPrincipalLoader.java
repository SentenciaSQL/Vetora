package com.animalin.security;

import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthPrincipalLoader {

    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;

    public AuthPrincipalLoader(UserRepository userRepository, TenantMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TenantContext.AuthPrincipal> load(Long userId, Long tenantId) {
        User user = userRepository.findByIdWithRoles(userId).orElse(null);
        if (user == null || !user.isEnabled()) {
            return Optional.empty();
        }
        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        user.getRoles().forEach(role -> {
            roles.add(role.getCode());
            role.getPermissions().forEach(p -> permissions.add(p.getCode()));
        });
        if (tenantId != null) {
            membershipRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE")
                    .ifPresent(membership -> {
                        roles.add(membership.getRole().getCode());
                        membership.getRole().getPermissions().forEach(p -> permissions.add(p.getCode()));
                    });
        }
        return Optional.of(new TenantContext.AuthPrincipal(
                user.getId(), user.getEmail(), user.fullName(), tenantId, null,
                roles, permissions, user.getLocale(), user.getTheme()
        ));
    }
}
