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

/* @test
 * @summary Test pinned objects lifecycle from old gen to eventual reclamation.
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.g1.pinnedobjs.TestPinnedOldObjectsEvacuation
 */

package gc.g1.pinnedobjs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

class TestResultTracker {
    private int trackedRegion = -1;
    private int curGC = -1;
    private String stdout;
    private int expectedMarkingSkipEvents;      // How many times has the region from the "marking" collection set candidate set been "skipped".
    private int expectedRetainedSkipEvents;     // How many times has the region from the "retained" collection set candidate set been "skipped".
    private int expectedDropEvents;             // How many times has the region from the "retained" collection set candidate set been "dropped".
    private int expectedMarkingReclaimEvents;   // How many times has the region from the "marking" collection set candidate set been put into the collection set.
    private int expectedRetainedReclaimEvents;  // How many times has the region from the "marking" collection set candidate set been put into the collection set.

    TestResultTracker(String stdout,
                      int expectedMarkingSkipEvents,
                      int expectedRetainedSkipEvents,
                      int expectedDropEvents,
                      int expectedMarkingReclaimEvents,
                      int expectedRetainedReclaimEvents) {
        this.stdout = stdout;
        this.expectedMarkingSkipEvents = expectedMarkingSkipEvents;
        this.expectedRetainedSkipEvents = expectedRetainedSkipEvents;
        this.expectedDropEvents = expectedDropEvents;
        this.expectedMarkingReclaimEvents = expectedMarkingReclaimEvents;
        this.expectedRetainedReclaimEvents = expectedRetainedReclaimEvents;
    }

    private void updateOrCompareCurRegion(String phase, int curRegion) {
        if (trackedRegion == -1) {
            trackedRegion = curRegion;
        } else {
            if (trackedRegion != curRegion) {
                Asserts.fail("Expected region " + trackedRegion + " to be used but is " + curRegion);
            }
        }
    }

    private void expectMoreMatches(Matcher matcher, String event) {
        if (!matcher.find()) {
            Asserts.fail("Expected one more " + event);
        }
    }

    private int expectIncreasingGC(Matcher matcher) {
        int nextGC = Integer.parseInt(matcher.group(1));
        if (nextGC <= curGC) {
            Asserts.fail("Non-increasing GC number from " + curGC + " to " + nextGC);
        }
        return nextGC;
    }

