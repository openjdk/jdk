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
 * @test id=coh-off
 * @summary Test that we see VM configs reported correctly in hs_err file
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestVMConfigInHsErrFile false
 */

/*
 * @test id=coh-on
 * @summary Test that we see VM configs reported correctly in hs_err file
 * @library /test/lib
 * @requires vm.bits == "64"
 * @requires vm.flagless
 * @requires vm.debug
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestVMConfigInHsErrFile true
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.io.File;
import java.util.regex.Pattern;

public class TestVMConfigInHsErrFile {

  public static void main(String[] args) throws Exception {
    test(Boolean.parseBoolean(args[0]));
  }

  private static void test(boolean coh) throws Exception {
    final String argument = "-XX:%cUseCompactObjectHeaders".formatted(coh ? '+' : '-');
    final Pattern[] pattern = new Pattern[] { Pattern.compile("# Java VM: .*compact obj headers.*") };

    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+UnlockExperimentalVMOptions",
        argument,
        "-Xmx100M",
        "-XX:-CreateCoredumpOnCrash",
        "-XX:ErrorHandlerTest=14",
        "-version");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldNotHaveExitValue(0);

    // extract hs-err file
    File f = HsErrFileUtils.openHsErrFileFromOutput(output);
    if (coh) {
      HsErrFileUtils.checkHsErrFileContent(f, pattern, null, true, true);
    } else {
      HsErrFileUtils.checkHsErrFileContent(f, null, pattern, true, true);
    }
  }
}
