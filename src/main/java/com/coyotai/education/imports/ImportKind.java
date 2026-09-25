package com.coyotai.education.imports;

import com.coyotai.education.common.BusinessRuleException;
import java.util.List;

public enum ImportKind {
    STUDENTS("Students", "STUDENT_CREATE", "full_name,parent_name,parent_phone,mobile,email,date_of_birth,admission_date",
            "Sample Student,Sample Parent,+919876543210,,student@example.com,2010-01-15,2026-09-24"),
    FACULTY("Faculty", "FACULTY_MANAGE", "full_name,employee_code,mobile,email,specialization,faculty_type,username,password",
            "Sample Faculty,FAC001,+919876543210,faculty@example.com,Mathematics,FULL_TIME,sample.faculty,Replace-this-password"),
    MENTORS("Mentors", "MENTOR_MANAGE", "full_name,employee_code,mobile,email,specialization,username,password",
            "Sample Mentor,MEN001,+919876543210,mentor@example.com,Science,sample.mentor,Replace-this-password"),
    FEES("Fee structure", "COURSE_MANAGE", "course_id,fee_amount,default_installments", "1,25000.00,3");

    public final String label;
    public final String permission;
    public final List<String> headers;
    public final List<String> example;
    ImportKind(String label, String permission, String headers, String example) {
        this.label = label; this.permission = permission;
        this.headers = List.of(headers.split(",", -1)); this.example = List.of(example.split(",", -1));
    }
    public static ImportKind parse(String value) {
        try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new BusinessRuleException("Choose students, faculty, mentors or fees"); }
    }
}
