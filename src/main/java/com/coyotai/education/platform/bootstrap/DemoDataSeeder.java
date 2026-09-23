package com.coyotai.education.platform.bootstrap;

import com.coyotai.education.academic.AcademicYear;
import com.coyotai.education.academic.AcademicYearRepository;
import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchRepository;
import com.coyotai.education.academic.Course;
import com.coyotai.education.academic.CourseRepository;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.academic.SubjectRepository;
import com.coyotai.education.assessment.AcademicTest;
import com.coyotai.education.assessment.AcademicTestRepository;
import com.coyotai.education.assessment.AcademicTestResult;
import com.coyotai.education.assessment.AcademicTestResultRepository;
import com.coyotai.education.assessment.Exam;
import com.coyotai.education.assessment.ExamRepository;
import com.coyotai.education.assessment.ExamResult;
import com.coyotai.education.assessment.ExamResultRepository;
import com.coyotai.education.assessment.ExamTypeRepository;
import com.coyotai.education.assessment.MarksValidator;
import com.coyotai.education.assessment.PublicationStatus;
import com.coyotai.education.attendance.AbsenceReason;
import com.coyotai.education.attendance.Attendance;
import com.coyotai.education.attendance.AttendanceRepository;
import com.coyotai.education.attendance.AttendanceStatus;
import com.coyotai.education.auth.User;
import com.coyotai.education.auth.UserRepository;
import com.coyotai.education.auth.UserService;
import com.coyotai.education.common.RecordStatus;
import com.coyotai.education.discipline.DisciplineRecord;
import com.coyotai.education.discipline.DisciplineRecordRepository;
import com.coyotai.education.discipline.DisciplineType;
import com.coyotai.education.discipline.DisciplineTypeRepository;
import com.coyotai.education.discipline.StudentFine;
import com.coyotai.education.discipline.StudentFineRepository;
import com.coyotai.education.fee.FeeCalculator;
import com.coyotai.education.fee.FeeInstallment;
import com.coyotai.education.fee.FeeInstallmentRepository;
import com.coyotai.education.fee.Payment;
import com.coyotai.education.fee.PaymentMethod;
import com.coyotai.education.fee.PaymentRepository;
import com.coyotai.education.fee.StudentFee;
import com.coyotai.education.fee.StudentFeeRepository;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.student.ParentContact;
import com.coyotai.education.parentmeeting.ParentMeeting;
import com.coyotai.education.parentmeeting.ParentMeetingRepository;
import com.coyotai.education.performance.GradeScale;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RoleCode;
import com.coyotai.education.progress.ProgressCard;
import com.coyotai.education.progress.ProgressCardItem;
import com.coyotai.education.progress.ProgressCardRepository;
import com.coyotai.education.schedule.ClassSchedule;
import com.coyotai.education.schedule.ClassScheduleRepository;
import com.coyotai.education.staff.Faculty;
import com.coyotai.education.staff.FacultyAssignment;
import com.coyotai.education.staff.FacultyAssignmentRepository;
import com.coyotai.education.staff.FacultyRepository;
import com.coyotai.education.staff.Mentor;
import com.coyotai.education.staff.MentorRepository;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentBatchAssignment;
import com.coyotai.education.student.StudentBatchAssignmentRepository;
import com.coyotai.education.student.StudentRepository;
import com.coyotai.education.syllabus.SyllabusProgress;
import com.coyotai.education.syllabus.SyllabusProgressRepository;
import com.coyotai.education.syllabus.SyllabusTopic;
import com.coyotai.education.syllabus.SyllabusTopicRepository;
import com.coyotai.education.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Development sample data for the configured client: courses, batches, staff, students with
 * portal logins, a month of schedule and attendance, tests, exams, syllabus progress, fines,
 * fees and a progress card. Runs only when SEED_SAMPLE_DATA=true on an empty database.
 *
 * <p>What was added to the demo later - more batches ({@link #EXTRA_BATCHES}), more students and
 * {@value #HISTORY_DAYS} days of attendance - is also added to a demo database seeded before it
 * existed. The demo database is recognised by its demo accounts; a real client's is never touched.
 *
 * <p>All demo passwords are development-only and must never be used in production.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String STUDENT_PASSWORD = "student123";

    /** A batch added to the demo after the first release: course, mentor, room and start hour, subjects with faculty, students. */
    record ExtraBatch(String name, String courseCode, String mentorUsername, String room, int startHour,
                      String[][] subjects, String[] students) {
    }

    /** Subjects are {course subject code, faculty username}. Each student's parent is named after the family. */
    static final List<ExtraBatch> EXTRA_BATCHES = List.of(
            new ExtraBatch("CA Inter C", "CA_INTER", "mentor3", "Room 4", 9,
                    new String[][]{{"ACC", "faculty3"}, {"TAX", "faculty3"}, {"LAW", "faculty4"}},
                    new String[]{"Aditya Rao", "Sneha Pillai", "Mohammed Irfan", "Pooja Shetty", "Vivek Menon", "Nisha Thomas"}),
            new ExtraBatch("CMA India A", "CMA_IN", "mentor3", "Room 5", 14,
                    new String[][]{{"FA", "faculty3"}, {"CA", "faculty3"}},
                    new String[]{"Rahul Verma", "Anjali Kapoor", "Siddharth Jain", "Meghna Das", "Farhan Ali"}),
            new ExtraBatch("CS Executive A", "CS_EXEC", "mentor4", "Room 6", 16,
                    new String[][]{{"CL", "faculty4"}},
                    new String[]{"Kavya Reddy", "Harish Kumar", "Shruti Nambiar", "Imran Khan", "Deepika Joshi"}),
            new ExtraBatch("ACCA Skills B", "ACCA", "mentor4", "Room 6", 18,
                    new String[][]{{"FR", "faculty4"}, {"PM", "faculty4"}},
                    new String[]{"Nikhil Bose", "Ritika Sen", "Joel Mathew", "Ayesha Siddiqui", "Varun Hegde"}),
            new ExtraBatch("CA Inter D", "CA_INTER", "mentor5", "Room 2", 11,
                    new String[][]{{"ACC", "faculty3"}, {"TAX", "faculty3"}, {"LAW", "faculty4"}},
                    new String[]{"Arjun Pillai", "Bhavya Shetty", "Chirag Mehta", "Divya Rao", "Eshan Gupta",
                            "Fiza Sheikh", "Gaurav Nair", "Hina Kapoor"}),
            new ExtraBatch("CMA USA B", "CMA_US", "mentor5", "Room 3", 10,
                    new String[][]{{"FPA", "faculty5"}},
                    new String[]{"Aditi Joshi", "Bilal Ahmed", "Charu Singh", "Dev Patel", "Esha Varghese",
                            "Faisal Khan", "Gita Menon", "Harsh Vardhan"}),
            new ExtraBatch("CMA India B", "CMA_IN", "mentor6", "Room 5", 16,
                    new String[][]{{"FA", "faculty5"}, {"CA", "faculty5"}},
                    new String[]{"Ira Bhat", "Jatin Arora", "Kriti Saxena", "Lokesh Yadav", "Mira Pillai",
                            "Nikhil Rao", "Ojas Kulkarni", "Priya Das"}),
            new ExtraBatch("CS Executive B", "CS_EXEC", "mentor6", "Room 4", 14,
                    new String[][]{{"CL", "faculty4"}},
                    new String[]{"Qasim Ali", "Riya Sen", "Samar Thakur", "Tara Iyer", "Uday Kiran",
                            "Vani Reddy", "Wasim Akram", "Yamini Rao"}));

    /** More students for the first demo batches. CMA USA A keeps its three: the fee tests rely on them. */
    private static final List<Map.Entry<String, String[]>> EXTRA_STUDENTS = List.of(
            Map.entry("CA Inter A", new String[]{"Farah Khan", "Ishaan Malhotra", "Lakshmi Menon", "Naveen Reddy", "Tanvi Desai"}),
            Map.entry("CA Inter B", new String[]{"Aarav Shah", "Bhavana Rao", "Chetan Patil", "Diya Banerjee"}),
            Map.entry("ACCA Skills A", new String[]{"Elena D'Souza", "Gautam Sinha", "Hema Krishnan", "Irfan Qureshi"}));

    /** The first demo batches that take attendance (CMA USA A has no mentor or timetable yet). */
    private static final List<String> FIRST_BATCHES_WITH_ATTENDANCE = List.of("CA Inter A", "CA Inter B", "ACCA Skills A");

    /** Days of attendance history the demo keeps, ending yesterday. */
    public static final int HISTORY_DAYS = 60;

    /** Mentors and faculty of the extra batches: {username, name, employee code, specialisation}. */
    private static final String[][] EXTRA_MENTORS = {
            {"mentor3", "Priya Menon", "MEN003"}, {"mentor4", "Arun Das", "MEN004"},
            {"mentor5", "Kavitha Iyer", "MEN005"}, {"mentor6", "Farooq Ahmed", "MEN006"}};
    private static final String[][] EXTRA_FACULTY = {
            {"faculty3", "Suresh Pillai", "FAC003", "Accounts and Cost Accounting"},
            {"faculty4", "Deepa Varma", "FAC004", "Law and Financial Reporting"},
            {"faculty5", "Manoj Kulkarni", "FAC005", "Management Accounting"}};

    private static final String[] PARENT_FIRST_NAMES = {"Ramesh", "Sunita", "Mahesh", "Anita", "Suresh", "Kavita",
            "Rajesh", "Meena", "Vijay", "Shobha"};
    private static final String[][] DISCOUNTS = {{"0", null}, {"2000", "Sibling concession"}, {"0", null},
            {"5000", "Merit scholarship"}};

    private final ProjectConfigService config;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AcademicYearRepository yearRepository;
    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final MentorRepository mentorRepository;
    private final FacultyRepository facultyRepository;
    private final BatchRepository batchRepository;
    private final FacultyAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final StudentBatchAssignmentRepository batchAssignmentRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final AttendanceRepository attendanceRepository;
    private final AcademicTestRepository testRepository;
    private final AcademicTestResultRepository testResultRepository;
    private final ExamRepository examRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamTypeRepository examTypeRepository;
    private final SyllabusTopicRepository topicRepository;
    private final SyllabusProgressRepository syllabusProgressRepository;
    private final DisciplineTypeRepository disciplineTypeRepository;
    private final DisciplineRecordRepository disciplineRecordRepository;
    private final StudentFineRepository fineRepository;
    private final StudentFeeRepository feeRepository;
    private final FeeInstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final ProgressCardRepository progressCardRepository;
    private final ParentMeetingRepository parentMeetingRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final GradeScale gradeScale;
    private final TransactionTemplate transactions;

    public DemoDataSeeder(ProjectConfigService config, UserRepository userRepository, UserService userService,
                          AcademicYearRepository yearRepository, CourseRepository courseRepository,
                          SubjectRepository subjectRepository, MentorRepository mentorRepository,
                          FacultyRepository facultyRepository, BatchRepository batchRepository,
                          FacultyAssignmentRepository assignmentRepository,
                          StudentRepository studentRepository,
                          StudentBatchAssignmentRepository batchAssignmentRepository,
                          ClassScheduleRepository scheduleRepository, AttendanceRepository attendanceRepository,
                          AcademicTestRepository testRepository, AcademicTestResultRepository testResultRepository,
                          ExamRepository examRepository, ExamResultRepository examResultRepository,
                          ExamTypeRepository examTypeRepository, SyllabusTopicRepository topicRepository,
                          SyllabusProgressRepository syllabusProgressRepository,
                          DisciplineTypeRepository disciplineTypeRepository,
                          DisciplineRecordRepository disciplineRecordRepository, StudentFineRepository fineRepository,
                          StudentFeeRepository feeRepository, FeeInstallmentRepository installmentRepository,
                          PaymentRepository paymentRepository, ProgressCardRepository progressCardRepository,
                          ParentMeetingRepository parentMeetingRepository, NotificationService notificationService,
                          NotificationMessageFactory messageFactory, GradeScale gradeScale,
                          PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.config = config;
        this.userRepository = userRepository;
        this.userService = userService;
        this.yearRepository = yearRepository;
        this.courseRepository = courseRepository;
        this.subjectRepository = subjectRepository;
        this.mentorRepository = mentorRepository;
        this.facultyRepository = facultyRepository;
        this.batchRepository = batchRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentRepository = studentRepository;
        this.batchAssignmentRepository = batchAssignmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.attendanceRepository = attendanceRepository;
        this.testRepository = testRepository;
        this.testResultRepository = testResultRepository;
        this.examRepository = examRepository;
        this.examResultRepository = examResultRepository;
        this.examTypeRepository = examTypeRepository;
        this.topicRepository = topicRepository;
        this.syllabusProgressRepository = syllabusProgressRepository;
        this.disciplineTypeRepository = disciplineTypeRepository;
        this.disciplineRecordRepository = disciplineRecordRepository;
        this.fineRepository = fineRepository;
        this.feeRepository = feeRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.progressCardRepository = progressCardRepository;
        this.parentMeetingRepository = parentMeetingRepository;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.gradeScale = gradeScale;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!config.bootstrap().isSeedSampleData()) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            if (userRepository.count() == 0) {
                seedDemo();
            }
        });
        try {
            transactions.executeWithoutResult(status -> topUpDemoData());
        } catch (RuntimeException e) {
            // Extra sample data must never stop the application from starting.
            log.warn("Could not top up the demo data: {}", e.getMessage());
        }
    }

    private void seedDemo() {
        LocalDate today = config.today();

        // ---- Staff logins -----------------------------------------------------
        userService.createAccount("admin", "admin123", "Centre Administrator", "admin@example.com", null,
                Set.of(RoleCode.ADMINISTRATIVE), false);
        userService.createAccount("academic", "academic123", "Academic Coordinator", null, null,
                Set.of(RoleCode.ACADEMICS), false);
        Mentor john = mentor("mentor", "mentor123", "John Mathew", "MEN001");
        Mentor sara = mentor("mentor2", "mentor123", "Sara Thomas", "MEN002");
        Faculty ravi = faculty("faculty", "faculty123", "Ravi Menon", "FAC001", Faculty.Type.FULL_TIME, "Accounts and Taxation");
        Faculty anjali = faculty("faculty2", "faculty123", "Anjali Nair", "FAC002", Faculty.Type.GUEST, "Law and Costing");

        // ---- Academic structure -------------------------------------------------
        AcademicYear year = new AcademicYear();
        LocalDate yearStart = LocalDate.of(today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1, 6, 1);
        year.setName(yearStart.getYear() + "-" + (yearStart.getYear() + 1));
        year.setStartDate(yearStart);
        year.setEndDate(yearStart.plusYears(1).minusDays(1));
        year.setCurrent(true);
        year.setStatus(AcademicYear.Status.ACTIVE);
        yearRepository.save(year);

        // Course fees are master data: every student of a course is billed the same fee and
        // individual students differ only by their discount.
        Course ca = course("CA_INTER", "CA Intermediate", 1, "60000", 3);
        Course cmaIndia = course("CMA_IN", "CMA India", 2, "40000", 2);
        Course cmaUsa = course("CMA_US", "CMA USA", 3, "20000", 2);
        Course acca = course("ACCA", "ACCA", 4, "45000", 3);
        Course cs = course("CS_EXEC", "CS Executive", 5, "35000", 2);

        Subject accounts = subject(ca, "ACC", "Accounts", 1);
        Subject taxation = subject(ca, "TAX", "Taxation", 2);
        Subject law = subject(ca, "LAW", "Law", 3);
        Subject costing = subject(ca, "COST", "Costing", 4);
        subject(cmaIndia, "FA", "Financial Accounting", 1);
        subject(cmaIndia, "CA", "Cost Accounting", 2);
        subject(cmaUsa, "FPA", "Financial Planning and Analytics", 1);
        Subject reporting = subject(acca, "FR", "Financial Reporting", 1);
        Subject performanceMgmt = subject(acca, "PM", "Performance Management", 2);
        subject(cs, "CL", "Company Law", 1);

        Batch caA = batch("CA Inter A", ca, year, john, 40);
        Batch caB = batch("CA Inter B", ca, year, sara, 40);
        Batch accaA = batch("ACCA Skills A", acca, year, sara, 30);
        // A new batch without a mentor or timetable yet - enough to show fees and discounts.
        Batch cmaA = batch("CMA USA A", cmaUsa, year, null, 25);

        assign(ravi, caA, accounts);
        assign(ravi, caA, taxation);
        assign(ravi, caB, accounts);
        assign(anjali, caA, law);
        assign(anjali, caA, costing);
        assign(anjali, accaA, reporting);

        // ---- Parents and students ---------------------------------------------------
        List<ParentContact> parents = List.of(
                parent("Anand Kumar", "+919876500001", true),
                parent("Meera Nair", "+919876500002", true),
                parent("Sanjay Gupta", "+919876500003", true),
                parent("Fatima Sheikh", "+919876500004", true),
                parent("David Fernandes", "+919876500005", false),
                parent("Lakshmi Iyer", "+919876500006", true));

        String[] caANames = {"Ahmed Kumar", "Divya Nair", "Karan Gupta", "Zaid Sheikh", "Ryan Fernandes"};
        String[] caBNames = {"Aisha Kumar", "Rohan Nair", "Neha Gupta", "Priya Iyer"};
        String[] accaNames = {"Sara Sheikh", "Tara Fernandes", "Arjun Iyer"};
        List<Student> caAStudents = new ArrayList<>();
        List<Student> caBStudents = new ArrayList<>();
        List<Student> accaStudents = new ArrayList<>();
        int seq = 1;
        for (int i = 0; i < caANames.length; i++) {
            caAStudents.add(student(seq++, caANames[i], parents.get(i % parents.size()), caA, yearStart.plusDays(i)));
        }
        for (int i = 0; i < caBNames.length; i++) {
            caBStudents.add(student(seq++, caBNames[i], parents.get((i + 1) % parents.size()), caB, yearStart.plusDays(i)));
        }
        for (int i = 0; i < accaNames.length; i++) {
            accaStudents.add(student(seq++, accaNames[i], parents.get((i + 3) % parents.size()), accaA, yearStart.plusDays(i)));
        }
        String[] cmaNames = {"Rahul Gupta", "Ayesha Sheikh", "Kevin Fernandes"};
        List<Student> cmaStudents = new ArrayList<>();
        for (int i = 0; i < cmaNames.length; i++) {
            cmaStudents.add(student(seq++, cmaNames[i], parents.get((i + 2) % parents.size()), cmaA, yearStart.plusDays(i)));
        }

        // ---- Schedule: every day from two weeks ago to two weeks ahead ------------------
        for (LocalDate day = today.minusDays(14); !day.isAfter(today.plusDays(14)); day = day.plusDays(1)) {
            int n = day.getDayOfYear();
            addClass(caA, n % 2 == 0 ? accounts : taxation, ravi, day, 18, 0, 20, 0, "Room 1");
            addClass(caA, n % 2 == 0 ? law : costing, anjali, day, 20, 15, 21, 15, "Room 1");
            addClass(caB, accounts, ravi, day, 16, 0, 17, 45, "Room 2");
            addClass(accaA, n % 2 == 0 ? reporting : performanceMgmt,
                    n % 2 == 0 ? anjali : null, day, 17, 0, 18, 30, "Room 3");
        }

        // ---- Attendance for the last ten days -----------------------------------------------
        int counter = 0;
        for (LocalDate day = today.minusDays(10); day.isBefore(today); day = day.plusDays(1)) {
            counter = markDay(caA, caAStudents, day, counter);
            counter = markDay(caB, caBStudents, day, counter);
            counter = markDay(accaA, accaStudents, day, counter);
        }

        // ---- Syllabus ---------------------------------------------------------------------
        seedSyllabus(ca, accounts, caA, today, "Accounting standards", "Partnership accounts", "Company accounts",
                "Cash flow statements", "Branch accounting", "Consolidation basics");
        seedSyllabus(ca, taxation, caA, today, "Residential status", "Income from salary", "House property",
                "Capital gains", "GST fundamentals");
        seedSyllabus(ca, law, caA, today, "Contract act", "Partnership act", "Companies act basics", "LLP act");
        seedSyllabus(ca, costing, caA, today, "Material costing", "Labour costing", "Overheads", "Marginal costing");

        // ---- Tests and exams ------------------------------------------------------------------
        seedTests(caA, caAStudents, accounts, taxation, today);
        seedTests(caB, caBStudents, accounts, accounts, today);
        seedExams(caA, caAStudents, taxation, accounts, ravi, today);

        // ---- Discipline, fines, meetings -------------------------------------------------------
        Student ahmed = caAStudents.get(0);
        DisciplineRecord late = discipline(ahmed, "LATE_COMING", today.minusDays(8), "Arrived 25 minutes late",
                "Warned; parent informed", DisciplineRecord.Status.RESOLVED);
        fine(ahmed, late, "Late coming", new BigDecimal("500.00"), today.minusDays(8), today.plusDays(2), StudentFine.Status.PENDING);
        DisciplineRecord uniform = discipline(caAStudents.get(2), "UNIFORM", today.minusDays(12), "Not in uniform",
                "Fine collected", DisciplineRecord.Status.RESOLVED);
        StudentFine paidFine = fine(caAStudents.get(2), uniform, "Uniform", new BigDecimal("200.00"),
                today.minusDays(12), today.minusDays(5), StudentFine.Status.PAID);
        paidFine.setPaidDate(today.minusDays(9));

        meeting(ahmed, john, today.plusDays(5), ParentMeeting.Status.SCHEDULED, null);
        meeting(caAStudents.get(3), john, today.minusDays(6), ParentMeeting.Status.COMPLETED,
                "Discussed attendance and weekly test scores; agreed on extra practice.");

        // ---- Fees: the course fee, less each student's own discount ---------------------------
        for (int i = 0; i < caAStudents.size(); i++) {
            Student student = caAStudents.get(i);
            String[] discount = switch (i) {
                case 1 -> new String[]{"2000", "Sibling concession"};
                case 3 -> new String[]{"3000", "Financial hardship"};
                default -> new String[]{"0", null};
            };
            seedFees(student, discount[0], discount[1], yearStart, student.getId() % 3 != 0);
        }
        for (int i = 0; i < caBStudents.size(); i++) {
            Student student = caBStudents.get(i);
            seedFees(student, i == 2 ? "5000" : "0", i == 2 ? "Merit scholarship" : null, yearStart,
                    student.getId() % 2 == 0);
        }
        for (Student student : accaStudents) {
            seedFees(student, "0", null, yearStart, true);
        }
        // CMA USA: fee 20,000 with a different discount for each student.
        String[][] cmaDiscounts = {{"2000", "Financial hardship"}, {"3000", "Family income concession"},
                {"5000", "Merit scholarship"}};
        for (int i = 0; i < cmaStudents.size(); i++) {
            seedFees(cmaStudents.get(i), cmaDiscounts[i][0], cmaDiscounts[i][1], yearStart, false);
        }

        // ---- Progress card ---------------------------------------------------------------------
        seedProgressCard(ahmed, caA, today);

        log.warn("Sample data created for {}. Development logins: admin/admin123, academic/academic123, "
                + "mentor/mentor123, faculty/faculty123, students ADM26001..ADM26015 / {}. "
                + "Never use these credentials in production.", config.getClientName(), STUDENT_PASSWORD);
    }

    // =========================================================================
    // Extra batches
    // =========================================================================

    /**
     * Brings a demo database up to the current demo: the {@link #EXTRA_BATCHES} with their
     * mentors, faculty, students, parents, fee plans and timetable, more students in the first
     * batches, and {@value #HISTORY_DAYS} days of attendance for all of them. Demo database only.
     * Once everything is there it does nothing, so it is safe on every start and DevTools restart.
     */
    private void topUpDemoData() {
        if (!isDemoDatabase()) {
            return;
        }
        AcademicYear year = yearRepository.findFirstByCurrentTrue().orElse(null);
        if (year == null) {
            return;
        }
        Map<String, Batch> batches = new HashMap<>();
        batchRepository.findAll().forEach(batch -> batches.put(key(batch.getName()), batch));
        List<ExtraBatch> missing = EXTRA_BATCHES.stream().filter(spec -> !batches.containsKey(key(spec.name()))).toList();

        LocalDate today = config.today();
        int[] seq = {16};
        int[] phone = {10001};
        List<String> added = missing.isEmpty() ? List.of() : addBatches(missing, year, today, batches, seq, phone);

        int joined = 0;
        for (Map.Entry<String, String[]> entry : EXTRA_STUDENTS) {
            Batch batch = batches.get(key(entry.getKey()));
            if (batch == null) {
                continue;
            }
            Set<String> enrolled = studentRepository.findActiveByBatchId(batch.getId()).stream()
                    .map(student -> key(student.getFullName())).collect(Collectors.toSet());
            int index = enrolled.size();
            for (String name : entry.getValue()) {
                if (!enrolled.contains(key(name))) {
                    demoStudent(name, batch, index++, year, seq, phone);
                    joined++;
                }
            }
        }
        int marks = 0;
        if (!added.isEmpty() || joined > 0) {
            List<Batch> withAttendance = Stream.concat(FIRST_BATCHES_WITH_ATTENDANCE.stream(), EXTRA_BATCHES.stream().map(ExtraBatch::name))
                    .map(name -> batches.get(key(name)))
                    .filter(Objects::nonNull)
                    .toList();
            marks = backfillAttendance(withAttendance, today);
        }
        int details = addHistoryDetails(batches, today);
        if (added.isEmpty() && joined == 0 && details == 0) {
            return;
        }
        log.warn("Demo data topped up: new batches {}, {} more students in the first batches, {} attendance marks "
                + "over the last {} days, {} absence reasons and discipline notes. Development logins: mentor3..mentor6 / "
                + "mentor123, faculty3..faculty5 / faculty123, students / {}. Never use these credentials in production.",
                added, joined, marks, HISTORY_DAYS, details, STUDENT_PASSWORD);
    }

    /**
     * Details on the generated history of the extra batches, so the administrator's dashboard has
     * something to analyse: a reason on two absences in three, and now and then a student without
     * uniform or ID tag (each also a discipline record, as the attendance screen creates) or another
     * incident - more often for the students who are often away. Only marks the demo generated
     * (marked at 09:30), never those of the first batches the tests rely on, and only once.
     */
    private int addHistoryDetails(Map<String, Batch> batches, LocalDate today) {
        List<Long> ids = EXTRA_BATCHES.stream()
                .map(spec -> batches.get(key(spec.name())))
                .filter(Objects::nonNull)
                .map(Batch::getId)
                .toList();
        LocalDate from = today.minusDays(HISTORY_DAYS);
        LocalDate to = today.minusDays(1);
        if (ids.isEmpty() || attendanceRepository.countAbsentWithReason(from, to, ids) > 0) {
            return 0;
        }
        DisciplineType uniform = disciplineTypeRepository.findFirstByCodeIgnoreCaseAndActiveTrue("UNIFORM").orElse(null);
        DisciplineType idTag = disciplineTypeRepository.findFirstByCodeIgnoreCaseAndActiveTrue("NAME_BADGE").orElse(null);
        DisciplineType other = disciplineTypeRepository.findFirstByCodeIgnoreCaseAndActiveTrue("OTHER").orElse(null);
        String[] incidents = {"Used a mobile phone in class", "Disturbed the class", "Homework not submitted",
                "Left the class without permission"};
        ZoneId zone = config.zoneId();
        int details = 0;
        for (Attendance mark : attendanceRepository.findWholeDayMarks(from, to, ids)) {
            if (mark.getMarkedAt() == null || !mark.getMarkedAt().equals(mark.getAttendanceDate().atTime(9, 30).atZone(zone).toInstant())) {
                continue; // taken by a teacher, not generated
            }
            int roll = new Random(mark.getId() * 31L + 7).nextInt(100);
            if (mark.getStatus() == AttendanceStatus.ABSENT) {
                mark.setAbsenceReason(roll < 38 ? AbsenceReason.MEDICAL : roll < 58 ? AbsenceReason.PERSONAL
                        : roll < 66 ? AbsenceReason.OTHER : null);
                details += mark.getAbsenceReason() == null ? 0 : 1;
                continue;
            }
            if (mark.getStatus() != AttendanceStatus.PRESENT && mark.getStatus() != AttendanceStatus.LATE) {
                continue;
            }
            // The students who are often away (see backfillAttendance) are also the ones most often out of uniform.
            boolean struggling = Math.floorMod(mark.getStudent().getId() * 7, 10) <= 1;
            if (uniform != null && roll < (struggling ? 8 : 3)) {
                mark.setNoUniform(true);
                disciplineNote(mark, uniform, "No uniform - noted while taking attendance", true, today);
                details++;
            } else if (idTag != null && roll >= 90 && roll < (struggling ? 95 : 92)) {
                mark.setNoIdTag(true);
                disciplineNote(mark, idTag, "No ID tag - noted while taking attendance", true, today);
                details++;
            } else if (other != null && roll == 50) {
                disciplineNote(mark, other, incidents[(int) Math.floorMod(mark.getId(), incidents.length)], false, today);
                details++;
            }
        }
        return details;
    }

    /** A discipline record for a demo mark; the ones older than two weeks are already resolved. */
    private void disciplineNote(Attendance mark, DisciplineType type, String description, boolean fromAttendance,
                                LocalDate today) {
        DisciplineRecord record = new DisciplineRecord();
        record.setStudent(mark.getStudent());
        record.setBatch(mark.getBatch());
        if (fromAttendance) {
            record.setAttendance(mark);
        }
        record.setDisciplineType(type);
        record.setIncidentDate(mark.getAttendanceDate());
        record.setDescription(description);
        boolean recent = !mark.getAttendanceDate().isBefore(today.minusDays(14));
        record.setStatus(recent ? DisciplineRecord.Status.OPEN : DisciplineRecord.Status.RESOLVED);
        if (!recent) {
            record.setActionTaken("Warned; parent informed");
        }
        disciplineRecordRepository.save(record);
    }

    /** Creates the missing extra batches with their staff, students, fees and a month of timetable. */
    private List<String> addBatches(List<ExtraBatch> missing, AcademicYear year, LocalDate today, Map<String, Batch> batches,
                                    int[] seq, int[] phone) {
        Map<String, Course> courses = new HashMap<>();
        courseRepository.findAll().forEach(course -> courses.put(course.getCode().toUpperCase(Locale.ROOT), course));

        Map<String, Mentor> mentors = new HashMap<>();
        for (String[] m : EXTRA_MENTORS) {
            Mentor mentor = mentorRepository.findAllWithUser().stream()
                    .filter(candidate -> candidate.getUser() != null && candidate.getUser().getUsername().equalsIgnoreCase(m[0]))
                    .findFirst()
                    .orElseGet(() -> userRepository.existsByUsernameIgnoreCase(m[0]) ? null : mentor(m[0], "mentor123", m[1], m[2]));
            if (mentor != null) {
                mentors.put(m[0], mentor);
            }
        }
        Map<String, Faculty> teachers = new HashMap<>();
        for (String[] f : EXTRA_FACULTY) {
            Faculty teacher = facultyRepository.findAllWithUser().stream()
                    .filter(candidate -> candidate.getUser() != null && candidate.getUser().getUsername().equalsIgnoreCase(f[0]))
                    .findFirst()
                    .orElseGet(() -> userRepository.existsByUsernameIgnoreCase(f[0]) ? null
                            : faculty(f[0], "faculty123", f[1], f[2], Faculty.Type.FULL_TIME, f[3]));
            if (teacher != null) {
                teachers.put(f[0], teacher);
            }
        }

        List<String> added = new ArrayList<>();
        for (ExtraBatch spec : missing) {
            Course course = courses.get(spec.courseCode());
            if (course == null) {
                continue;
            }
            Batch batch = batch(spec.name(), course, year, mentors.get(spec.mentorUsername()), 40);
            batches.put(key(spec.name()), batch);

            Map<String, Subject> courseSubjects = new HashMap<>();
            subjectRepository.search(course.getId())
                    .forEach(subject -> courseSubjects.put(subject.getCode().toUpperCase(Locale.ROOT), subject));
            List<Subject> subjects = new ArrayList<>();
            List<Faculty> subjectTeachers = new ArrayList<>();
            for (String[] s : spec.subjects()) {
                Subject subject = courseSubjects.get(s[0]);
                if (subject == null) {
                    continue;
                }
                Faculty teacher = teachers.get(s[1]);
                if (teacher != null) {
                    assign(teacher, batch, subject);
                }
                subjects.add(subject);
                subjectTeachers.add(teacher);
            }

            for (int i = 0; i < spec.students().length; i++) {
                demoStudent(spec.students()[i], batch, i, year, seq, phone);
            }

            // One class a day, rotating through the batch's subjects.
            for (LocalDate day = today.minusDays(14); !subjects.isEmpty() && !day.isAfter(today.plusDays(14)); day = day.plusDays(1)) {
                int pick = Math.floorMod(day.getDayOfYear(), subjects.size());
                addClass(batch, subjects.get(pick), subjectTeachers.get(pick), day, spec.startHour(), 0,
                        spec.startHour() + 1, 30, spec.room());
            }
            added.add(spec.name());
        }
        return added;
    }

    /**
     * A demo student with a parent of the same family (one family in four has not opted in to
     * WhatsApp), a portal login and the course fee - every other family with a concession.
     */
    private Student demoStudent(String name, Batch batch, int index, AcademicYear year, int[] seq, int[] phone) {
        seq[0] = nextFreeAdmission(seq[0]);
        String family = name.substring(name.lastIndexOf(' ') + 1);
        ParentContact parent = parent(PARENT_FIRST_NAMES[index % PARENT_FIRST_NAMES.length] + " " + family,
                nextFreePhone(phone), index % 4 != 2);
        Student student = student(seq[0]++, name, parent, batch, year.getStartDate().plusDays(index));
        String[] discount = DISCOUNTS[index % DISCOUNTS.length];
        seedFees(student, discount[0], discount[1], year.getStartDate(), index % 2 == 0);
        return student;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** The demo fixture's own mentor and first student - a real client's database never has both. */
    private boolean isDemoDatabase() {
        return userRepository.findByUsernameIgnoreCase("mentor").map(User::getFullName).filter("John Mathew"::equals).isPresent()
                && userRepository.findByUsernameIgnoreCase("ADM26001").map(User::getFullName).filter("Ahmed Kumar"::equals).isPresent();
    }

    private int nextFreeAdmission(int from) {
        int seq = from;
        while (studentRepository.existsByAdmissionNumberIgnoreCase(String.format("ADM26%03d", seq))
                || userRepository.existsByUsernameIgnoreCase(String.format("ADM26%03d", seq))) {
            seq++;
        }
        return seq;
    }

    private String nextFreePhone(int[] counter) {
        String phone;
        do {
            phone = "+9198765" + counter[0]++;
        } while (studentRepository.existsByParentPhoneNumber(phone));
        return phone;
    }

    /**
     * Whole-day attendance for every active student of the given batches on each of the last
     * {@value #HISTORY_DAYS} days they have no mark yet - attendance a teacher took is never
     * replaced. Each student keeps a steady pattern (most attend nearly always, one in ten misses
     * often, a third are often late); Mondays see more absences, Saturdays more late arrivals, and
     * on one holiday most of the centre stays away - so the charts have a story to tell.
     */
    private int backfillAttendance(List<Batch> batches, LocalDate today) {
        LocalDate from = today.minusDays(HISTORY_DAYS);
        LocalDate holiday = today.minusDays(18);
        Set<String> marked = new HashSet<>();
        attendanceRepository.findStudentDays(from, today.minusDays(1)).forEach(row -> marked.add(row[0] + ":" + row[1]));
        ZoneId zone = config.zoneId();
        int created = 0;
        for (Batch batch : batches) {
            Long markedBy = batch.getMentor() == null || batch.getMentor().getUser() == null ? null
                    : batch.getMentor().getUser().getId();
            for (Student student : studentRepository.findActiveByBatchId(batch.getId())) {
                Random random = new Random(student.getId() * 7919L);
                int profile = Math.floorMod(student.getId() * 7, 10);
                int absentPercent = profile == 0 ? 18 : profile == 1 ? 9 : profile < 5 ? 4 : 2;
                int latePercent = profile % 3 == 0 ? 9 : 3;
                for (LocalDate day = from; day.isBefore(today); day = day.plusDays(1)) {
                    // Drawn for every day, so a student's pattern does not depend on which days were already taken.
                    int roll = random.nextInt(100);
                    int detail = random.nextInt(4);
                    if (!marked.add(student.getId() + ":" + day)) {
                        continue;
                    }
                    int absent = absentPercent + (day.getDayOfWeek() == DayOfWeek.MONDAY ? 4 : 0) + (day.equals(holiday) ? 45 : 0);
                    int late = latePercent + (day.getDayOfWeek() == DayOfWeek.SATURDAY ? 5 : 0);
                    AttendanceStatus status = roll < absent ? AttendanceStatus.ABSENT
                            : roll < absent + 3 ? AttendanceStatus.EXCUSED
                            : roll < absent + 3 + late ? AttendanceStatus.LATE
                            : AttendanceStatus.PRESENT;
                    Attendance attendance = new Attendance();
                    attendance.setStudent(student);
                    attendance.setBatch(batch);
                    attendance.setSessionKey(Attendance.WHOLE_DAY);
                    attendance.setAttendanceDate(day);
                    attendance.setStatus(status);
                    if (status == AttendanceStatus.LATE) {
                        attendance.setLateMinutes(10 + detail * 10);
                    } else if (status == AttendanceStatus.EXCUSED) {
                        attendance.setAbsenceReason(detail % 2 == 0 ? AbsenceReason.MEDICAL : AbsenceReason.PERSONAL);
                    }
                    attendance.setMarkedBy(markedBy);
                    attendance.setMarkedAt(day.atTime(9, 30).atZone(zone).toInstant());
                    // Past demo marks: no parent messages are sent for them.
                    attendance.setNotificationStatus(status.notificationEvent() == null
                            ? Attendance.NotificationDecision.NOT_REQUIRED : Attendance.NotificationDecision.SKIPPED);
                    attendanceRepository.save(attendance);
                    created++;
                }
            }
        }
        return created;
    }

    // =========================================================================
    // Builders
    // =========================================================================

    private Mentor mentor(String username, String password, String name, String code) {
        User user = userService.createAccount(username, password, name, null, null, Set.of(RoleCode.MENTORS), false);
        Mentor mentor = new Mentor();
        mentor.setUser(user);
        mentor.setFullName(name);
        mentor.setEmployeeCode(code);
        mentor.setSpecialization("Mentoring");
        return mentorRepository.save(mentor);
    }

    private Faculty faculty(String username, String password, String name, String code, Faculty.Type type,
                            String specialization) {
        User user = userService.createAccount(username, password, name, null, null, Set.of(RoleCode.FACULTY), false);
        Faculty faculty = new Faculty();
        faculty.setUser(user);
        faculty.setFullName(name);
        faculty.setEmployeeCode(code);
        faculty.setFacultyType(type);
        faculty.setSpecialization(specialization);
        return facultyRepository.save(faculty);
    }

    private Course course(String code, String name, int order, String fee, int installments) {
        Course course = new Course();
        course.setCode(code);
        course.setName(name);
        course.setStatus(RecordStatus.ACTIVE);
        course.setDisplayOrder(order);
        course.setFeeAmount(new BigDecimal(fee).setScale(2));
        course.setDefaultInstallments(installments);
        return courseRepository.save(course);
    }

    private Subject subject(Course course, String code, String name, int order) {
        Subject subject = new Subject();
        subject.setCourse(course);
        subject.setCode(code);
        subject.setName(name);
        subject.setDisplayOrder(order);
        subject.setStatus(RecordStatus.ACTIVE);
        return subjectRepository.save(subject);
    }

    private Batch batch(String name, Course course, AcademicYear year, Mentor mentor, int capacity) {
        Batch batch = new Batch();
        batch.setName(name);
        batch.setCourse(course);
        batch.setAcademicYear(year);
        batch.setMentor(mentor);
        batch.setStartDate(year.getStartDate());
        batch.setEndDate(year.getEndDate());
        batch.setCapacity(capacity);
        batch.setStatus(Batch.Status.ACTIVE);
        return batchRepository.save(batch);
    }

    private void assign(Faculty faculty, Batch batch, Subject subject) {
        FacultyAssignment assignment = new FacultyAssignment();
        assignment.setFaculty(faculty);
        assignment.setBatch(batch);
        assignment.setSubject(subject);
        assignment.setActive(true);
        assignmentRepository.save(assignment);
    }

    private ParentContact parent(String name, String phone, boolean optIn) {
        ParentContact parent = new ParentContact();
        parent.setName(name);
        parent.setRelation("Parent");
        parent.setPhoneNumber(phone);
        parent.setWhatsappNumber(phone);
        parent.setWhatsappOptIn(optIn);
        parent.setActive(true);
        return parent;
    }

    private Student student(int seq, String name, ParentContact parent, Batch batch, LocalDate admissionDate) {
        String admission = String.format("ADM26%03d", seq);
        User user = userService.createAccount(admission, STUDENT_PASSWORD, name, null, null, Set.of(RoleCode.STUDENTS), false);
        Student student = new Student();
        student.setUser(user);
        student.setStudentCode(String.format("STU%05d", seq));
        student.setAdmissionNumber(admission);
        student.setFullName(name);
        student.setDateOfBirth(admissionDate.minusYears(20).minusDays(seq * 17L));
        student.setGender(seq % 2 == 0 ? "Female" : "Male");
        student.setMobile(String.format("+91987650%04d", 1000 + seq));
        student.setParent(parent);
        student.setBatch(batch);
        student.setCourse(batch.getCourse());
        student.setAcademicYear(batch.getAcademicYear());
        student.setAdmissionDate(admissionDate);
        student.setStatus(Student.Status.ACTIVE);
        studentRepository.save(student);

        StudentBatchAssignment assignment = new StudentBatchAssignment();
        assignment.setStudent(student);
        assignment.setBatch(batch);
        assignment.setCourse(batch.getCourse());
        assignment.setAcademicYear(batch.getAcademicYear());
        assignment.setStartDate(admissionDate);
        assignment.setStatus(StudentBatchAssignment.Status.CURRENT);
        assignment.setReason("Admission");
        batchAssignmentRepository.save(assignment);
        return student;
    }

    private void addClass(Batch batch, Subject subject, Faculty faculty,
                          LocalDate day, int startHour, int startMinute, int endHour, int endMinute, String room) {
        ClassSchedule schedule = new ClassSchedule();
        schedule.setBatch(batch);
        schedule.setCourse(batch.getCourse());
        schedule.setSubject(subject);
        schedule.setFaculty(faculty);
        schedule.setScheduleDate(day);
        schedule.setStartTime(LocalTime.of(startHour, startMinute));
        schedule.setEndTime(LocalTime.of(endHour, endMinute));
        schedule.setRoom(room);
        schedule.setStatus(day.isBefore(config.today()) ? ClassSchedule.Status.COMPLETED : ClassSchedule.Status.SCHEDULED);
        scheduleRepository.save(schedule);
    }

    private int markDay(Batch batch, List<Student> students, LocalDate day, int counter) {
        for (Student student : students) {
            int slot = counter++ % 13;
            AttendanceStatus status = switch (slot) {
                case 4 -> AttendanceStatus.ABSENT;
                case 8 -> AttendanceStatus.LATE;
                case 11 -> AttendanceStatus.EXCUSED;
                default -> AttendanceStatus.PRESENT;
            };
            Attendance attendance = new Attendance();
            attendance.setStudent(student);
            attendance.setBatch(batch);
            attendance.setSessionKey(Attendance.WHOLE_DAY);
            attendance.setAttendanceDate(day);
            attendance.setStatus(status);
            if (status == AttendanceStatus.LATE) {
                attendance.setLateMinutes(15);
            } else if (status == AttendanceStatus.EXCUSED) {
                attendance.setAbsenceReason(AbsenceReason.MEDICAL);
            }
            attendance.setMarkedAt(Instant.now());
            attendance.setNotificationStatus(Attendance.NotificationDecision.NOT_REQUIRED);
            if (status.notificationEvent() != null) {
                String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
                var content = status == AttendanceStatus.ABSENT
                        ? messageFactory.absent(parentName, student.getFullName(), batch.getName(), day)
                        : messageFactory.late(parentName, student.getFullName(), batch.getName(), day);
                boolean queued = notificationService.publish(status.notificationEvent(), student, content).anyQueued();
                attendance.setNotificationStatus(queued
                        ? Attendance.NotificationDecision.QUEUED : Attendance.NotificationDecision.SKIPPED);
            }
            attendanceRepository.save(attendance);
        }
        return counter;
    }

    private void seedSyllabus(Course course, Subject subject, Batch batch, LocalDate today, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            SyllabusTopic topic = new SyllabusTopic();
            topic.setCourse(course);
            topic.setSubject(subject);
            topic.setTitle(titles[i]);
            topic.setSequenceNo(i + 1);
            topic.setPlannedHours(new BigDecimal("6.0"));
            topic.setActive(true);
            topicRepository.save(topic);

            SyllabusProgress progress = new SyllabusProgress();
            progress.setBatch(batch);
            progress.setTopic(topic);
            progress.setPlannedDate(today.minusDays(30L - i * 10L));
            if (i < titles.length / 2) {
                progress.setStatus(SyllabusProgress.Status.COMPLETED);
                progress.setCompletedDate(today.minusDays(28L - i * 10L));
            } else if (i == titles.length / 2) {
                progress.setStatus(SyllabusProgress.Status.IN_PROGRESS);
            } else {
                progress.setStatus(SyllabusProgress.Status.NOT_STARTED);
            }
            syllabusProgressRepository.save(progress);
        }
    }

    private void seedTests(Batch batch, List<Student> students, Subject first, Subject second, LocalDate today) {
        for (int i = 1; i <= 4; i++) {
            Subject subject = i % 2 == 0 ? second : first;
            AcademicTest test = test(batch, subject, AcademicTest.Type.DAILY, subject.getName() + " daily test " + i,
                    today.minusDays(14L - i * 3L), new BigDecimal("25"), PublicationStatus.PUBLISHED);
            for (int s = 0; s < students.size(); s++) {
                testResult(test, students.get(s), new BigDecimal(12 + ((s * 7 + i * 3) % 13)), false);
            }
        }
        AcademicTest weekly = test(batch, first, AcademicTest.Type.WEEKLY, first.getName() + " weekly test",
                today.minusDays(6), new BigDecimal("50"), PublicationStatus.PUBLISHED);
        weekly.setWeekNumber(weekly.getTestDate().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        for (int s = 0; s < students.size(); s++) {
            testResult(weekly, students.get(s), s == students.size() - 1 ? null : new BigDecimal(28 + (s * 5) % 20),
                    s == students.size() - 1);
        }
        // A draft with only some marks entered, so the faculty dashboard shows pending marks.
        AcademicTest draft = test(batch, second, AcademicTest.Type.DAILY, second.getName() + " daily test 5",
                today.minusDays(1), new BigDecimal("25"), PublicationStatus.DRAFT);
        testResult(draft, students.get(0), new BigDecimal("19"), false);
    }

    private AcademicTest test(Batch batch, Subject subject, AcademicTest.Type type, String title, LocalDate date,
                              BigDecimal max, PublicationStatus status) {
        AcademicTest test = new AcademicTest();
        test.setTestType(type);
        test.setTitle(title);
        test.setBatch(batch);
        test.setSubject(subject);
        test.setTestDate(date);
        test.setMaxMarks(max);
        test.setStatus(status);
        if (status == PublicationStatus.PUBLISHED) {
            test.setPublishedAt(Instant.now());
        }
        return testRepository.save(test);
    }

    private void testResult(AcademicTest test, Student student, BigDecimal marks, boolean absent) {
        AcademicTestResult result = new AcademicTestResult();
        result.setTest(test);
        result.setStudent(student);
        result.setMarksObtained(marks);
        result.setAbsent(absent);
        testResultRepository.save(result);
    }

    private void seedExams(Batch batch, List<Student> students, Subject upcomingSubject, Subject pastSubject,
                           Faculty faculty, LocalDate today) {
        var classExam = examTypeRepository.findAllByOrderByDisplayOrderAscNameAsc().get(0);
        var modelExam = examTypeRepository.findAllByOrderByDisplayOrderAscNameAsc().get(1);

        Exam past = exam(batch, pastSubject, classExam, pastSubject.getName() + " class exam", today.minusDays(9), faculty,
                PublicationStatus.PUBLISHED);
        for (int s = 0; s < students.size(); s++) {
            BigDecimal marks = new BigDecimal(52 + (s * 11) % 45);
            ExamResult result = new ExamResult();
            result.setExam(past);
            result.setStudent(students.get(s));
            result.setMarksObtained(marks);
            result.setGrade(gradeScale.gradeFor(MarksValidator.percentage(marks, past.getMaxMarks())));
            examResultRepository.save(result);
        }
        exam(batch, upcomingSubject, modelExam, upcomingSubject.getName() + " model exam", today.plusDays(7), faculty,
                PublicationStatus.DRAFT);
    }

    private Exam exam(Batch batch, Subject subject, com.coyotai.education.assessment.ExamType type, String name, LocalDate date,
                      Faculty faculty, PublicationStatus status) {
        Exam exam = new Exam();
        exam.setName(name);
        exam.setExamType(type);
        exam.setCourse(batch.getCourse());
        exam.setBatch(batch);
        exam.setSubject(subject);
        exam.setExamDate(date);
        exam.setStartTime(LocalTime.of(10, 0));
        exam.setEndTime(LocalTime.of(13, 0));
        exam.setMaxMarks(new BigDecimal("100"));
        exam.setPassingMarks(new BigDecimal("40"));
        exam.setFaculty(faculty);
        exam.setStatus(status);
        if (status == PublicationStatus.PUBLISHED) {
            exam.setPublishedAt(Instant.now());
        }
        return examRepository.save(exam);
    }

    private DisciplineRecord discipline(Student student, String typeCode, LocalDate date, String description,
                                        String action, DisciplineRecord.Status status) {
        DisciplineRecord record = new DisciplineRecord();
        record.setStudent(student);
        record.setBatch(student.getBatch());
        record.setDisciplineType(disciplineTypeRepository.findAll().stream()
                .filter(type -> type.getCode().equals(typeCode)).findFirst().orElseThrow());
        record.setIncidentDate(date);
        record.setDescription(description);
        record.setActionTaken(action);
        record.setStatus(status);
        return disciplineRecordRepository.save(record);
    }

    private StudentFine fine(Student student, DisciplineRecord record, String reason, BigDecimal amount,
                             LocalDate fineDate, LocalDate dueDate, StudentFine.Status status) {
        StudentFine fine = new StudentFine();
        fine.setStudent(student);
        fine.setDisciplineRecord(record);
        fine.setReason(reason);
        fine.setAmount(amount);
        fine.setFineDate(fineDate);
        fine.setDueDate(dueDate);
        fine.setStatus(status);
        return fineRepository.save(fine);
    }

    private void meeting(Student student, Mentor mentor, LocalDate date, ParentMeeting.Status status, String discussion) {
        ParentMeeting meeting = new ParentMeeting();
        meeting.setStudent(student);
        meeting.setParent(student.getParent());
        meeting.setMentor(mentor);
        meeting.setMeetingDate(date);
        meeting.setDiscussion(discussion);
        meeting.setStatus(status);
        if (status == ParentMeeting.Status.COMPLETED) {
            meeting.setActionItems("Extra practice sessions twice a week");
            meeting.setFollowUpDate(date.plusDays(30));
        }
        parentMeetingRepository.save(meeting);
    }

    private void seedFees(Student student, String discountAmount, String reason, LocalDate yearStart, boolean paidSecond) {
        Course course = student.getCourse();
        BigDecimal fee = course.getFeeAmount();
        BigDecimal discount = new BigDecimal(discountAmount).setScale(2);
        BigDecimal net = fee.subtract(discount);

        StudentFee plan = new StudentFee();
        plan.setStudent(student);
        plan.setCourse(course);
        plan.setAcademicYear(student.getAcademicYear());
        plan.setTitle(course.getName() + " " + student.getAcademicYear().getName());
        plan.setTotalAmount(fee);
        plan.setDiscountAmount(discount);
        plan.setDiscountReason(reason);
        plan.setNetAmount(net);
        plan.setStatus(StudentFee.Status.ACTIVE);
        feeRepository.save(plan);

        LocalDate today = config.today();
        List<BigDecimal> parts = FeeCalculator.split(net, course.getDefaultInstallments());
        for (int i = 0; i < parts.size(); i++) {
            FeeInstallment installment = new FeeInstallment();
            installment.setStudentFee(plan);
            installment.setStudent(student);
            installment.setInstallmentNo(i + 1);
            installment.setLabel("Installment " + (i + 1));
            installment.setDueDate(yearStart.plusDays(40).plusMonths(i * 3L));
            installment.setAmount(parts.get(i));
            installment.setPaidAmount(Money.ZERO);
            FeeCalculator.recalculate(installment, today);
            installmentRepository.save(installment);

            boolean pay = i == 0 || (i == 1 && paidSecond && !installment.getDueDate().isAfter(today));
            if (pay) {
                Payment payment = new Payment();
                payment.setInstallment(installment);
                payment.setStudent(student);
                payment.setAmount(installment.getAmount());
                payment.setPaymentDate(installment.getDueDate().minusDays(2).isAfter(today) ? today : installment.getDueDate().minusDays(2));
                payment.setPaymentMethod(i == 0 ? PaymentMethod.BANK_TRANSFER : PaymentMethod.UPI);
                payment.setReferenceNumber("REF" + student.getId() + (i + 1));
                payment.setReceiptNumber(FeeCalculator.nextReceiptNumber(config.receiptPrefix(),
                        payment.getPaymentDate().getYear(),
                        paymentRepository.findMaxReceiptNumber(config.receiptPrefix() + "-RCPT-"
                                + payment.getPaymentDate().getYear() + "-").orElse(null)));
                paymentRepository.save(payment);
                installment.setPaidAmount(installment.getAmount());
                FeeCalculator.recalculate(installment, today);
            }
            plan.getInstallments().add(installment);
        }
    }

    private void seedProgressCard(Student student, Batch batch, LocalDate today) {
        ProgressCard card = new ProgressCard();
        card.setStudent(student);
        card.setBatch(batch);
        card.setTitle("Monthly progress - " + today.minusMonths(1).getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH));
        card.setPeriodStart(today.minusDays(30));
        card.setPeriodEnd(today.minusDays(1));
        card.setAttendancePercentage(new BigDecimal("92.30"));
        card.setDailyTestAverage(new BigDecimal("74.00"));
        card.setWeeklyTestAverage(new BigDecimal("70.00"));
        card.setExamAverage(new BigDecimal("78.00"));
        card.setPerformanceScore(new BigDecimal("76.40"));
        card.setGrade(gradeScale.gradeFor(76.4));
        card.setSyllabusCompletion(new BigDecimal("45.00"));
        card.setDisciplineCount(1);
        card.setPendingFineAmount(new BigDecimal("500.00"));
        card.setMentorRemarks("Consistent effort in Accounts. Needs more practice in Taxation problems.");
        card.setStatus(PublicationStatus.PUBLISHED);
        card.setPublishedAt(Instant.now());
        String[][] items = {{"Accounts", "82.00"}, {"Taxation", "68.00"}, {"Law", "71.00"}, {"Costing", "80.00"}};
        for (int i = 0; i < items.length; i++) {
            ProgressCardItem item = new ProgressCardItem();
            item.setProgressCard(card);
            item.setSubjectName(items[i][0]);
            item.setOverallPercentage(new BigDecimal(items[i][1]));
            item.setGrade(gradeScale.gradeFor(Double.parseDouble(items[i][1])));
            item.setDisplayOrder(i);
            card.getItems().add(item);
        }
        progressCardRepository.save(card);
        String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
        notificationService.publish(NotificationEvent.PROGRESS_CARD_PUBLISHED, student,
                messageFactory.progressCard(parentName, student.getFullName(), card.getTitle(), "76.4%", card.getGrade()));
    }
}
