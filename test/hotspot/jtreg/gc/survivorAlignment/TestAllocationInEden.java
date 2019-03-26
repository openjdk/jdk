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
 * @summary Verify that object's alignment in eden space is not affected by
 *          SurvivorAlignmentInBytes option.
 * @requires vm.gc != "Z" & vm.gc != "Shenandoah"
 * @library /test/lib
 * @library /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32 -XX:-UseTLAB -XX:-ResizePLAB
 *                   -XX:OldSize=128m -XX:MaxHeapSize=192m
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   gc.survivorAlignment.TestAllocationInEden 10m 9 EDEN
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=32 -XX:-UseTLAB -XX:-ResizePLAB
 *                   -XX:OldSize=128m -XX:MaxHeapSize=192m
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   gc.survivorAlignment.TestAllocationInEden 10m 47 EDEN
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64 -XX:-UseTLAB -XX:-ResizePLAB
 *                   -XX:OldSize=128m  -XX:MaxHeapSize=192m
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   gc.survivorAlignment.TestAllocationInEden 10m 9 EDEN
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=64 -XX:-UseTLAB -XX:-ResizePLAB
 *                   -XX:OldSize=128m  -XX:MaxHeapSize=192m
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   gc.survivorAlignment.TestAllocationInEden 10m 87 EDEN
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128 -XX:-UseTLAB -XX:-ResizePLAB
 *                   -XX:OldSize=128m -XX:MaxHeapSize=192m
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   gc.survivorAlignment.TestAllocationInEden 10m 9 EDEN
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:NewSize=64m -XX:MaxNewSize=64m
 *                   -XX:SurvivorRatio=1 -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=128 -XX:-UseTLAB -XX:-ResizePLAB
 *                   -XX:OldSize=128m -XX:MaxHeapSize=192m
 *                   -XX:-ExplicitGCInvokesConcurrent
 *                   gc.survivorAlignment.TestAllocationInEden 10m 147 EDEN
 */
public class TestAllocationInEden {
    public static void main(String args[]) {
        SurvivorAlignmentTestMain test
                = SurvivorAlignmentTestMain.fromArgs(args);
        System.out.println(test);

        long expectedMemoryUsage = test.getExpectedMemoryUsage();
        test.baselineMemoryAllocation();
        System.gc();

        test.allocate();

        test.verifyMemoryUsage(expectedMemoryUsage);
    }
}
