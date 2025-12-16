/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestEagerReclaimHumongousRegions
 * @bug 8051973
 * @summary Test to make sure that eager reclaim of humongous objects correctly works.
 * @requires vm.gc.G1
 * @requires vm.debug
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI gc.g1.TestEagerReclaimHumongousRegions
 */

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestEagerReclaimHumongousRegions {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    enum ObjectType { TYPE_ARRAY, OBJ_ARRAY }
    enum ReferencePolicy { KEEP, DROP }
    enum AllocationTiming { BEFORE_MARK_START, AFTER_MARK_START}

    enum ExpectedState {
        MARKED_CANDIDATE_RECLAIMED(true, true, true),
        MARKED_CANDIDATE_NOT_RECLAIMED(true, true, false),
        MARKED_NOTCANDIDATE_NOTRECLAIMED(true, false, false),
        NOTMARKED_CANDIDATE_RECLAIMED(false, true, true),
        NOTMARKED_CANDIDATE_NOTRECLAIMED(false, true, false),
        NOTMARKED_NOTCANDIDATE_NOTRECLAIMED(false, false, false);

        final boolean marked;
        final boolean candidate;
        final boolean reclaimed;

        ExpectedState(boolean marked, boolean candidate, boolean reclaimed) {
            this.marked = marked;
            this.candidate = candidate;
            this.reclaimed = reclaimed;
        }
    }

    /**
     * Run the helper VM, passing configuration arguments, simulating an application allocating some kind of humongous object at a
     * point during the induced concurrent mark, and executing a young gc.
     *
     * @param type Whether the allocated humongous object should be a typeArray, or an objArray.
     * @param refPolicy Drop the reference to the allocated object after reaching the given phase or keep.
     * @param timing Allocate the humongous objects before or after reaching the given phase.
     * @param phase The phase during concurrent mark to reach before triggering a young garbage collection.
     * @return Returns the stdout of the VM.
     */
    private static String runHelperVM(ObjectType type, ReferencePolicy refPolicy, AllocationTiming timing, String phase) throws Exception {

        boolean useTypeArray = (type == ObjectType.TYPE_ARRAY);
        boolean keepReference = (refPolicy == ReferencePolicy.KEEP);
        boolean allocateAfter = (timing == AllocationTiming.AFTER_MARK_START);

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx20M",
                                                                    "-Xms20m",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+VerifyAfterGC",
                                                                    "-Xbootclasspath/a:.",
                                                                    "-Xlog:gc=debug,gc+humongous=debug",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    TestEagerReclaimHumongousRegionsClearMarkBitsRunner.class.getName(),
                                                                    String.valueOf(useTypeArray),
                                                                    String.valueOf(keepReference),
                                                                    String.valueOf(allocateAfter),
                                                                    phase);

        String log = output.getStdout();
        System.out.println(log);
        output.shouldHaveExitValue(0);
        return log;
    }

    private static String boolToInt(boolean value) {
        return value ? "1" : "0";
    }

    private static void runTest(ObjectType type,
                                ReferencePolicy refPolicy,
                                AllocationTiming timing,
                                String phase,
                                ExpectedState expected) throws Exception {
        String log = runHelperVM(type, refPolicy, timing, phase);

        // Find the log output indicating that the humongous object has been reclaimed, and marked and verify for the expected results.
// [0.351s][debug][gc,humongous] GC(3) Humongous region 2 (object size 4194320 @ 0x00000000fee00000) remset 0 code roots 0 marked 1 pinned count 0 reclaim candidate 1 type array 1

        // Now check the result of the reclaim attempt. We are interested in the last such message (as mentioned above, we might get two).
        String patternString = "gc,humongous.* marked (\\d) pin.*candidate (\\d)";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(log);

        List<MatchResult> found = new ArrayList<MatchResult>();
        while (matcher.find()) {
          found.add(matcher.toMatchResult());
        }

        Asserts.assertTrue(found.size() == 1 || found.size() == 2, "Unexpected number of log messages " + found.size());

        if (found.size() == 2) {
          Asserts.assertTrue(timing == AllocationTiming.BEFORE_MARK_START, "Should only have two messages if allocating the object before mark start");
          MatchResult mr = found.removeFirst();
          Asserts.assertTrue(mr.group(1).equals(boolToInt(false)), "Should not be marked before mark start " + mr.group());
          Asserts.assertTrue(mr.group(2).equals(boolToInt(true)), "Should be candidate before mark start " + mr.group());
        }

        MatchResult mr = found.removeFirst();
        Asserts.assertTrue(mr.group(1).equals(boolToInt(expected.marked)), "Expected that region was " + (expected.marked ? "" : "not ") + " marked but is " + mr.group());
        Asserts.assertTrue(mr.group(2).equals(boolToInt(expected.candidate)), "Expected that region was " + (expected.candidate ? "" : "not ") + " candidate but is " + mr.group());

        boolean reclaimed = Pattern.compile("Reclaimed humongous region .*").matcher(log).find();
        Asserts.assertTrue(expected.reclaimed == reclaimed, "Wrong log output reclaiming humongous region");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Tests checking eager reclaim for when the object is allocated before mark start.");
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.DROP, AllocationTiming.BEFORE_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.MARKED_CANDIDATE_RECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.DROP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.MARKED_CANDIDATE_RECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.DROP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);

        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.KEEP, AllocationTiming.BEFORE_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.MARKED_CANDIDATE_NOT_RECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.KEEP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.MARKED_CANDIDATE_NOT_RECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.KEEP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);

        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.DROP, AllocationTiming.BEFORE_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.MARKED_NOTCANDIDATE_NOTRECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.DROP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.MARKED_CANDIDATE_RECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.DROP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);

        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.KEEP, AllocationTiming.BEFORE_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.MARKED_NOTCANDIDATE_NOTRECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.KEEP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.MARKED_CANDIDATE_NOT_RECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.KEEP, AllocationTiming.BEFORE_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);

        System.out.println("Tests checking eager reclaim for when the object is allocated after mark start.");
        // These must not be marked (as they were allocated after mark start), and they are always candidates. Reclamation depends on whether there is a reference.
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.DROP, AllocationTiming.AFTER_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.DROP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.DROP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);

        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.KEEP, AllocationTiming.AFTER_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.KEEP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);
        runTest(ObjectType.TYPE_ARRAY, ReferencePolicy.KEEP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);

        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.DROP, AllocationTiming.AFTER_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.DROP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.DROP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_RECLAIMED);

        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.KEEP, AllocationTiming.AFTER_MARK_START, WB.BEFORE_MARKING_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.KEEP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_REBUILD_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);
        runTest(ObjectType.OBJ_ARRAY, ReferencePolicy.KEEP, AllocationTiming.AFTER_MARK_START, WB.G1_BEFORE_CLEANUP_COMPLETED, ExpectedState.NOTMARKED_CANDIDATE_NOTRECLAIMED);
    }
}

