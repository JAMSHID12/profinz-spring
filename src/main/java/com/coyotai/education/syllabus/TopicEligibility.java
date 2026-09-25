package com.coyotai.education.syllabus;

import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.student.Student.EducationCategory;

public enum TopicEligibility {
    PLUS_TWO_ONLY, DEGREE_ONLY, BOTH, CATEGORY_ONLY;

    public boolean requires(EducationCategory category) {
        if (this == BOTH) return true;
        if (this == CATEGORY_ONLY) throw new BusinessRuleException("Select an education category for this topic");
        if (category == null) {
            throw new BusinessRuleException("Set the education category for every active student before taking attendance for a restricted topic");
        }
        return this == PLUS_TWO_ONLY ? category == EducationCategory.PLUS_TWO : category == EducationCategory.DEGREE;
    }
}
