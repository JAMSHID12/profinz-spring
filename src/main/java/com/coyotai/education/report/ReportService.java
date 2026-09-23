package com.coyotai.education.report;

import com.coyotai.education.attendance.AttendanceRepository;
import com.coyotai.education.attendance.AttendanceStatus;
import com.coyotai.education.attendance.AttendanceSummary;
import com.coyotai.education.fee.FeeInstallmentRepository;
import com.coyotai.education.fee.InstallmentStatus;
import com.coyotai.education.fee.PaymentRepository;
import com.coyotai.education.notification.NotificationRepository;
import com.coyotai.education.performance.PerformanceService;
import com.coyotai.education.performance.PerformanceService.BatchRow;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final AttendanceRepository attendanceRepository;
    private final FeeInstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;
    private final PerformanceService performanceService;
    private final ProjectConfigService configService;

    public ReportService(AttendanceRepository attendanceRepository, FeeInstallmentRepository installmentRepository,
                         PaymentRepository paymentRepository, NotificationRepository notificationRepository,
                         PerformanceService performanceService, ProjectConfigService configService) {
        this.attendanceRepository = attendanceRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.notificationRepository = notificationRepository;
        this.performanceService = performanceService;
        this.configService = configService;
    }

    public record AttendanceRow(Long studentId, String studentName, String admissionNumber, String batchName,
                                AttendanceSummary summary) {
    }

    public record AttendanceReport(LocalDate from, LocalDate to, Long batchId, AttendanceSummary totals,
                                   List<AttendanceRow> rows) {
    }

    public record FeeRow(InstallmentStatus status, long installments, BigDecimal billed, BigDecimal paid,
                         BigDecimal pending) {
    }

    public record FeeReport(LocalDate from, LocalDate to, BigDecimal billed, BigDecimal paid, BigDecimal pending,
                            BigDecimal collectedInPeriod, List<FeeRow> rows) {
    }

    public record Count(String label, long count) {
    }

    public record NotificationReport(LocalDate from, LocalDate to, long total, List<Count> byStatus,
                                     List<Count> byEvent, List<Count> byChannel) {
    }

    public record AcademicReport(Long batchId, LocalDate from, LocalDate to, List<BatchRow> students) {
    }

    @Transactional(readOnly = true)
    public AttendanceReport attendance(LocalDate from, LocalDate to, Long batchId) {
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        Map<Long, Object[]> info = new LinkedHashMap<>();
        Map<Long, Map<AttendanceStatus, Long>> counts = new LinkedHashMap<>();
        Map<AttendanceStatus, Long> totals = new EnumMap<>(AttendanceStatus.class);
        for (Object[] row : attendanceRepository.reportByStudent(start, end, batchId)) {
            Long studentId = ((Number) row[0]).longValue();
            info.putIfAbsent(studentId, new Object[]{row[1], row[2], row[3]});
            AttendanceStatus status = (AttendanceStatus) row[4];
            long count = ((Number) row[5]).longValue();
            counts.computeIfAbsent(studentId, key -> new EnumMap<>(AttendanceStatus.class)).merge(status, count, Long::sum);
            totals.merge(status, count, Long::sum);
        }
        List<AttendanceRow> rows = new ArrayList<>();
        info.forEach((id, values) -> rows.add(new AttendanceRow(id, (String) values[0], (String) values[1],
                (String) values[2], AttendanceSummary.fromCounts(counts.get(id)))));
        return new AttendanceReport(start, end, batchId, AttendanceSummary.fromCounts(totals), rows);
    }

    @Transactional(readOnly = true)
    public FeeReport fees(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        List<FeeRow> rows = new ArrayList<>();
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        for (Object[] row : installmentRepository.summaryByStatus(start, end)) {
            FeeRow fee = new FeeRow((InstallmentStatus) row[0], ((Number) row[1]).longValue(),
                    Money.scale((BigDecimal) row[2]), Money.scale((BigDecimal) row[3]), Money.scale((BigDecimal) row[4]));
            rows.add(fee);
            billed = billed.add(fee.billed());
            paid = paid.add(fee.paid());
            if (fee.status() != InstallmentStatus.WAIVED) {
                pending = pending.add(fee.pending());
            }
        }
        return new FeeReport(start, end, Money.scale(billed), Money.scale(paid), Money.scale(pending),
                Money.scale(paymentRepository.sumCollectedBetween(start, end)), rows);
    }

    @Transactional(readOnly = true)
    public NotificationReport notifications(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        Instant startInstant = start.atStartOfDay(configService.zoneId()).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(configService.zoneId()).toInstant();
        List<Count> byStatus = counts(notificationRepository.countByStatusBetween(startInstant, endInstant));
        return new NotificationReport(start, end, byStatus.stream().mapToLong(Count::count).sum(), byStatus,
                counts(notificationRepository.countByEventBetween(startInstant, endInstant)),
                counts(notificationRepository.countByChannelBetween(startInstant, endInstant)));
    }

    @Transactional(readOnly = true)
    public AcademicReport academic(Long batchId, LocalDate from, LocalDate to) {
        return new AcademicReport(batchId, from, to, performanceService.forBatch(batchId, from, to));
    }

    private List<Count> counts(List<Object[]> rows) {
        return rows.stream().map(row -> new Count(String.valueOf(row[0]), ((Number) row[1]).longValue())).toList();
    }
}
