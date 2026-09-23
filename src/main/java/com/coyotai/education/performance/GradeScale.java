package com.coyotai.education.performance;

import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.ProjectProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Maps a percentage to a grade using the configured bands ({@code project.academics.performance.grades}). */
@Component
public class GradeScale {

    private final List<ProjectProperties.GradeBand> bands;

    public GradeScale(ProjectConfigService configService) {
        this.bands = configService.academics().getPerformance().getGrades().stream()
                .sorted(Comparator.comparingDouble(ProjectProperties.GradeBand::getMin).reversed())
                .toList();
    }

    public String gradeFor(Double percentage) {
        if (percentage == null) {
            return null;
        }
        for (ProjectProperties.GradeBand band : bands) {
            if (percentage >= band.getMin()) {
                return band.getGrade();
            }
        }
        return bands.isEmpty() ? null : bands.get(bands.size() - 1).getGrade();
    }
}
