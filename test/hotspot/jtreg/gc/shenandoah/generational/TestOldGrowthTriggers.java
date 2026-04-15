/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test id=generational
 * @summary Test that growth of old-gen triggers old-gen marking
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run driver TestOldGrowthTriggers
 */

import java.util.Arrays;
import java.util.BitSet;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestOldGrowthTriggers {

    public static void makeOldAllocations() {
        // Expect most of the BitSet entries placed into array to be promoted, and most will eventually become garbage within old

        final int ArraySize = 1024;  // 1K entries
        final int RefillIterations = 128;
        BitSet[] array = new BitSet[ArraySize];

        for (int i = 0; i < ArraySize; i++) {
            array[i] = new BitSet(256 * 1024);
        }

        for (int refillCount = 0; refillCount < RefillIterations; refillCount++) {
            // Each refill repopulates ArraySize
            for (int i = 1; i < ArraySize; i++) {
                int replaceIndex = i;
                int deriveIndex = i-1;

                switch (i & 0x7) {
                    case 0,1,2 -> {
                        // creates new BitSet, releases old BitSet,
                        // create ephemeral data while computing
                        BitSet result = (BitSet) array[deriveIndex].clone();
                        for (int j=0; j<10; j++) {
                            result = (BitSet) array[deriveIndex].clone();
                        }
                        array[replaceIndex] = result;
                    }
                    case 3,4 -> {
                        // creates new BitSet, releases old BitSet
                        BitSet result = (BitSet) array[deriveIndex].clone();
                        array[replaceIndex] = result;
                    }
                    case 5,6,7 -> {
                        // do nothing, let all objects in the array age to increase pressure on old generation
                    }
                }
            }
        }
    }

    public static void testOld(String... args) throws Exception {
        String[] cmds = Arrays.copyOf(args, args.length + 2);
        cmds[args.length] = TestOldGrowthTriggers.class.getName();
        cmds[args.length + 1] = "test";
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(cmds);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("Trigger (Old): Old has overgrown");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("test")) {
            makeOldAllocations();
            return;
        }

        testOld("-Xlog:gc",
                "-XX:ConcGCThreads=1",
                "-XX:ParallelGCThreads=1",
                "-Xms96m",
                "-Xmx96m",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseShenandoahGC",
                "-XX:ShenandoahGCMode=generational",
                "-XX:ShenandoahMinOldGenGrowthPercent=12.5",
                "-XX:ShenandoahIgnoreOldGrowthBelowPercentage=10",
                "-XX:ShenandoahMinOldGenGrowthRemainingHeapPercent=100",
                "-XX:ShenandoahGuaranteedYoungGCInterval=0",
                "-XX:ShenandoahGuaranteedOldGCInterval=0",
                "-XX:-UseCompactObjectHeaders"
        );

        testOld("-Xlog:gc",
                "-XX:ConcGCThreads=1",
                "-XX:ParallelGCThreads=1",
                "-Xms96m",
                "-Xmx96m",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseShenandoahGC",
                "-XX:ShenandoahGCMode=generational",
                "-XX:ShenandoahMinOldGenGrowthPercent=12.5",
                "-XX:ShenandoahIgnoreOldGrowthBelowPercentage=10",
                "-XX:ShenandoahMinOldGenGrowthRemainingHeapPercent=100",
                "-XX:ShenandoahGuaranteedYoungGCInterval=0",
                "-XX:ShenandoahGuaranteedOldGCInterval=0",
                "-XX:+UseCompactObjectHeaders"
        );
    }
}
