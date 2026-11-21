/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.lang.System.Logger.Level;
import java.lang.System.LoggerFinder;
import java.util.List;
import java.util.logging.Logger;

public class FileHandlerTest {
    private static String sysLoggerName = "dormantLoggerFromJULLoggerWithHandler";
    private static String sysLoggerNameNoHandler = "dormantLoggerFromJULLoggerWithNoHandler";
    private static String uniqueDormantLoggerName = "dormantLogger";
    private static System.Logger sysLogger;
    private static System.Logger sysLoggerWithNoHandler;
    private static System.Logger dormantLogger;

    public static void main(String[] args) {
        System.err.println("SCENARIO=FILEHANDLER");

        LoggerFinder finder = LoggerFinder.getLoggerFinder();
        System.err.println("FINDER_CLASS=" + finder.getClass().getName());
        System.err.println("FINDER_MODULE=" + finder.getClass().getModule().getName());

        Logger jul = Logger.getLogger(sysLoggerName);
        Logger jul2 = Logger.getLogger(sysLoggerNameNoHandler);
        jul.setUseParentHandlers(false);
        jul.setLevel(java.util.logging.Level.FINE);

        var h = new LoggerFinderServiceLauncherTest.CapturingHandler();
        h.setLevel(java.util.logging.Level.FINE);
        jul.addHandler(h);

        try {
            sysLogger = System.getLogger(sysLoggerName);
            sysLoggerWithNoHandler = System.getLogger(sysLoggerNameNoHandler);
            System.err.println("Logger class type=" + sysLogger.getClass());
            // test scenario where Logger already exists
            // in such a case, the logging framework config which generated
            // the original logger maintain control over config/level
            sysLogger =
                LoggerFinderServiceLauncherTest.getDormantLogger(sysLoggerName);
            // another scenario where Dormant Logger inherits a logger name
            // already used by logging framework but without handler configured
            sysLoggerWithNoHandler =
                LoggerFinderServiceLauncherTest.getDormantLogger(sysLoggerNameNoHandler);
            // test scenario where new Logger with unique name is created
            dormantLogger =
                LoggerFinderServiceLauncherTest.getDormantLogger(uniqueDormantLoggerName);
            System.Logger nonDormant = System.getLogger("regSys");
            LoggerFinderServiceLauncherTest.testLoggers(List.of(sysLogger,
                    sysLoggerWithNoHandler, dormantLogger, nonDormant));

            java.util.logging.LogRecord r = h.last;
            if (r == null) throw new AssertionError("No JUL LogRecord captured");
            if (!r.getMessage().contains("hello"))
                throw new AssertionError("Expected message: " + r.getMessage());
            if (r.getLevel() != java.util.logging.Level.INFO)
                throw new AssertionError("Unexpected JUL level: " + r.getLevel());
            if (!sysLoggerName.equals(r.getLoggerName()))
                throw new AssertionError("Unexpected logger name: " + r.getLoggerName());
        } finally {
            jul.removeHandler(h);
        }
    }
}
