/*
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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

/*
 * @test
 * @summary Test that we get ASAN-reports and hs-err files on ASAN error
 * @library /test/lib
 * @requires vm.asan
 * @requires vm.flagless
 * @requires vm.debug == true & os.family == "linux"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver AsanReportTest
 */

// Note: this test can only run on debug since it relies on VMError::controlled_crash() which
// only exists in debug builds.
import java.io.File;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AsanReportTest {

    private static void do_test() throws Exception {

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xmx64M", "-XX:CompressedClassSpaceSize=64M",
                // Default ASAN options should prevent core file generation, which should overrule +CreateCoredumpOnCrash.
                // We test below.
                "-XX:+CreateCoredumpOnCrash",
                "-Xlog:asan",
                // Switch off NMT since it can alter the error ASAN sees; we want the pure double free error
                "-XX:NativeMemoryTracking=off",
                // Causes double-free in controlled_crash
                "-XX:ErrorHandlerTest=18",
                "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldNotHaveExitValue(0);

        // ASAN error should appear on stderr
        output.shouldContain("CreateCoredumpOnCrash overruled");
        output.shouldContain("JVM caught ASAN Error");
        output.shouldMatch("AddressSanitizer.*double-free");
        output.shouldMatch("# +A fatal error has been detected by the Java Runtime Environment");
        output.shouldMatch("# +fatal error: ASAN");
        output.shouldNotContain("Aborted (core dumped)");

        File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(output);
        Pattern[] pat = new Pattern[] {
                Pattern.compile(".*A S A N.*"),
                Pattern.compile(".*AddressSanitizer.*double-free.*"),
                Pattern.compile(".*(crash_with_segfault|controlled_crash).*")
        };
        HsErrFileUtils.checkHsErrFileContent(hs_err_file, pat, false);
    }

    public static void main(String[] args) throws Exception {
        do_test();
    }

}

