package com.coyotai.education.performance;

import com.coyotai.education.assessment.AcademicTest;
import com.coyotai.education.assessment.AcademicTestResult;
import com.coyotai.education.assessment.AcademicTestResultRepository;
import com.coyotai.education.assessment.ExamResult;
import com.coyotai.education.assessment.ExamResultRepository;
import com.coyotai.education.assessment.MarksValidator;
import com.coyotai.education.attendance.AttendanceService;
import com.coyotai.education.attendance.AttendanceSummary;
import com.coyotai.education.common.Ref;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.ProjectProperties;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentRepository;
import com.coyotai.education.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Performance from published tests and exams plus attendance. Only published marks count. */
@Service
public class PerformanceService {

    private final AcademicTestResultRepository testResultRepository;
    private final ExamResultRepository examResultRepository;
    private final AttendanceService attendanceService;
    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final DataScopeService dataScopeService;
    private final GradeScale gradeScale;
    private final ProjectConfigService configService;

    public PerformanceService(AcademicTestResultRepository testResultRepository, ExamResultRepository examResultRepository,
                              AttendanceService attendanceService, StudentService studentService,
                              StudentRepository studentRepository, DataScopeService dataScopeService,
                              GradeScale gradeScale, ProjectConfigService configService) {
        this.testResultRepository = testResultRepository;
        this.examResultRepository = examResultRepository;
        this.attendanceService = attendanceService;
        this.studentService = studentService;
        this.studentRepository = studentRepository;
        this.dataScopeService = dataScopeService;
        this.gradeScale = gradeScale;
        this.configService = configService;
    }

    public record Assessment(LocalDate date, String kind, String title, Ref subject, BigDecimal marks,
                             BigDecimal maxMarks, boolean absent, Double percentage, String grade) {
    }

    public record SubjectPerformance(Ref subject, Double testAverage, Double examAverage, Double overall,
                                     String grade, int assessments) {
    }

    public record StudentPerformance(Ref student, LocalDate from, LocalDate to, Double dailyTestAverage,
                                     Double weeklyTestAverage, Double examAverage, Double attendancePercentage,
                                     AttendanceSummary attendance, Double overall, String grade,
                                     ProjectProperties.Weights weights, List<SubjectPerformance> subjects,
                                     List<Assessment> trend) {
    }

    public record BatchRow(Ref student, String admissionNumber, Double overall, String grade,
                           Double attendancePercentage, Double examAverage) {
    }

    @Transactional(readOnly = true)
    public StudentPerformance forStudentAsStaff(Long studentId, LocalDate from, LocalDate to) {
        Student student = studentService.getDetail(studentId);
        dataScopeService.requireStudent(student);
        return compute(student, from, to);
    }

    @Transactional(readOnly = true)
    public List<BatchRow> forBatch(Long batchId, LocalDate from, LocalDate to) {
        dataScopeService.current().requireBatch(batchId);
        return studentRepository.findActiveByBatchId(batchId).stream()
                .map(student -> {
                    StudentPerformance p = compute(student, from, to);
                    return new BatchRow(p.student(), student.getAdmissionNumber(), p.overall(), p.grade(),
                            p.attendancePercentage(), p.examAverage());
                })
                .sorted(Comparator.comparing(BatchRow::overall, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** No scope check - callers are responsible (the student portal passes the signed-in student). */
    @Transactional(readOnly = true)
    public StudentPerformance compute(Student student, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? defaultStart(student, end) : from;

        List<Assessment> assessments = new ArrayList<>();
        List<Double> daily = new ArrayList<>();
        List<Double> weekly = new ArrayList<>();
        for (AcademicTestResult result : testResultRepository.findPublishedForStudent(student.getId(), null, start, end)) {
            AcademicTest test = result.getTest();
            Double pct = result.isAbsent() ? 0d : MarksValidator.percentage(result.getMarksObtained(), test.getMaxMarks());
            (test.getTestType() == AcademicTest.Type.DAILY ? daily : weekly).add(pct);
            assessments.add(new Assessment(test.getTestDate(),
                    test.getTestType() == AcademicTest.Type.DAILY ? "Daily test" : "Weekly test", test.getTitle(),
                    Ref.of(test.getSubject().getId(), test.getSubject().getName()), result.getMarksObtained(),
                    test.getMaxMarks(), result.isAbsent(), pct, gradeScale.gradeFor(pct)));
        }
        List<Double> exams = new ArrayList<>();
        for (ExamResult result : examResultRepository.findPublishedForStudent(student.getId(), start, end)) {
            var exam = result.getExam();
            Double pct = result.isAbsent() ? 0d : MarksValidator.percentage(result.getMarksObtained(), exam.getMaxMarks());
            exams.add(pct);
            assessments.add(new Assessment(exam.getExamDate(), exam.getExamType().getName(), exam.getName(),
                    Ref.of(exam.getSubject().getId(), exam.getSubject().getName()), result.getMarksObtained(),
                    exam.getMaxMarks(), result.isAbsent(), pct, gradeScale.gradeFor(pct)));
        }

        AttendanceSummary attendance = attendanceService.summaryFor(student.getId(), start, end);
        Double attendancePct = attendance.hasData() ? attendance.attendancePercentage() : null;
        Double dailyAvg = PerformanceCalculator.average(daily);
        Double weeklyAvg = PerformanceCalculator.average(weekly);
        Double examAvg = PerformanceCalculator.average(exams);
        ProjectProperties.Weights weights = configService.academics().getPerformance().getWeights();
        Double overall = PerformanceCalculator.overall(dailyAvg, weeklyAvg, examAvg, attendancePct, weights);

        return new StudentPerformance(Ref.of(student.getId(), student.getFullName()), start, end, dailyAvg, weeklyAvg,
                examAvg, attendancePct, attendance, overall, gradeScale.gradeFor(overall), weights,
                bySubject(assessments), trend(assessments));
    }

    private List<SubjectPerformance> bySubject(List<Assessment> assessments) {
        Map<Ref, List<Assessment>> grouped = new LinkedHashMap<>();
        assessments.stream()
                .sorted(Comparator.comparing(a -> a.subject().name()))
                .forEach(a -> grouped.computeIfAbsent(a.subject(), key -> new ArrayList<>()).add(a));
        List<SubjectPerformance> result = new ArrayList<>();
        grouped.forEach((subject, items) -> {
            Double tests = PerformanceCalculator.average(items.stream()
                    .filter(a -> a.kind().endsWith("test")).map(Assessment::percentage).toList());
            Double exams = PerformanceCalculator.average(items.stream()
                    .filter(a -> !a.kind().endsWith("test")).map(Assessment::percentage).toList());
            Double overall = PerformanceCalculator.average(items.stream().map(Assessment::percentage).toList());
            result.add(new SubjectPerformance(subject, tests, exams, overall, gradeScale.gradeFor(overall), items.size()));
        });
        return result;
    }

    /** The most recent assessments, oldest first, for a trend line. */
    private List<Assessment> trend(List<Assessment> assessments) {
        List<Assessment> sorted = assessments.stream().sorted(Comparator.comparing(Assessment::date)).toList();
        return sorted.size() <= 15 ? sorted : sorted.subList(sorted.size() - 15, sorted.size());
    }

    private LocalDate defaultStart(Student student, LocalDate end) {
        if (student.getAcademicYear() != null && student.getAcademicYear().getStartDate().isBefore(end)) {
            return student.getAcademicYear().getStartDate();
        }
        return end.minusMonths(6);
    }
}
