/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=global-limit-fatal
 * @summary Verify -XX:MallocLimit / -XX:MmapLimit with a global limit
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest global-limit-fatal
 */

/*
 * @test id=global-limit-oom
 * @summary Verify -XX:MallocLimit / -XX:MmapLimit with a global limit
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest global-limit-oom
 */

/*
 * @test id=compiler-limit-fatal
 * @summary Verify -XX:MallocLimit with a compiler-specific limit (for "mtCompiler" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest compiler-limit-fatal
 */

/*
 * @test id=compiler-limit-oom
 * @summary Verify -XX:MallocLimit / -XX:MmapLimit with a compiler-specific limit (for "mtCompiler" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest compiler-limit-oom
 */

/*
 * @test id=gc-limit-fatal
 * @summary Verify -XX:MallocLimit / -XX:MmapLimit with a gc-specific limit (for "mtGC" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest gc-limit-fatal
 */

/*
 * @test id=gc-limit-oom
 * @summary Verify -XX:MallocLimit / -XX:MmapLimit with a gc-specific limit (for "mtGC" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest gc-limit-oom
 */

/*
 * @test id=malloc-mmap-limit-mix-malloc-fatal
 * @summary Verify -XX:MallocLimit & -XX:MmapLimit with a gc-specific limit (for "mtGC" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest malloc-mmap-limit-mix-malloc-fatal
 */

/*
 * @test id=malloc-mmap-limit-mix-mmap-fatal
 * @summary Verify -XX:MallocLimit & -XX:MmapLimit with a gc-specific limit (for "mtGC" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest malloc-mmap-limit-mix-mmap-fatal
 */

/*
 * @test id=malloc-mmap-limit-mix-neither-failed
 * @summary Verify -XX:MallocLimit & -XX:MmapLimit with a gc-specific limit (for "mtGC" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest malloc-mmap-limit-mix-neither-failed
 */

/*
 * @test id=multi-limit
 * @summary Verify -XX:MallocLimit / -XX:MmapLimit with multiple limits
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest multi-limit
 */

/*
 * @test id=limit-without-nmt
 * @summary Verify that the VM warns if -XX:MallocLimit / -XX:MmapLimit is given but NMT is disabled
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMemLimitTest limit-without-nmt
 */


