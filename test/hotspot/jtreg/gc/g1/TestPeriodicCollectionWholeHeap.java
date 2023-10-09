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
 */

package gc.g1;

/**
 * @test id=young-only
 * @requires vm.gc.G1 & vm.flagless
 * @library /test/lib /
 * @run driver gc.g1.TestPeriodicCollectionWholeHeap young-only
 */

/**
 * @test id=concurrent
 * @requires vm.gc.G1 & vm.flagless
 * @library /test/lib /
 * @run driver gc.g1.TestPeriodicCollectionWholeHeap concurrent
 */

/**
 * @test id=full
 * @requires vm.gc.G1 & vm.flagless
 * @library /test/lib /
 * @run driver gc.g1.TestPeriodicCollectionWholeHeap full
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestPeriodicCollectionWholeHeap {

    public static void main(String[] args) throws Exception {
        String mode = args[0];

        List<String> commonOpts = Arrays.asList(
            "-XX:+UseG1GC",
            "-XX:+G1PeriodicGCInvokesConcurrent",
            "-Xms128M",
            "-Xmx128M",
            "-Xlog:gc*,gc+periodic",
            Workload.class.getName()
        );

        final String MSG_YOUNG = "Pause Young";
        final String MSG_PERIODIC = "(Concurrent Start) (G1 Periodic Collection)";
        final String MSG_CONCURRENT = "(Concurrent Start) (System.gc())";
        final String MSG_FULL = "Pause Full (System.gc())";

        switch(mode) {

            // Young GC should not prevent periodic GC to start.
            case "young-only": {
                List<String> opts = new ArrayList<>();
                opts.add("-XX:G1PeriodicGCInterval=3000");
                opts.addAll(commonOpts);

                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(opts);
                OutputAnalyzer output = new OutputAnalyzer(pb.start());

                output.shouldContain(MSG_YOUNG);
                output.shouldNotContain(MSG_CONCURRENT);
                output.shouldNotContain(MSG_FULL);
                output.shouldContain(MSG_PERIODIC);

                break;
            }

            // Periodic GC should not start when concurrent GCs are running frequently.
            case "concurrent": {
                List<String> opts = new ArrayList<>();
                opts.add("-XX:G1PeriodicGCInterval=3000");
                opts.add("-XX:+ExplicitGCInvokesConcurrent");
                opts.addAll(commonOpts);
                opts.add("1000");

                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(opts);
                OutputAnalyzer output = new OutputAnalyzer(pb.start());

                output.shouldContain(MSG_YOUNG);
                output.shouldContain(MSG_CONCURRENT);
                output.shouldNotContain(MSG_FULL);
                output.shouldNotContain(MSG_PERIODIC);

                break;
            }

            // Periodic GC should not start when Full GCs are running frequently.
            case "full": {
                List<String> opts = new ArrayList<>();
                opts.add("-XX:G1PeriodicGCInterval=3000");
                opts.add("-XX:-ExplicitGCInvokesConcurrent");
                opts.addAll(commonOpts);
                opts.add("1000");

                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(opts);
                OutputAnalyzer output = new OutputAnalyzer(pb.start());

                output.shouldContain(MSG_YOUNG);
                output.shouldNotContain(MSG_CONCURRENT);
                output.shouldContain(MSG_FULL);
                output.shouldNotContain(MSG_PERIODIC);

                break;
            }

            default:
                throw new IllegalArgumentException("Unknown test mode: " + mode);
        }
    }

    public static class Workload {
        static Object sink;

        public static void main(String... args) {
            final long gcEachMs = (args.length > 0) ? Integer.parseInt(args[0]) : Integer.MAX_VALUE;

            long nextGCAt = System.nanoTime() + gcEachMs * 1_000_000L;
            long stopAt = System.nanoTime() + 10_000_000_000L;

            while (true) {
                long now = System.nanoTime();
                if (now >= stopAt) return;

                sink = new byte[16];
                if (now >= nextGCAt) {
                    System.gc();
                    nextGCAt = now + gcEachMs;
                }
            }
        }
    }

}
