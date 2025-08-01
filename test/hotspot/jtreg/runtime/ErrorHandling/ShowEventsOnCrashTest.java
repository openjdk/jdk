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


/*
 * @test
 * @bug 8355627
 * @summary Test that events are listed in the hs_err_pid file
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug == true & (os.family == "linux" | os.family == "windows")
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver ShowEventsOnCrashTest
 */

// Note: this test can only run on debug since it relies on VMError::controlled_crash() which
// only exists in debug builds.

import jdk.test.whitebox.WhiteBox;
import java.io.File;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ShowEventsOnCrashTest {

    public static void main(String[] args) throws Exception {

        if (args.length > 0 && args[0].equals("test")) {
            Thread.sleep(2000); // Wait to accumulate log entries
            WhiteBox.getWhiteBox().controlledCrash(2);
            throw new RuntimeException("Still alive?");
        }

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xbootclasspath/a:.",
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
            "-Xmx100M", "-XX:-CreateCoredumpOnCrash",
            ShowEventsOnCrashTest.class.getName(), "test");

        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

        // we should have crashed with an internal error. We should definitely NOT have crashed with a segfault
        // (which would be a sign that the assert poison page mechanism does not work).
        output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
        output_detail.shouldMatch("# +Internal Error.*");
        File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(output_detail);
        // Pattern match the hs_err_pid file.
        Pattern[] patterns = new Pattern[] {
            Pattern.compile("Compilation events \\([0-9]* events\\):"),
            Pattern.compile("GC Heap Usage History \\([0-9]* events\\):"),
            Pattern.compile("Metaspace Usage History \\([0-9]* events\\):"),
            Pattern.compile("Dll operation events \\([0-9]* events\\):"),
            Pattern.compile("Deoptimization events \\([0-9]* events\\):"),
            Pattern.compile("Classes loaded \\([0-9]* events\\):"),
            Pattern.compile("Classes unloaded \\([0-9]* events\\):"),
            Pattern.compile("Classes redefined \\([0-9]* events\\):"),
            Pattern.compile("Internal exceptions \\([0-9]* events\\):"),
            Pattern.compile("VM Operations \\([0-9]* events\\):"),
            Pattern.compile("Memory protections \\([0-9]* events\\):")
        };

        HsErrFileUtils.checkHsErrFileContent(hs_err_file, patterns, false);

    }
}

