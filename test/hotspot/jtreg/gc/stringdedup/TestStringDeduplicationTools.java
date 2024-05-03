/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

package gc.stringdedup;

/*
 * Common code for string deduplication tests
 */

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.reflect.*;
import java.lang.management.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.management.*;
import javax.management.openmbean.*;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import sun.misc.*;

class TestStringDeduplicationTools {
    private static final String YoungGC = "YoungGC";
    private static final String FullGC  = "FullGC";

    private static final int Xmn = 50;  // MB
    private static final int Xms = 100; // MB
    private static final int Xmx = 100; // MB
    private static final int MB = 1024 * 1024;
    private static final int StringLength = 50;

    private static final int LargeNumberOfStrings = 10000;
    private static final int SmallNumberOfStrings = 10;

    private static Field valueField;
    private static Unsafe unsafe;
    private static byte[] dummy;

    private static String selectedGC = null;
    private static String selectedGCMode = null;

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

    public static void selectGC(String[] args) {
        selectedGC = args[0];
        if (args.length > 1) {
            selectedGCMode = args[1];
        }
    }

    private static Object getValue(String string) {
        try {
            return valueField.get(string);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get system load.
     *
     * <dl>
     *   <dt>load() ~=   1 </dt><dd> fully loaded system, all cores are used 100%</dd>
     *   <dt>load() &lt; 1 </dt><dd> some cpu resources are available</dd>
     *   <dt>load() &gt; 1 </dt><dd> system is overloaded</dd>
     * </dl>
     *
     * @return the load of the system or Optional.empty() if the load can not be determined.
     */
    private static Optional<Double> systemLoad() {
        OperatingSystemMXBean bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double average = bean.getSystemLoadAverage() / bean.getAvailableProcessors();
        return (average < 0)
            ? Optional.empty()
            : Optional.of(average);
    }

    private static String minMax(List<Optional<Double>> l) {
        DoubleSummaryStatistics minmax = l.stream().flatMap(Optional::stream).collect(Collectors.summarizingDouble(d -> d));
        return minmax.getCount() != 0
            ? "min: " + minmax.getMin() + ", max: " + minmax.getMax()
            : "could not gather load statistics from system";
    }

    private static void doFullGc(int numberOfTimes) {
        List<List<String>> newStrings = new ArrayList<List<String>>();
        for (int i = 0; i < numberOfTimes; i++) {
            // Create some more strings for every collection, to ensure
            // there will be deduplication work that will be reported.
            newStrings.add(createStrings(SmallNumberOfStrings, SmallNumberOfStrings));
            System.out.println("Begin: Full GC " + (i + 1) + "/" + numberOfTimes);
            System.gc();
            System.out.println("End: Full GC " + (i + 1) + "/" + numberOfTimes);
        }
    }

    private static volatile int gcCount;
    private static NotificationListener listener = new NotificationListener() {
        @Override
        public void handleNotification(Notification n, Object o) {
            if (n.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
                // Shenandoah and Z GC also report GC pauses, skip them
                if (info.getGcName().startsWith("Shenandoah")) {
                    if ("end of GC cycle".equals(info.getGcAction())) {
                        gcCount++;
                    }
                } else if (info.getGcName().startsWith("ZGC")) {
                    // Generational ZGC only triggers string deduplications from major collections
                    if (info.getGcName().startsWith("ZGC Major") && "end of GC cycle".equals(info.getGcAction())) {
                        gcCount++;
                    }

                    // Single-gen ZGC
                    if (!info.getGcName().startsWith("ZGC Major") && !info.getGcName().startsWith("ZGC Minor") &&
                            "end of GC cycle".equals(info.getGcAction())) {
                        gcCount++;
                    }
                } else if (info.getGcName().startsWith("G1")) {
                    if ("end of minor GC".equals(info.getGcAction())) {
                        gcCount++;
                    }
                } else {
                    gcCount++;
                }
            }
        }
    };

    private static void registerGCListener() {
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ((NotificationEmitter)bean).addNotificationListener(listener, null, null);
        }
    }

    private static void unregisterGCListener() {
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            try {
                ((NotificationEmitter) bean).removeNotificationListener(listener, null, null);
            } catch (Exception e) {
            }
        }
    }

