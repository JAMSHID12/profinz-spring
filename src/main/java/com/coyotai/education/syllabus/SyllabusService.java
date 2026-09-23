package com.coyotai.education.syllabus;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchService;
import com.coyotai.education.academic.CourseService;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.Ref;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.security.DataScopeService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Syllabus topics per subject, and each batch's progress through them. */
@Service
public class SyllabusService {

    private final SyllabusTopicRepository topicRepository;
    private final SyllabusProgressRepository progressRepository;
    private final CourseService courseService;
    private final BatchService batchService;
    private final DataScopeService dataScopeService;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public SyllabusService(SyllabusTopicRepository topicRepository, SyllabusProgressRepository progressRepository,
                           CourseService courseService, BatchService batchService, DataScopeService dataScopeService,
                           ProjectConfigService configService, AuditService auditService) {
        this.topicRepository = topicRepository;
        this.progressRepository = progressRepository;
        this.courseService = courseService;
        this.batchService = batchService;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
        this.auditService = auditService;
    }

    public record TopicRequest(
            @NotNull(message = "Subject is required") Long subjectId,
            @NotBlank(message = "Topic title is required") @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @Min(value = 1, message = "Sequence starts at 1") Integer sequenceNo,
            @DecimalMin(value = "0", message = "Planned hours cannot be negative") BigDecimal plannedHours,
            Boolean active
    ) {
    }

    public record TopicResponse(Long id, Ref course, Ref subject, String title, String description, int sequenceNo,
                                BigDecimal plannedHours, boolean active) {

        static TopicResponse from(SyllabusTopic t) {
            return new TopicResponse(t.getId(), Ref.of(t.getCourse().getId(), t.getCourse().getName()),
                    Ref.of(t.getSubject().getId(), t.getSubject().getName()), t.getTitle(), t.getDescription(),
                    t.getSequenceNo(), t.getPlannedHours(), t.isActive());
        }
    }

    public record ProgressRequest(
            LocalDate plannedDate,
            LocalDate completedDate,
            @NotNull(message = "Status is required") SyllabusProgress.Status status,
            @Size(max = 500) String remarks
    ) {
    }

    /** One topic's progress for a batch. {@code status} is the effective status (overdue -> DELAYED). */
    public record ProgressRow(Long topicId, Ref subject, String title, int sequenceNo, LocalDate plannedDate,
                              LocalDate completedDate, SyllabusProgress.Status status, String remarks) {
    }

    public record SubjectProgress(Ref subject, int totalTopics, int completedTopics, double completion) {
    }

    public record BatchSyllabus(Ref batch, double completion, List<SubjectProgress> subjects, List<ProgressRow> topics) {
    }

