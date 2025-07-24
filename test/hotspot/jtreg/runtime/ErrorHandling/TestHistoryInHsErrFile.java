/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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
 * @summary Test the short history feature in the hs-err file
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestHistoryInHsErrFile
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.util.regex.Pattern;

public class TestHistoryInHsErrFile {

  public static void main(String[] args) throws Exception {

    if (args.length > 0 && args[0].equals("sleep")) {
      Thread.sleep(20000);
      throw new RuntimeException("not killed?");
    }
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xmx100M",
        "-XX:+UseHistory",
        "-XX:HistoryInterval=1000",
        "-XX:ErrorHandlerTest=14",
        "-XX:ErrorHandlerTestDelay=6000",
        TestHistoryInHsErrFile.class.getName(), "sleep");
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldNotHaveExitValue(0);

    output.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
    output.shouldMatch("# +SIGSEGV.*");
    File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(output);

    Pattern[] patterns = new Pattern[] {
            Pattern.compile("History:"),
            Pattern.compile("sss"),
    };
    HsErrFileUtils.checkHsErrFileContent(hs_err_file, patterns, false);
  }
}
