/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 SAP SE. All rights reserved.
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
 * @test segv
 * @summary Test that for a given crash situation we see the correct siginfo in the hs-err file
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @requires os.family != "windows"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestSigInfoInHsErrFile
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class TestSigInfoInHsErrFile {

  public static void main(String[] args) throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xmx100M",
        "-XX:-CreateCoredumpOnCrash",
        "-XX:ErrorHandlerTest=14",
        "-version");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldNotHaveExitValue(0);

    // we should have crashed with a SIGSEGV
    output.shouldMatch("#.+SIGSEGV.*");

    // extract hs-err file
    File f = HsErrFileUtils.openHsErrFileFromOutput(output);

    ArrayList<Pattern> patterns = new ArrayList<>();
    patterns.add(Pattern.compile("# A fatal error has been detected.*"));
    patterns.add(Pattern.compile("# *SIGSEGV.*"));
    patterns.add(Pattern.compile("# *Problematic frame.*"));
    patterns.add(Pattern.compile("# .*VMError::controlled_crash.*"));

    // Crash address: see VMError::_segfault_address
    String crashAddress = "0x0*400";
    if (Platform.isAix()) {
        crashAddress = "0xffffffffffffffff";
    } else if (Platform.isS390x()) {
        // All faults on s390x give the address only on page granularity.
        // Hence fault address is first page address.
        crashAddress = "0x0*1000";
    }
    patterns.add(Pattern.compile("siginfo: si_signo: \\d+ \\(SIGSEGV\\), si_code: \\d+ \\(SEGV_.*\\), si_addr: " + crashAddress + ".*"));

    HsErrFileUtils.checkHsErrFileContent(f, patterns.toArray(new Pattern[] {}), true);

  }

}


