package com.coyotai.education.schedule;

import com.coyotai.education.schedule.ScheduleDtos.Conflict;
import com.coyotai.education.schedule.ScheduleDtos.ConflictType;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure scheduling rules, kept free of the database so they are easy to test:
 * a faculty member, a batch and a room can each be in only one class at a time.
 */
public final class ScheduleConflictChecker {

    private ScheduleConflictChecker() {
    }

    /** Half-open intervals: a class ending at 10:00 does not clash with one starting at 10:00. */
    public static boolean overlaps(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    public static List<Conflict> find(LocalDate date, LocalTime start, LocalTime end, Long facultyId, Long batchId,
                                      String room, Long excludeId, List<ClassSchedule> sameDay) {
        List<Conflict> conflicts = new ArrayList<>();
        for (ClassSchedule existing : sameDay) {
            if (existing.getStatus() == ClassSchedule.Status.CANCELLED
                    || Objects.equals(existing.getId(), excludeId)
                    || !existing.getScheduleDate().equals(date)
                    || !overlaps(start, end, existing.getStartTime(), existing.getEndTime())) {
                continue;
            }
            String window = existing.getStartTime() + "-" + existing.getEndTime();
            if (facultyId != null && existing.getFaculty() != null && facultyId.equals(existing.getFaculty().getId())) {
                conflicts.add(new Conflict(ConflictType.FACULTY, date,
                        existing.getFaculty().getFullName() + " already teaches " + existing.getBatch().getName()
                                + " " + window + " on " + date, ScheduleResponse.from(existing)));
            }
            if (batchId != null && batchId.equals(existing.getBatch().getId())) {
                conflicts.add(new Conflict(ConflictType.BATCH, date,
                        existing.getBatch().getName() + " already has " + existing.getSubject().getName()
                                + " " + window + " on " + date, ScheduleResponse.from(existing)));
            }
            if (room != null && !room.isBlank() && room.equalsIgnoreCase(existing.getRoom())) {
                conflicts.add(new Conflict(ConflictType.ROOM, date,
                        "Room " + room + " is booked " + window + " on " + date, ScheduleResponse.from(existing)));
            }
        }
        return conflicts;
    }
}
