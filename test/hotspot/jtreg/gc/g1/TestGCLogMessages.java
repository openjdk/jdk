/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/*
 * @test TestGCLogMessages
 * @bug 8035406 8027295 8035398 8019342 8027959 8048179 8027962 8069330 8076463 8150630 8160055 8177059 8166191
 * @summary Ensure the output for a minor GC with G1
 * includes the expected necessary messages.
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   gc.g1.TestGCLogMessages
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.code.Compiler;

public class TestGCLogMessages {

    private enum Level {
        OFF(""),
        INFO("info"),
        DEBUG("debug"),
        TRACE("trace");

        private String logName;

        Level(String logName) {
            this.logName = logName;
        }

        public boolean lessThan(Level other) {
            return this.compareTo(other) < 0;
        }

        public String toString() {
            return logName;
        }
    }

    private class LogMessageWithLevel {
        String message;
        Level level;

        public LogMessageWithLevel(String message, Level level) {
            this.message = message;
            this.level = level;
        }

        public boolean isAvailable() {
            return true;
        }
    };

    private class LogMessageWithLevelC2OrJVMCIOnly extends LogMessageWithLevel {
        public LogMessageWithLevelC2OrJVMCIOnly(String message, Level level) {
            super(message, level);
        }

        public boolean isAvailable() {
            return Compiler.isC2OrJVMCIIncluded();
        }
    }

    private class LogMessageWithJFROnly extends LogMessageWithLevel {
        public LogMessageWithJFROnly(String message, Level level) {
            super(message, level);
        }

        public boolean isAvailable() {
            jdk.test.whitebox.WhiteBox WB = jdk.test.whitebox.WhiteBox.getWhiteBox();
            return WB.isJFRIncluded();
        }
    }

    private LogMessageWithLevel allLogMessages[] = new LogMessageWithLevel[] {
        new LogMessageWithLevel("Pre Evacuate Collection Set:", Level.INFO),
        new LogMessageWithLevel("Evacuate Collection Set:", Level.INFO),
        new LogMessageWithLevel("Post Evacuate Collection Set:", Level.INFO),
        new LogMessageWithLevel("Other:", Level.INFO),

        // Pre Evacuate Collection Set
        new LogMessageWithLevel("JT Retire TLABs And Flush Logs \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Non-JT Flush Logs \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Choose Collection Set:", Level.DEBUG),
        new LogMessageWithLevel("Region Register:", Level.DEBUG),
        new LogMessageWithLevel("Prepare Heap Roots:", Level.DEBUG),
        // Merge Heap Roots
        new LogMessageWithLevel("Merge Heap Roots:", Level.INFO),
        new LogMessageWithLevel("Prepare Merge Heap Roots:", Level.DEBUG),
        new LogMessageWithLevel("Eager Reclaim \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Remembered Sets \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Merged Inline:", Level.DEBUG),
        new LogMessageWithLevel("Merged ArrayOfCards:", Level.DEBUG),
        new LogMessageWithLevel("Merged Howl:", Level.DEBUG),
        new LogMessageWithLevel("Merged Full:", Level.DEBUG),
        new LogMessageWithLevel("Merged Howl Inline:", Level.DEBUG),
        new LogMessageWithLevel("Merged Howl ArrayOfCards:", Level.DEBUG),
        new LogMessageWithLevel("Merged Howl BitMap:", Level.DEBUG),
        new LogMessageWithLevel("Merged Howl Full:", Level.DEBUG),
        new LogMessageWithLevel("Log Buffers \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Dirty Cards:", Level.DEBUG),
        new LogMessageWithLevel("Merged Cards:", Level.DEBUG),
        new LogMessageWithLevel("Skipped Cards:", Level.DEBUG),
        // Evacuate Collection Set
        new LogMessageWithLevel("Ext Root Scanning \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Thread Roots \\(ms\\):", Level.TRACE),
        new LogMessageWithLevel("CLDG Roots \\(ms\\):", Level.TRACE),
        new LogMessageWithLevel("CM RefProcessor Roots \\(ms\\):", Level.TRACE),
        new LogMessageWithLevel("JNI Global Roots", Level.TRACE),
        new LogMessageWithLevel("VM Global Roots", Level.TRACE),
        // Scan Heap Roots
        new LogMessageWithLevel("Scan Heap Roots \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Scanned Cards:", Level.DEBUG),
        new LogMessageWithLevel("Scanned Blocks:", Level.DEBUG),
        new LogMessageWithLevel("Claimed Chunks:", Level.DEBUG),
        new LogMessageWithLevel("Found Roots:", Level.DEBUG),
        // Code Roots Scan
        new LogMessageWithLevel("Code Root Scan \\(ms\\):", Level.DEBUG),
        // Object Copy
        new LogMessageWithLevel("Object Copy \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Copied Bytes:", Level.DEBUG),
        new LogMessageWithLevel("LAB Waste:", Level.DEBUG),
        new LogMessageWithLevel("LAB Undo Waste:", Level.DEBUG),
        // Termination
        new LogMessageWithLevel("Termination \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Termination Attempts:", Level.DEBUG),
        // Post Evacuate Collection Set
        // NMethod List Cleanup
        new LogMessageWithLevel("NMethod List Cleanup:", Level.DEBUG),
        // Reference Processing
        new LogMessageWithLevel("Reference Processing:", Level.DEBUG),
        // VM internal reference processing
        new LogMessageWithLevel("Weak Processing:", Level.DEBUG),
        new LogMessageWithLevel("VM Weak", Level.DEBUG),
        new LogMessageWithLevel("ObjectSynchronizer Weak", Level.DEBUG),
        new LogMessageWithLevel("JVMTI Tag Weak OopStorage", Level.DEBUG),
        new LogMessageWithLevel("StringTable Weak", Level.DEBUG),
        new LogMessageWithLevel("ResolvedMethodTable Weak", Level.DEBUG),
        new LogMessageWithJFROnly("Weak JFR Old Object Samples", Level.DEBUG),
        new LogMessageWithLevel("JNI Weak", Level.DEBUG),

        // Post Evacuate Cleanup 1
        new LogMessageWithLevel("Post Evacuate Cleanup 1:", Level.DEBUG),
        new LogMessageWithLevel("Merge Per-Thread State \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("LAB Waste:", Level.DEBUG),
        new LogMessageWithLevel("LAB Undo Waste:", Level.DEBUG),
        new LogMessageWithLevel("Evac Fail Extra Cards:", Level.DEBUG),
        new LogMessageWithLevel("Clear Logged Cards \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Recalculate Used Memory \\(ms\\):", Level.DEBUG),

        // Post Evacuate Cleanup 2
        new LogMessageWithLevel("Post Evacuate Cleanup 2:", Level.DEBUG),
        new LogMessageWithLevelC2OrJVMCIOnly("Update Derived Pointers", Level.DEBUG),
        new LogMessageWithLevel("Redirty Logged Cards \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Redirtied Cards:", Level.DEBUG),
        new LogMessageWithLevel("Resize TLABs \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Free Collection Set \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Serial Free Collection Set:", Level.TRACE),
        new LogMessageWithLevel("Young Free Collection Set \\(ms\\):", Level.TRACE),
        new LogMessageWithLevel("Non-Young Free Collection Set \\(ms\\):", Level.TRACE),

        // Misc Top-level
        new LogMessageWithLevel("Rebuild Free List:", Level.DEBUG),
        new LogMessageWithLevel("Serial Rebuild Free List:", Level.TRACE),
        new LogMessageWithLevel("Parallel Rebuild Free List \\(ms\\):", Level.TRACE),
        new LogMessageWithLevel("Prepare For Mutator:", Level.DEBUG),
        new LogMessageWithLevel("Expand Heap After Collection:", Level.DEBUG),
    };

    void checkMessagesAtLevel(OutputAnalyzer output, LogMessageWithLevel messages[], Level level) throws Exception {
        for (LogMessageWithLevel l : messages) {
            if (level.lessThan(l.level) || !l.isAvailable()) {
                output.shouldNotContain(l.message);
            } else {
                output.shouldMatch("\\[" + l.level + ".*" + l.message);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new TestGCLogMessages().testNormalLogs();
        new TestGCLogMessages().testConcurrentRefinementLogs();
        if (Platform.isDebugBuild()) {
          new TestGCLogMessages().testWithEvacuationFailureLogs();
        }
        new TestGCLogMessages().testWithConcurrentStart();
        new TestGCLogMessages().testExpandHeap();
    }

    private void testNormalLogs() throws Exception {

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx10M",
                                                                    GCTest.class.getName());

        checkMessagesAtLevel(output, allLogMessages, Level.OFF);
        output.shouldHaveExitValue(0);

        output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                     "-Xmx10M",
                                                     "-Xlog:gc+phases=debug",
                                                     GCTest.class.getName());

        checkMessagesAtLevel(output, allLogMessages, Level.DEBUG);

        output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                     "-Xmx10M",
                                                     "-Xlog:gc+phases=trace",
                                                     GCTest.class.getName());

        checkMessagesAtLevel(output, allLogMessages, Level.TRACE);
        output.shouldHaveExitValue(0);
    }

    LogMessageWithLevel concRefineMessages[] = new LogMessageWithLevel[] {
        new LogMessageWithLevel("Mutator refinement: ", Level.DEBUG),
        new LogMessageWithLevel("Concurrent refinement: ", Level.DEBUG),
        new LogMessageWithLevel("Total refinement: ", Level.DEBUG),
        // "Concurrent refinement rate" optionally printed if any.
        // "Generate dirty cards rate" optionally printed if any.
    };

    private void testConcurrentRefinementLogs() throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx10M",
                                                                    "-Xlog:gc+refine+stats=debug",
                                                                    GCTest.class.getName());
        checkMessagesAtLevel(output, concRefineMessages, Level.DEBUG);
    }

    LogMessageWithLevel exhFailureMessages[] = new LogMessageWithLevel[] {
        new LogMessageWithLevel("Recalculate Used Memory \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Restore Preserved Marks \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Restore Evacuation Failed Regions \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Process Evacuation Failed Regions \\(ms\\):", Level.DEBUG),
        new LogMessageWithLevel("Evacuation Failed Regions:", Level.DEBUG),
        new LogMessageWithLevel("Pinned Regions:", Level.DEBUG),
        new LogMessageWithLevel("Allocation Failed Regions:", Level.DEBUG),
    };

    private void testWithEvacuationFailureLogs() throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx32M",
                                                                    "-Xmn16M",
                                                                    "-XX:+G1GCAllocationFailureALot",
                                                                    "-XX:G1GCAllocationFailureALotCount=100",
                                                                    "-XX:G1GCAllocationFailureALotInterval=1",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-Xlog:gc+phases=debug",
                                                                    GCTestWithAllocationFailure.class.getName());

        checkMessagesAtLevel(output, exhFailureMessages, Level.DEBUG);
        output.shouldHaveExitValue(0);

        output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                     "-Xmx32M",
                                                     "-Xmn16M",
                                                     "-Xms32M",
                                                     "-XX:+UnlockDiagnosticVMOptions",
                                                     "-Xlog:gc+phases=trace",
                                                     GCTestWithAllocationFailure.class.getName());

        checkMessagesAtLevel(output, exhFailureMessages, Level.TRACE);
        output.shouldHaveExitValue(0);
    }

    LogMessageWithLevel concurrentStartMessages[] = new LogMessageWithLevel[] {
        new LogMessageWithLevel("Reset Marking State", Level.DEBUG),
        new LogMessageWithLevel("Note Start Of Mark", Level.DEBUG),
    };

    private void testWithConcurrentStart() throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx10M",
                                                                    "-Xbootclasspath/a:.",
                                                                    "-Xlog:gc*=debug",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    GCTestWithConcurrentStart.class.getName());

        checkMessagesAtLevel(output, concurrentStartMessages, Level.TRACE);
        output.shouldHaveExitValue(0);
    }

    private void testExpandHeap() throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx10M",
                                                                    "-Xbootclasspath/a:.",
                                                                    "-Xlog:gc+ergo+heap=debug",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    GCTest.class.getName());

        output.shouldContain("Expand the heap. requested expansion amount: ");
        output.shouldContain("B expansion amount: ");
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

    static class GCTestWithAllocationFailure {
        private static byte[] garbage;
        private static byte[] largeObject;
        private static Object[] holder = new Object[200]; // Must be larger than G1GCAllocationFailureALotCount

        public static void main(String [] args) {
            largeObject = new byte[16*1024*1024];
            System.out.println("Creating garbage");
            // Create 16 MB of garbage. This should result in at least one GC,
            // (Heap size is 32M, we use 17MB for the large object above)
            // which is larger than G1GCAllocationFailureALotInterval.
            for (int i = 0; i < 16 * 1024; i++) {
                holder[i % holder.length] = new byte[1024];
            }
            System.out.println("Done");
        }
    }

    static class GCTestWithConcurrentStart {
        public static void main(String [] args) {
            jdk.test.whitebox.WhiteBox WB = jdk.test.whitebox.WhiteBox.getWhiteBox();
            WB.g1StartConcurrentGC();
        }
    }

}
