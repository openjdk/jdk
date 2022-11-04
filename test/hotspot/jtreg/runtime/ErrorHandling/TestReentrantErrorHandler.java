/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @summary Test Reentrant Error Handling Steps when generating hs_err files
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires (vm.debug == true) & (os.family != "windows")
 * @run driver TestReentrantErrorHandler
 */

public class TestReentrantErrorHandler {

    public static final boolean verbose = System.getProperty("verbose") != null;
    // 16 seconds for hs_err generation timeout = 4 seconds per step timeout
    public static final int ERROR_LOG_TIMEOUT = 16;

    public static void main(String[] args) throws Exception {
        /* TODO: Write something
         */

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xmx100M",
            "-XX:ErrorHandlerTest=14",
            "-XX:+TestReentrantErrorHandler",
            "-XX:ErrorLogTimeout=" + ERROR_LOG_TIMEOUT,
            "-XX:-CreateCoredumpOnCrash",
            "-version");

        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

        if (verbose) {
            System.err.println("<begin cmd output>");
            System.err.println(output_detail.getOutput());
            System.err.println("<end cmd output>");
        }

        // we should have crashed with a SIGSEGV
        output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
        output_detail.shouldMatch("# +(?:SIGSEGV|SIGBUS|EXCEPTION_ACCESS_VIOLATION).*");

        // extract hs-err file
        String hs_err_file = output_detail.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        if (hs_err_file == null) {
            if (!verbose) {
                System.err.println("<begin cmd output>");
                System.err.println(output_detail.getOutput());
                System.err.println("<end cmd output>");
            }
            throw new RuntimeException("Did not find hs-err file in output.\n");
        }

        File f = new File(hs_err_file);
        if (!f.exists()) {
            if (!verbose) {
                System.err.println("<begin cmd output>");
                System.err.println(output_detail.getOutput());
                System.err.println("<end cmd output>");
            }
            throw new RuntimeException("hs-err file missing at "
                + f.getAbsolutePath() + ".\n");
        }

        System.out.println("Found hs_err file. Scanning...");

        FileInputStream fis = new FileInputStream(f);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String line = null;


        Pattern [] pattern = new Pattern[] {
            Pattern.compile(".*TestReentrantErrorHandler Step: Start.*"),
            Pattern.compile(".*error occurred during error reporting \\(TestReentrantErrorHandler Step\\).*"),
            Pattern.compile(".*error occurred during error reporting \\(TestReentrantErrorHandler Step, iteration step #0\\).*"),
            Pattern.compile(".*timeout occurred during error reporting in step \"TestReentrantErrorHandler Step, iteration step #1\".*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: 2.*"),
            Pattern.compile(".*timeout occurred during error reporting in step \"TestReentrantErrorHandler Step, iteration step #3\".*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: Finished.*"),
            Pattern.compile(".*error occurred during error reporting \\(TestReentrantErrorHandler Step\\).*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: After still works.*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: After 0.*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: After 1.*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: After 2.*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: After 3.*"),
            Pattern.compile(".*TestReentrantErrorHandler Step: After End.*"),
            Pattern.compile(".*error occurred during error reporting \\(TestReentrantErrorHandler Step: Condition\\).*")
        };
        Pattern badPattern = Pattern.compile(".*TestReentrantErrorHandler: BAD LINE.*");
        int currentPattern = 0;

        boolean badPatternFound = false;
        String lastLine = null;
        StringBuilder saved_hs_err = new StringBuilder();
        while ((line = br.readLine()) != null) {
            saved_hs_err.append(line + System.lineSeparator());
            if (currentPattern < pattern.length) {
                if (pattern[currentPattern].matcher(line).matches()) {
                    System.out.println("Found: " + line + ".");
                    currentPattern ++;
                }
            }
            if (badPattern.matcher(line).matches()) {
                badPatternFound = true;
            }
            lastLine = line;
        }
        br.close();

        if (verbose) {
            System.err.println("<begin hs_err contents>");
            System.err.print(saved_hs_err);
            System.err.println("<end hs_err contents>");
        }

        if (badPatternFound) {
            if (!verbose) {
                System.err.println("<begin hs_err contents>");
                System.err.print(saved_hs_err);
                System.err.println("<end hs_err contents>");
            }
            throw new RuntimeException("hs-err file contained a bad print statement");
        }

        if (currentPattern < pattern.length) {
            if (!verbose) {
                System.err.println("<begin hs_err contents>");
                System.err.print(saved_hs_err);
                System.err.println("<end hs_err contents>");
            }
            throw new RuntimeException("hs-err file incomplete (first missing pattern: \"" +  pattern[currentPattern].pattern() + "\")");
        }

        if (!lastLine.equals("END.")) {
          throw new RuntimeException("hs-err file incomplete (missing END marker.)");
        } else {
          System.out.println("End marker found.");
        }

        System.out.println("OK.");

    }

}