    // Verify log messages based on expected events.
    //
    // There are two log messages printed with -Xlog:ergo+cset=trace that report about success or failure to
    // evacuate particular regions (in this case) due to pinning:
    //
    //   1) GC(<x>) Marking/Retained candidate <region-idx> can not be reclaimed currently. Skipping/Dropping.
    //
    // and
    //
    //   2) GC(<x>) Finish adding retained/marking candidates to collection set. Initial: <y> ... pinned: <z>
    //
    // 1) reports about whether the given region has been added to the collection set or not. The last word indicates whether the
    // region has been removed from the collection set candidates completely ("Dropping"), or just skipped for this collection
    // ("Skipping")
    //
    // This message is printed for every such region, however since the test only pins a single object/region and can only be
    // in one of the collection set candidate sets, there will be only one message per GC.
    //
    // 2) reports statistics about how many regions were added to the initial collection set, optional collection set (not shown
    // here) and the amount of pinned regions for every kind of collection set candidate sets ("marking" or "retained").
    //
    // There are two such messages per GC.
    //
    // The code below tracks that single pinned region through the various stages as defined by the policy.
    //
    public void verify() throws Exception {
        final String skipDropEvents = "GC\\((\\d+)\\).*(Marking|Retained) candidate (\\d+) can not be reclaimed currently\\. (Skipping|Dropping)";
        final String reclaimEvents = "GC\\((\\d+)\\) Finish adding (retained|marking) candidates to collection set\\. Initial: (\\d+).*pinned: (\\d+)";

        Matcher skipDropMatcher = Pattern.compile(skipDropEvents, Pattern.MULTILINE).matcher(stdout);
        Matcher reclaimMatcher = Pattern.compile(reclaimEvents, Pattern.MULTILINE).matcher(stdout);

        for (int i = 0; i < expectedMarkingSkipEvents; i++) {
            expectMoreMatches(skipDropMatcher, "expectedMarkingSkipEvents");
            curGC = expectIncreasingGC(skipDropMatcher);

            Asserts.assertEQ("Marking", skipDropMatcher.group(2), "Expected \"Marking\" tag for GC " + curGC + " but got \"" + skipDropMatcher.group(2) + "\"");
            updateOrCompareCurRegion("MarkingSkip", Integer.parseInt(skipDropMatcher.group(3)));
            Asserts.assertEQ("Skipping", skipDropMatcher.group(4), "Expected \"Skipping\" tag for GC " + curGC + " but got \"" + skipDropMatcher.group(4) + "\"");

            while (true) {
                if (!reclaimMatcher.find()) {
                    Asserts.fail("Could not find \"Finish adding * candidates\" line for GC " + curGC);
                }
                if (reclaimMatcher.group(2).equals("retained")) {
                    continue;
                }
                if (Integer.parseInt(reclaimMatcher.group(1)) == curGC) {
                    int actual = Integer.parseInt(reclaimMatcher.group(4));
                    Asserts.assertEQ(actual, 1, "Expected number of pinned to be 1 after marking skip but is " + actual);
                    break;
                }
            }
        }

        for (int i = 0; i < expectedRetainedSkipEvents; i++) {
            expectMoreMatches(skipDropMatcher, "expectedRetainedSkipEvents");
            curGC = expectIncreasingGC(skipDropMatcher);

            Asserts.assertEQ("Retained", skipDropMatcher.group(2), "Expected \"Retained\" tag for GC " + curGC + " but got \"" + skipDropMatcher.group(2) + "\"");
            updateOrCompareCurRegion("RetainedSkip", Integer.parseInt(skipDropMatcher.group(3)));
            Asserts.assertEQ("Skipping", skipDropMatcher.group(4), "Expected \"Skipping\" tag for GC " + curGC + " but got \"" + skipDropMatcher.group(4) + "\"");

            while (true) {
                if (!reclaimMatcher.find()) {
                    Asserts.fail("Could not find \"Finish adding * candidates\" line for GC " + curGC);
                }
                if (reclaimMatcher.group(2).equals("marking")) {
                    continue;
                }
                if (Integer.parseInt(reclaimMatcher.group(1)) == curGC) {
                    int actual = Integer.parseInt(reclaimMatcher.group(4));
                    Asserts.assertEQ(actual, 1, "Expected number of pinned to be 1 after retained skip but is " + actual);
                    break;
                }
            }
        }

        for (int i = 0; i < expectedDropEvents; i++) {
            expectMoreMatches(skipDropMatcher, "expectedDropEvents");
            curGC = expectIncreasingGC(skipDropMatcher);

            Asserts.assertEQ("Retained", skipDropMatcher.group(2), "Expected \"Retained\" tag for GC " + curGC + " but got \"" + skipDropMatcher.group(2) + "\"");
            updateOrCompareCurRegion("RetainedDrop", Integer.parseInt(skipDropMatcher.group(3)));
            Asserts.assertEQ("Dropping", skipDropMatcher.group(4), "Expected \"Dropping\" tag for GC " + curGC + " but got \"" + skipDropMatcher.group(4) + "\"");

            while (true) {
                if (!reclaimMatcher.find()) {
                    Asserts.fail("Could not find \"Finish adding * candidates\" line for GC " + curGC);
                }
                if (reclaimMatcher.group(2).equals("marking")) {
                    continue;
                }
                if (Integer.parseInt(reclaimMatcher.group(1)) == curGC) {
                    int actual = Integer.parseInt(reclaimMatcher.group(4));
                    if (actual != 1) {
                        Asserts.fail("Expected number of pinned to be 1 after dropping but is " + actual);
                    }
                    break;
                }
            }
        }

        for (int i = 0; i < expectedMarkingReclaimEvents; i++) {
            expectMoreMatches(reclaimMatcher, "\"Finish adding * candidates\" line for GC " + curGC);

            int nextGC = Integer.parseInt(reclaimMatcher.group(1));
            curGC = nextGC;
            if (reclaimMatcher.group(2).equals("retained")) {
                continue;
            }

            if (Integer.parseInt(reclaimMatcher.group(1)) == nextGC) {
                int actual = Integer.parseInt(reclaimMatcher.group(4));
                if (actual != 0) {
                    Asserts.fail("Expected number of pinned to be 0 after marking reclaim but is " + actual);
                }
            }
        }

        for (int i = 0; i < expectedRetainedReclaimEvents; i++) {
            expectMoreMatches(reclaimMatcher, "\"Finish adding * candidates\" line for GC " + curGC);

            int nextGC = Integer.parseInt(reclaimMatcher.group(1));
            curGC = nextGC;
            if (reclaimMatcher.group(2).equals("marking")) {
                continue;
            }

            if (Integer.parseInt(reclaimMatcher.group(1)) == nextGC) {
                int actual = Integer.parseInt(reclaimMatcher.group(4));
                if (actual != 0) {
                    Asserts.fail("Expected number of pinned to be 0 after retained reclaim but is " + actual);
                }
            }
        }
    }
}

