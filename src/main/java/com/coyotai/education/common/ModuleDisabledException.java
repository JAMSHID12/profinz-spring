package com.coyotai.education.common;

import com.coyotai.education.platform.ModuleCode;

/** A request reached a module this client has switched off in configuration. */
public class ModuleDisabledException extends RuntimeException {

    private final ModuleCode module;

    public ModuleDisabledException(ModuleCode module) {
        super("The " + module.displayName() + " module is not enabled for this client");
        this.module = module;
    }

    public ModuleCode getModule() {
        return module;
    }
}
