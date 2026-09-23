package com.coyotai.education.performance;

import com.coyotai.education.platform.ProjectProperties;

import java.util.List;

/**
 * Weighted performance score. Weights come from configuration; a component without data
 * (e.g. no exams yet) is left out and the remaining weights are re-normalised, so a student
 * is never marked down for an assessment that has not happened.
 */
public final class PerformanceCalculator {

    private PerformanceCalculator() {
    }

    public static Double overall(Double dailyTest, Double weeklyTest, Double exam, Double attendance,
                                 ProjectProperties.Weights weights) {
        double weighted = 0;
        double totalWeight = 0;
        double[][] components = {
                {value(dailyTest), weights.getDailyTest(), dailyTest == null ? 0 : 1},
                {value(weeklyTest), weights.getWeeklyTest(), weeklyTest == null ? 0 : 1},
                {value(exam), weights.getExam(), exam == null ? 0 : 1},
                {value(attendance), weights.getAttendance(), attendance == null ? 0 : 1},
        };
        for (double[] component : components) {
            if (component[2] == 1 && component[1] > 0) {
                weighted += component[0] * component[1];
                totalWeight += component[1];
            }
        }
        return totalWeight == 0 ? null : round(weighted / totalWeight);
    }

    /** Average of percentages, or null when there are none. */
    public static Double average(List<Double> percentages) {
        List<Double> values = percentages.stream().filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    public static double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static double value(Double value) {
        return value == null ? 0 : value;
    }
}
