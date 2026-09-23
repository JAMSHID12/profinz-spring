package com.coyotai.education.parentmeeting;

import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.Ref;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.staff.MentorRepository;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.ParentContact;
import com.coyotai.education.student.StudentService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Foundation for recording parent meetings; richer parent communication comes later. */
@Service
public class ParentMeetingService {

    private final ParentMeetingRepository repository;
    private final StudentService studentService;
    private final MentorRepository mentorRepository;
    private final DataScopeService dataScopeService;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public ParentMeetingService(ParentMeetingRepository repository, StudentService studentService,
                                MentorRepository mentorRepository,
                                DataScopeService dataScopeService, ProjectConfigService configService,
                                AuditService auditService) {
        this.repository = repository;
        this.studentService = studentService;
        this.mentorRepository = mentorRepository;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
        this.auditService = auditService;
    }

    public record MeetingRequest(
            @NotNull(message = "Student is required") Long studentId,
            @NotNull(message = "Meeting date is required") LocalDate meetingDate,
            @Size(max = 5000) String discussion,
            @Size(max = 1000) String academicIssues,
            @Size(max = 1000) String attendanceIssues,
            @Size(max = 1000) String disciplineIssues,
            @Size(max = 1000) String actionItems,
            LocalDate followUpDate,
            ParentMeeting.Status status
    ) {
    }

    public record MeetingResponse(Long id, Ref student, com.coyotai.education.student.StudentDtos.ParentSummary parent, Ref mentor, LocalDate meetingDate,
                                  String discussion, String academicIssues, String attendanceIssues,
                                  String disciplineIssues, String actionItems, LocalDate followUpDate,
                                  ParentMeeting.Status status) {

        static MeetingResponse from(ParentMeeting m) {
            return new MeetingResponse(m.getId(), Ref.of(m.getStudent().getId(), m.getStudent().getFullName()),
                    com.coyotai.education.student.StudentDtos.ParentSummary.from(m.getParent()),
                    m.getMentor() == null ? null : Ref.of(m.getMentor().getId(), m.getMentor().getFullName()),
                    m.getMeetingDate(), m.getDiscussion(), m.getAcademicIssues(), m.getAttendanceIssues(),
                    m.getDisciplineIssues(), m.getActionItems(), m.getFollowUpDate(), m.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> search(Long studentId, ParentMeeting.Status status, LocalDate from, LocalDate to) {
        DataScope scope = dataScopeService.current();
        LocalDate start = from == null ? configService.today().minusMonths(3) : from;
        LocalDate end = to == null ? configService.today().plusMonths(3) : to;
        return repository.search(studentId, status, start, end, scope.isGlobal(), scope.batchIdsForQuery())
                .stream().map(MeetingResponse::from).toList();
    }

    @Transactional
    public MeetingResponse save(Long id, MeetingRequest request) {
        Student student = studentService.getDetail(request.studentId());
        dataScopeService.requireStudent(student);
        if (request.followUpDate() != null && request.followUpDate().isBefore(request.meetingDate())) {
            throw new BusinessRuleException("The follow-up date cannot be before the meeting");
        }
        ParentMeeting meeting = id == null ? new ParentMeeting()
                : repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Parent meeting", id));
        meeting.setStudent(student);
        if (id == null || meeting.getParent() == null) meeting.setParent(ParentContact.copyOf(student.getParent()));
        if (meeting.getMentor() == null) {
            AppUserDetails user = CurrentUser.require();
            if (user.getMentorId() != null) {
                meeting.setMentor(mentorRepository.findById(user.getMentorId()).orElse(null));
            } else if (student.getBatch() != null) {
                meeting.setMentor(student.getBatch().getMentor());
            }
        }
        meeting.setMeetingDate(request.meetingDate());
        meeting.setDiscussion(trim(request.discussion()));
        meeting.setAcademicIssues(trim(request.academicIssues()));
        meeting.setAttendanceIssues(trim(request.attendanceIssues()));
        meeting.setDisciplineIssues(trim(request.disciplineIssues()));
        meeting.setActionItems(trim(request.actionItems()));
        meeting.setFollowUpDate(request.followUpDate());
        meeting.setStatus(request.status() == null ? ParentMeeting.Status.SCHEDULED : request.status());
        repository.save(meeting);
        auditService.record("ParentMeeting", meeting.getId(), id == null ? AuditService.CREATE : AuditService.UPDATE,
                "Parent meeting for " + student.getFullName() + " on " + request.meetingDate() + " (" + meeting.getStatus() + ")");
        return MeetingResponse.from(meeting);
    }

    private String trim(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
