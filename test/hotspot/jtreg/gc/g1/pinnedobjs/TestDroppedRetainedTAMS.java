/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Check that TAMSes are correctly updated for regions dropped from
 *          the retained collection set candidates during a Concurrent Start pause.
 * @requires vm.gc.G1
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
                     -XX:+WhiteBoxAPI -Xbootclasspath/a:. -Xmx32m -XX:G1NumCollectionsKeepPinned=1
                     -XX:+VerifyBeforeGC -XX:+VerifyAfterGC -XX:G1MixedGCLiveThresholdPercent=100
                     -XX:G1HeapWastePercent=0 -Xlog:gc,gc+ergo+cset=trace gc.g1.pinnedobjs.TestDroppedRetainedTAMS
 */

package gc.g1.pinnedobjs;

import jdk.test.whitebox.WhiteBox;

public class TestDroppedRetainedTAMS {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    private static final char[] dummy = new char[100];

    public static void main(String[] args) {
        wb.fullGC(); // Move the target dummy object to old gen.

        wb.pinObject(dummy);

        // After this concurrent cycle the pinned region will be in the the (marking)
        // collection set candidates.
        wb.g1RunConcurrentGC();

        // Pass the Prepare mixed gc which will not do anything about the marking
        // candidates.
        wb.youngGC();
        // Mixed GC. Will complete. That pinned region is now retained. The mixed gcs
        // will end here.
        wb.youngGC();

        // The pinned region will be dropped from the retained candidates during the
        // Concurrent Start GC, leaving that region's TAMS broken.
        wb.g1RunConcurrentGC();

        // Verification will find a lot of broken objects.
        wb.youngGC();
        System.out.println(dummy);
    }
}
