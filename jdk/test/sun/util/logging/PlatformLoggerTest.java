/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug     6882376
 * @summary Test if java.util.logging.Logger is created before and after
 *          logging is enabled.  Also validate some basic PlatformLogger
 *          operations.
 *
 * @compile -XDignore.symbol.file PlatformLoggerTest.java
 * @run main PlatformLoggerTest
 */

import java.util.logging.*;
import sun.util.logging.PlatformLogger;

public class PlatformLoggerTest {
    private static final int defaultEffectiveLevel = 0;
    public static void main(String[] args) throws Exception {
        final String FOO_PLATFORM_LOGGER = "test.platformlogger.foo";
        final String BAR_PLATFORM_LOGGER = "test.platformlogger.bar";
        final String GOO_PLATFORM_LOGGER = "test.platformlogger.goo";
        final String BAR_LOGGER = "test.logger.bar";
        PlatformLogger goo = PlatformLogger.getLogger(GOO_PLATFORM_LOGGER);

        // Create a platform logger using the default
        PlatformLogger foo = PlatformLogger.getLogger(FOO_PLATFORM_LOGGER);
        checkPlatformLogger(foo, FOO_PLATFORM_LOGGER);

        // create a java.util.logging.Logger
        // now java.util.logging.Logger should be created for each platform logger
        Logger logger = Logger.getLogger(BAR_LOGGER);
        logger.setLevel(Level.WARNING);

        PlatformLogger bar = PlatformLogger.getLogger(BAR_PLATFORM_LOGGER);
        checkPlatformLogger(bar, BAR_PLATFORM_LOGGER);

        checkLogger(FOO_PLATFORM_LOGGER, Level.FINER);
        checkLogger(BAR_PLATFORM_LOGGER, Level.FINER);

        checkLogger(GOO_PLATFORM_LOGGER, null);
        checkLogger(BAR_LOGGER, Level.WARNING);

        foo.setLevel(PlatformLogger.SEVERE);
        checkLogger(FOO_PLATFORM_LOGGER, Level.SEVERE);
    }

    private static void checkPlatformLogger(PlatformLogger logger, String name) {
        if (!logger.getName().equals(name)) {
            throw new RuntimeException("Invalid logger's name " +
                logger.getName() + " but expected " + name);
        }

        if (logger.getLevel() != defaultEffectiveLevel) {
            throw new RuntimeException("Invalid default level for logger " +
                logger.getName());
        }

        if (logger.isLoggable(PlatformLogger.FINE) != false) {
            throw new RuntimeException("isLoggerable(FINE) returns true for logger " +
                logger.getName() + " but expected false");
        }

        logger.setLevel(PlatformLogger.FINER);
        if (logger.getLevel() != Level.FINER.intValue()) {
            throw new RuntimeException("Invalid level for logger " +
                logger.getName() + " " + logger.getLevel());
        }

        if (logger.isLoggable(PlatformLogger.FINE) != true) {
            throw new RuntimeException("isLoggerable(FINE) returns false for logger " +
                logger.getName() + " but expected true");
        }

        logger.info("OK: Testing log message");
    }

    private static void checkLogger(String name, Level level) {
        Logger logger = LogManager.getLogManager().getLogger(name);
        if (logger == null) {
            throw new RuntimeException("Logger " + name +
                " does not exist");
        }

        if (logger.getLevel() != level) {
            throw new RuntimeException("Invalid level for logger " +
                logger.getName() + " " + logger.getLevel());
        }
    }
}
