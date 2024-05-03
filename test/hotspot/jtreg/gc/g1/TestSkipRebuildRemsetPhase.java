/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestSkipRebuildRemsetPhase
 * @summary Skip Rebuild Remset Phase if the Remark pause does not identify any rebuild candidates.
 *          Fill up a region to above the set G1MixedGCLiveThresholdPercent.
 * @requires vm.gc.G1
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.g1.TestSkipRebuildRemsetPhase
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestSkipRebuildRemsetPhase {
    public static void main(String[] args) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-Xbootclasspath/a:.",
                                                                    "-XX:+UseG1GC",
                                                                    "-XX:+UnlockExperimentalVMOptions",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    "-XX:G1MixedGCLiveThresholdPercent=0",
                                                                    "-Xlog:gc+marking=debug,gc+phases=debug,gc+remset+tracking=trace",
                                                                    "-Xms10M",
                                                                    "-Xmx10M",
                                                                    GCTest.class.getName());
        output.shouldContain("Skipping Remembered Set Rebuild.");
        output.shouldContain("No Remembered Sets to update after rebuild");
        output.shouldHaveExitValue(0);
    }

    public static class GCTest {
        public static void main(String args[]) throws Exception {
            WhiteBox wb = WhiteBox.getWhiteBox();
            // Allocate some memory less than region size. Any object is just fine as we set
            // G1MixedGCLiveThresholdPercent to zero (and no region should be selected).
            Object used = new byte[2000];

            // Trigger the full GC using the WhiteBox API to make sure that at least "used"
            // has been promoted to old gen.
            wb.fullGC();

            // Memory objects have been promoted to old by full GC.
            // Concurrent cycle should not select any regions for rebuilding and print the
            // appropriate message.
            wb.g1RunConcurrentGC();
            System.out.println(used);
        }
    }
}

