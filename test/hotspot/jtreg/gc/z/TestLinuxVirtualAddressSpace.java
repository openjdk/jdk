/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package gc.z;

/*
 * @test id=BlockUpper
 * @summary Tests linux virtual address space and ZGC heap reservation interactions
 * @library /test/lib
 * @requires vm.flagless & vm.gc.Z & os.family == "linux"
 * @run driver gc.z.TestLinuxVirtualAddressSpace ScenarioBlockUpper
 */

/*
 * @test id=BlockLower
 * @summary Tests linux virtual address space and ZGC heap reservation interactions
 * @library /test/lib
 * @requires vm.flagless & vm.gc.Z & os.family == "linux"
 * @run driver gc.z.TestLinuxVirtualAddressSpace ScenarioBlockLower
 */

/*
 * @test id=ScenarioBlockPreferred
 * @summary Tests linux virtual address space and ZGC heap reservation interactions
 * @library /test/lib
 * @requires vm.flagless & vm.gc.Z & os.family == "linux"
 * @run driver gc.z.TestLinuxVirtualAddressSpace ScenarioBlockPreferred
 */


import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import jtreg.SkippedException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TestLinuxVirtualAddressSpace {
    private static record Range (long start, long end) {
        List<String> asArgs() {
            if (start >= end) { throw new RuntimeException("Bad Range"); }
            return List.of("" + start, "" + end);
        }
    }

    private static final long K = 1024;
    private static final long M = 1024 * K;
    private static final long G = 1024 * M;
    private static final long T = 1024 * G;

    private static final int SIGKILL = 127;

    private static String toXmxFlag(long xmx) {
        if (xmx % T == 0) { return "-Xmx" + (xmx / T) + "T"; }
        if (xmx % G == 0) { return "-Xmx" + (xmx / G) + "G"; }
        if (xmx % M == 0) { return "-Xmx" + (xmx / M) + "M"; }
        if (xmx % K == 0) { return "-Xmx" + (xmx / K) + "K"; }
        return "-Xmx" + xmx;
    }

    private static abstract class ScenarioBase {
        List<Range> ranges;
        Optional<Long> xmx;

        ScenarioBase(List<Range> ranges) {
            this.ranges = ranges;
            this.xmx = Optional.empty();
        }

        ScenarioBase(List<Range> ranges, long xmx) {
            this.ranges = ranges;
            this.xmx = Optional.of(xmx);
        }

        private String[] args() {
            return Stream.concat(
                xmx.stream().map(TestLinuxVirtualAddressSpace::toXmxFlag),
                ranges.stream().map(Range::asArgs).flatMap(Collection::stream)
            ).toArray(String[]::new);
        }

        public void run() throws Exception {
            var pb = ProcessTools.createNativeTestProcessBuilder("gc_z_TestLinuxVirtualAddressSpace", args());
            var oa = new OutputAnalyzer(pb.start());

            // Check for SkippedException conditions
            if (oa.getExitValue() == SIGKILL) {
                // OS killed the test process
                throw new SkippedException("Received a SIGKILL");
            }

            if (oa.stdoutContains("MAP_FIXED_NOREPLACE unsupported")) {
                // Old linux kernel
                throw new SkippedException("MAP_FIXED_NOREPLACE unsupported");
            }

            if (oa.stdoutContains("ENOMEM restriction encountered")) {
                // Hit some resource limit
                throw new SkippedException("ENOMEM restriction encountered");
            }

            oa.shouldHaveExitValue(0);

            oa.reportDiagnosticSummary();

            analyze(oa);
        }

        void error(String errorMessage) {
            throw new RuntimeException(errorMessage);
        }

        Range parseReservedSpaceSpan(OutputAnalyzer oa) {
            String stdout = oa.getStdout();
            var pattern = Pattern.compile("Reserved Space Span: \\[(0x\\w+) - (0x\\w+)\\)");
            var matcher = pattern.matcher(stdout);
            if (!matcher.find()) {
                error("Reserved Space Span string missing from output");
            }
            return new Range(Long.decode(matcher.group(1)), Long.decode(matcher.group(2)));
        }

        abstract void analyze(OutputAnalyzer oa);
    }

    public static class ScenarioBlockUpper extends ScenarioBase {
        static final long top = 1L << 48;
        static final long bottom = 1L << 40;

        public ScenarioBlockUpper() {
            super(List.of(new Range(bottom, top)));
        }

        void analyze(OutputAnalyzer oa) {
            var range = parseReservedSpaceSpan(oa);
            if (range.end >= bottom) {
                error("Reserved Space Span above reserved range.");
            }
        }
    }

    public static class ScenarioBlockLower extends ScenarioBase {
        static final long top = 1L << 42;
        static final long bottom = 1L << 34;

        public ScenarioBlockLower() {
            super(List.of(new Range(bottom, top)));
        }

        void analyze(OutputAnalyzer oa) {
            var range = parseReservedSpaceSpan(oa);
            if (range.start < top) {
                error("Reserved Space Span below reserved range.");
            }
        }
    }

    public static class ScenarioBlockPreferred extends ScenarioBase {
        static final long xmx = 1L << 40; // 1 TB
        static final long top = 1L << 48;
        static final long bottom = 1L << 42;

        public ScenarioBlockPreferred() {
            super(List.of(new Range(bottom, top)), xmx);
        }

        void analyze(OutputAnalyzer oa) {
            var range = parseReservedSpaceSpan(oa);
            if (range.end >= bottom) {
                error("Reserved Space Span above reserved range.");
            }
            oa.shouldMatch("Reserved Space Type: \\w+/\\w+/Degraded");
        }
    }

    public static void main(String args[]) throws Exception {
        var scenarioClass = Class.forName(TestLinuxVirtualAddressSpace.class.getCanonicalName() + "$" + args[0]);
        var scenario = (ScenarioBase)scenarioClass.getConstructor().newInstance();
        scenario.run();
    }
}

