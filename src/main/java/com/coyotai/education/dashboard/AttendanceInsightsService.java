package com.coyotai.education.dashboard;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchRepository;
import com.coyotai.education.attendance.AbsenceReason;
import com.coyotai.education.attendance.Attendance;
import com.coyotai.education.attendance.AttendanceDtos.Record;
import com.coyotai.education.attendance.AttendanceRepository;
import com.coyotai.education.attendance.AttendanceStatus;
import com.coyotai.education.attendance.AttendanceSummary;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.Ref;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.dashboard.DashboardDtos.AdminAttendanceDashboard;
import com.coyotai.education.dashboard.DashboardDtos.ClassAttendance;
import com.coyotai.education.dashboard.DashboardDtos.DisciplineTotals;
import com.coyotai.education.dashboard.DashboardDtos.DisciplineWeek;
import com.coyotai.education.dashboard.DashboardDtos.ReasonCount;
import com.coyotai.education.dashboard.DashboardDtos.Risk;
import com.coyotai.education.dashboard.DashboardDtos.StudentAttendanceDetail;
import com.coyotai.education.dashboard.DashboardDtos.StudentDay;
import com.coyotai.education.dashboard.DashboardDtos.StudentRisk;
import com.coyotai.education.dashboard.DashboardDtos.TodaySnapshot;
import com.coyotai.education.dashboard.DashboardDtos.TrendPoint;
import com.coyotai.education.dashboard.DashboardDtos.WeekdayRate;
import com.coyotai.education.discipline.DisciplineRecord;
import com.coyotai.education.discipline.DisciplineRecordRepository;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.coyotai.education.attendance.AttendanceStatus.ABSENT;
import static com.coyotai.education.attendance.AttendanceStatus.EXCUSED;
import static com.coyotai.education.attendance.AttendanceStatus.LATE;
import static com.coyotai.education.attendance.AttendanceStatus.PRESENT;

/**
 * The administrator's attendance and discipline dashboard, and one student's attendance behind
 * it. Administrators see every batch; the filters narrow it to a course, a batch or the batches
 * of one mentor.
 */
