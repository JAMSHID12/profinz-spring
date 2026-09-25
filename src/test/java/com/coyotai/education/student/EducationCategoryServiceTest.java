package com.coyotai.education.student;

import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EducationCategoryServiceTest {
    private final EducationCategoryRepository repository = mock(EducationCategoryRepository.class);
    private final EducationCategoryService service = new EducationCategoryService(repository, mock(AuditService.class));
    private EducationCategory existing(boolean active) {
        var category = new EducationCategory(); category.setId(7L); category.setCode("DIPLOMA");
        category.setName("Diploma Completed"); category.setActive(active);
        when(repository.findById(7L)).thenReturn(Optional.of(category)); return category;
    }
    @Test void createsAndNormalizesCategory() {
        var result = service.save(null, new EducationCategoryService.Request("diploma", " Diploma Completed ", 3, true));
        assertThat(result.code()).isEqualTo("DIPLOMA");
        assertThat(result.name()).isEqualTo("Diploma Completed");
        assertThat(result.displayOrder()).isEqualTo(3);
        verify(repository).save(any());
    }
    @Test void duplicateCodeIsRejected() {
        when(repository.existsByCodeIgnoreCase("DIPLOMA")).thenReturn(true);
        assertThatThrownBy(() -> service.save(null, new EducationCategoryService.Request("diploma", "Diploma", 0, true)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repository, never()).save(any());
    }
    @Test void renameAndDeactivatePreserveIdAndCode() {
        existing(true);
        var result = service.save(7L, new EducationCategoryService.Request("DIPLOMA", "Diploma Graduates", 2, false));
        assertThat(result.id()).isEqualTo(7L); assertThat(result.code()).isEqualTo("DIPLOMA");
        assertThat(result.name()).isEqualTo("Diploma Graduates"); assertThat(result.active()).isFalse();
    }
    @Test void changingCodeIsRejected() {
        existing(true);
        assertThatThrownBy(() -> service.save(7L, new EducationCategoryService.Request("OTHER", "Other", 0, true)))
                .isInstanceOf(BusinessRuleException.class);
    }
    @Test void inactiveCategoryCannotBeNewlyAssignedButExistingReferencesAreKept() {
        var category = existing(false);
        assertThatThrownBy(() -> service.select(7L, null)).isInstanceOf(BusinessRuleException.class);
        assertThat(service.select(7L, category)).isSameAs(category);
    }
    @Test void missingOrOtherTenantCategoryCannotBeSelected() {
        assertThatThrownBy(() -> service.select(999L, null)).isInstanceOf(ResourceNotFoundException.class);
    }
}
