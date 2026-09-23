package com.coyotai.education.student;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.tenant.TenantIdentifierResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

/** Database row locks serialize allocations across backend instances within the student transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StudentIdentifiers {
    private final JdbcTemplate jdbc;
    private final TenantIdentifierResolver tenants;
    private final StudentRepository students;

    public StudentIdentifiers(JdbcTemplate jdbc, TenantIdentifierResolver tenants, StudentRepository students) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.students = students;
    }

    private long next(String key) {
        long client = tenants.resolveCurrentTenantIdentifier();
        jdbc.update("INSERT INTO student_identifier_sequences (client_id, sequence_key, next_value) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE next_value=next_value+1", client, key);
        return jdbc.queryForObject("SELECT next_value FROM student_identifier_sequences WHERE client_id=? AND sequence_key=? FOR UPDATE",
                Long.class, client, key);
    }

    public String nextAdmission(int year) {
        String admission;
        do { admission = "ADM-" + year + "-" + String.format(Locale.ROOT, "%06d", next("admission:" + year)); }
        while (students.existsByAdmissionNumberIgnoreCase(admission));
        return admission;
    }

    public String nextStudentCode(Batch batch) {
        String code;
        do {
            code = segment(batch.getCourse().getCode()) + "-" + segment(batch.getName()) + "-"
                    + String.format(Locale.ROOT, "%06d", next("student"));
        } while (students.existsByStudentCodeIgnoreCase(code));
        return code;
    }

    static String segment(String value) {
        String clean = value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return clean.isEmpty() ? "CLASS" : clean.substring(0, Math.min(7, clean.length()));
    }
}
