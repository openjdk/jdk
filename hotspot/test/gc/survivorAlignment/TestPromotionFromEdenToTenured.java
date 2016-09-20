/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8031323
 * @summary Verify that objects promoted from eden space to tenured space during
 *          full GC are not aligned to SurvivorAlignmentInBytes value.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:OldSize=32m -XX:MaxHeapSize=96m -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32
 *                   TestPromotionFromEdenToTenured 10m 9 TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:OldSize=32m -XX:MaxHeapSize=96m -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32
 *                   TestPromotionFromEdenToTenured 10m 47 TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:OldSize=32m  -XX:MaxHeapSize=96m
 *                   -XX:SurvivorRatio=1 -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64
 *                   TestPromotionFromEdenToTenured 10m 9 TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:OldSize=32m -XX:MaxHeapSize=128m
 *                   -XX:SurvivorRatio=1 -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64
 *                   TestPromotionFromEdenToTenured 10m 87 TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:OldSize=32M -XX:MaxHeapSize=96m -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128
 *                   TestPromotionFromEdenToTenured 10m 9 TENURED
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:OldSize=32m -XX:MaxHeapSize=96m -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128
 *                   TestPromotionFromEdenToTenured 10m 147 TENURED
 */
public class TestPromotionFromEdenToTenured {
    public static void main(String args[]) {
        SurvivorAlignmentTestMain test
                = SurvivorAlignmentTestMain.fromArgs(args);
        System.out.println(test);

        long expectedMemoryUsage = test.getExpectedMemoryUsage();
        test.baselineMemoryAllocation();
        System.gc();
        // increase expected usage by current old gen usage
        expectedMemoryUsage += SurvivorAlignmentTestMain.getAlignmentHelper(
                SurvivorAlignmentTestMain.HeapSpace.TENURED)
                .getActualMemoryUsage();

        test.allocate();
        System.gc();

        test.verifyMemoryUsage(expectedMemoryUsage);
    }
}
