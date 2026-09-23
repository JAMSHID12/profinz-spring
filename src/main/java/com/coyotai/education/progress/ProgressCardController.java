package com.coyotai.education.progress;

import com.coyotai.education.assessment.AssessmentDtos.StatusRequest;
import com.coyotai.education.assessment.PublicationStatus;
import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.progress.ProgressCardService.Card;
import com.coyotai.education.progress.ProgressCardService.GenerateRequest;
import com.coyotai.education.progress.ProgressCardService.UpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/progress-cards")
@RequiresModule(ModuleCode.ACADEMICS)
public class ProgressCardController {

    private final ProgressCardService service;

    public ProgressCardController(ProgressCardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROGRESS_CARD_VIEW')")
    public ApiResponse<List<Card>> search(@RequestParam(required = false) Long studentId,
                                         @RequestParam(required = false) Long batchId,
                                         @RequestParam(required = false) PublicationStatus status) {
        return ApiResponse.ok(service.search(studentId, batchId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROGRESS_CARD_VIEW')")
    public ApiResponse<Card> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('PROGRESS_CARD_CREATE')")
    public ApiResponse<List<Card>> generate(@Valid @RequestBody GenerateRequest request) {
        List<Card> cards = service.generate(request);
        return ApiResponse.ok(cards, cards.size() + " progress card(s) generated as drafts");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROGRESS_CARD_CREATE')")
    public ApiResponse<Card> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request), "Progress card updated");
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PROGRESS_CARD_PUBLISH')")
    public ApiResponse<Card> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(service.changeStatus(id, request.status()), "Progress card moved to " + request.status());
    }
}