    // ---- Topics ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TopicResponse> topics(Long courseId, Long subjectId) {
        return topicRepository.search(courseId, subjectId, false).stream().map(TopicResponse::from).toList();
    }

    @Transactional
    public TopicResponse saveTopic(Long id, TopicRequest request) {
        Subject subject = courseService.getSubject(request.subjectId());
        SyllabusTopic topic = id == null ? new SyllabusTopic()
                : topicRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Topic", id));
        topic.setCourse(subject.getCourse());
        topic.setSubject(subject);
        topic.setTitle(request.title().trim());
        topic.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        topic.setSequenceNo(request.sequenceNo() == null ? nextSequence(subject.getId()) : request.sequenceNo());
        topic.setPlannedHours(request.plannedHours());
        topic.setActive(request.active() == null || request.active());
        topicRepository.save(topic);
        auditService.record("SyllabusTopic", topic.getId(), id == null ? AuditService.CREATE : AuditService.UPDATE,
                (id == null ? "Added" : "Updated") + " topic \"" + topic.getTitle() + "\" in " + subject.getName());
        return TopicResponse.from(topic);
    }

    private int nextSequence(Long subjectId) {
        return topicRepository.search(null, subjectId, false).stream()
                .mapToInt(SyllabusTopic::getSequenceNo).max().orElse(0) + 1;
    }

    // ---- Progress ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public BatchSyllabus batchProgress(Long batchId, Long subjectId) {
        dataScopeService.current().requireBatch(batchId);
        return progressFor(batchService.getBatch(batchId), subjectId);
    }

    /** Also used by the student portal and progress cards; performs no scope check itself. */
    @Transactional(readOnly = true)
    public BatchSyllabus progressFor(Batch batch, Long subjectId) {
        Map<Long, SyllabusProgress> progress = progressRepository.findByBatch(batch.getId()).stream()
                .collect(Collectors.toMap(p -> p.getTopic().getId(), Function.identity()));
        LocalDate today = configService.today();
        List<ProgressRow> rows = topicRepository.search(batch.getCourse().getId(), subjectId, true).stream()
                .map(topic -> {
                    SyllabusProgress p = progress.get(topic.getId());
                    SyllabusProgress.Status status = p == null ? SyllabusProgress.Status.NOT_STARTED : p.getStatus();
                    LocalDate planned = p == null ? null : p.getPlannedDate();
                    if (status != SyllabusProgress.Status.COMPLETED && planned != null && planned.isBefore(today)) {
                        status = SyllabusProgress.Status.DELAYED;
                    }
                    return new ProgressRow(topic.getId(), Ref.of(topic.getSubject().getId(), topic.getSubject().getName()),
                            topic.getTitle(), topic.getSequenceNo(), planned, p == null ? null : p.getCompletedDate(),
                            status, p == null ? null : p.getRemarks());
                })
                .toList();

        Map<Ref, List<ProgressRow>> bySubject = rows.stream()
                .collect(Collectors.groupingBy(ProgressRow::subject, java.util.LinkedHashMap::new, Collectors.toList()));
        List<SubjectProgress> subjects = bySubject.entrySet().stream()
                .map(entry -> {
                    int total = entry.getValue().size();
                    int done = (int) entry.getValue().stream().filter(r -> r.status() == SyllabusProgress.Status.COMPLETED).count();
                    return new SubjectProgress(entry.getKey(), total, done, percent(done, total));
                })
                .toList();
        int completed = (int) rows.stream().filter(r -> r.status() == SyllabusProgress.Status.COMPLETED).count();
        return new BatchSyllabus(Ref.of(batch.getId(), batch.getName()), percent(completed, rows.size()), subjects, rows);
    }

    @Transactional
    public ProgressRow updateProgress(Long batchId, Long topicId, ProgressRequest request) {
        Batch batch = batchService.getBatch(batchId);
        SyllabusTopic topic = topicRepository.findById(topicId).orElseThrow(() -> ResourceNotFoundException.of("Topic", topicId));
        if (!topic.getCourse().getId().equals(batch.getCourse().getId())) {
            throw new BusinessRuleException("This topic is not part of " + batch.getName() + "'s course");
        }
        dataScopeService.current().requireBatchSubject(batchId, topic.getSubject().getId());
        if (request.status() == SyllabusProgress.Status.COMPLETED && request.completedDate() == null) {
            throw new BusinessRuleException("Enter the date the topic was completed");
        }
        if (request.completedDate() != null && request.completedDate().isAfter(configService.today())) {
            throw new BusinessRuleException("The completion date cannot be in the future");
        }
        SyllabusProgress progress = progressRepository.findByBatchIdAndTopicId(batchId, topicId).orElseGet(SyllabusProgress::new);
        SyllabusProgress.Status previous = progress.getId() == null ? null : progress.getStatus();
        progress.setBatch(batch);
        progress.setTopic(topic);
        progress.setPlannedDate(request.plannedDate());
        progress.setCompletedDate(request.status() == SyllabusProgress.Status.COMPLETED ? request.completedDate() : null);
        progress.setStatus(request.status());
        progress.setRemarks(request.remarks() == null || request.remarks().isBlank() ? null : request.remarks().trim());
        progressRepository.save(progress);
        auditService.record("SyllabusProgress", progress.getId(), AuditService.UPDATE,
                batch.getName() + ": \"" + topic.getTitle() + "\" " + (previous == null ? "" : previous + " -> ") + request.status());
        return new ProgressRow(topic.getId(), Ref.of(topic.getSubject().getId(), topic.getSubject().getName()),
                topic.getTitle(), topic.getSequenceNo(), progress.getPlannedDate(), progress.getCompletedDate(),
                progress.getStatus(), progress.getRemarks());
    }

    private double percent(int part, int total) {
        return total == 0 ? 0d : Math.round(part * 1000d / total) / 10d;
    }
}
