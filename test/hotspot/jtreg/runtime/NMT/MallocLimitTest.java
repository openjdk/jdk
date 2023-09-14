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
 * @summary Verify -XX:MallocLimit with a global limit
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest global-limit-fatal
 */

/*
 * @test id=global-limit-oom
 * @summary Verify -XX:MallocLimit with a global limit
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest global-limit-oom
 */

/*
 * @test id=compiler-limit-fatal
 * @summary Verify -XX:MallocLimit with a compiler-specific limit (for "mtCompiler" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest compiler-limit-fatal
 */

/*
 * @test id=compiler-limit-oom
 * @summary Verify -XX:MallocLimit with a compiler-specific limit (for "mtCompiler" category)
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest compiler-limit-oom
 */

/*
 * @test id=multi-limit
 * @summary Verify -XX:MallocLimit with multiple limits
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest multi-limit
 */

/*
 * @test id=limit-without-nmt
 * @summary Verify that the VM warns if -XX:MallocLimit is given but NMT is disabled
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest limit-without-nmt
 */


import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MallocLimitTest {

    private static ProcessBuilder processBuilderWithSetting(String... extraSettings) {
        List<String> args = new ArrayList<>();
        args.add("-XX:+UnlockDiagnosticVMOptions"); // MallocLimit is diagnostic
        args.add("-Xmx64m");
        args.add("-XX:-CreateCoredumpOnCrash");
        args.add("-Xlog:nmt");
        args.add("-XX:NativeMemoryTracking=summary");
        args.addAll(Arrays.asList(extraSettings));
        args.add("-version");
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
        return pb;
    }

    private static void testGlobalLimitFatal() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=1m");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: total limit: 1024K (fatal)");
        output.shouldMatch("#  fatal error: MallocLimit: reached global limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1024K\\)");
    }

    private static void testGlobalLimitOOM() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=1m:oom");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: total limit: 1024K (oom)");
        output.shouldMatch(".*\\[warning\\]\\[nmt\\] MallocLimit: reached global limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1024K\\)");
        // The rest is fuzzy. We may get SIGSEGV or a native OOM message, depending on how the failing allocation was handled.
    }

    private static void testCompilerLimitFatal() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=compiler:1234k", "-Xcomp");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: category \"mtCompiler\" limit: 1234K (fatal)");
        output.shouldMatch("#  fatal error: MallocLimit: reached category \"mtCompiler\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)");
    }

    private static void testCompilerLimitOOM() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=compiler:1234k:oom", "-Xcomp");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: category \"mtCompiler\" limit: 1234K (oom)");
        output.shouldMatch(".*\\[warning\\]\\[nmt\\] MallocLimit: reached category \"mtCompiler\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1234K\\)");
        // The rest is fuzzy. We may get SIGSEGV or a native OOM message, depending on how the failing allocation was handled.
    }

    private static void testMultiLimit() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=other:2g,compiler:1g:oom,internal:1k");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: category \"mtCompiler\" limit: 1024M (oom)");
        output.shouldContain("[nmt] MallocLimit: category \"mtInternal\" limit: 1024B (fatal)");
        output.shouldContain("[nmt] MallocLimit: category \"mtOther\" limit: 2048M (fatal)");
        output.shouldMatch("#  fatal error: MallocLimit: reached category \"mtInternal\" limit \\(triggering allocation size: \\d+[BKM], allocated so far: \\d+[BKM], limit: 1024B\\)");
    }

    private static void testLimitWithoutNmt() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:NativeMemoryTracking=off", // overrides "summary" from processBuilderWithSetting()
                "-XX:MallocLimit=3g");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0); // Not a fatal error, just a warning
        output.shouldContain("MallocLimit will be ignored since NMT is disabled");
    }

    public static void main(String args[]) throws Exception {

        if (args[0].equals("global-limit-fatal")) {
            testGlobalLimitFatal();
        } else if (args[0].equals("global-limit-oom")) {
            testGlobalLimitOOM();
        } else if (args[0].equals("compiler-limit-fatal")) {
            testCompilerLimitFatal();
        } else if (args[0].equals("compiler-limit-oom")) {
            testCompilerLimitOOM();
        } else if (args[0].equals("multi-limit")) {
            testMultiLimit();
        } else if (args[0].equals("limit-without-nmt")) {
            testLimitWithoutNmt();
        } else {
            throw new RuntimeException("invalid test: " + args[0]);
        }
    }
}
