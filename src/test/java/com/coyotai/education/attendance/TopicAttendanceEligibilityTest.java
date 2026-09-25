package com.coyotai.education.attendance;

import com.coyotai.education.academic.*;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.auth.UserRepository;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.discipline.*;
import com.coyotai.education.notification.*;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.schedule.*;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.student.*;
import com.coyotai.education.syllabus.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TopicAttendanceEligibilityTest {
    private final AttendanceRepository repository = mock(AttendanceRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final BatchService batches = mock(BatchService.class);
    private final ScheduleService schedules = mock(ScheduleService.class);
    private final AttendanceAccess access = mock(AttendanceAccess.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ProjectConfigService config = mock(ProjectConfigService.class);
    private final ClassScheduleRepository scheduleRepository = mock(ClassScheduleRepository.class);
    private final AttendanceService service = new AttendanceService(repository, access, students, batches,
            mock(BatchRepository.class), schedules, scheduleRepository, mock(DataScopeService.class),
            mock(DisciplineRecordRepository.class), mock(DisciplineTypeRepository.class), mock(StudentFineRepository.class),
            mock(UserRepository.class), notifications, mock(NotificationMessageFactory.class), config, mock(AuditService.class));
    private final LocalDate date = LocalDate.of(2026, 9, 24);
    private final Batch batch = new Batch();
    private final ClassSchedule schedule = new ClassSchedule();
    private final SyllabusTopic topic = new SyllabusTopic();
    private final Student plusTwo = student(1L, Student.EducationCategory.PLUS_TWO);
    private final Student degree = student(2L, Student.EducationCategory.DEGREE);

    private static Student student(long id, Student.EducationCategory category) {
        Student s = new Student(); s.setId(id); s.setFullName("Student " + id); s.setEducationCategory(category); return s;
    }
    @BeforeEach void setup() {
        batch.setId(10L); batch.setName("CMA");
        Course course = new Course(); course.setId(20L); course.setName("CMA USA"); batch.setCourse(course);
        Subject subject = new Subject(); subject.setId(30L); subject.setName("Accounts");
        schedule.setId(40L); schedule.setBatch(batch); schedule.setCourse(course); schedule.setSubject(subject); schedule.setScheduleDate(date);
        topic.setId(50L); topic.setTitle("Degree foundations"); topic.setEligibility(TopicEligibility.PLUS_TWO_ONLY); schedule.setTopic(topic);
        when(repository.save(any())).thenAnswer(call -> {
            Attendance mark = call.getArgument(0); if (mark.getId() == null) mark.setId(100L + mark.getStudent().getId()); return mark;
        });
        when(config.today()).thenReturn(date);
        when(batches.getBatch(10L)).thenReturn(batch);
        when(schedules.get(40L)).thenReturn(schedule);
        when(students.findActiveByBatchId(10L)).thenReturn(List.of(plusTwo, degree));
        when(access.canTake(batch, schedule)).thenReturn(true);
    }
    private AttendanceDtos.BulkRequest request(List<AttendanceDtos.BulkRequest.Entry> entries) {
        return new AttendanceDtos.BulkRequest(10L, date, 40L, entries);
    }
    private AttendanceDtos.BulkRequest.Entry mark(long id, AttendanceStatus status) {
        return new AttendanceDtos.BulkRequest.Entry(id, status, null, null, false, false, null);
    }
    private List<Attendance> saved() {
        var captor = ArgumentCaptor.forClass(Attendance.class);
        verify(repository, times(2)).save(captor.capture()); return captor.getAllValues();
    }
    @Test void sheetOnlyContainsEligibleStudents() {
        var sheet = service.sheet(10L, date, 40L);
        assertThat(sheet.rows()).extracting(AttendanceDtos.SheetRow::studentId).containsExactly(1L);
        assertThat(sheet.holidays()).extracting(com.coyotai.education.common.Ref::id).containsExactly(2L);
    }
    @Test void omittedExemptStudentAutomaticallyGetsHolidayWithoutNotifications() {
        assertThat(service.saveBulk(request(List.of(mark(1, AttendanceStatus.PRESENT)))).saved()).isEqualTo(2);
        var marks = saved();
        assertThat(marks).extracting(Attendance::getStatus).containsExactly(AttendanceStatus.PRESENT, AttendanceStatus.HOLIDAY);
        assertThat(marks.get(1).getAbsenceReason()).isNull();
        assertThat(marks.get(1).getNotificationStatus()).isEqualTo(Attendance.NotificationDecision.NOT_REQUIRED);
        verifyNoInteractions(notifications);
    }
    @Test void forgedAbsentForExemptStudentIsOverridden() {
        service.saveBulk(request(List.of(mark(1, AttendanceStatus.PRESENT), mark(2, AttendanceStatus.ABSENT))));
        assertThat(saved().get(1).getStatus()).isEqualTo(AttendanceStatus.HOLIDAY);
        verifyNoInteractions(notifications);
    }
    @Test void degreeOnlyReversesEligibility() {
        topic.setEligibility(TopicEligibility.DEGREE_ONLY);
        service.saveBulk(request(List.of(mark(2, AttendanceStatus.PRESENT))));
        assertThat(saved()).filteredOn(a -> a.getStudent().getId().equals(1L))
                .extracting(Attendance::getStatus).containsExactly(AttendanceStatus.HOLIDAY);
    }
    @Test void bothCategoriesAttend() {
        topic.setEligibility(TopicEligibility.BOTH);
        service.saveBulk(request(List.of(mark(1, AttendanceStatus.PRESENT), mark(2, AttendanceStatus.PRESENT))));
        assertThat(saved()).extracting(Attendance::getStatus).containsOnly(AttendanceStatus.PRESENT);
    }
    @Test void entireBatchCanBeExempt() {
        plusTwo.setEducationCategory(Student.EducationCategory.DEGREE);
        service.saveBulk(request(List.of()));
        assertThat(saved()).extracting(Attendance::getStatus).containsOnly(AttendanceStatus.HOLIDAY);
    }
    @Test void unknownCategoryBlocksRestrictedAttendanceWithoutSaving() {
        degree.setEducationCategory(null);
        assertThatThrownBy(() -> service.saveBulk(request(List.of(mark(1, AttendanceStatus.PRESENT)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("education category");
        verify(repository, never()).save(any());
    }
    @Test void missingEligibleStudentIsRejected() {
        assertThatThrownBy(() -> service.saveBulk(request(List.of())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("every eligible student");
    }
    @Test void clientsCannotAssignHolidayToEligibleStudent() {
        assertThatThrownBy(() -> service.saveBulk(request(List.of(mark(1, AttendanceStatus.HOLIDAY)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("automatically");
    }
    @Test void savedHolidaySurvivesCategoryAndTopicChangesAndCannotBeCorrected() {
        Attendance holiday = new Attendance(); holiday.setId(60L); holiday.setStudent(degree); holiday.setBatch(batch);
        holiday.setClassSchedule(schedule); holiday.setStatus(AttendanceStatus.HOLIDAY); holiday.setMarkedAt(Instant.now());
        when(repository.findSheet(10L, date, 40L)).thenReturn(List.of(holiday));
        topic.setEligibility(TopicEligibility.BOTH);
        degree.setEducationCategory(Student.EducationCategory.PLUS_TWO);
        assertThat(service.sheet(10L, date, 40L).holidays()).hasSize(1);
        when(repository.findById(60L)).thenReturn(Optional.of(holiday));
        assertThatThrownBy(() -> service.update(60L, new AttendanceDtos.UpdateRequest(AttendanceStatus.ABSENT, null, null, false, false, null)))
                .isInstanceOf(BusinessRuleException.class);
    }
    @Test void holidaysDoNotReducePercentageOrCountAsAbsence() {
        var summary = AttendanceSummary.fromCounts(Map.of(AttendanceStatus.PRESENT, 1L, AttendanceStatus.HOLIDAY, 9L));
        assertThat(summary.totalClasses()).isEqualTo(1);
        assertThat(summary.attendancePercentage()).isEqualTo(100);
        assertThat(AttendanceStatus.HOLIDAY.isAway()).isFalse();
    }
    @Test void customMasterCategoryControlsAttendanceByIdEvenAfterRenameOrDeactivation() {
        var diploma = new com.coyotai.education.student.EducationCategory();
        diploma.setId(70L); diploma.setCode("DIPLOMA"); diploma.setName("Diploma Completed");
        var other = new com.coyotai.education.student.EducationCategory(); other.setId(71L);
        topic.setEligibility(TopicEligibility.CATEGORY_ONLY); topic.setEducationCategory(diploma);
        plusTwo.setEducationCategoryMaster(diploma); degree.setEducationCategoryMaster(other);
        diploma.setName("Renamed diploma"); diploma.setActive(false);
        service.saveBulk(request(List.of(mark(1, AttendanceStatus.PRESENT))));
        assertThat(saved()).extracting(Attendance::getStatus).containsExactly(AttendanceStatus.PRESENT, AttendanceStatus.HOLIDAY);
    }
    @Test void masterTopicRejectsStudentWithoutMasterCategory() {
        var category = new com.coyotai.education.student.EducationCategory(); category.setId(70L);
        topic.setEligibility(TopicEligibility.CATEGORY_ONLY); topic.setEducationCategory(category);
        assertThatThrownBy(() -> service.sheet(10L, date, 40L)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void wholeDayAndUnlinkedClassesStillIncludeEveryone() {
        schedule.setTopic(null); degree.setEducationCategory(null);
        assertThat(service.sheet(10L, date, 40L).rows()).hasSize(2);
        when(access.canTake(batch, null)).thenReturn(true);
        assertThat(service.sheet(10L, date, null).rows()).hasSize(2);
    }
}
