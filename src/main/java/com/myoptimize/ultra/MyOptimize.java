package com.myoptimize.ultra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Константы и логгер мода.
 */
public final class MyOptimize {

    public static final String MOD_ID = "myoptimize-ultra";
    public static final String MOD_NAME = "MyOptimize Ultra";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private MyOptimize() {
    }

    /** Единая точка доступа к логгеру подсистем. */
    public static Logger logger(String subsystem) {
        return LoggerFactory.getLogger(MOD_NAME + "/" + subsystem);
    }
}
