package com.coyotai.education.common;

/** Compact reference to another record in API responses: its id and a display name. */
public record Ref(Long id, String name) {

    public static Ref of(Long id, String name) {
        return id == null ? null : new Ref(id, name);
    }
}