public class TestPinnedOldObjectsEvacuation {

    public static void main(String[] args) throws Exception {
        // younGCsBeforeUnpin, expectedMarkingSkipEvents, expectedRetainedSkipEvents, expectedDropEvents, expectedMarkingReclaimEvents, expectedRetainedReclaimEvents
        testPinnedEvacuation(1, 1, 0, 0, 0, 1);
        testPinnedEvacuation(2, 1, 1, 0, 0, 1);
        testPinnedEvacuation(3, 1, 2, 0, 0, 1);
        testPinnedEvacuation(4, 1, 2, 1, 0, 0);
    }

    private static int numMatches(String stringToMatch, String pattern) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(stringToMatch);
        return (int)m.results().count();
    }

    private static void assertMatches(int expected, int actual, String what) {
        if (expected != actual) {
          Asserts.fail("Expected " + expected + " " + what + " events but got " + actual);
        }
    }

    private static void testPinnedEvacuation(int youngGCsBeforeUnpin,
                                             int expectedMarkingSkipEvents,
                                             int expectedRetainedSkipEvents,
                                             int expectedDropEvents,
                                             int expectedMarkingReclaimEvents,
                                             int expectedRetainedReclaimEvents) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    "-Xbootclasspath/a:.",
                                                                    "-Xmx32M",
                                                                    "-Xmn16M",
                                                                    "-XX:MarkSweepDeadRatio=0",
                                                                    "-XX:G1NumCollectionsKeepPinned=3",
                                                                    "-XX:+UnlockExperimentalVMOptions",
                                                                    // Take all old regions to make sure that the pinned one is included in the collection set.
                                                                    "-XX:G1MixedGCLiveThresholdPercent=100",
                                                                    "-XX:G1HeapWastePercent=0",
                                                                    "-XX:+VerifyAfterGC",
                                                                    "-Xlog:gc,gc+ergo+cset=trace",
                                                                    TestObjectPin.class.getName(),
                                                                    String.valueOf(youngGCsBeforeUnpin));

        System.out.println(output.getStdout());
        output.shouldHaveExitValue(0);

        TestResultTracker t = new TestResultTracker(output.getStdout(),
                                                    expectedMarkingSkipEvents,
                                                    expectedRetainedSkipEvents,
                                                    expectedDropEvents,
                                                    expectedMarkingReclaimEvents,
                                                    expectedRetainedReclaimEvents);
        t.verify();
    }

}

class TestObjectPin {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static long pinAndGetAddress(Object o) {
        wb.pinObject(o);
        return wb.getObjectAddress(o);
    }

    public static void unpinAndCompareAddress(Object o, long expectedAddress) {
        Asserts.assertEQ(expectedAddress, wb.getObjectAddress(o), "Object has moved during pinning.");
        wb.unpinObject(o);
    }

    public static void main(String[] args) {

        int youngGCBeforeUnpin = Integer.parseInt(args[0]);
        // Remove garbage from VM initialization
        wb.fullGC();

        Object o = new int[100];
        Asserts.assertTrue(!wb.isObjectInOldGen(o), "should not be pinned in old gen");

        long address = pinAndGetAddress(o);

        // Move pinned object into old gen. That region containing it should be almost completely empty,
        // so it will be picked up as collection set candidate.
        wb.fullGC();
        Asserts.assertTrue(wb.isObjectInOldGen(o), "Pinned object not in old gen after young GC");

        // Do a concurrent cycle to move the region into the marking candidates.
        wb.g1RunConcurrentGC();
        // Perform the "Prepare Mixed" GC.
        wb.youngGC();
        // The object is (still) pinned. Do some configurable young gcs that fail to add it to the
        // collection set candidates.
        for (int i = 0; i < youngGCBeforeUnpin; i++) {
          wb.youngGC();
        }
        unpinAndCompareAddress(o, address);

        // Unpinned the object. This next gc should take the region if not dropped.
        wb.youngGC();
    }
}
