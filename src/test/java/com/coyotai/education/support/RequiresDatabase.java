package com.coyotai.education.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Skips integration tests with an explanation when no MySQL is available. */
public class RequiresDatabase implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return TestDatabase.available()
                ? ConditionEvaluationResult.enabled("MySQL available for integration tests")
                : ConditionEvaluationResult.disabled("No MySQL for integration tests: set IT_DB_URL "
                + "(and IT_DB_USERNAME / IT_DB_PASSWORD) or start Docker for Testcontainers");
    }
}
