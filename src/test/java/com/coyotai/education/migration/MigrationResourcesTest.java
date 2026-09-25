package com.coyotai.education.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationResourcesTest {
    @Test void packagedMigrationsHaveValidNamesAndCompleteVersionHistory() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql");
        Set<String> names = Arrays.stream(resources).map(r -> r.getFilename()).collect(Collectors.toSet());
        assertThat(names).hasSize(13).allMatch(name -> name.matches("V\\d+__.+\\.sql"));
        for (int version=1; version<=13; version++) {
            String prefix="V"+version+"__";
            assertThat(names.stream().filter(name -> name.startsWith(prefix)).count()).isEqualTo(1);
        }
    }
}
