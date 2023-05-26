/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308751
 * @summary Test ErrorFileWithStderr and ErrorFileWithStdout
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @requires (vm.debug == true)
 * @run driver ErrorFileWithRedirectTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Pattern;

public class ErrorFileWithRedirectTest {

  public static void do_test(boolean errorFileWithStdout, boolean errorFileWithStderr,
                             boolean errorFileToStdout, boolean errorFileToStderr) throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx64M",
            "-XX:-CreateCoredumpOnCrash",
            "-XX:ErrorHandlerTest=14",
            "-XX:" + (errorFileToStdout ? "+" : "-") + "ErrorFileToStdout",
            "-XX:" + (errorFileToStderr ? "+" : "-") + "ErrorFileToStderr",
            "-XX:" + (errorFileWithStdout ? "+" : "-") + "ErrorFileWithStdout",
            "-XX:" + (errorFileWithStderr ? "+" : "-") + "ErrorFileWithStderr",
            "-version");

    OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

    // we should have crashed with a SIGSEGV
    output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
    output_detail.shouldMatch("# +(?:SIGSEGV|SIGBUS|EXCEPTION_ACCESS_VIOLATION).*");

    // Note that last override the prior switch when more than one of the following options are specified:
    // ErrorFileToStdout, ErrorFileToStderr, ErrorFileWithStdout or ErrorFileWithStderr
    String hs_err_file = output_detail.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
    if (hs_err_file == null) {
      throw new RuntimeException("Expected hs-err file but none found.");
    } else {
      System.out.println("Found hs error file mentioned as expected: " + hs_err_file);
    }

    // Check the output. Note that since stderr was specified last it has preference if both are set.
    if (errorFileWithStdout == false && errorFileWithStderr == false) {
      output_detail.stderrShouldNotMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
      output_detail.stderrShouldNotMatch("# +(?:SIGSEGV|SIGBUS|EXCEPTION_ACCESS_VIOLATION).*");
      output_detail.shouldNotContain("---------------  S U M M A R Y ------------");
      output_detail.stdoutShouldContain("# An error report file with more information is saved as");
      output_detail.stderrShouldNotContain("# An error report file");
      System.out.println("Default behaviour - ok! ");
    } else if (errorFileWithStdout == true && errorFileWithStderr == false) {
      output_detail.stderrShouldNotMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
      output_detail.stderrShouldNotMatch("# +(?:SIGSEGV|SIGBUS|EXCEPTION_ACCESS_VIOLATION).*");
      output_detail.stdoutShouldContain("---------------  S U M M A R Y ------------");
      output_detail.stderrShouldNotContain("---------------  S U M M A R Y ------------");
      output_detail.stdoutShouldContain("# An error report file is saved as");
      output_detail.stderrShouldNotContain("# An error report file");
      System.out.println("Found report on stdout - ok! ");
    } else if (errorFileWithStderr == true) {
      output_detail.stdoutShouldNotMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
      output_detail.stdoutShouldNotMatch("# +(?:SIGSEGV|SIGBUS|EXCEPTION_ACCESS_VIOLATION).*");
      output_detail.stderrShouldContain("---------------  S U M M A R Y ------------");
      output_detail.stdoutShouldNotContain("---------------  S U M M A R Y ------------");
      output_detail.stdoutShouldContain("# An error report file is saved as");
      output_detail.stderrShouldNotContain("# An error report file");
      System.out.println("Found report on stderr - ok! ");
    } else {
      throw new RuntimeException("Should not reach here.");
    }

    System.out.println("OK.");

  }

  public static void main(String[] args) throws Exception {
    do_test(false, false, false, false);
    do_test(false, true, false, false);
    do_test(true, false, false, false);

    // ErrorFileWithStdout, ErrorFileWithStderr, ErrorFileToStdout and ErrorFileToStderr options should be exclusive.
    do_test(true, true, false, false);
    do_test(false, true, true, false);
    do_test(true, false, true, false);
    do_test(false, true, false, true);
    do_test(true, false, false, true);
  }

}


