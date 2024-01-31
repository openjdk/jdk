/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8314263
 * @summary Creating a logger while loading the Logger finder
 *          triggers recursion and StackOverflowError
 * @modules java.base/sun.util.logging java.base/jdk.internal.logger:+open
 * @library ../lib
 * @compile RecursiveLoadingTest.java SimpleLoggerFinder.java
 * @run main/othervm PlatformRecursiveLoadingTest
 */

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.logging.LogRecord;

import sun.util.logging.PlatformLogger;

public class PlatformRecursiveLoadingTest {

    /**
     * This test triggers recursion by calling `System.getLogger` in the class init and constructor
     * of a custom LoggerFinder. Without the fix, this is expected to throw
     * java.lang.NoClassDefFoundError: Could not initialize class jdk.internal.logger.LoggerFinderLoader$ErrorPolicy
     * caused by: java.lang.StackOverflowError
     */
    public static void main(String[] args) throws Throwable {
        PlatformLogger.getLogger("main").info("in main");
        // allow time to let bootstrap logger flush data
        BootstrapLoggerUtils.awaitPending();
        List<Object> logs = loggerfinder.SimpleLoggerFinder.LOGS;
        logs.stream().map(SimpleLogRecord::of).forEach(System.out::println);
        logs.stream().map(SimpleLogRecord::of).forEach(SimpleLogRecord::check);
        assertEquals(String.valueOf(logs.size()), String.valueOf(3));
    }

    static List<Object> asList(Object[] params) {
        return params == null ? null : Arrays.asList(params);
    }

    record SimpleLogRecord(String message, Instant instant, String loggerName,
                           java.util.logging.Level level, List<Object> params,
                           String resourceBundleName, long seqNumber,
                           String sourceClassName, String methodName, Throwable thrown) {
        SimpleLogRecord(LogRecord record) {
            this(record.getMessage(), record.getInstant(), record.getLoggerName(), record.getLevel(),
                    asList(record.getParameters()), record.getResourceBundleName(), record.getSequenceNumber(),
                    record.getSourceClassName(), record.getSourceMethodName(), record.getThrown());
        }
        static SimpleLogRecord of(Object o) {
            return (o instanceof LogRecord record) ? new SimpleLogRecord(record) : null;
        }
        static SimpleLogRecord check(SimpleLogRecord record) {
            if (record.loggerName.equals("dummy")) {
                assertEquals(record.sourceClassName, "jdk.internal.logger.BootstrapLogger$LogEvent");
                assertEquals(record.methodName(), "log");
            }
            if (record.loggerName.equals("main")) {
                assertEquals(record.sourceClassName, PlatformRecursiveLoadingTest.class.getName());
                assertEquals(record.methodName, "main");
            }
            return record;
        }
    }

    private static void assertEquals(String received, String expected) {
        if (!expected.equals(received)) {
            throw new RuntimeException("Received: " + received);
        }
    }
}
