package com.coyotai.education.discipline;

import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.discipline.DisciplineDtos.FineRequest;
import com.coyotai.education.discipline.DisciplineDtos.FineResponse;
import com.coyotai.education.discipline.DisciplineDtos.FineStatusRequest;
import com.coyotai.education.discipline.DisciplineDtos.RecordRequest;
import com.coyotai.education.discipline.DisciplineDtos.RecordResponse;
import com.coyotai.education.discipline.DisciplineDtos.TypeRequest;
import com.coyotai.education.discipline.DisciplineDtos.TypeResponse;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Discipline records, student fines and the configurable list of discipline types. */
@Service
public class DisciplineService {

    private final DisciplineRecordRepository recordRepository;
    private final StudentFineRepository fineRepository;
    private final DisciplineTypeRepository typeRepository;
    private final StudentService studentService;
    private final DataScopeService dataScopeService;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public DisciplineService(DisciplineRecordRepository recordRepository, StudentFineRepository fineRepository,
                             DisciplineTypeRepository typeRepository, StudentService studentService,
                             DataScopeService dataScopeService, NotificationService notificationService,
                             NotificationMessageFactory messageFactory, ProjectConfigService configService,
                             AuditService auditService) {
        this.recordRepository = recordRepository;
        this.fineRepository = fineRepository;
        this.typeRepository = typeRepository;
        this.studentService = studentService;
        this.dataScopeService = dataScopeService;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.configService = configService;
        this.auditService = auditService;
    }

    // ---- Discipline records -------------------------------------------------

    @Transactional(readOnly = true)
    public List<RecordResponse> records(Long studentId, Long batchId, LocalDate from, LocalDate to) {
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.minusMonths(3) : from;
        return recordRepository.search(studentId, batchId, start, end, scope.isGlobal(), scope.batchIdsForQuery())
                .stream().map(RecordResponse::from).toList();
    }

    @Transactional
    public RecordResponse createRecord(RecordRequest request) {
        Student student = studentService.getDetail(request.studentId());
        dataScopeService.requireStudent(student);
        if (request.incidentDate().isAfter(configService.today())) {
            throw new BusinessRuleException("The incident date cannot be in the future");
        }
        DisciplineType type = typeRepository.findById(request.disciplineTypeId())
                .orElseThrow(() -> ResourceNotFoundException.of("Discipline type", request.disciplineTypeId()));
        DisciplineRecord record = new DisciplineRecord();
        record.setStudent(student);
        record.setBatch(student.getBatch());
        record.setDisciplineType(type);
        record.setIncidentDate(request.incidentDate());
        record.setDescription(request.description().trim());
        record.setActionTaken(blankToNull(request.actionTaken()));
        record.setStatus(request.status() == null ? DisciplineRecord.Status.OPEN : request.status());
        recordRepository.save(record);
        auditService.record("DisciplineRecord", record.getId(), AuditService.CREATE,
                type.getName() + " recorded for " + student.getFullName() + " on " + request.incidentDate());

        if (request.fineAmount() != null) {
            createFine(student, record, type.getName(), request.fineAmount(), request.incidentDate(),
                    request.fineDueDate(), null);
        }
        return RecordResponse.from(record);
    }

    @Transactional
    public RecordResponse updateRecord(Long id, RecordRequest request) {
        DisciplineRecord record = recordRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Discipline record", id));
        dataScopeService.requireStudent(record.getStudent());
        record.setDisciplineType(typeRepository.findById(request.disciplineTypeId())
                .orElseThrow(() -> ResourceNotFoundException.of("Discipline type", request.disciplineTypeId())));
        record.setIncidentDate(request.incidentDate());
        record.setDescription(request.description().trim());
        record.setActionTaken(blankToNull(request.actionTaken()));
        record.setStatus(request.status() == null ? record.getStatus() : request.status());
        auditService.record("DisciplineRecord", id, AuditService.UPDATE,
                "Updated discipline record of " + record.getStudent().getFullName());
        return RecordResponse.from(record);
    }

