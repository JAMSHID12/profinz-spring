package com.coyotai.education.student;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Contact owned by a student; embedded snapshots also preserve notification/meeting history. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ParentContact {
    @Column(name = "parent_name", length = 150)
    private String name;
    @Column(name = "parent_relation", length = 30)
    private String relation;
    @Column(name = "parent_phone_number", length = 20)
    private String phoneNumber;
    @Column(name = "parent_whatsapp_number", length = 20)
    private String whatsappNumber;
    @Column(name = "parent_email", length = 150)
    private String email;
    @Column(name = "parent_whatsapp_opt_in")
    private boolean whatsappOptIn;
    @Column(name = "parent_active")
    private boolean active = true;

    public String resolveWhatsappNumber() {
        return whatsappNumber == null || whatsappNumber.isBlank() ? phoneNumber : whatsappNumber;
    }

    public static ParentContact copyOf(ParentContact source) {
        if (source == null) return null;
        ParentContact copy = new ParentContact();
        copy.name = source.name;
        copy.relation = source.relation;
        copy.phoneNumber = source.phoneNumber;
        copy.whatsappNumber = source.whatsappNumber;
        copy.email = source.email;
        copy.whatsappOptIn = source.whatsappOptIn;
        copy.active = source.active;
        return copy;
    }
}
