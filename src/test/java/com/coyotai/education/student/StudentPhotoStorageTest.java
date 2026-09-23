package com.coyotai.education.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.coyotai.education.common.BusinessRuleException;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class StudentPhotoStorageTest {
    @TempDir Path directory;
    @Test void rejectsEmptyAndDisguisedFiles() {
        var storage = new StudentPhotoStorage(directory.toString());
        assertThatThrownBy(() -> storage.validate(new MockMultipartFile("photo",new byte[0]))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> storage.validate(new MockMultipartFile("photo","fake.jpg","image/jpeg","<script>bad</script>".getBytes()))).isInstanceOf(BusinessRuleException.class);
    }
    @Test void removesNewPhotoOnRollbackAndKeepsPrevious() throws Exception {
        var storage = new StudentPhotoStorage(directory.toString());
        String old = java.util.UUID.randomUUID()+".jpg";
        Files.write(directory.resolve(old),new byte[]{1});
        TransactionSynchronizationManager.initSynchronization();
        try {
            String fresh = storage.store(new byte[]{2},old);
            assertThat(directory.resolve(fresh)).exists();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(directory.resolve(fresh)).doesNotExist();
            assertThat(directory.resolve(old)).exists();
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void removesPreviousPhotoOnlyOnCommit() throws Exception {
        var storage = new StudentPhotoStorage(directory.toString());
        String old = java.util.UUID.randomUUID()+".jpg";
        Files.write(directory.resolve(old),new byte[]{1});
        TransactionSynchronizationManager.initSynchronization();
        try {
            String fresh = storage.store(new byte[]{2},old);
            assertThat(directory.resolve(old)).exists();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertThat(directory.resolve(old)).doesNotExist();
            assertThat(directory.resolve(fresh)).exists();
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void rejectsPathTraversal() {
        var storage = new StudentPhotoStorage(directory.toString());
        assertThatThrownBy(() -> storage.load("../outside.jpg")).isInstanceOf(BusinessRuleException.class);
    }
}
