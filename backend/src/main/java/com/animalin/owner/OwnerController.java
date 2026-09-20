package com.animalin.owner;

import com.animalin.common.api.PageResponse;
import com.animalin.dto.AppDtos;
import com.animalin.pet.PetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/owners")
public class OwnerController {

    private final OwnerService ownerService;
    private final PetService petService;

    public OwnerController(OwnerService ownerService, PetService petService) {
        this.ownerService = ownerService;
        this.petService = petService;
    }

    @GetMapping
    public PageResponse<AppDtos.OwnerResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ownerService.search(q, status, pageable);
    }

    @GetMapping("/{id}")
    public AppDtos.OwnerResponse get(@PathVariable Long id) {
        return ownerService.get(id);
    }

    @GetMapping("/{id}/pets")
    public List<AppDtos.PetResponse> pets(@PathVariable Long id) {
        return petService.byOwner(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.OwnerResponse create(@Valid @RequestBody AppDtos.OwnerRequest request) {
        return ownerService.create(request);
    }

    @PutMapping("/{id}")
    public AppDtos.OwnerResponse update(@PathVariable Long id, @Valid @RequestBody AppDtos.OwnerRequest request) {
        return ownerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        ownerService.delete(id);
    }
}
