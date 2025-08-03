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


/*
 * @test
 * @summary Test that we see VM configs reported correctly in hs_err file
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestVMConfigInHsErrFile
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class TestVMConfigInHsErrFile {

  public static void main(String[] args) throws Exception {
    testCompactObjectHeaders();
    testCompressedClassPointers();
  }

  private static void testCompactObjectHeaders() throws Exception {
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+UseCompactObjectHeaders",
        "-Xmx100M",
        "-XX:-CreateCoredumpOnCrash",
        "-XX:ErrorHandlerTest=14",
        "-version");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldNotHaveExitValue(0);

    // extract hs-err file
    File f = HsErrFileUtils.openHsErrFileFromOutput(output);

    Pattern[] expectedPatterns = new Pattern[] {
      Pattern.compile("# Java VM: .*compact obj headers.*")
    };
    Pattern[] notExpectedPatterns = new Pattern[] {
      Pattern.compile("# Java VM: .*compressed class ptrs.*")
    };

    HsErrFileUtils.checkHsErrFileContent(f, expectedPatterns, notExpectedPatterns, true, true);

  }

  private static void testCompressedClassPointers() throws Exception {
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:-UseCompactObjectHeaders",
        "-XX:+UseCompressedClassPointers",
        "-Xmx100M",
        "-XX:-CreateCoredumpOnCrash",
        "-XX:ErrorHandlerTest=14",
        "-version");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldNotHaveExitValue(0);

    // extract hs-err file
    File f = HsErrFileUtils.openHsErrFileFromOutput(output);

    Pattern[] expectedPatterns = new Pattern[] {
      Pattern.compile("# Java VM: .*compressed class ptrs.*")
    };
    Pattern[] notExpectedPatterns = new Pattern[] {
      Pattern.compile("# Java VM: .*compact obj headers.*")
    };

    HsErrFileUtils.checkHsErrFileContent(f, expectedPatterns, notExpectedPatterns, true, true);

  }
}
