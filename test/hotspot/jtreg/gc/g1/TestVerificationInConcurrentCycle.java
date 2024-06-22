/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestVerificationInConcurrentCycle
 * @requires vm.gc.G1
 * @requires vm.debug
 * @summary Basic testing of various GC pause verification during the G1 concurrent cycle.
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *   -Xbootclasspath/a:.
 *   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *   -XX:+VerifyBeforeGC -XX:+VerifyDuringGC -XX:+VerifyAfterGC
 *   -XX:+UseG1GC -XX:+G1VerifyHeapRegionCodeRoots
 *   -XX:+G1VerifyBitmaps
 *   gc.g1.TestVerificationInConcurrentCycle
 */

/*
 * @test TestVerificationInConcurrentCycle
 * @requires vm.gc.G1
 * @requires !vm.debug
 * @summary Basic testing of various GC pause verification during the G1 concurrent cycle. It leaves
 *          out G1VerifyBitmaps as this is a debug-only option.
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *   -Xbootclasspath/a:.
 *   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *   -XX:+VerifyBeforeGC -XX:+VerifyDuringGC -XX:+VerifyAfterGC
 *   -XX:+UseG1GC -XX:+G1VerifyHeapRegionCodeRoots
 *   gc.g1.TestVerificationInConcurrentCycle
 */

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.gc.GC;

public class TestVerificationInConcurrentCycle {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    // All testN() assume initial state is idle, and restore that state.

    private static void testFullGCAt(String at) throws Exception {
        System.out.println("testSimpleCycle");
        try {
            // Run one cycle.
            WB.concurrentGCRunTo(at);
            WB.fullGC();
        } finally {
            WB.concurrentGCRunToIdle();
        }
    }

    private static void testYoungGCAt(String at) throws Exception {
        System.out.println("testSimpleCycle");
        try {
            // Run one cycle.
            WB.concurrentGCRunTo(at);
            WB.youngGC();
        } finally {
            WB.concurrentGCRunToIdle();
        }
    }

    private static void testGCAt(String at) throws Exception {
        testYoungGCAt(at);
        testFullGCAt(at);
    }

    private static void test() throws Exception {
        try {
            System.out.println("taking control");
            WB.concurrentGCAcquireControl();
            testGCAt(WB.AFTER_MARKING_STARTED);
            testGCAt(WB.BEFORE_MARKING_COMPLETED);
            testGCAt(WB.G1_AFTER_REBUILD_STARTED);
            testGCAt(WB.G1_BEFORE_REBUILD_COMPLETED);
            testGCAt(WB.G1_AFTER_CLEANUP_STARTED);
            testGCAt(WB.G1_BEFORE_CLEANUP_COMPLETED);
        } finally {
            System.out.println("releasing control");
            WB.concurrentGCReleaseControl();
        }
    }

    public static void main(String[] args) throws Exception {
        if (!WB.supportsConcurrentGCBreakpoints()) {
            throw new RuntimeException("G1 should support GC breakpoints");
        }
        test();
    }
}
