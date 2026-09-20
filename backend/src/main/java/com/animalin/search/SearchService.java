package com.animalin.search;

import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.Pet;
import com.animalin.pet.PetRepository;
import com.animalin.security.AccessGuard;
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private final PetRepository petRepository;
    private final OwnerRepository ownerRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final AccessGuard accessGuard;

    public SearchService(PetRepository petRepository, OwnerRepository ownerRepository, VeterinarianRepository veterinarianRepository, AccessGuard accessGuard) {
        this.petRepository = petRepository;
        this.ownerRepository = ownerRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(String q) {
        Long tenantId = accessGuard.requireStaffTenant();
        if (q == null || q.isBlank()) {
            return Map.of("pets", List.of(), "owners", List.of(), "veterinarians", List.of());
        }
        var page = PageRequest.of(0, 8);
        List<Map<String, Object>> pets = petRepository.search(tenantId, q, null, null, null, page).stream()
                .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName(), "species", p.getSpecies(), "owner", p.getOwner().fullName()))
                .toList();
        List<Map<String, Object>> owners = ownerRepository.search(tenantId, q, null, page).stream()
                .map(o -> Map.<String, Object>of("id", o.getId(), "name", o.fullName(), "email", o.getEmail() == null ? "" : o.getEmail(), "phone", o.getPhone() == null ? "" : o.getPhone()))
                .toList();
        List<Map<String, Object>> vets = veterinarianRepository.findByTenantId(tenantId, page).stream()
                .filter(v -> v.getUser().fullName().toLowerCase().contains(q.toLowerCase())
                        || v.getUser().getEmail().toLowerCase().contains(q.toLowerCase()))
                .map(v -> Map.<String, Object>of("id", v.getId(), "name", v.getUser().fullName(), "specialty", v.getSpecialty() == null ? "" : v.getSpecialty()))
                .toList();
        return Map.of("pets", pets, "owners", owners, "veterinarians", vets);
    }
}

@RestController
@RequestMapping("/api/v1/search")
class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public Map<String, Object> search(@RequestParam String q) {
        return searchService.search(q);
    }
}