@Service
public class AttendanceInsightsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    /** Periods up to this many days are shown per day, longer ones per week. */
    static final int DAILY_UP_TO = 45;
    /** Below this attendance a student is at risk. */
    static final double AT_RISK_BELOW = 75;

    /** Which mark stands for a day with several (whole day plus classes): the most serious one. */
    private static final List<AttendanceStatus> SEVERITY = List.of(ABSENT, EXCUSED, LATE, PRESENT, AttendanceStatus.HOLIDAY);

    // Discipline record kinds, as indexes into per-student and per-day counters.
    private static final int UNIFORM = 0;
    private static final int ID_TAG = 1;
    private static final int OTHER = 2;
    private static final int LATE_COMING = 3;

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final DisciplineRecordRepository disciplineRecordRepository;
    private final DataScopeService dataScopeService;
    private final ProjectConfigService configService;

    public AttendanceInsightsService(AttendanceRepository attendanceRepository, StudentRepository studentRepository,
                                     BatchRepository batchRepository, DisciplineRecordRepository disciplineRecordRepository,
                                     DataScopeService dataScopeService, ProjectConfigService configService) {
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.disciplineRecordRepository = disciplineRecordRepository;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
    }

    @Transactional(readOnly = true)
    public AdminAttendanceDashboard adminDashboard(LocalDate from, LocalDate to, Long courseId, Long batchId,
                                                   Long mentorId, boolean compare) {
        Period period = period(from, to);
        Period previous = period.previous();
        LocalDate today = configService.today();

        List<Batch> batches = batchRepository.search(courseId, null, Batch.Status.ACTIVE, true, List.of(-1L)).stream()
                .filter(batch -> batchId == null || batch.getId().equals(batchId))
                .filter(batch -> mentorId == null || (batch.getMentor() != null && mentorId.equals(batch.getMentor().getId())))
                .toList();
        List<Long> ids = batches.isEmpty() ? List.of(-1L) : batches.stream().map(Batch::getId).toList();
        long activeStudents = batches.isEmpty() ? 0 : studentRepository.countActive(false, ids);

        Map<LocalDate, Map<AttendanceStatus, Long>> days = countByDay(period, ids);
        Map<LocalDate, Map<AttendanceStatus, Long>> previousDays = countByDay(previous, ids);
        AttendanceSummary totals = summaryOf(days.values());
        AttendanceSummary previousTotals = summaryOf(previousDays.values());
        boolean daily = period.days() <= DAILY_UP_TO;

        // Discipline records of the period: per student and per day, by kind.
        Map<Long, long[]> recordsByStudent = new HashMap<>();
        Map<LocalDate, long[]> recordsByDay = new HashMap<>();
        long open = 0;
        for (Object[] row : disciplineRecordRepository.findForDashboard(period.from(), period.to(), ids)) {
            int kind = kindOf((String) row[1]);
            recordsByStudent.computeIfAbsent(((Number) row[0]).longValue(), key -> new long[4])[kind]++;
            recordsByDay.computeIfAbsent((LocalDate) row[2], key -> new long[4])[kind]++;
            if (row[3] == DisciplineRecord.Status.OPEN) {
                open++;
            }
        }
        long[] recordTotals = new long[4];
        recordsByDay.values().forEach(counts -> {
            for (int kind = 0; kind < counts.length; kind++) {
                recordTotals[kind] += counts[kind];
            }
        });

        List<StudentRisk> students = students(period, ids, recordsByStudent);
        return new AdminAttendanceDashboard(period.from(), period.to(), today, courseId, batchId, mentorId,
                activeStudents,
                totals,
                previousTotals.hasData() ? previousTotals : null,
                todaySnapshot(today, ids, activeStudents),
                students.stream().filter(student -> student.summary().hasData() && student.summary().attendancePercentage() < AT_RISK_BELOW).count(),
                daily ? "DAY" : "WEEK",
                trend(period, days, daily),
                compare ? trend(previous, previousDays, daily) : List.of(),
                classes(period, batches, ids),
                new DisciplineTotals(totals.late() + recordTotals[LATE_COMING], recordTotals[UNIFORM], recordTotals[ID_TAG],
                        recordTotals[OTHER], open),
                disciplineWeeks(period, days, recordsByDay),
                absenceReasons(period, ids),
                weekdays(days),
                students);
    }

    /** Students with marks in the period, most serious risk first and then the lowest attendance. */
    private List<StudentRisk> students(Period period, Collection<Long> ids, Map<Long, long[]> recordsByStudent) {
        Map<Long, Object[]> info = new LinkedHashMap<>();
        Map<Long, Map<AttendanceStatus, Long>> counts = new HashMap<>();
        for (Object[] row : attendanceRepository.countByStudentAndStatus(period.from(), period.to(), null, false, ids)) {
            Long id = ((Number) row[0]).longValue();
            info.putIfAbsent(id, row);
            counts.computeIfAbsent(id, key -> new EnumMap<>(AttendanceStatus.class))
                    .merge((AttendanceStatus) row[6], ((Number) row[7]).longValue(), Long::sum);
        }
        return info.entrySet().stream()
                .map(entry -> {
                    Object[] row = entry.getValue();
                    AttendanceSummary summary = AttendanceSummary.fromCounts(counts.get(entry.getKey()));
                    long[] records = recordsByStudent.getOrDefault(entry.getKey(), new long[4]);
                    long late = summary.late() + records[LATE_COMING];
                    long issues = records[UNIFORM] + records[ID_TAG] + records[OTHER];
                    Ref batch = row[4] == null ? null : Ref.of(((Number) row[4]).longValue(), (String) row[5]);
                    return new StudentRisk(entry.getKey(), (String) row[1], (String) row[2], (String) row[3], batch, summary,
                            summary.absent() + summary.excused(), late, records[UNIFORM], records[ID_TAG], records[OTHER],
                            issues, riskOf(summary, late, issues));
                })
                .sorted(Comparator.comparing(StudentRisk::risk)
                        .thenComparingDouble(student -> student.summary().attendancePercentage())
                        .thenComparing(StudentRisk::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Critical: attendance under 65%, or under 75% with 3 or more discipline issues. At risk: under
     * 75%, 4 or more issues, or late for a quarter of the classes. Monitor: under 85%, 2 or more
     * issues, or late for one class in eight.
     */
    static Risk riskOf(AttendanceSummary summary, long late, long issues) {
        double attendance = summary.hasData() ? summary.attendancePercentage() : 100d;
        double lateShare = summary.totalClasses() == 0 ? 0 : late * 100d / summary.totalClasses();
        if (attendance < 65 || (attendance < AT_RISK_BELOW && issues >= 3)) {
            return Risk.CRITICAL;
        }
        if (attendance < AT_RISK_BELOW || issues >= 4 || lateShare >= 25) {
            return Risk.AT_RISK;
        }
        if (attendance < 85 || issues >= 2 || lateShare >= 12.5) {
            return Risk.MONITOR;
        }
        return Risk.OK;
    }

    /** Batches with marks in the period, lowest attendance first. */
    private List<ClassAttendance> classes(Period period, List<Batch> batches, Collection<Long> ids) {
        Map<Long, Long> studentCounts = new HashMap<>();
        studentRepository.countActiveByBatch()
                .forEach(row -> studentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        Map<Long, Map<AttendanceStatus, Long>> byBatch = new HashMap<>();
        for (Object[] row : attendanceRepository.countByBatchAndStatus(period.from(), period.to(), null, false, ids)) {
            byBatch.computeIfAbsent(((Number) row[0]).longValue(), key -> new EnumMap<>(AttendanceStatus.class))
                    .merge((AttendanceStatus) row[2], ((Number) row[3]).longValue(), Long::sum);
        }
        return batches.stream()
                .filter(batch -> byBatch.containsKey(batch.getId()))
                .map(batch -> new ClassAttendance(Ref.of(batch.getId(), batch.getName()),
                        Ref.of(batch.getCourse().getId(), batch.getCourse().getName()),
                        batch.getMentor() == null ? null : Ref.of(batch.getMentor().getId(), batch.getMentor().getFullName()),
                        studentCounts.getOrDefault(batch.getId(), 0L),
                        AttendanceSummary.fromCounts(byBatch.get(batch.getId()))))
                .sorted(Comparator.comparingDouble((ClassAttendance row) -> row.summary().attendancePercentage())
                        .thenComparing(row -> row.batch().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Each student counted once, by their most serious mark today. */
    private TodaySnapshot todaySnapshot(LocalDate today, Collection<Long> ids, long activeStudents) {
        Map<Long, AttendanceStatus> worst = new HashMap<>();
        Map<Long, AbsenceReason> reasons = new HashMap<>();
        for (Object[] row : attendanceRepository.findDayMarks(today, ids)) {
            Long studentId = ((Number) row[0]).longValue();
            AttendanceStatus status = (AttendanceStatus) row[1];
            AttendanceStatus current = worst.get(studentId);
            if (current == null || SEVERITY.indexOf(status) < SEVERITY.indexOf(current)) {
                worst.put(studentId, status);
                reasons.put(studentId, (AbsenceReason) row[2]);
            }
        }
        long present = worst.values().stream().filter(status -> status == PRESENT || status == LATE).count();
        long late = worst.values().stream().filter(status -> status == LATE).count();
        long absentWithoutReason = worst.entrySet().stream()
                .filter(entry -> entry.getValue() == ABSENT && reasons.get(entry.getKey()) != AbsenceReason.INFORMED).count();
        return new TodaySnapshot(worst.size(), present, late, worst.values().stream().filter(AttendanceStatus::isAway).count(), absentWithoutReason,
                Math.max(0, activeStudents - worst.size()));
    }

    /** Away marks grouped by whether the centre was informed. */
    private List<ReasonCount> absenceReasons(Period period, Collection<Long> ids) {
        long informed = 0;
        long notInformed = 0;
        for (Object[] row : attendanceRepository.countAwayByReason(period.from(), period.to(), ids)) {
            long count = ((Number) row[2]).longValue();
            if (row[1] == AbsenceReason.INFORMED) informed += count;
            else notInformed += count;
        }
        return List.of(new ReasonCount("INFORMED", "Informed", informed),
                new ReasonCount("NOT_INFORMED", "Not informed", notInformed));
    }

    private static List<WeekdayRate> weekdays(Map<LocalDate, Map<AttendanceStatus, Long>> days) {
        Map<DayOfWeek, long[]> totals = new EnumMap<>(DayOfWeek.class);
        days.forEach((day, counts) -> {
            long[] total = totals.computeIfAbsent(day.getDayOfWeek(), key -> new long[2]);
            total[0] += AttendanceSummary.fromCounts(counts).totalClasses();
            total[1] += count(counts, PRESENT) + count(counts, LATE);
        });
        return Arrays.stream(DayOfWeek.values())
                .map(day -> {
                    long[] total = totals.getOrDefault(day, new long[2]);
                    return new WeekdayRate(day, total[0], total[1]);
                })
                .toList();
    }

    private static List<TrendPoint> trend(Period period, Map<LocalDate, Map<AttendanceStatus, Long>> days, boolean daily) {
        List<Period> buckets = period.buckets(daily ? 1 : 7);
        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            Period bucket = buckets.get(i);
            Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
            for (LocalDate day = bucket.from(); !day.isAfter(bucket.to()); day = day.plusDays(1)) {
                days.getOrDefault(day, Map.of()).forEach((status, count) -> counts.merge(status, count, Long::sum));
            }
            points.add(new TrendPoint(bucket.from(), bucket.to(), daily ? bucket.from().toString() : "W" + (i + 1),
                    count(counts, PRESENT), count(counts, LATE), count(counts, ABSENT), count(counts, EXCUSED)));
        }
        return points;
    }

    private static List<DisciplineWeek> disciplineWeeks(Period period, Map<LocalDate, Map<AttendanceStatus, Long>> days,
                                                        Map<LocalDate, long[]> recordsByDay) {
        List<Period> weeks = period.buckets(7);
        List<DisciplineWeek> result = new ArrayList<>();
        for (int i = 0; i < weeks.size(); i++) {
            Period week = weeks.get(i);
            long late = 0;
            long[] records = new long[4];
            for (LocalDate day = week.from(); !day.isAfter(week.to()); day = day.plusDays(1)) {
                late += count(days.getOrDefault(day, Map.of()), LATE);
                long[] counts = recordsByDay.getOrDefault(day, new long[4]);
                for (int kind = 0; kind < counts.length; kind++) {
                    records[kind] += counts[kind];
                }
            }
            result.add(new DisciplineWeek(week.from(), week.to(), "W" + (i + 1), late + records[LATE_COMING],
                    records[UNIFORM], records[ID_TAG], records[OTHER]));
        }
        return result;
    }

    private Map<LocalDate, Map<AttendanceStatus, Long>> countByDay(Period period, Collection<Long> ids) {
        Map<LocalDate, Map<AttendanceStatus, Long>> days = new TreeMap<>();
        for (Object[] row : attendanceRepository.countByDayAndStatus(period.from(), period.to(), null, false, ids)) {
            days.computeIfAbsent((LocalDate) row[0], day -> new EnumMap<>(AttendanceStatus.class))
                    .merge((AttendanceStatus) row[1], ((Number) row[2]).longValue(), Long::sum);
        }
        return days;
    }

    private static AttendanceSummary summaryOf(Collection<Map<AttendanceStatus, Long>> days) {
        Map<AttendanceStatus, Long> total = new EnumMap<>(AttendanceStatus.class);
        days.forEach(day -> day.forEach((status, count) -> total.merge(status, count, Long::sum)));
        return AttendanceSummary.fromCounts(total);
    }

    /** Uniform and ID-tag records by type; late coming is counted with the late marks; everything else is other. */
    private static int kindOf(String typeCode) {
        return switch (typeCode == null ? "" : typeCode.toUpperCase(Locale.ROOT)) {
            case "UNIFORM" -> UNIFORM;
            case "NAME_BADGE" -> ID_TAG;
            case "LATE_COMING" -> LATE_COMING;
            default -> OTHER;
        };
    }

    // ---- One student ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public StudentAttendanceDetail student(Long studentId, LocalDate from, LocalDate to) {
        Period period = period(from, to);
        Student student = studentRepository.findDetail(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        dataScopeService.requireStudent(student);
        DataScope scope = dataScopeService.current();

        List<Attendance> marks = attendanceRepository.findForStudent(studentId, period.from(), period.to()).stream()
                .filter(mark -> scope.isGlobal() || scope.canAccessBatch(mark.getBatch().getId()))
                .toList();

        Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
        Map<LocalDate, List<AttendanceStatus>> byDay = new TreeMap<>();
        for (Attendance mark : marks) {
            counts.merge(mark.getStatus(), 1L, Long::sum);
            byDay.computeIfAbsent(mark.getAttendanceDate(), day -> new ArrayList<>()).add(mark.getStatus());
        }
        List<StudentDay> days = byDay.entrySet().stream()
                .map(entry -> new StudentDay(entry.getKey(),
                        entry.getValue().stream().min(Comparator.comparingInt(SEVERITY::indexOf)).orElseThrow(),
                        entry.getValue().size()))
                .toList();
        List<Record> records = marks.stream()
                .sorted(Comparator.comparing(Attendance::getAttendanceDate).reversed())
                .map(Record::from)
                .toList();

        Ref batch = student.getBatch() == null ? null : Ref.of(student.getBatch().getId(), student.getBatch().getName());
        return new StudentAttendanceDetail(student.getId(), student.getFullName(), student.getAdmissionNumber(),
                student.getPhotoUrl(), batch, period.from(), period.to(), AttendanceSummary.fromCounts(counts), days,
                records);
    }

    /** The last 30 days by default; never past today and never longer than a year. */
    private Period period(LocalDate from, LocalDate to) {
        LocalDate today = configService.today();
        LocalDate end = to == null || to.isAfter(today) ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("The start date must not be after the end date");
        }
        if (start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new BusinessRuleException("Choose a period of at most " + MAX_DAYS + " days");
        }
        return new Period(start, end);
    }

    private static long count(Map<AttendanceStatus, Long> counts, AttendanceStatus status) {
        return counts.getOrDefault(status, 0L);
    }

    private record Period(LocalDate from, LocalDate to) {

        long days() {
            return ChronoUnit.DAYS.between(from, to) + 1;
        }

        /** The period of the same length just before this one. */
        Period previous() {
            LocalDate end = from.minusDays(1);
            return new Period(end.minusDays(days() - 1), end);
        }

        /** Consecutive pieces of {@code size} days from the start; the last one may be shorter. */
        List<Period> buckets(int size) {
            List<Period> buckets = new ArrayList<>();
            for (LocalDate start = from; !start.isAfter(to); start = start.plusDays(size)) {
                LocalDate end = start.plusDays(size - 1L);
                buckets.add(new Period(start, end.isAfter(to) ? to : end));
            }
            return buckets;
        }
    }
}
