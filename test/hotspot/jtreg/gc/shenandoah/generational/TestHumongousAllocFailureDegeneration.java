/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8391591
 * @summary Humongous allocation failure triggers a degenerated cycle.
 *          Consecutive degenerated cycles escalate to a full GC.
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run driver TestHumongousAllocFailureDegeneration
 *      -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC
 *      -XX:ShenandoahGCMode=generational -Xmx128m -Xms128m
 *      -XX:ShenandoahRegionSize=1m -XX:+AlwaysPreTouch
 *      -Xlog:gc,gc+phases=info,gc+thread=debug
 *      -XX:ConcGCThreads=1 -XX:ParallelGCThreads=1
 */

import java.util.Arrays;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHumongousAllocFailureDegeneration {
    // Continuously allocate humongous objects to hit humongous allocation failure.
    // There will be consecutive degenerated cycles that can cause full GCs. However,
    // the degenerated cycles should make "good progress", so we won't transition into
    // a full GC in the middle of the degenerated cycle.
    public static class Allocator {
        static final int MB = 1024 * 1024;
        static final int HUMONGOUS_SIZE_MB = 48;
        static final int ITERATIONS = 32;
        static Object sink;

        public static void main(String[] args) {
            for (int i = 0; i < ITERATIONS; i++) {
                // Requests 49 contiguous regions. sink keeps the previous array live until it
                // gets reassigned and the one before it is often still unreclaimed. Those two
                // arrays occupy 98 of 128 regions, so the third allocation cannot be satisfied.
                sink = new byte[MB * HUMONGOUS_SIZE_MB];
            }
        }
    }

    public static void main(String[] args) throws Exception {
        final String degenStartMsg = "Starting GC (degenerated): Humongous Allocation Failure, Young";
        final String degenUpgradeMsg = "Degenerated GC upgrading to Full GC";
        final String fullStartMsg = "Starting GC (full): Humongous Allocation Failure";

        String[] flags = Arrays.copyOf(args, args.length + 1);
        flags[flags.length - 1] = Allocator.class.getName();

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(flags);
        output.shouldHaveExitValue(0);
        output.shouldContain(degenStartMsg);
        output.shouldContain(fullStartMsg);
        output.shouldNotContain(degenUpgradeMsg);
    }
}
