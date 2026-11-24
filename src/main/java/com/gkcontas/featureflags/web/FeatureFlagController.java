package com.gkcontas.featureflags.web;

import com.gkcontas.featureflags.flag.Evaluation;
import com.gkcontas.featureflags.flag.FeatureFlagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService service;

    @PostMapping
    public ResponseEntity<FlagResponse> create(@Valid @RequestBody FlagRequests.Create request) {
        FlagResponse created = FlagResponse.of(service.create(request.key(), request.description(),
                request.enabled(), request.rolloutPercentage()));
        return ResponseEntity.created(URI.create("/api/flags/" + created.key())).body(created);
    }

    @GetMapping
    public List<FlagResponse> list() {
        return service.findAll().stream().map(FlagResponse::of).toList();
    }

    @GetMapping("/{key}")
    public FlagResponse get(@PathVariable String key) {
        return FlagResponse.of(service.find(key));
    }

    @PutMapping("/{key}")
    public FlagResponse update(@PathVariable String key, @Valid @RequestBody FlagRequests.Update request) {
        return FlagResponse.of(service.update(key, request.version(), request.description(),
                request.enabled(), request.rolloutPercentage()));
    }

    @GetMapping("/{key}/evaluation")
    public Evaluation evaluate(@PathVariable String key,
            @RequestParam @NotBlank(message = "userId is required") String userId) {
        return service.evaluate(key, userId);
    }
}
