package com.coyotai.education.attendance;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.common.ForbiddenException;
import com.coyotai.education.platform.RoleCode;
import com.coyotai.education.schedule.ClassSchedule;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Who may take attendance. Only mentors and faculty do, and only for their own classes:
 * <ul>
 *   <li>a mentor, for the batches they mentor - the whole day or any class of that batch;</li>
 *   <li>faculty, for the classes they teach.</li>
 * </ul>
 * Administrators and academic staff can view attendance but never mark it. This is checked in
 * code on every change, so granting the permission to another role would not open it up.
 */
@Component
public class AttendanceAccess {

    public boolean canTake(Batch batch, ClassSchedule schedule) {
        return canTake(CurrentUser.require(), batch, schedule);
    }

    static boolean canTake(AppUserDetails user, Batch batch, ClassSchedule schedule) {
        if (isMentorOf(user, batch)) {
            return true;
        }
        return schedule != null && user.hasRole(RoleCode.FACULTY) && user.getFacultyId() != null
                && schedule.getFaculty() != null && Objects.equals(schedule.getFaculty().getId(), user.getFacultyId());
    }

    public void requireCanTake(Batch batch, ClassSchedule schedule) {
        AppUserDetails user = CurrentUser.require();
        if (RoleCode.ATTENDANCE_TAKERS.stream().noneMatch(user::hasRole)) {
            throw new ForbiddenException("Attendance is taken by mentors and faculty only");
        }
        if (canTake(user, batch, schedule)) {
            return;
        }
        if (schedule == null && user.hasRole(RoleCode.FACULTY) && !user.hasRole(RoleCode.MENTORS)) {
            throw new ForbiddenException("Faculty take attendance for the classes they teach - choose your class");
        }
        throw new ForbiddenException("You can take attendance only for the batches you mentor or the classes you teach");
    }

    private static boolean isMentorOf(AppUserDetails user, Batch batch) {
        return user.hasRole(RoleCode.MENTORS) && user.getMentorId() != null && batch.getMentor() != null
                && Objects.equals(batch.getMentor().getId(), user.getMentorId());
    }
}
