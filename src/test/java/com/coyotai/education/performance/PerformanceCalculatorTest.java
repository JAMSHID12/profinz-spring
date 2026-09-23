package com.coyotai.education.performance;

import com.coyotai.education.platform.ProjectProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceCalculatorTest {

    private ProjectProperties.Weights weights(int daily, int weekly, int exam, int attendance) {
        ProjectProperties.Weights weights = new ProjectProperties.Weights();
        weights.setDailyTest(daily);
        weights.setWeeklyTest(weekly);
        weights.setExam(exam);
        weights.setAttendance(attendance);
        return weights;
    }

    @Test
    @DisplayName("Uses the configured weights (20/20/50/10 by default)")
    void weightedScore() {
        // 0.2*80 + 0.2*70 + 0.5*90 + 0.1*100 = 16 + 14 + 45 + 10 = 85
        assertThat(PerformanceCalculator.overall(80d, 70d, 90d, 100d, weights(20, 20, 50, 10))).isEqualTo(85.0);
    }

    @Test
    @DisplayName("A component without data is skipped and the other weights re-normalised")
    void missingComponentsAreReweighted() {
        // No exams yet: (20*80 + 20*70 + 10*100) / 50 = 80
        assertThat(PerformanceCalculator.overall(80d, 70d, null, 100d, weights(20, 20, 50, 10))).isEqualTo(80.0);
    }

    @Test
    @DisplayName("Changing the configuration changes the result - nothing is hard-coded")
    void differentClientWeights() {
        assertThat(PerformanceCalculator.overall(80d, 70d, 90d, 100d, weights(0, 0, 100, 0))).isEqualTo(90.0);
    }

    @Test
    @DisplayName("No data at all gives no score rather than zero")
    void noData() {
        assertThat(PerformanceCalculator.overall(null, null, null, null, weights(20, 20, 50, 10))).isNull();
        assertThat(PerformanceCalculator.average(List.of())).isNull();
    }

    @Test
    @DisplayName("Averages ignore missing values and round to one decimal")
    void averages() {
        assertThat(PerformanceCalculator.average(Arrays.asList(80d, null, 71d))).isEqualTo(75.5);
    }
}
