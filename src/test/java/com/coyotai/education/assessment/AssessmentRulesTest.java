package com.coyotai.education.assessment;

import com.coyotai.education.common.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentRulesTest {

    private static final BigDecimal MAX = new BigDecimal("100");

    @Test
    @DisplayName("Marks must be between 0 and the maximum")
    void marksBounds() {
        assertThat(MarksValidator.validate(new BigDecimal("0"), false, MAX, "A")).isEqualByComparingTo("0");
        assertThat(MarksValidator.validate(new BigDecimal("100"), false, MAX, "A")).isEqualByComparingTo("100");
        assertThatThrownBy(() -> MarksValidator.validate(new BigDecimal("100.5"), false, MAX, "Ahmed"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exceed the maximum");
        assertThatThrownBy(() -> MarksValidator.validate(new BigDecimal("-1"), false, MAX, "Ahmed"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("negative");
    }

    @Test
    @DisplayName("Absent students have no marks; present students must have marks")
    void absence() {
        assertThat(MarksValidator.validate(new BigDecimal("50"), true, MAX, "A")).isNull();
        assertThatThrownBy(() -> MarksValidator.validate(null, false, MAX, "Ahmed"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("or mark them absent");
    }

    @Test
    @DisplayName("Percentages are rounded to one decimal")
    void percentage() {
        assertThat(MarksValidator.percentage(new BigDecimal("78"), MAX)).isEqualTo(78.0);
        assertThat(MarksValidator.percentage(new BigDecimal("17"), new BigDecimal("25"))).isEqualTo(68.0);
        assertThat(MarksValidator.percentage(null, MAX)).isNull();
    }

    @Test
    @DisplayName("Results follow DRAFT -> REVIEW -> PUBLISHED; published can only go back to review")
    void publicationWorkflow() {
        assertThat(PublicationStatus.DRAFT.canMoveTo(PublicationStatus.REVIEW)).isTrue();
        assertThat(PublicationStatus.REVIEW.canMoveTo(PublicationStatus.PUBLISHED)).isTrue();
        assertThat(PublicationStatus.PUBLISHED.canMoveTo(PublicationStatus.REVIEW)).isTrue();
        assertThat(PublicationStatus.PUBLISHED.canMoveTo(PublicationStatus.DRAFT)).isFalse();
        assertThat(PublicationStatus.DRAFT.canMoveTo(PublicationStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("Marks are frozen once published")
    void marksLockedWhenPublished() {
        assertThat(PublicationStatus.DRAFT.marksEditable()).isTrue();
        assertThat(PublicationStatus.REVIEW.marksEditable()).isTrue();
        assertThat(PublicationStatus.PUBLISHED.marksEditable()).isFalse();
    }
}
