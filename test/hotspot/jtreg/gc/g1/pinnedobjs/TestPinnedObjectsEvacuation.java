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
 * @summary Test pinned objects lifecycle from young gen to eventual reclamation.
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.g1.pinnedobjs.TestPinnedObjectsEvacuation
 */

package gc.g1.pinnedobjs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestPinnedObjectsEvacuation {

    public static void main(String[] args) throws Exception {
        testPinnedEvacuation(0, 0, 0, 1);
        testPinnedEvacuation(1, 1, 0, 1);
        testPinnedEvacuation(2, 1, 1, 0);
        testPinnedEvacuation(3, 1, 1, 0);
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

    private static void testPinnedEvacuation(int younGCsBeforeUnpin, int expectedSkipEvents, int expectedDropEvents, int expectedReclaimEvents) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    "-Xbootclasspath/a:.",
                                                                    "-Xmx32M",
                                                                    "-Xmn16M",
                                                                    "-XX:G1NumCollectionsKeepPinned=2",
                                                                    "-XX:+VerifyAfterGC",
                                                                    "-Xlog:gc,gc+ergo+cset=trace",
                                                                    TestObjectPin.class.getName(),
                                                                    String.valueOf(younGCsBeforeUnpin));

        System.out.println(output.getStdout());
        output.shouldHaveExitValue(0);

        assertMatches(expectedSkipEvents, numMatches(output.getStdout(), ".*Retained candidate \\d+ can not be reclaimed currently. Skipping.*"), "skip");
        assertMatches(expectedDropEvents, numMatches(output.getStdout(), ".*Retained candidate \\d+ can not be reclaimed currently. Dropping.*"), "drop");
        assertMatches(expectedReclaimEvents, numMatches(output.getStdout(), ".*Finish adding retained candidates to collection set. Initial: 1,.*"), "reclaim");
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

        // Remove garbage from VM initialization.
        wb.fullGC();

        Object o = new int[100];
        Asserts.assertTrue(!wb.isObjectInOldGen(o), "should not be pinned in old gen");

        long address = pinAndGetAddress(o);

        // First young GC: should move the object into old gen.
        wb.youngGC();
        Asserts.assertTrue(wb.isObjectInOldGen(o), "Pinned object not in old gen after young GC");

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
