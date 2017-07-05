/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Common code for string deduplication tests
 */

import java.lang.management.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import com.oracle.java.testlibrary.*;
import sun.misc.*;

class TestStringDeduplicationTools {
    private static final String YoungGC = "YoungGC";
    private static final String FullGC  = "FullGC";

    private static final int Xmn = 50;  // MB
    private static final int Xms = 100; // MB
    private static final int Xmx = 100; // MB
    private static final int MB = 1024 * 1024;
    private static final int StringLength = 50;

    private static Field valueField;
    private static Unsafe unsafe;
    private static byte[] dummy;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe)field.get(null);

            valueField = String.class.getDeclaredField("value");
            valueField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getValue(String string) {
        try {
            return valueField.get(string);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void doFullGc(int numberOfTimes) {
        for (int i = 0; i < numberOfTimes; i++) {
            System.out.println("Begin: Full GC " + (i + 1) + "/" + numberOfTimes);
            System.gc();
            System.out.println("End: Full GC " + (i + 1) + "/" + numberOfTimes);
        }
    }

    private static void doYoungGc(int numberOfTimes) {
        // Provoke at least numberOfTimes young GCs
        final int objectSize = 128;
        final int maxObjectInYoung = (Xmn * MB) / objectSize;
        for (int i = 0; i < numberOfTimes; i++) {
            System.out.println("Begin: Young GC " + (i + 1) + "/" + numberOfTimes);
            for (int j = 0; j < maxObjectInYoung + 1; j++) {
                dummy = new byte[objectSize];
            }
            System.out.println("End: Young GC " + (i + 1) + "/" + numberOfTimes);
        }
    }

    private static void forceDeduplication(int ageThreshold, String gcType) {
        // Force deduplication to happen by either causing a FullGC or a YoungGC.
        // We do several collections to also provoke a situation where the the
        // deduplication thread needs to yield while processing the queue. This
        // also tests that the references in the deduplication queue are adjusted
        // accordingly.
        if (gcType.equals(FullGC)) {
            doFullGc(3);
        } else {
            doYoungGc(ageThreshold + 3);
        }
    }

    private static String generateString(int id) {
        StringBuilder builder = new StringBuilder(StringLength);

        builder.append("DeduplicationTestString:" + id + ":");

        while (builder.length() < StringLength) {
            builder.append('X');
        }

        return builder.toString();
    }

    private static ArrayList<String> createStrings(int total, int unique) {
        System.out.println("Creating strings: total=" + total + ", unique=" + unique);
        if (total % unique != 0) {
            throw new RuntimeException("Total must be divisible by unique");
        }

        ArrayList<String> list = new ArrayList<String>(total);
        for (int j = 0; j < total / unique; j++) {
            for (int i = 0; i < unique; i++) {
                list.add(generateString(i));
            }
        }

        return list;
    }

    private static void verifyStrings(ArrayList<String> list, int uniqueExpected) {
        for (;;) {
            // Check number of deduplicated strings
            ArrayList<Object> unique = new ArrayList<Object>(uniqueExpected);
            for (String string: list) {
                Object value = getValue(string);
                boolean uniqueValue = true;
                for (Object obj: unique) {
                    if (obj == value) {
                        uniqueValue = false;
                        break;
                    }
                }

                if (uniqueValue) {
                    unique.add(value);
                }
            }

            System.out.println("Verifying strings: total=" + list.size() +
                               ", uniqueFound=" + unique.size() +
                               ", uniqueExpected=" + uniqueExpected);

            if (unique.size() == uniqueExpected) {
                System.out.println("Deduplication completed");
                break;
            } else {
                System.out.println("Deduplication not completed, waiting...");

                // Give the deduplication thread time to complete
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static OutputAnalyzer runTest(String... extraArgs) throws Exception {
        String[] defaultArgs = new String[] {
            "-Xmn" + Xmn + "m",
            "-Xms" + Xms + "m",
            "-Xmx" + Xmx + "m",
            "-XX:+UseG1GC",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+VerifyAfterGC" // Always verify after GC
        };

        ArrayList<String> args = new ArrayList<String>();
        args.addAll(Arrays.asList(defaultArgs));
        args.addAll(Arrays.asList(extraArgs));

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args.toArray(new String[args.size()]));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.err.println(output.getStderr());
        System.out.println(output.getStdout());
        return output;
    }

    private static class DeduplicationTest {
        public static void main(String[] args) {
            System.out.println("Begin: DeduplicationTest");

            final int numberOfStrings = Integer.parseUnsignedInt(args[0]);
            final int numberOfUniqueStrings = Integer.parseUnsignedInt(args[1]);
            final int ageThreshold = Integer.parseUnsignedInt(args[2]);
            final String gcType = args[3];

            ArrayList<String> list = createStrings(numberOfStrings, numberOfUniqueStrings);
            forceDeduplication(ageThreshold, gcType);
            verifyStrings(list, numberOfUniqueStrings);

            System.out.println("End: DeduplicationTest");
        }

        public static OutputAnalyzer run(int numberOfStrings, int ageThreshold, String gcType, String... extraArgs) throws Exception {
            String[] defaultArgs = new String[] {
                "-XX:+UseStringDeduplication",
                "-XX:StringDeduplicationAgeThreshold=" + ageThreshold,
                DeduplicationTest.class.getName(),
                "" + numberOfStrings,
                "" + numberOfStrings / 2,
                "" + ageThreshold,
                gcType
            };

            ArrayList<String> args = new ArrayList<String>();
            args.addAll(Arrays.asList(extraArgs));
            args.addAll(Arrays.asList(defaultArgs));

            return runTest(args.toArray(new String[args.size()]));
        }
    }

    private static class InternedTest {
        public static void main(String[] args) {
            // This test verifies that interned strings are always
            // deduplicated when being interned, and never after
            // being interned.

            System.out.println("Begin: InternedTest");

            final int ageThreshold = Integer.parseUnsignedInt(args[0]);
            final String baseString = "DeduplicationTestString:" + InternedTest.class.getName();

            // Create duplicate of baseString
            StringBuilder sb1 = new StringBuilder(baseString);
            String dupString1 = sb1.toString();
            if (getValue(dupString1) == getValue(baseString)) {
                throw new RuntimeException("Values should not match");
            }

            // Force baseString to be inspected for deduplication
            // and be inserted into the deduplication hashtable.
            forceDeduplication(ageThreshold, FullGC);

            // Wait for deduplication to occur
            while (getValue(dupString1) != getValue(baseString)) {
                System.out.println("Waiting...");
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            // Create a new duplicate of baseString
            StringBuilder sb2 = new StringBuilder(baseString);
            String dupString2 = sb2.toString();
            if (getValue(dupString2) == getValue(baseString)) {
                throw new RuntimeException("Values should not match");
            }

            // Intern the new duplicate
            Object beforeInternedValue = getValue(dupString2);
            String internedString = dupString2.intern();
            if (internedString != dupString2) {
                throw new RuntimeException("String should match");
            }
            if (getValue(internedString) != getValue(baseString)) {
                throw new RuntimeException("Values should match");
            }

            // Check original value of interned string, to make sure
            // deduplication happened on the interned string and not
            // on the base string
            if (beforeInternedValue == getValue(baseString)) {
                throw new RuntimeException("Values should not match");
            }

            System.out.println("End: InternedTest");
        }

        public static OutputAnalyzer run() throws Exception {
            return runTest("-XX:+PrintGC",
                           "-XX:+PrintGCDetails",
                           "-XX:+UseStringDeduplication",
                           "-XX:+PrintStringDeduplicationStatistics",
                           "-XX:StringDeduplicationAgeThreshold=" + DefaultAgeThreshold,
                           InternedTest.class.getName(),
                           "" + DefaultAgeThreshold);
        }
    }

    private static class MemoryUsageTest {
        public static void main(String[] args) {
            System.out.println("Begin: MemoryUsageTest");

            final boolean useStringDeduplication = Boolean.parseBoolean(args[0]);
            final int numberOfStrings = LargeNumberOfStrings;
            final int numberOfUniqueStrings = 1;

            ArrayList<String> list = createStrings(numberOfStrings, numberOfUniqueStrings);
            forceDeduplication(DefaultAgeThreshold, FullGC);

            if (useStringDeduplication) {
                verifyStrings(list, numberOfUniqueStrings);
            }

            System.gc();
            System.out.println("Heap Memory Usage: " + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());

            System.out.println("End: MemoryUsageTest");
        }

        public static OutputAnalyzer run(boolean useStringDeduplication) throws Exception {
            String[] extraArgs = new String[0];

            if (useStringDeduplication) {
                extraArgs = new String[] {
                    "-XX:+UseStringDeduplication",
                    "-XX:+PrintStringDeduplicationStatistics",
                    "-XX:StringDeduplicationAgeThreshold=" + DefaultAgeThreshold
                };
            }

            String[] defaultArgs = new String[] {
                "-XX:+PrintGC",
                "-XX:+PrintGCDetails",
                MemoryUsageTest.class.getName(),
                "" + useStringDeduplication
            };

            ArrayList<String> args = new ArrayList<String>();
            args.addAll(Arrays.asList(extraArgs));
            args.addAll(Arrays.asList(defaultArgs));

            return runTest(args.toArray(new String[args.size()]));
        }
    }

    /*
     * Tests
     */

    private static final int LargeNumberOfStrings = 10000;
    private static final int SmallNumberOfStrings = 10;

    private static final int MaxAgeThreshold      = 15;
    private static final int DefaultAgeThreshold  = 3;
    private static final int MinAgeThreshold      = 1;

    private static final int TooLowAgeThreshold   = MinAgeThreshold - 1;
    private static final int TooHighAgeThreshold  = MaxAgeThreshold + 1;

    public static void testYoungGC() throws Exception {
        // Do young GC to age strings to provoke deduplication
        OutputAnalyzer output = DeduplicationTest.run(LargeNumberOfStrings,
                                                      DefaultAgeThreshold,
                                                      YoungGC,
                                                      "-XX:+PrintGC",
                                                      "-XX:+PrintStringDeduplicationStatistics");
        output.shouldNotContain("Full GC");
        output.shouldContain("GC pause (G1 Evacuation Pause) (young)");
        output.shouldContain("GC concurrent-string-deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);
    }

    public static void testFullGC() throws Exception {
        // Do full GC to age strings to provoke deduplication
        OutputAnalyzer output = DeduplicationTest.run(LargeNumberOfStrings,
                                                      DefaultAgeThreshold,
                                                      FullGC,
                                                      "-XX:+PrintGC",
                                                      "-XX:+PrintStringDeduplicationStatistics");
        output.shouldNotContain("GC pause (G1 Evacuation Pause) (young)");
        output.shouldContain("Full GC");
        output.shouldContain("GC concurrent-string-deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);
    }

    public static void testTableResize() throws Exception {
        // Test with StringDeduplicationResizeALot
        OutputAnalyzer output = DeduplicationTest.run(LargeNumberOfStrings,
                                                      DefaultAgeThreshold,
                                                      YoungGC,
                                                      "-XX:+PrintGC",
                                                      "-XX:+PrintStringDeduplicationStatistics",
                                                      "-XX:+StringDeduplicationResizeALot");
        output.shouldContain("GC concurrent-string-deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldNotContain("Resize Count: 0");
        output.shouldHaveExitValue(0);
    }

    public static void testTableRehash() throws Exception {
        // Test with StringDeduplicationRehashALot
        OutputAnalyzer output = DeduplicationTest.run(LargeNumberOfStrings,
                                                      DefaultAgeThreshold,
                                                      YoungGC,
                                                      "-XX:+PrintGC",
                                                      "-XX:+PrintStringDeduplicationStatistics",
                                                      "-XX:+StringDeduplicationRehashALot");
        output.shouldContain("GC concurrent-string-deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldNotContain("Rehash Count: 0");
        output.shouldNotContain("Hash Seed: 0x0");
        output.shouldHaveExitValue(0);
    }

    public static void testAgeThreshold() throws Exception {
        OutputAnalyzer output;

        // Test with max age theshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       MaxAgeThreshold,
                                       YoungGC,
                                       "-XX:+PrintGC",
                                       "-XX:+PrintStringDeduplicationStatistics");
        output.shouldContain("GC concurrent-string-deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);

        // Test with min age theshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       MinAgeThreshold,
                                       YoungGC,
                                       "-XX:+PrintGC",
                                       "-XX:+PrintStringDeduplicationStatistics");
        output.shouldContain("GC concurrent-string-deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);

        // Test with too low age threshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       TooLowAgeThreshold,
                                       YoungGC);
        output.shouldContain("StringDeduplicationAgeThreshold of " + TooLowAgeThreshold +
                             " is invalid; must be between " + MinAgeThreshold + " and " + MaxAgeThreshold);
        output.shouldHaveExitValue(1);

        // Test with too high age threshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       TooHighAgeThreshold,
                                       YoungGC);
        output.shouldContain("StringDeduplicationAgeThreshold of " + TooHighAgeThreshold +
                             " is invalid; must be between " + MinAgeThreshold + " and " + MaxAgeThreshold);
        output.shouldHaveExitValue(1);
    }

    public static void testPrintOptions() throws Exception {
        OutputAnalyzer output;

        // Test without PrintGC and without PrintStringDeduplicationStatistics
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       DefaultAgeThreshold,
                                       YoungGC);
        output.shouldNotContain("GC concurrent-string-deduplication");
        output.shouldNotContain("Deduplicated:");
        output.shouldHaveExitValue(0);

        // Test with PrintGC but without PrintStringDeduplicationStatistics
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       DefaultAgeThreshold,
                                       YoungGC,
                                       "-XX:+PrintGC");
        output.shouldContain("GC concurrent-string-deduplication");
        output.shouldNotContain("Deduplicated:");
        output.shouldHaveExitValue(0);
    }

    public static void testInterned() throws Exception {
        // Test that interned strings are deduplicated before being interned
        OutputAnalyzer output = InternedTest.run();
        output.shouldHaveExitValue(0);
    }

    public static void testMemoryUsage() throws Exception {
        // Test that memory usage is reduced after deduplication
        OutputAnalyzer output;
        final String usagePattern = "Heap Memory Usage: (\\d+)";

        // Run without deduplication
        output = MemoryUsageTest.run(false);
        output.shouldHaveExitValue(0);
        final long memoryUsageWithoutDedup = Long.parseLong(output.firstMatch(usagePattern, 1));

        // Run with deduplication
        output = MemoryUsageTest.run(true);
        output.shouldHaveExitValue(0);
        final long memoryUsageWithDedup = Long.parseLong(output.firstMatch(usagePattern, 1));

        // Calculate expected memory usage with deduplication enabled. This calculation does
        // not take alignment and padding into account, so it's a conservative estimate.
        final long sizeOfChar = 2; // bytes
        final long bytesSaved = (LargeNumberOfStrings - 1) * (StringLength * sizeOfChar + unsafe.ARRAY_CHAR_BASE_OFFSET);
        final long memoryUsageWithDedupExpected = memoryUsageWithoutDedup - bytesSaved;

        System.out.println("Memory usage summary:");
        System.out.println("   memoryUsageWithoutDedup:      " + memoryUsageWithoutDedup);
        System.out.println("   memoryUsageWithDedup:         " + memoryUsageWithDedup);
        System.out.println("   memoryUsageWithDedupExpected: " + memoryUsageWithDedupExpected);

        if (memoryUsageWithDedup > memoryUsageWithDedupExpected) {
            throw new Exception("Unexpected memory usage, memoryUsageWithDedup should less or equal to memoryUsageWithDedupExpected");
        }
    }
}
