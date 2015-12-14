/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestGCLogMessages
 * @bug 8035406 8027295 8035398 8019342 8027959 8048179 8027962 8069330
 * @summary Ensure the output for a minor GC with G1
 * includes the expected necessary messages.
 * @key gc
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 */

import jdk.test.lib.ProcessTools;
import jdk.test.lib.OutputAnalyzer;

public class TestGCLogMessages {

    private enum Level {
        OFF, DEBUG, TRACE;
        public boolean lessOrEqualTo(Level other) {
            return this.compareTo(other) < 0;
        }
    }

    private class LogMessageWithLevel {
        String message;
        Level level;

        public LogMessageWithLevel(String message, Level level) {
            this.message = message;
            this.level = level;
        }
    };

    private LogMessageWithLevel allLogMessages[] = new LogMessageWithLevel[] {
        // Update RS
        new LogMessageWithLevel("Scan HCC", Level.DEBUG),
        // Ext Root Scan
        new LogMessageWithLevel("Thread Roots:", Level.DEBUG),
        new LogMessageWithLevel("StringTable Roots:", Level.DEBUG),
        new LogMessageWithLevel("Universe Roots:", Level.DEBUG),
        new LogMessageWithLevel("JNI Handles Roots:", Level.DEBUG),
        new LogMessageWithLevel("ObjectSynchronizer Roots:", Level.DEBUG),
        new LogMessageWithLevel("FlatProfiler Roots", Level.DEBUG),
        new LogMessageWithLevel("Management Roots", Level.DEBUG),
        new LogMessageWithLevel("SystemDictionary Roots", Level.DEBUG),
        new LogMessageWithLevel("CLDG Roots", Level.DEBUG),
        new LogMessageWithLevel("JVMTI Roots", Level.DEBUG),
        new LogMessageWithLevel("SATB Filtering", Level.DEBUG),
        new LogMessageWithLevel("CM RefProcessor Roots", Level.DEBUG),
        new LogMessageWithLevel("Wait For Strong CLD", Level.DEBUG),
        new LogMessageWithLevel("Weak CLD Roots", Level.DEBUG),
        // Redirty Cards
        new LogMessageWithLevel("Redirty Cards", Level.DEBUG),
        new LogMessageWithLevel("Parallel Redirty", Level.DEBUG),
        new LogMessageWithLevel("Redirtied Cards", Level.DEBUG),
        // Misc Top-level
        new LogMessageWithLevel("Code Root Purge", Level.DEBUG),
        new LogMessageWithLevel("String Dedup Fixup", Level.DEBUG),
        new LogMessageWithLevel("Expand Heap After Collection", Level.DEBUG),
        // Free CSet
        new LogMessageWithLevel("Young Free CSet", Level.TRACE),
        new LogMessageWithLevel("Non-Young Free CSet", Level.TRACE),
        // Humongous Eager Reclaim
        new LogMessageWithLevel("Humongous Reclaim", Level.DEBUG),
        new LogMessageWithLevel("Humongous Register", Level.DEBUG),
    };

    void checkMessagesAtLevel(OutputAnalyzer output, LogMessageWithLevel messages[], Level level) throws Exception {
        for (LogMessageWithLevel l : messages) {
            if (level.lessOrEqualTo(l.level)) {
                output.shouldNotContain(l.message);
            } else {
                output.shouldContain(l.message);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new TestGCLogMessages().testNormalLogs();
        new TestGCLogMessages().testWithToSpaceExhaustionLogs();
    }

    private void testNormalLogs() throws Exception {

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                                  "-Xmx10M",
                                                                  GCTest.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        checkMessagesAtLevel(output, allLogMessages, Level.OFF);
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                   "-XX:+UseStringDeduplication",
                                                   "-Xmx10M",
                                                   "-Xlog:gc+phases=debug",
                                                   GCTest.class.getName());

        output = new OutputAnalyzer(pb.start());
        checkMessagesAtLevel(output, allLogMessages, Level.DEBUG);

        pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                   "-XX:+UseStringDeduplication",
                                                   "-Xmx10M",
                                                   "-Xlog:gc+phases=trace",
                                                   GCTest.class.getName());

        output = new OutputAnalyzer(pb.start());
        checkMessagesAtLevel(output, allLogMessages, Level.TRACE);
        output.shouldHaveExitValue(0);
    }

    LogMessageWithLevel exhFailureMessages[] = new LogMessageWithLevel[] {
        new LogMessageWithLevel("Evacuation Failure", Level.DEBUG),
        new LogMessageWithLevel("Recalculate Used", Level.TRACE),
        new LogMessageWithLevel("Remove Self Forwards", Level.TRACE),
        new LogMessageWithLevel("Restore RemSet", Level.TRACE),
    };

    private void testWithToSpaceExhaustionLogs() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                                  "-Xmx32M",
                                                                  "-Xmn16M",
                                                                  "-Xlog:gc+phases=debug",
                                                                  GCTestWithToSpaceExhaustion.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        checkMessagesAtLevel(output, exhFailureMessages, Level.DEBUG);
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                   "-Xmx32M",
                                                   "-Xmn16M",
                                                   "-Xlog:gc+phases=trace",
                                                   GCTestWithToSpaceExhaustion.class.getName());

        output = new OutputAnalyzer(pb.start());
        checkMessagesAtLevel(output, exhFailureMessages, Level.TRACE);
        output.shouldHaveExitValue(0);
    }

    static class GCTest {
        private static byte[] garbage;
        public static void main(String [] args) {
            System.out.println("Creating garbage");
            // create 128MB of garbage. This should result in at least one GC
            for (int i = 0; i < 1024; i++) {
                garbage = new byte[128 * 1024];
            }
            System.out.println("Done");
        }
    }

    static class GCTestWithToSpaceExhaustion {
        private static byte[] garbage;
        private static byte[] largeObject;
        public static void main(String [] args) {
            largeObject = new byte[16*1024*1024];
            System.out.println("Creating garbage");
            // create 128MB of garbage. This should result in at least one GC,
            // some of them with to-space exhaustion.
            for (int i = 0; i < 1024; i++) {
                garbage = new byte[128 * 1024];
            }
            System.out.println("Done");
        }
    }
}

