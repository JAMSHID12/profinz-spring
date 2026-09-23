package com.coyotai.education.schedule;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.Course;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.schedule.ScheduleDtos.Conflict;
import com.coyotai.education.schedule.ScheduleDtos.ConflictType;
import com.coyotai.education.staff.Faculty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleConflictCheckerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 14);

    private final Course course = course();
    private final Batch batchA = batch(1L, "CA Inter A");
    private final Faculty facultyA = faculty(1L, "Faculty A");

    /** The specification example: Faculty A teaches Batch A 10:00-12:00. */
    private ClassSchedule existing() {
        ClassSchedule schedule = new ClassSchedule();
        schedule.setId(100L);
        schedule.setBatch(batchA);
        schedule.setCourse(course);
        Subject subject = new Subject();
        subject.setId(1L);
        subject.setName("Accounts");
        subject.setCourse(course);
        schedule.setSubject(subject);
        schedule.setFaculty(facultyA);
        schedule.setScheduleDate(DAY);
        schedule.setStartTime(LocalTime.of(10, 0));
        schedule.setEndTime(LocalTime.of(12, 0));
        schedule.setRoom("Room 1");
        schedule.setStatus(ClassSchedule.Status.SCHEDULED);
        return schedule;
    }

    @Test
    @DisplayName("Faculty A 11:00-13:00 with Batch B clashes with Faculty A 10:00-12:00 with Batch A")
    void facultyDoubleBooked() {
        List<Conflict> conflicts = ScheduleConflictChecker.find(DAY, LocalTime.of(11, 0), LocalTime.of(13, 0),
                1L, 2L, null, null, List.of(existing()));
        assertThat(conflicts).extracting(Conflict::type).containsExactly(ConflictType.FACULTY);
    }

    @Test
    @DisplayName("The same batch cannot have two simultaneous classes")
    void batchDoubleBooked() {
        List<Conflict> conflicts = ScheduleConflictChecker.find(DAY, LocalTime.of(11, 30), LocalTime.of(12, 30),
                2L, 1L, null, null, List.of(existing()));
        assertThat(conflicts).extracting(Conflict::type).containsExactly(ConflictType.BATCH);
    }

    @Test
    @DisplayName("A room can only host one class at a time")
    void roomDoubleBooked() {
        List<Conflict> conflicts = ScheduleConflictChecker.find(DAY, LocalTime.of(9, 0), LocalTime.of(10, 30),
                2L, 2L, "room 1", null, List.of(existing()));
        assertThat(conflicts).extracting(Conflict::type).containsExactly(ConflictType.ROOM);
    }

    @Test
    @DisplayName("Back-to-back classes do not clash")
    void backToBack() {
        assertThat(ScheduleConflictChecker.find(DAY, LocalTime.of(12, 0), LocalTime.of(13, 0),
                1L, 1L, "Room 1", null, List.of(existing()))).isEmpty();
    }

    @Test
    @DisplayName("Cancelled classes and the class being edited are ignored")
    void cancelledAndSelfIgnored() {
        ClassSchedule cancelled = existing();
        cancelled.setStatus(ClassSchedule.Status.CANCELLED);
        assertThat(ScheduleConflictChecker.find(DAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                1L, 1L, null, null, List.of(cancelled))).isEmpty();
        assertThat(ScheduleConflictChecker.find(DAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                1L, 1L, null, 100L, List.of(existing()))).isEmpty();
    }

    private static Course course() {
        Course course = new Course();
        course.setId(1L);
        course.setName("CA");
        return course;
    }

    private Batch batch(Long id, String name) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setName(name);
        batch.setCourse(course);
        return batch;
    }

    private Faculty faculty(Long id, String name) {
        Faculty faculty = new Faculty();
        faculty.setId(id);
        faculty.setFullName(name);
        return faculty;
    }
}
