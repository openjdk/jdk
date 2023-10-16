/*
 * Copyright (c) 2015, 2022 SAP SE. All rights reserved.
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8074552
 * @summary SafeFetch32 and SafeFetchN do not work in error handling
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @requires vm.flavor != "zero"
 * @author Thomas Stuefe (SAP)
 * @run driver SafeFetchInErrorHandlingTest
 */

public class SafeFetchInErrorHandlingTest {

  public static void main(String[] args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xmx100M",
        "-XX:ErrorHandlerTest=14",
        "-XX:+TestSafeFetchInErrorHandler",
        "-XX:-CreateCoredumpOnCrash",
        "-version");

    OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

    // we should have crashed with a SIGSEGV
    output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
    output_detail.shouldMatch("# +(?:SIGSEGV|SIGBUS|EXCEPTION_ACCESS_VIOLATION).*");

    // extract hs-err file
    File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(output_detail);

    // File should contain "Will test SafeFetch, SafeFetch OK". The latter indicates we survived the SafeFetch without
    // a secondary crash.
    Pattern [] pattern = new Pattern[] {
        Pattern.compile("Will test SafeFetch..."),
        Pattern.compile("SafeFetch OK."),
    };

    HsErrFileUtils.checkHsErrFileContent(hs_err_file, pattern, true);

    System.out.println("OK.");

  }

}

