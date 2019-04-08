/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.survivorAlignment;

/**
 * @test gc.survivorAlignment.TestPromotionFromSurvivorToTenuredAfterMinorGC
 * @bug 8031323
 * @summary Verify that objects promoted from survivor space to tenured space
 *          when their age exceeded tenuring threshold are not aligned to
 *          SurvivorAlignmentInBytes value.
 * @requires vm.gc != "Z" & vm.gc != "Shenandoah"
 * @library /test/lib
 * @library /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:OldSize=32M -XX:MaxHeapSize=160m -XX:SurvivorRatio=1 -XX:-ResizePLAB
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32
 *                   gc.survivorAlignment.TestPromotionFromSurvivorToTenuredAfterMinorGC 10m 9
 *                   TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:OldSize=32M -XX:MaxHeapSize=160m -XX:SurvivorRatio=1 -XX:-ResizePLAB
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32
 *                   gc.survivorAlignment.TestPromotionFromSurvivorToTenuredAfterMinorGC 20m 47
 *                   TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=200m -XX:MaxNewSize=200m
 *                   -XX:OldSize=32M -XX:MaxHeapSize=232m -XX:SurvivorRatio=1 -XX:-ResizePLAB
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64
 *                   gc.survivorAlignment.TestPromotionFromSurvivorToTenuredAfterMinorGC 10m 9
 *                   TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:OldSize=32M -XX:MaxHeapSize=160m -XX:SurvivorRatio=1 -XX:-ResizePLAB
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64
 *                   gc.survivorAlignment.TestPromotionFromSurvivorToTenuredAfterMinorGC 20m 87
 *                   TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=256m -XX:MaxNewSize=256m
 *                   -XX:OldSize=32M -XX:MaxHeapSize=288m -XX:SurvivorRatio=1 -XX:-ResizePLAB
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128
 *                    gc.survivorAlignment.TestPromotionFromSurvivorToTenuredAfterMinorGC 10m 9
 *                    TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:OldSize=32M -XX:MaxHeapSize=160m -XX:SurvivorRatio=1 -XX:-ResizePLAB
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128
 *                   gc.survivorAlignment.TestPromotionFromSurvivorToTenuredAfterMinorGC 20m 147
 *                   TENURED
 */
public class TestPromotionFromSurvivorToTenuredAfterMinorGC {
    public static void main(String args[]) throws Exception {
        SurvivorAlignmentTestMain test
                = SurvivorAlignmentTestMain.fromArgs(args);
        System.out.println(test);

        long expectedMemoryUsage = test.getExpectedMemoryUsage();
        test.baselineMemoryAllocation();
        SurvivorAlignmentTestMain.WHITE_BOX.fullGC();
        // increase expected usage by current old gen usage
        expectedMemoryUsage += SurvivorAlignmentTestMain.getAlignmentHelper(
                SurvivorAlignmentTestMain.HeapSpace.TENURED)
                .getActualMemoryUsage();

        test.allocate();
        for (int i = 0; i <= SurvivorAlignmentTestMain.MAX_TENURING_THRESHOLD; i++) {
            SurvivorAlignmentTestMain.WHITE_BOX.youngGC();
        }

        // Sometimes we see that data unrelated to the test has been allocated during
        // the loop. This data is included in the expectedMemoryUsage since we look
        // through all threads to see what they allocated. If this data is still in
        // the survivor area however, it should not be included in expectedMemoryUsage
        // since the verification below only look at what's in tenured space.
        expectedMemoryUsage -= SurvivorAlignmentTestMain.getAlignmentHelper(
                                   SurvivorAlignmentTestMain.HeapSpace.SURVIVOR)
                                   .getActualMemoryUsage();
        test.verifyMemoryUsage(expectedMemoryUsage);
    }
}
