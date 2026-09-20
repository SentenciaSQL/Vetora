package com.animalin.pet;

import com.animalin.common.api.PageResponse;
import com.animalin.dto.AppDtos;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping
    public PageResponse<AppDtos.PetResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String species,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            Pageable pageable) {
        return petService.search(q, species, status, ownerId, pageable);
    }

    @GetMapping("/mine")
    public List<AppDtos.PetResponse> mine() {
        return petService.mine();
    }

    @GetMapping("/owner/{ownerId}")
    public List<AppDtos.PetResponse> byOwner(@PathVariable Long ownerId) {
        return petService.byOwner(ownerId);
    }

    @GetMapping("/{id}")
    public AppDtos.PetResponse get(@PathVariable Long id) {
        return petService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.PetResponse create(@RequestBody AppDtos.PetRequest request) {
        return petService.create(request);
    }

    @PutMapping("/{id}")
    public AppDtos.PetResponse update(@PathVariable Long id, @RequestBody AppDtos.PetRequest request) {
        return petService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        petService.delete(id);
    }

    @PostMapping("/{id}/photo")
    public AppDtos.PetResponse photo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return petService.uploadPhoto(id, file);
    }

    @GetMapping("/{id}/weights")
    public List<AppDtos.WeightResponse> weights(@PathVariable Long id) {
        return petService.weights(id);
    }

    @PostMapping("/{id}/weights")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.WeightResponse addWeight(@PathVariable Long id, @RequestBody AppDtos.WeightRequest request) {
        return petService.addWeight(id, request);
    }
}
