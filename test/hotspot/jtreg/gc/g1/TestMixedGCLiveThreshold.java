/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestMixedGCLiveThreshold
 * @summary Test G1MixedGCLiveThresholdPercent. Fill up a region to at least 1/3 region-size,
 * the region should not be selected for mixed GC cycle if liveness is above threshold.
 * @requires vm.gc.G1
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver gc.g1.TestMixedGCLiveThreshold
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

public class TestMixedGCLiveThreshold {
    private static final String pattern = "Remembered Set Tracking update regions total ([0-9]+), selected ([0-9]+)$";

    public static void main(String[] args) throws Exception {
        // -XX:G1MixedGCLiveThresholdPercent=0
        testMixedGCLiveThresholdPercent(0, false);

        // -XX:G1MixedGCLiveThresholdPercent=25
        testMixedGCLiveThresholdPercent(25, false);

        // -XX:G1MixedGCLiveThresholdPercent=100
        testMixedGCLiveThresholdPercent(100, true);
    }

    private static void testMixedGCLiveThresholdPercent(int liveThresholdPercent, boolean expectedRebuild) throws Exception {
        OutputAnalyzer output = testWithMixedGCLiveThresholdPercent(liveThresholdPercent);

        boolean regionsSelected = regionsSelectedForRebuild(output.getStdout());

        Asserts.assertEquals(regionsSelected, expectedRebuild,
                             (expectedRebuild ?
                             "No Regions selected for rebuild. G1MixedGCLiveThresholdPercent=" + liveThresholdPercent +
                             " at least one region should be selected" :
                             "Regions selected for rebuild. G1MixedGCLiveThresholdPercent=" + liveThresholdPercent +
                             " no regions should be selected")
                            );
        output.shouldHaveExitValue(0);
    }

    private static OutputAnalyzer testWithMixedGCLiveThresholdPercent(int percent) throws Exception {
        ArrayList<String> basicOpts = new ArrayList<>();
        Collections.addAll(basicOpts, new String[] {
                                       "-Xbootclasspath/a:.",
                                       "-XX:+UseG1GC",
                                       "-XX:+UnlockDiagnosticVMOptions",
                                       "-XX:+UnlockExperimentalVMOptions",
                                       "-XX:+WhiteBoxAPI",
                                       "-Xlog:gc+remset+tracking=trace",
                                       "-Xms10M",
                                       "-Xmx10M"});

        basicOpts.add("-XX:G1MixedGCLiveThresholdPercent=" + percent);

        basicOpts.add(GCTest.class.getName());

        ProcessBuilder procBuilder =  ProcessTools.createJavaProcessBuilder(basicOpts);
        OutputAnalyzer analyzer = new OutputAnalyzer(procBuilder.start());
        return analyzer;
    }

    private static boolean regionsSelectedForRebuild(String output) throws Exception {
        Matcher m = Pattern.compile(pattern, Pattern.MULTILINE).matcher(output);

        if (!m.find()) {
            throw new Exception("Could not find correct output for Remembered Set Tracking in stdout," +
              " should match the pattern \"" + pattern + "\", but stdout is \n" + output);
        }
        return Integer.parseInt(m.group(2)) > 0;
    }

    public static class GCTest {
        public static void main(String args[]) throws Exception {
            WhiteBox wb = WhiteBox.getWhiteBox();
            // Allocate some memory less than region size.
            Object used = allocate();

            // Trigger the full GC using the WhiteBox API.
            wb.fullGC();  // full

            // Memory objects have been promoted to old by full GC.
            // Concurrent cycle may select regions for rebuilding
            wb.g1StartConcMarkCycle(); // concurrent-start, remark and cleanup

            // Sleep to make sure concurrent cycle is done
            while (wb.g1InConcurrentMark()) {
                Thread.sleep(1000);
            }
            System.out.println(used);
        }

        private static Object allocate() {
            final int objectSize = WhiteBox.getWhiteBox().g1RegionSize() / 3;
            Object ret = new byte[objectSize];
            return ret;
        }
    }
}
