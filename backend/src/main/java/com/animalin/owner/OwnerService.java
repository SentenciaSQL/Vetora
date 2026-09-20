package com.animalin.owner;

import com.animalin.audit.AuditService;
import com.animalin.common.api.PageResponse;
import com.animalin.common.exception.ApiException;
import com.animalin.dto.AppDtos;
import com.animalin.pet.PetRepository;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard accessGuard;
    private final AuditService auditService;

    public OwnerService(OwnerRepository ownerRepository, PetRepository petRepository, UserRepository userRepository, RoleRepository roleRepository, TenantMembershipRepository membershipRepository, TenantRepository tenantRepository, PasswordEncoder passwordEncoder, AccessGuard accessGuard, AuditService auditService) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppDtos.OwnerResponse> search(String q, String status, Pageable pageable) {
        Long tenantId = accessGuard.requireStaffTenant();
        accessGuard.requirePermission("OWNER_READ");
        return PageResponse.of(ownerRepository.search(tenantId, emptyToNull(q), emptyToNull(status), pageable)
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public AppDtos.OwnerResponse get(Long id) {
        return toDto(accessGuard.requireOwner(id));
    }

    @Transactional
    public AppDtos.OwnerResponse create(AppDtos.OwnerRequest request) {
        Long tenantId = accessGuard.requireStaffTenant();
        accessGuard.requirePermission("OWNER_CREATE");
        Owner owner = new Owner();
        owner.setTenantId(tenantId);
        apply(owner, request);
        linkOrCreateUser(owner, tenantId);
        ownerRepository.save(owner);
        auditService.record("CREATE", "OWNER", owner.getId(), owner.fullName());
        return toDto(owner);
    }

    @Transactional
    public AppDtos.OwnerResponse update(Long id, AppDtos.OwnerRequest request) {
        accessGuard.requirePermission("OWNER_UPDATE");
        Owner owner = accessGuard.requireOwner(id);
        apply(owner, request);
        auditService.record("UPDATE", "OWNER", owner.getId(), owner.fullName());
        return toDto(owner);
    }

    @Transactional
    public void delete(Long id) {
        accessGuard.requirePermission("OWNER_UPDATE");
        Owner owner = accessGuard.requireOwner(id);
        owner.softDelete();
        auditService.record("DELETE", "OWNER", owner.getId(), owner.fullName());
    }

    private void apply(Owner owner, AppDtos.OwnerRequest request) {
        if (!StringUtils.hasText(request.firstName()) || !StringUtils.hasText(request.lastName())) {
            throw ApiException.badRequest("Nombre y apellidos son obligatorios");
        }
        owner.setFirstName(request.firstName());
        owner.setLastName(request.lastName());
        owner.setDocumentId(request.documentId());
        owner.setPhone(request.phone());
        owner.setEmail(request.email() == null ? null : request.email().trim().toLowerCase());
        owner.setAddress(request.address());
        owner.setCity(request.city());
        owner.setCountry(request.country());
        owner.setNotes(request.notes());
        if (request.status() != null) {
            owner.setStatus(request.status());
        }
    }

    private void linkOrCreateUser(Owner owner, Long tenantId) {
        if (!StringUtils.hasText(owner.getEmail())) {
            return;
        }
        User user = userRepository.findByEmailIgnoreCase(owner.getEmail()).orElseGet(() -> {
            User created = new User();
            created.setEmail(owner.getEmail());
            created.setFirstName(owner.getFirstName());
            created.setLastName(owner.getLastName());
            created.setPhone(owner.getPhone());
            created.setPasswordHash(passwordEncoder.encode("Tmp-" + UUID.randomUUID()));
            roleRepository.findByCode("PET_OWNER").ifPresent(created.getRoles()::add);
            return userRepository.save(created);
        });
        owner.setUser(user);
        if (!membershipRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            TenantMembership membership = new TenantMembership();
            membership.setTenant(tenantRepository.getReferenceById(tenantId));
            membership.setUser(user);
            membership.setRole(roleRepository.findByCode("PET_OWNER").orElseThrow());
            membership.setStatus("ACTIVE");
            membershipRepository.save(membership);
        }
    }

    private AppDtos.OwnerResponse toDto(Owner owner) {
        int pets = petRepository.findByOwnerIdAndTenantId(owner.getId(), owner.getTenantId()).size();
        return new AppDtos.OwnerResponse(
                owner.getId(), owner.getFirstName(), owner.getLastName(), owner.fullName(),
                owner.getDocumentId(), owner.getPhone(), owner.getEmail(), owner.getAddress(),
                owner.getCity(), owner.getCountry(), owner.getNotes(), owner.getStatus(),
                owner.getCreatedAt(), owner.getUser() == null ? null : owner.getUser().getId(), pets
        );
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
