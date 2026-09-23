package com.coyotai.education.security;

import com.coyotai.education.common.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeTest {

    @Test
    @DisplayName("Global roles reach every batch")
    void globalScope() {
        DataScope scope = DataScope.global();
        assertThat(scope.canAccessBatch(99L)).isTrue();
        assertThat(scope.canAccessBatchSubject(99L, 7L)).isTrue();
    }

    @Test
    @DisplayName("A mentor reaches every subject of their own batches and nothing else")
    void mentorScope() {
        DataScope scope = new DataScope(false, Set.of(1L), Set.of(), Set.of(), null);
        assertThat(scope.canAccessBatch(1L)).isTrue();
        assertThat(scope.canAccessBatchSubject(1L, 42L)).isTrue();
        assertThat(scope.canAccessBatch(2L)).isFalse();
        assertThatThrownBy(() -> scope.requireBatch(2L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Faculty reach their batches, but only the subjects they teach there")
    void facultyScope() {
        DataScope scope = new DataScope(false, Set.of(), Set.of(1L), Set.of(DataScope.pair(1L, 10L)), null);
        assertThat(scope.canAccessBatch(1L)).isTrue();
        assertThat(scope.canAccessBatchSubject(1L, 10L)).isTrue();
        assertThat(scope.canAccessBatchSubject(1L, 11L)).isFalse();
        assertThatThrownBy(() -> scope.requireBatchSubject(1L, 11L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("An empty scope still produces a valid query filter")
    void emptyScopeQueryFilter() {
        DataScope scope = new DataScope(false, Set.of(), Set.of(), Set.of(), 5L);
        assertThat(scope.batchIdsForQuery()).containsExactly(-1L);
        assertThat(scope.canAccessBatch(1L)).isFalse();
    }
}
