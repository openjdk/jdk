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

import java.util.Arrays;
import java.util.ArrayList;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test id=satb
 * @bug 8305503
 * @summary Test humongous heuristics
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @run driver TestHumongousHeuristics
 */

public class TestHumongousHeuristics {
    static void testWith(String... flags) throws Exception {
        String[] cmds = Arrays.copyOf(flags, flags.length + 2);
        cmds[flags.length] = TestHumongousHeuristics.class.getName();
        cmds[flags.length + 1] = "test";
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(cmds);
        output.shouldHaveExitValue(0);

        output.shouldNotContain("Allocation Failure");
        output.shouldNotContain("Allocation stalls:");
        output.shouldNotContain("Pause Degenerated");
        output.shouldNotContain("Pause Full");

        output.shouldMatch("Trigger: (Triggering concurrent gc for humongous alloc failure risk"
                             + "|Max humongous allocatable)");
        // Output has humongous key word, but not 'Trigger: GC Request (Humongous Allocation Failure)'
        output.shouldMatch("Trigger( \\(\\w+\\))?: (?!GC Request).*[Hh]umongous");
    }


    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("test")) {
            Test.run();
            return;
        }

        testWith("-Xlog:gc*",
                "-Xms1g",
                "-Xmx1g",
                "-XX:+UseShenandoahGC",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:ShenandoahGCMode=satb",
                "-XX:ShenandoahGCHeuristics=adaptive",
                "-XX:ShenandoahRegionSize=1m");
    }

    static class Test {
        static final int REGION_SIZE = 1024 * 1024; // 1M ShenandoahRegionSize
        static final int SMALL_HUMONGOUS_OBJ_SIZE = REGION_SIZE + 8; // 2 region size
        static final int LARGE_HUMONGOUS_OBJ_SIZE = 2 * REGION_SIZE + 8; // 3 region size
        static final ArrayList<byte[]> RETAIN_LIST = new ArrayList<>();
        static final int ITERATION = 200;

        // Step 1: Create a heap with region holes: fill out 90% heap with smaller humongous object first,
        //         and retain half of the objects.
        // Step 2: Slowly allocate and drop temporary humongous objects that are too large to fit into the smaller holes created in step 1 and can only use
        //         the tail space left in step 1.
        //         The heap after step 1 is only less than half full, so the heap utilization trigger will not trigger gc to release more space
        //         for the larger objects even though there is not where to fit. The humongous heuristic is supposed to catch this.
        static void run() throws Exception {
            int totalRegions = (int) (Runtime.getRuntime().maxMemory() / REGION_SIZE);

            int targetFillingNumber = (int) (totalRegions * 0.9) / 2; // Fill 90% of heap with small humongous objects
            for (int i = 0; i < targetFillingNumber; i++) {
                byte[] bArr = new byte[SMALL_HUMONGOUS_OBJ_SIZE];
                bArr[0] = 1; // use the array so it's not cleared
                if (i % 2 == 0) {
                    RETAIN_LIST.add(bArr);
                }
            }

            for (int i = 0; i < ITERATION; i++) {
                byte[] bArr = new byte[LARGE_HUMONGOUS_OBJ_SIZE];
                bArr[0] = 1;
                Thread.sleep(50);
            }
        }
    }
}
