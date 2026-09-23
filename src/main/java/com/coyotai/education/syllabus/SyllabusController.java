package com.coyotai.education.syllabus;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.syllabus.SyllabusService.BatchSyllabus;
import com.coyotai.education.syllabus.SyllabusService.ProgressRequest;
import com.coyotai.education.syllabus.SyllabusService.ProgressRow;
import com.coyotai.education.syllabus.SyllabusService.TopicRequest;
import com.coyotai.education.syllabus.SyllabusService.TopicResponse;
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
@RequestMapping("/api/syllabus")
@RequiresModule(ModuleCode.ACADEMICS)
public class SyllabusController {

    private final SyllabusService syllabusService;

    public SyllabusController(SyllabusService syllabusService) {
        this.syllabusService = syllabusService;
    }

    @GetMapping("/topics")
    @PreAuthorize("hasAuthority('SYLLABUS_VIEW')")
    public ApiResponse<List<TopicResponse>> topics(@RequestParam(required = false) Long courseId,
                                                   @RequestParam(required = false) Long subjectId) {
        return ApiResponse.ok(syllabusService.topics(courseId, subjectId));
    }

    @PostMapping("/topics")
    @PreAuthorize("hasAuthority('SYLLABUS_MANAGE')")
    public ApiResponse<TopicResponse> createTopic(@Valid @RequestBody TopicRequest request) {
        return ApiResponse.ok(syllabusService.saveTopic(null, request), "Topic added");
    }

    @PutMapping("/topics/{id}")
    @PreAuthorize("hasAuthority('SYLLABUS_MANAGE')")
    public ApiResponse<TopicResponse> updateTopic(@PathVariable Long id, @Valid @RequestBody TopicRequest request) {
        return ApiResponse.ok(syllabusService.saveTopic(id, request), "Topic updated");
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAuthority('SYLLABUS_VIEW')")
    public ApiResponse<BatchSyllabus> progress(@RequestParam Long batchId,
                                               @RequestParam(required = false) Long subjectId) {
        return ApiResponse.ok(syllabusService.batchProgress(batchId, subjectId));
    }

    @PutMapping("/progress/{batchId}/{topicId}")
    @PreAuthorize("hasAuthority('SYLLABUS_UPDATE')")
    public ApiResponse<ProgressRow> updateProgress(@PathVariable Long batchId, @PathVariable Long topicId,
                                                   @Valid @RequestBody ProgressRequest request) {
        return ApiResponse.ok(syllabusService.updateProgress(batchId, topicId, request), "Syllabus progress updated");
    }
}
