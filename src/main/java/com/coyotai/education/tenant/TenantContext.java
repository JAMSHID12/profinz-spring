package com.coyotai.education.tenant;

import java.util.function.Supplier;

/**
 * Optional per-thread override of the current client. A deployment normally serves the
 * configured client only; the override exists for tests and future per-request tenancy.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> OVERRIDE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Long get() {
        return OVERRIDE.get();
    }

    public static void set(Long clientId) {
        OVERRIDE.set(clientId);
    }

    public static void clear() {
        OVERRIDE.remove();
    }

    /** Runs the action as another client, restoring the previous state afterwards. */
    public static <T> T callAs(Long clientId, Supplier<T> action) {
        Long previous = OVERRIDE.get();
        OVERRIDE.set(clientId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
    }
}
