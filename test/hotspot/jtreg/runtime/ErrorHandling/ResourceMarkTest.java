/*
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
 */


/*
 * @test
 * @bug 8284274
 * @summary Test that reporting doesn't crash because missing ResourceMarks
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @requires os.family != "windows"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver ResourceMarkTest
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ResourceMarkTest {


  public static void main(String[] args) throws Exception {
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xmx100M",
        "-XX:-CreateCoredumpOnCrash",
        "-XX:ErrorHandlerTest=15",
        "-XX:TestCrashInErrorHandler=2",
        "-version");

    OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

    // we should have crashed with a SIGFPE
    output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
    output_detail.shouldMatch("#.+SIGFPE.*");
    output_detail.shouldMatch("This is a message with no ResourceMark");

    // extract hs-err file
    File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(output_detail);

    // scan hs-err file: File should NOT contain the "[error occurred during error reporting..]"
    // markers which show that the secondary error handling kicked in and handled the
    // error successfully. As an added test, we check that the last line contains "END.",
    // which is an end marker written in the last step and proves that hs-err file was
    // completely written.
    Pattern [] negativePattern = new Pattern[] {
        Pattern.compile("\\[error occurred during error reporting \\(test missing ResourceMark does not crash\\), id 0xe0000000, Internal Error \\(.*resourceArea.cpp:.*\\)\\]"),
    };

    HsErrFileUtils.checkHsErrFileContent(hs_err_file, null, negativePattern, true /* check end marker */, false /* verbose */);

    System.out.println("OK.");

  }
}
