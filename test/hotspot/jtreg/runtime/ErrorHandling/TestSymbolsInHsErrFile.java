/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @test symbolsHsErr
 * @summary Test that function names are present in native frames of hs-err file as a proof that symbols are available.
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @requires os.family == "windows"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestSymbolsInHsErrFile
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestSymbolsInHsErrFile {

  public static void main(String[] args) throws Exception {

    // Start a jvm and cause a SIGSEGV / ACCESS_VIOLATION
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xmx100M",
        "-XX:-CreateCoredumpOnCrash",
        "-XX:ErrorHandlerTest=14",
        "-version");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldNotHaveExitValue(0);

    // Verify that the hs_err problematic frame contains a function name that points to origin of the crash;
    // on Windows/MSVC, if symbols are present and loaded, we should see a ref to  either 'crash_with_segfault'
    // 'VMError::controlled_crash' depending on whether the compile optimizations (i.e. crash_with_segfault
    // was inlined or not):
    // # Problematic frame:
    // # V  [jvm.dll+0x.....]  crash_with_segfault+0x10
    // or
    // # V  [jvm.dll+0x.....]  VMError::controlled_crash+0x99
    //
    // If symbols could not be loaded, however, then the frame will contain not function name at all, i.e.
    // # Problematic frame:
    // # V  [jvm.dll+0x.....]
    // NB: this is not true for other OS/Compilers, where the functions names are present even with no symbols,
    // hence this test being restricted to Windows only.
    output.shouldMatch(("# V  \\[jvm.dll.*\\].*(crash_with_segfault|controlled_crash).*"));

  }

}


