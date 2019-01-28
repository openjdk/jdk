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
 * @test
 * @bug 8031323
 * @summary Verify that objects promoted from eden space to survivor space after
 *          minor GC are aligned to SurvivorAlignmentInBytes.
 * @requires vm.gc != "Z" & vm.gc != "Shenandoah"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32 -XX:OldSize=128m
 *                   -XX:MaxHeapSize=256m -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionToSurvivor 10m 9 SURVIVOR
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32 -XX:OldSize=128m
 *                   -XX:MaxHeapSize=256m -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionToSurvivor 20m 47 SURVIVOR
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64 -XX:OldSize=128m
 *                   -XX:MaxHeapSize=256m -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionToSurvivor 8m 9 SURVIVOR
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64 -XX:OldSize=128m
 *                   -XX:MaxHeapSize=256m -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionToSurvivor 20m 87 SURVIVOR
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=256m -XX:MaxNewSize=256m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128 -XX:OldSize=128m
 *                   -XX:MaxHeapSize=384m  -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionToSurvivor 10m 9 SURVIVOR
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=128m -XX:MaxNewSize=128m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128 -XX:OldSize=128m
 *                   -XX:MaxHeapSize=256m -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionToSurvivor 20m 147 SURVIVOR
 */
public class TestPromotionToSurvivor {
    public static void main(String args[]) {
        SurvivorAlignmentTestMain test
                = SurvivorAlignmentTestMain.fromArgs(args);
        System.out.println(test);

        long expectedUsage = test.getExpectedMemoryUsage();
        test.baselineMemoryAllocation();
        SurvivorAlignmentTestMain.WHITE_BOX.fullGC();

        test.allocate();
        SurvivorAlignmentTestMain.WHITE_BOX.youngGC();

        test.verifyMemoryUsage(expectedUsage);
    }
}
