/*
 * Copyright (c) 2018, 2022 SAP SE. All rights reserved.
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8191101
 * @summary Show Registers on assert/guarantee
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug == true & (os.family == "linux" | os.family == "windows")
 * @author Thomas Stuefe (SAP)
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver ShowRegistersOnAssertTest
 */

// Note: this test can only run on debug since it relies on VMError::controlled_crash() which
// only exists in debug builds.
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

public class ShowRegistersOnAssertTest {

    private static void do_test(boolean do_assert, // true - assert, false - guarantee
        boolean show_registers_on_assert) throws Exception
    {
        System.out.println("Testing " + (do_assert ? "assert" : "guarantee") +
                           " with " + (show_registers_on_assert ? "-XX:+ShowRegistersOnAssert" : "-XX:-ShowRegistersOnAssert") + "...");
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions", "-Xmx100M", "-XX:-CreateCoredumpOnCrash",
            "-XX:ErrorHandlerTest=" + (do_assert ? "1" : "2"),
            (show_registers_on_assert ? "-XX:+ShowRegistersOnAssert" : "-XX:-ShowRegistersOnAssert"),
            "-version");

        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

        // we should have crashed with an internal error. We should definitely NOT have crashed with a segfault
        // (which would be a sign that the assert poison page mechanism does not work).
        output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
        output_detail.shouldMatch("# +Internal Error.*");
        if (show_registers_on_assert) {
            // Extract the hs_err_pid file.
            File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(output_detail);
            Pattern[] pattern = null;
            if (Platform.isX64()) {
                pattern = new Pattern[] { Pattern.compile("Registers:"), Pattern.compile("RAX=.*")};
            } else if (Platform.isX86()) {
                pattern = new Pattern[] { Pattern.compile("Registers:"), Pattern.compile("EAX=.*")};
            } else if (Platform.isAArch64()) {
                if (Platform.isLinux()) {
                    pattern = new Pattern[] { Pattern.compile("Registers:"), Pattern.compile("R0=.*")};
                } else if (Platform.isWindows()) {
                    pattern = new Pattern[] { Pattern.compile("Registers:"), Pattern.compile("X0 =.*")};
                }
            } else if (Platform.isS390x()) {
                pattern = new Pattern[] { Pattern.compile("General Purpose Registers:"),
                                          Pattern.compile("^-{26}$"),
                                          Pattern.compile("  r0  =.*")};
            } else if (Platform.isPPC()) {
                pattern = new Pattern[] { Pattern.compile("Registers:"), Pattern.compile("pc =.*")};
            } else {
                pattern = new Pattern[] { Pattern.compile("Registers:") };
            }
            // Pattern match the hs_err_pid file.
            HsErrFileUtils.checkHsErrFileContent(hs_err_file, pattern, false);
        }
    }

    public static void main(String[] args) throws Exception {
        // Note: for now, this is only a regression test testing that the addition of ShowRegistersOnAssert does
        // not break normal assert/guarantee handling. The feature is not implemented on all platforms and really testing
        // it requires more effort.
        do_test(false, false);
        do_test(false, true);
        do_test(true, false);
        do_test(true, true);
    }

}

