package com.mojang.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal runtime-compatible logger facade for unit tests that do not boot Minecraft. */
public final class LogUtils {
    private LogUtils() {}

    public static Logger getLogger() {
        return LoggerFactory.getLogger("computed-tests");
    }
}
