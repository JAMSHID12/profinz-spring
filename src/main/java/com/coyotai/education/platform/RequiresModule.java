package com.coyotai.education.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller (or a single endpoint) as belonging to one or more modules. The request
 * is rejected with 403 unless every listed module is enabled for the client. Method-level
 * declarations add to the class-level ones.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresModule {

    ModuleCode[] value();
}
