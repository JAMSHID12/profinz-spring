package com.coyotai.education.assessment;

import java.util.Set;

/**
 * DRAFT -> REVIEW -> PUBLISHED. Marks can change until publication; students only ever see
 * PUBLISHED. Publishing can be withdrawn (back to REVIEW) to correct a mistake, which is audited.
 */
public enum PublicationStatus {
    DRAFT,
    REVIEW,
    PUBLISHED;

    public boolean canMoveTo(PublicationStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<PublicationStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> Set.of(REVIEW, PUBLISHED);
            case REVIEW -> Set.of(DRAFT, PUBLISHED);
            case PUBLISHED -> Set.of(REVIEW);
        };
    }

    /** Marks are frozen once results are visible to students. */
    public boolean marksEditable() {
        return this != PUBLISHED;
    }
}
