package com.coyotai.education.imports;

import com.coyotai.education.academic.*;
import com.coyotai.education.auth.UserRepository;
import com.coyotai.education.common.*;
import com.coyotai.education.platform.*;
import com.coyotai.education.staff.*;
import com.coyotai.education.student.*;
import com.coyotai.education.tenant.TenantIdentifierResolver;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMINISTRATIVE')")
public class ExcelImportService {
    private final ExcelFiles files;
    private final StudentService students;
    private final StudentRepository studentRepository;
    private final StaffService staff;
    private final MentorRepository mentors;
    private final FacultyRepository faculty;
    private final UserRepository users;
    private final BatchRepository batches;
    private final CourseRepository courses;
    private final CourseService courseService;
    private final Validator validator;
    private final EntityManager entityManager;
    private final TenantIdentifierResolver tenants;
    private final ProjectConfigService config;
    public ExcelImportService(ExcelFiles files, StudentService students, StudentRepository studentRepository,
            StaffService staff, MentorRepository mentors, FacultyRepository faculty, UserRepository users,
            BatchRepository batches, CourseRepository courses, CourseService courseService,
            Validator validator, EntityManager entityManager, TenantIdentifierResolver tenants, ProjectConfigService config) {
        this.files=files;this.students=students;this.studentRepository=studentRepository;this.staff=staff;
        this.mentors=mentors;this.faculty=faculty;this.users=users;this.batches=batches;this.courses=courses;
        this.courseService=courseService;this.validator=validator;this.entityManager=entityManager;this.tenants=tenants;this.config=config;
    }
    public record RowPreview(int row, Map<String,String> values, List<String> errors) { }
    public record Result(String kind, String digest, int total, int valid, int invalid, int saved, List<RowPreview> rows) { }
    private record Prepared(int row, Object request, Long target, Map<String,String> values, List<String> errors) { }