    // ---- Fines --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FineResponse> fines(Long studentId, StudentFine.Status status, Long batchId) {
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        return fineRepository.search(studentId, status, batchId, scope.isGlobal(), scope.batchIdsForQuery())
                .stream().map(FineResponse::from).toList();
    }

    @Transactional
    public FineResponse createFine(FineRequest request) {
        Student student = studentService.getDetail(request.studentId());
        dataScopeService.requireStudent(student);
        return FineResponse.from(createFine(student, null, request.reason().trim(), request.amount(),
                request.fineDate(), request.dueDate(), blankToNull(request.remarks())));
    }

    private StudentFine createFine(Student student, DisciplineRecord record, String reason, BigDecimal amount,
                                   LocalDate fineDate, LocalDate dueDate, String remarks) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("A fine must be greater than zero");
        }
        if (dueDate != null && dueDate.isBefore(fineDate)) {
            throw new BusinessRuleException("The due date cannot be before the fine date");
        }
        StudentFine fine = new StudentFine();
        fine.setStudent(student);
        fine.setDisciplineRecord(record);
        fine.setReason(reason);
        fine.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        fine.setFineDate(fineDate);
        fine.setDueDate(dueDate);
        fine.setStatus(StudentFine.Status.PENDING);
        fine.setRemarks(remarks);
        fineRepository.save(fine);
        auditService.record("StudentFine", fine.getId(), AuditService.CREATE,
                "Fine of " + fine.getAmount().toPlainString() + " for " + student.getFullName() + " (" + reason + ")");
        String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
        notificationService.publish(NotificationEvent.FINE_CREATED, student,
                messageFactory.fine(parentName, student.getFullName(), reason, fine.getAmount(), dueDate));
        return fine;
    }

    /** PENDING fines can be paid, waived or cancelled; nothing moves back to PENDING. */
    @Transactional
    public FineResponse changeFineStatus(Long id, FineStatusRequest request) {
        StudentFine fine = fineRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Fine", id));
        dataScopeService.requireStudent(fine.getStudent());
        if (fine.getStatus() != StudentFine.Status.PENDING) {
            throw new BusinessRuleException("Only pending fines can change status (this one is " + fine.getStatus() + ")");
        }
        if (request.status() == StudentFine.Status.PENDING) {
            throw new BusinessRuleException("The fine is already pending");
        }
        fine.setStatus(request.status());
        if (request.status() == StudentFine.Status.PAID) {
            fine.setPaidDate(request.paidDate() == null ? configService.today() : request.paidDate());
            fine.setPaymentReference(blankToNull(request.paymentReference()));
        }
        if (request.remarks() != null && !request.remarks().isBlank()) {
            fine.setRemarks(request.remarks().trim());
        }
        auditService.record("StudentFine", id, AuditService.STATUS_CHANGE,
                "Fine of " + fine.getAmount().toPlainString() + " for " + fine.getStudent().getFullName()
                        + " marked " + request.status());
        return FineResponse.from(fine);
    }

    // ---- Discipline types ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<TypeResponse> types() {
        return typeRepository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(TypeResponse::from).toList();
    }

    @Transactional
    public TypeResponse saveType(Long id, TypeRequest request) {
        String code = request.code().trim().toUpperCase().replace(' ', '_');
        DisciplineType type = id == null ? new DisciplineType()
                : typeRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Discipline type", id));
        if ((type.getId() == null || !code.equalsIgnoreCase(type.getCode())) && typeRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("A discipline type with code " + code + " already exists");
        }
        type.setCode(code);
        type.setName(request.name().trim());
        type.setDefaultFineAmount(request.defaultFineAmount());
        type.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        type.setActive(request.active() == null || request.active());
        typeRepository.save(type);
        auditService.record("DisciplineType", type.getId(), id == null ? AuditService.CREATE : AuditService.UPDATE,
                (id == null ? "Created" : "Updated") + " discipline type " + type.getName());
        return TypeResponse.from(type);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