    private static void doYoungGc(int numberOfTimes) {
        final int objectSize = 128;
        List<List<String>> newStrings = new ArrayList<List<String>>();

        // Provoke at least numberOfTimes young GCs
        gcCount = 0;
        registerGCListener();
        while (gcCount < numberOfTimes) {
            int currentCount = gcCount;
            // Create some more strings for every collection, to ensure
            // there will be deduplication work that will be reported.
            newStrings.add(createStrings(SmallNumberOfStrings, SmallNumberOfStrings));
            System.out.println("Begin: Young GC " + (currentCount + 1) + "/" + numberOfTimes);
            while (currentCount == gcCount) {
                dummy = new byte[objectSize];
            }
            System.out.println("End: Young GC " + (currentCount + 1) + "/" + numberOfTimes);
        }
        unregisterGCListener();
    }

    private static void forceDeduplication(int ageThreshold, String gcType) {
        // Force deduplication to happen by either causing a FullGC or a YoungGC.
        // We do several collections to also provoke a situation where the
        // deduplication thread needs to yield while processing the queue. This
        // also tests that the references in the deduplication queue are adjusted
        // accordingly.
        if (gcType.equals(FullGC)) {
            doFullGc(3);
        } else {
            doYoungGc(ageThreshold + 3);
        }
    }

    private static void waitForDeduplication(String s1, String s2) {
        boolean first = true;
        int timeout = 10000;     // 10sec in ms
        int iterationWait = 100; // 100ms
        List<Optional<Double>> loadHistory = new ArrayList<>();
        for (int attempts = 0; attempts < (timeout / iterationWait); attempts++) {
            loadHistory.add(systemLoad());
            if (getValue(s1) == getValue(s2)) {
                return;
            }
            if (first) {
                System.out.println("Waiting for deduplication...");
                first = false;
            }
            try {
                Thread.sleep(iterationWait);
            } catch (Exception e) {
                throw new RuntimeException("Deduplication has not occurred: Thread.sleep() threw", e);
            }
        }
        throw new RuntimeException("Deduplication has not occurred, load history: " + minMax(loadHistory));
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

    /**
     * Verifies that the given list contains expected number of unique strings.
     * It's possible that deduplication hasn't completed yet, so the method
     * will perform several attempts to check with a little pause between.
     * The method throws RuntimeException to signal that verification failed.
     *
     * @param list strings to check
     * @param uniqueExpected expected number of unique strings
     * @throws RuntimeException if check fails
     */
    private static void verifyStrings(ArrayList<String> list, int uniqueExpected) {
        boolean passed = false;
        List<Optional<Double>> loadHistory = new ArrayList<>();
        for (int attempts = 0; attempts < 10; attempts++) {
            loadHistory.add(systemLoad());
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
                System.out.println("Deduplication completed (as fast as " + attempts + " iterations)");
                passed = true;
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
        if (!passed) {
            throw new RuntimeException("String verification failed, load history: " + minMax(loadHistory));
        }
    }

    private static OutputAnalyzer runTest(String... extraArgs) throws Exception {
        String[] defaultArgs = new String[] {
            "-Xmn" + Xmn + "m",
            "-Xms" + Xms + "m",
            "-Xmx" + Xmx + "m",
            "-XX:+UnlockDiagnosticVMOptions",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "-XX:+VerifyAfterGC" // Always verify after GC
        };

        ArrayList<String> args = new ArrayList<String>();
        args.add("-XX:+Use" + selectedGC + "GC");
        if (selectedGCMode != null) {
            args.add(selectedGCMode);
        }
        args.addAll(Arrays.asList(defaultArgs));
        args.addAll(Arrays.asList(extraArgs));

        OutputAnalyzer output = ProcessTools.executeTestJava(args);
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

            checkNotDeduplicated(getValue(dupString1), getValue(baseString));

            // Force baseString to be inspected for deduplication
            // and be inserted into the deduplication hashtable.
            forceDeduplication(ageThreshold, FullGC);

            waitForDeduplication(dupString1, baseString);

            // Create a new duplicate of baseString
            StringBuilder sb2 = new StringBuilder(baseString);
            String dupString2 = sb2.toString();

            checkNotDeduplicated(getValue(dupString2), getValue(baseString));

            // Intern the new duplicate
            Object beforeInternedValue = getValue(dupString2);
            String internedString = dupString2.intern();
            Object afterInternedValue = getValue(dupString2);

            // Force internedString to be inspected for deduplication.
            // Because it was interned it should be queued up for
            // dedup, even though it hasn't reached the age threshold.
            doYoungGc(1);

            if (internedString != dupString2) {
                throw new RuntimeException("String should match");
            }

            // Check original value of interned string, to make sure
            // deduplication happened on the interned string and not
            // on the base string
            checkNotDeduplicated(beforeInternedValue, getValue(baseString));

            // Create duplicate of baseString
            StringBuilder sb3 = new StringBuilder(baseString);
            String dupString3 = sb3.toString();

            checkNotDeduplicated(dupString3, getValue(baseString));

            forceDeduplication(ageThreshold, FullGC);

            waitForDeduplication(dupString3, internedString);

            if (afterInternedValue != getValue(dupString2)) {
                throw new RuntimeException("Interned string value changed");
            }

            System.out.println("End: InternedTest");
        }

        private static void checkNotDeduplicated(Object value1, Object value2) {
            // Note that the following check is invalid since a GC
            // can run and actually deduplicate the strings.
            //
            // if (value1 == value2) {
            //     throw new RuntimeException("Values should not match");
            // }
        }

        public static OutputAnalyzer run() throws Exception {
            return runTest("-Xlog:gc=debug,stringdedup*=debug",
                           "-XX:+UseStringDeduplication",
                           "-XX:StringDeduplicationAgeThreshold=" + DefaultAgeThreshold,
                           InternedTest.class.getName(),
                           "" + DefaultAgeThreshold);
        }
    }

    /*
     * Tests
     */

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
                                                      "-Xlog:gc*,stringdedup*=debug");
        output.shouldContain("Concurrent String Deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);
    }