class TestEagerReclaimHumongousRegionsClearMarkBitsRunner {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int SIZE = 1024 * 1024;

    private static Object allocateHumongousObj(boolean useTypeArray) {
        if (useTypeArray) {
            return new int[SIZE];
        } else {
            return new Object[SIZE];
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new Exception("Invalid number of arguments " + args.length);
        }
        boolean useTypeArray = Boolean.parseBoolean(args[0]);
        boolean keepReference = Boolean.parseBoolean(args[1]);
        boolean allocateAfter = Boolean.parseBoolean(args[2]);
        String phase = args[3];

        System.out.println("useTypeArray: " + useTypeArray + " keepReference: " + keepReference + " allocateAfter " + allocateAfter + " phase: " + phase);
        WB.fullGC();

        Object largeObj = null; // Allocated humongous object.
        if (!allocateAfter) {
          largeObj = allocateHumongousObj(useTypeArray);
        }

        WB.concurrentGCAcquireControl();
        WB.concurrentGCRunTo(phase);

        System.out.println("Phase " + phase + " reached");

        if (allocateAfter) {
          largeObj = allocateHumongousObj(useTypeArray);
        }

        if (!keepReference) {
          largeObj = null;
        }
        WB.youngGC(); // May reclaim the humongous object.

        WB.concurrentGCRunToIdle();

        System.out.println("Large object at " + largeObj); // Keepalive.
    }
}