    @Transactional(readOnly=true)
    public byte[] template(ImportKind kind) {
        authorize(kind);
        List<List<String>> lookup = new ArrayList<>();
        if(kind==ImportKind.FEES) for(Course c:courses.findAllByOrderByDisplayOrderAscNameAsc())
            lookup.add(List.of(c.getId().toString(),c.getName(),c.getCode()));
        return files.template(kind,lookup);
    }
    @Transactional(readOnly=true)
    public Result preview(ImportKind kind, MultipartFile file, Long batchId) {
        authorize(kind);return result(kind,digest(file, kind, batchId),prepare(kind,file,batchId),0);
    }
    @Transactional
    public Result save(ImportKind kind, MultipartFile file, String expectedDigest, Long batchId) {
        authorize(kind);
        String digest=digest(file, kind, batchId);
        if(expectedDigest==null || !digest.equals(expectedDigest)) throw new BusinessRuleException("The file changed. Validate it again before saving.");
        // Serialize imports for this tenant. Revalidation happens inside the same transaction as all writes.
        entityManager.createNativeQuery("SELECT id FROM clients WHERE id = :id FOR UPDATE")
                .setParameter("id",tenants.resolveCurrentTenantIdentifier()).getSingleResult();
        List<Prepared> rows=prepare(kind,file,batchId);
        if(rows.stream().anyMatch(r->!r.errors().isEmpty())) return result(kind,digest,rows,0);
        for(Prepared row:rows) {
            switch(kind) {
                case STUDENTS -> students.create((StudentDtos.StudentRequest)row.request());
                case FACULTY -> staff.createFaculty((StaffDtos.StaffRequest)row.request());
                case MENTORS -> staff.createMentor((StaffDtos.StaffRequest)row.request());
                case FEES -> courseService.updateCourse(row.target(),(AcademicDtos.CourseRequest)row.request());
            }
        }
        entityManager.flush(); // Any constraint failure rolls back the entire upload.
        return result(kind,digest,rows,rows.size());
    }
    private void authorize(ImportKind kind) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals(kind.permission)))
            throw new AccessDeniedException("You do not have permission to import "+kind.label);
        ModuleCode module=kind==ImportKind.FEES?ModuleCode.FEES:ModuleCode.ACADEMICS;
        if(!config.isModuleEnabled(ModuleCode.ADMINISTRATION) || !config.isModuleEnabled(module))
            throw new AccessDeniedException("This import module is disabled");
    }
    private List<Prepared> prepare(ImportKind kind, MultipartFile file, Long selectedBatchId) {
        if (kind == ImportKind.STUDENTS) {
            if (selectedBatchId == null || selectedBatchId <= 0) throw new BusinessRuleException("Choose a batch before validating students.");
            Batch selected = batches.findDetail(selectedBatchId).orElseThrow(() -> new BusinessRuleException("The selected batch no longer exists. Choose another batch."));
            if (selected.getStatus() != Batch.Status.ACTIVE) throw new BusinessRuleException("Choose an active batch.");
        }
        List<Prepared> output=new ArrayList<>();Set<String> seen=new HashSet<>();Set<String> employeeCodes=new HashSet<>();
        for(ExcelFiles.RowData row:files.read(kind,file)) {
            Map<String,String> v=row.values();List<String> errors=new ArrayList<>();Object request=null;Long target=null;
            try {
                switch(kind) {
                    case STUDENTS -> {
                        String name=required(v,"full_name"),phone=required(v,"parent_phone");
                        long batchId=selectedBatchId;
                        Batch batch=batches.findDetail(batchId).orElseThrow(()->new IllegalArgumentException("batch_id does not exist"));
                        if(batch.getStatus()!=Batch.Status.ACTIVE) errors.add("batch_id must reference an active batch");
                        String key=name.toLowerCase(Locale.ROOT)+"|"+batchId+"|"+phone.replace("+","");
                        if(!seen.add(key)) errors.add("Duplicate student in this workbook");
                        boolean exists=studentRepository.search(name,null,batchId,null,true,List.of(-1L)).stream()
                                .anyMatch(s->s.getFullName().equalsIgnoreCase(name) && s.getParent()!=null
                                        && Objects.equals(normalizePhone(s.getParent().getPhoneNumber()),normalizePhone(phone)));
                        if(exists) errors.add("A student with this name, batch and parent phone already exists");
                        request=new StudentDtos.StudentRequest(name,date(v,"date_of_birth"),null,optional(v,"mobile"),optional(v,"email"),null,
                                required(v,"parent_name"),phone,false,batchId,null,date(v,"admission_date"),Student.Status.ACTIVE,false,null,null);
                    }
                    case FACULTY, MENTORS -> {
                        String code=required(v,"employee_code"),username=required(v,"username");
                        String password=required(v,"password");
                        if(password.equals("Replace-this-password")) errors.add("Replace the example password with a private initial password");
                        if(!seen.add(username.toLowerCase(Locale.ROOT)) || users.existsByUsernameIgnoreCase(username)) errors.add("Username is already in use");
                        boolean existing=kind==ImportKind.FACULTY?faculty.existsByEmployeeCodeIgnoreCase(code):mentors.existsByEmployeeCodeIgnoreCase(code);
                        if(!employeeCodes.add(code.toLowerCase(Locale.ROOT)) || existing) errors.add("Employee code is already in use");
                        Faculty.Type type=kind==ImportKind.FACULTY?Faculty.Type.valueOf(required(v,"faculty_type")):null;
                        request=new StaffDtos.StaffRequest(required(v,"full_name"),code,optional(v,"mobile"),optional(v,"email"),
                                optional(v,"specialization"),type,true,username,password);
                    }
                    case FEES -> {
                        target=id(v,"course_id");
                        Course c=courses.findById(target).orElseThrow(()->new IllegalArgumentException("course_id does not exist"));
                        if(!seen.add(target.toString())) errors.add("Only one fee structure row is allowed per course");
                        BigDecimal amount=new BigDecimal(required(v,"fee_amount"));
                        int installments=Integer.parseInt(required(v,"default_installments"));
                        request=new AcademicDtos.CourseRequest(c.getCode(),c.getName(),c.getDescription(),c.getStatus(),c.getDisplayOrder(),amount,installments);
                    }
                }
                if(request!=null) validator.validate(request).stream().sorted(Comparator.comparing(x->x.getPropertyPath().toString()))
                        .forEach(x->errors.add(x.getPropertyPath()+": "+x.getMessage()));
            } catch(IllegalArgumentException | java.time.format.DateTimeParseException ex) {
                errors.add("Invalid or missing value. Check required fields, IDs, YYYY-MM-DD dates, true/false values and faculty type.");
            }
            Map<String,String> safe=new LinkedHashMap<>(v);
            if(safe.containsKey("password")) safe.put("password",safe.get("password").isBlank()?"":"••••••••");
            output.add(new Prepared(row.row(),request,target,safe,errors));
        }
        return output;
    }
    private Result result(ImportKind kind,String digest,List<Prepared> rows,int saved) {
        int invalid=(int)rows.stream().filter(r->!r.errors().isEmpty()).count();
        return new Result(kind.name(),digest,rows.size(),rows.size()-invalid,invalid,saved,
                rows.stream().map(r->new RowPreview(r.row(),r.values(),r.errors())).toList());
    }
    private String digest(MultipartFile file, ImportKind kind, Long batchId) {
        if(file==null || file.getSize()>2*1024*1024) throw new BusinessRuleException("Upload an .xlsx file up to 2 MB");
        try {
            var hash = java.security.MessageDigest.getInstance("SHA-256");
            hash.update((kind.name()+":"+(kind==ImportKind.STUDENTS ? batchId : "")+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash.digest(file.getBytes()));
        }
        catch(Exception ex) { throw new BusinessRuleException("Unable to read upload"); }
    }
    private String required(Map<String,String> row,String field) {
        String value=row.get(field);if(value==null || value.isBlank()) throw new IllegalArgumentException(field+" is required");return value;
    }
    private String optional(Map<String,String> row,String field) { String v=row.get(field);return v==null || v.isBlank()?null:v; }
    private long id(Map<String,String> row,String field) { long v=Long.parseLong(required(row,field));if(v<=0)throw new IllegalArgumentException();return v; }
    private LocalDate date(Map<String,String> row,String field) { String v=optional(row,field);return v==null?null:LocalDate.parse(v); }
    private String normalizePhone(String value) {return value==null?null:value.replace("+","");}
}