    public static void testFullGC() throws Exception {
        // Do full GC to age strings to provoke deduplication
        OutputAnalyzer output = DeduplicationTest.run(LargeNumberOfStrings,
                                                      DefaultAgeThreshold,
                                                      FullGC,
                                                      "-Xlog:gc*,stringdedup*=debug");
        output.shouldContain("Concurrent String Deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);
    }

    public static void testTableResize() throws Exception {
        // Test with StringDeduplicationResizeALot
        OutputAnalyzer output = DeduplicationTest.run(LargeNumberOfStrings,
                                                      DefaultAgeThreshold,
                                                      YoungGC,
                                                      "-Xlog:gc*,stringdedup*=debug",
                                                      "-XX:+StringDeduplicationResizeALot");
        output.shouldContain("Concurrent String Deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldNotContain("Resize Count: 0");
        output.shouldHaveExitValue(0);
    }

    public static void testAgeThreshold() throws Exception {
        OutputAnalyzer output;

        // Test with max age theshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       MaxAgeThreshold,
                                       YoungGC,
                                       "-Xlog:gc*,stringdedup*=debug");
        output.shouldContain("Concurrent String Deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);

        // Test with min age theshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       MinAgeThreshold,
                                       YoungGC,
                                       "-Xlog:gc*,stringdedup*=debug");
        output.shouldContain("Concurrent String Deduplication");
        output.shouldContain("Deduplicated:");
        output.shouldHaveExitValue(0);

        // Test with too low age threshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       TooLowAgeThreshold,
                                       YoungGC);
        output.shouldContain("outside the allowed range");
        output.shouldHaveExitValue(1);

        // Test with too high age threshold
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       TooHighAgeThreshold,
                                       YoungGC);
        output.shouldContain("outside the allowed range");
        output.shouldHaveExitValue(1);
    }

    public static void testPrintOptions() throws Exception {
        OutputAnalyzer output;

        // Test without -Xlog:gc
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       DefaultAgeThreshold,
                                       YoungGC);
        output.shouldNotContain("Concurrent String Deduplication");
        output.shouldNotContain("Deduplicated:");
        output.shouldHaveExitValue(0);

        // Test with -Xlog:stringdedup
        output = DeduplicationTest.run(SmallNumberOfStrings,
                                       DefaultAgeThreshold,
                                       YoungGC,
                                       "-Xlog:stringdedup");
        output.shouldContain("Concurrent String Deduplication");
        output.shouldNotContain("Deduplicated:");
        output.shouldHaveExitValue(0);
    }

    public static void testInterned() throws Exception {
        // Test that interned strings are deduplicated before being interned
        OutputAnalyzer output = InternedTest.run();
        output.shouldHaveExitValue(0);
    }
}