import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NMemLimitTest {

    private static ProcessBuilder processBuilderWithSetting(String... extraSettings) {
        List<String> args = new ArrayList<>();
        args.add("-XX:+UnlockDiagnosticVMOptions"); // MallocLimit is diagnostic
        args.add("-Xmx64m");
        args.add("-XX:-CreateCoredumpOnCrash");
        args.add("-Xlog:nmt");
        args.add("-XX:NativeMemoryTracking=summary");
        args.addAll(Arrays.asList(extraSettings));
        args.add("-version");
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
        return pb;
    }

    private enum LimitType {
        Malloc,
        Mmap
    }

    private static void testGlobalLimitFatal(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting(String.format("-XX:%sLimit=1m", type.toString()));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] %sLimit: total limit: 1024K (fatal)", type.toString()));
        output.shouldMatch(String.format("#  fatal error: %sLimit: reached global limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1024K\\)", type.toString()));
    }

    private static void testGlobalLimitOOM(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting(String.format("-XX:%sLimit=1m:oom", type.toString()));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] %sLimit: total limit: 1024K (oom)", type.toString()));
        output.shouldMatch(String.format(".*\\[warning\\]\\[nmt\\] %sLimit: reached global limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1024K\\)", type.toString()));
        // The rest is fuzzy. We may get SIGSEGV or a native OOM message, depending on how the failing allocation was handled.
    }

    private static void testCompilerLimitFatal(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting(String.format("-XX:%sLimit=compiler:1234k", type.toString()), "-Xcomp");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] %sLimit: category \"mtCompiler\" limit: 1234K (fatal)", type.toString()));
        output.shouldMatch(String.format("#  fatal error: %sLimit: reached category \"mtCompiler\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)", type.toString()));
    }

    private static void testCompilerLimitOOM(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting(String.format("-XX:%sLimit=compiler:1234k:oom", type.toString()), "-Xcomp");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] %sLimit: category \"mtCompiler\" limit: 1234K (oom)", type.toString()));
        output.shouldMatch(String.format(".*\\[warning\\]\\[nmt\\] %sLimit: reached category \"mtCompiler\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)", type.toString()));
        // The rest is fuzzy. We may get SIGSEGV or a native OOM message, depending on how the failing allocation was handled.
    }

    private static void testGCLimitFatal(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting(String.format("-XX:%sLimit=gc:1234K", type.toString()));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] %sLimit: category \"mtGC\" limit: 1234K (fatal)", type.toString()));
        output.shouldMatch(String.format("#  fatal error: %sLimit: reached category \"mtGC\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)", type.toString()));
    }

    private static void testGCLimitOOM(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting(String.format("-XX:%sLimit=gc:1234K:oom", type.toString()));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] %sLimit: category \"mtGC\" limit: 1234K (oom)", type.toString()));
        output.shouldMatch(String.format(".*\\[warning\\]\\[nmt\\] %sLimit: reached category \"mtGC\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)", type.toString()));
        // The rest is fuzzy. We may get SIGSEGV or a native OOM message, depending on how the failing allocation was handled.
    }

    private static void testMultiLimit(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting(String.format("-XX:%sLimit=other:2g,compiler:1g:oom,internal:1k", type.toString()));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] %sLimit: category \"mtCompiler\" limit: 1024M (oom)", type.toString()));
        output.shouldContain(String.format("[nmt] %sLimit: category \"mtInternal\" limit: 1024B (fatal)", type.toString()));
        output.shouldContain(String.format("[nmt] %sLimit: category \"mtOther\" limit: 2048M (fatal)", type.toString()));
        output.shouldMatch(String.format("#  fatal error: %sLimit: reached category \"mtInternal\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1024B\\)", type.toString()));
    }

    private static void testMallocMMapLimitMixMMallocFatal() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=gc:1234K", "-XX:MmapLimit=gc:500M");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] MallocLimit: category \"mtGC\" limit: 1234K (fatal)"));
        output.shouldContain(String.format("[nmt] MmapLimit: category \"mtGC\" limit: 500M (fatal)"));
        output.shouldMatch(String.format("#  fatal error: MallocLimit: reached category \"mtGC\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)"));
    }

    private static void testMallocMMapLimitMixMMapFatal() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=gc:500M", "-XX:MmapLimit=gc:1234k");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain(String.format("[nmt] MallocLimit: category \"mtGC\" limit: 500M (fatal)"));
        output.shouldContain(String.format("[nmt] MmapLimit: category \"mtGC\" limit: 1234K (fatal)"));
        output.shouldMatch(String.format("#  fatal error: MmapLimit: reached category \"mtGC\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)"));
    }

    private static void testMallocMMapLimitMixNeitherFailed() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=gc:500M", "-XX:MmapLimit=gc:500M");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(String.format("[nmt] MallocLimit: category \"mtGC\" limit: 500M (fatal)"));
        output.shouldContain(String.format("[nmt] MmapLimit: category \"mtGC\" limit: 500M (fatal)"));
    }

    private static void testLimitWithoutNmt(LimitType type) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:NativeMemoryTracking=off", // overrides "summary" from processBuilderWithSetting()
                String.format("-XX:%sLimit=3g", type.toString()));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0); // Not a fatal error, just a warning
        output.shouldContain(String.format("%sLimit will be ignored since NMT is disabled", type.toString()));
    }

    public static void main(String args[]) throws Exception {

        if (args[0].equals("global-limit-fatal")) {
            testGlobalLimitFatal(LimitType.Malloc);
            testGlobalLimitFatal(LimitType.Mmap);
        } else if (args[0].equals("global-limit-oom")) {
            testGlobalLimitOOM(LimitType.Malloc);
            testGlobalLimitOOM(LimitType.Mmap);
        } else if (args[0].equals("compiler-limit-fatal")) {
            testCompilerLimitFatal(LimitType.Malloc); // compiler only has malloc, but not mmap
        } else if (args[0].equals("compiler-limit-oom")) {
            testCompilerLimitOOM(LimitType.Malloc); // compiler only has malloc, but not mmap
        } else if (args[0].equals("gc-limit-fatal")) {
            testGCLimitOOM(LimitType.Malloc);
            testGCLimitOOM(LimitType.Mmap);
        } else if (args[0].equals("gc-limit-oom")) {
            testMultiLimit(LimitType.Malloc);
            testMultiLimit(LimitType.Mmap);
        } else if (args[0].equals("multi-limit")) {
            testMultiLimit(LimitType.Malloc);
            testMultiLimit(LimitType.Mmap);
        } else if (args[0].equals("malloc-mmap-limit-mix-malloc-fatal")){
            testMallocMMapLimitMixMMallocFatal();
        }  else if (args[0].equals("malloc-mmap-limit-mix-mmap-fatal")){
            testMallocMMapLimitMixMMapFatal();
        }  else if (args[0].equals("malloc-mmap-limit-mix-neither-failed")){
            testMallocMMapLimitMixNeitherFailed();
        } else if (args[0].equals("limit-without-nmt")) {
            testLimitWithoutNmt(LimitType.Malloc);
            testLimitWithoutNmt(LimitType.Mmap);
        } else {
            throw new RuntimeException("invalid test: " + args[0]);
        }
    }
}
