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
 * @test TestEagerReclaimHumongousThreshold
 * @summary Check that G1EagerReclaimRemSetThreshold is working
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver gc.g1.TestEagerReclaimHumongousThreshold
 */

import sun.hotspot.WhiteBox;

import java.util.Arrays;
import jdk.test.lib.Asserts;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestEagerReclaimHumongousThreshold {

    private static final String LogSeparator = ": ";

    private static final String SumSeparator = "Sum: ";

    private static String getSumValue(String s) {
        return s.substring(s.indexOf(SumSeparator) + SumSeparator.length(), s.indexOf(", Workers"));
    }

    private static void checkForCandidates(String[] lines, int expectedCandidates) {
        Asserts.assertTrue(lines.length == 6, "Expecting an array of six lines with eager reclaim information, is " + lines.length);

        for (int i = 0; i < lines.length; i++) {
System.out.println("Lines: " + lines[i]);
}
 System.out.println("lines3: " + lines[3]);
        int candidates = Integer.parseInt(getSumValue(lines[3]));
        System.out.println("Candidates " + candidates + " expected " + expectedCandidates);

        Asserts.assertEQ(candidates, expectedCandidates, "Should have " + expectedCandidates + " actually has " + candidates);
    }

    public static void runTest(int threshold) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xbootclasspath/a:.",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-XX:+UseG1GC",
            "-XX:G1HeapRegionSize=1M",
            "-XX:+NeverTenure",                    // Hold off tenuring as much as possible.
            "-XX:-G1UseAdaptiveConcRefinement",
            "-XX:G1ConcRefinementThreads=0",
            "-XX:G1ConcRefinementGreenZone=0",
            "-XX:G1ConcRefinementYellowZone=0",
            "-XX:G1ConcRefinementRedZone=0",
            "-XX:G1UpdateBufferSize=1",            // We want immediate refinement with a minimum buffer
            "-XX:G1SATBBufferEnqueueingThresholdPercent=0",
            "-XX:G1EagerReclaimRemSetThreshold=" + threshold,
            "-Xms128M",
            "-Xmx128M",
            "-Xlog:gc+phases=trace,gc+heap=info,gc+humongous=debug",
            GCTest.class.getName(),
            String.valueOf(threshold));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldHaveExitValue(0);

        System.out.println(output.getStdout());

        // This gives an array of lines containing eager reclaim of humongous regions
        // log messages contents after the ":" in the following order for every GC:
        //   Region Register: a.ams
        //   Eagerly Reclaim Humonguous Objects b.cms
        //   Humongous Total: Min: 1, Avg:  1.0, Max: 1, Diff: 0, Sum: c, Workers: 1
        //   Humongous Candidate: Min: 1, Avg:  1.0, Max: 1, Diff: 0, Sum: d, Workers: 1
        //   Humongous Reclaimed: Min: 1, Avg:  1.0, Max: 1, Diff: 0, Sum: e, Workers: 1
        //   Humongous Regions: f->g

        String[] lines = Arrays.stream(output.getStdout().split("\\R"))
                         .filter(s -> (s.contains("Humongous") || s.contains("Region Register"))).map(s -> s.substring(s.indexOf(LogSeparator) + LogSeparator.length()))
                         .toArray(String[]::new);

        Asserts.assertTrue(lines.length >= 12, "There seems to be an unexpected amount of log messages (total: " + lines.length + "), should be at least 12");

        // Inspect the last two GC logs. The first should have one candidate,
        // the second none.
        checkForCandidates(Arrays.copyOfRange(lines, lines.length - 12, lines.length - 6), 1);
        checkForCandidates(Arrays.copyOfRange(lines, lines.length - 6, lines.length), 0);
    }

    public static void main(String[] args) throws Exception {
        runTest(128);
        runTest(10);
    }

    static class GCTest {
        private static final WhiteBox WB = WhiteBox.getWhiteBox();

        private static final int ReferencesPerCard = 512 / 4; // Upper bound on the number of references per card (=512 bytes)

        public static Object[] references;

        public static void main(String [] args) throws Exception {
            int threshold = Integer.parseInt(args[0]);

            // Create threshold amount of references to the object. Use another humongous
            // object holding these to make sure they are from old gen.
            references = new Object[1024 * 1024];

            // Create a humongous object, referenced by the references array in the first
            // card, also in old gen. This is our target region we check for being a candidate.
            references[0] = new byte[4 * 1024 * 1024];

            // Add exactly "threshold" number of cards that reference the candidate region.
            // This means that the target object will still be a candidate.
            for (int i = 0; i < threshold; i++) {
                references[i * ReferencesPerCard] = references[0];
            }
            // Do a single "random" reference writes so that the queue buffers we care about
            // are all flushed.
            references[references.length-1] = references;
            // This GC should be a GC with one eager reclaim candidates
            WB.youngGC();
            // Add one more card to the remembered set of the candidate region. The next GC
            // should not have a eager reclaim candidate region.
            references[(threshold + 1) * ReferencesPerCard] = references[0];
            // The last gc left threshold+1 references in the DCQ buffers. We need to make sure
            // that that one is completely processed (flushed into the remembered set) too in
            // addition to above card and one more to flush.
            for (int i = references.length - ReferencesPerCard * (threshold + 1 + 1 + 1); i < references.length; i += ReferencesPerCard) {
                references[i] = references;
            }
            // This GC should be a GC with no eager reclaim candidates
            WB.youngGC();
            // Keep the array we use alive.
            System.out.println(references);
        }
    }
}

