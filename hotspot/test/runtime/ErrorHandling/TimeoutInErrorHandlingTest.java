/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8166944
 * @summary Hanging Error Reporting steps may lead to torn error logs
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires (vm.debug == true) & (os.family != "windows")
 * @author Thomas Stuefe (SAP)
 */

public class TimeoutInErrorHandlingTest {


    public static void main(String[] args) throws Exception {

        /* Start the VM and let it crash. Specify TestUnresponsiveErrorHandler which will
         * let five subsequent error reporting steps hang. The Timeout handling triggered
         * by the WatcherThread should kick in and interrupt those steps. In theory, the
         * text "timeout occurred during error reporting in step .." (the little timeouts)
         * should occur in the error log up to four times, followed by the final big timeout
         * "------ Timeout during error reporting after xx s. ------"
         *
         * Note that there are a number of uncertainties which make writing a 100% foolproof
         * test challenging. The time the error reporting thread takes to react to the
         * timeout triggers is unknown. So it is difficult to predict how many little timeouts
         * will be visible before the big timeout kicks in. Also, once the big timeout hits,
         * error reporting thread and Watcherthread will race. The former writes his last
         * message to the error logs and flushes, the latter waits 200ms and then exits the
         * process without further synchronization with the error reporting thread.
         *
         * Because of all this and the desire to write a bullet proof test which does
         * not fail sporadically, we will not test for the final timeout message nor for all
         * of the optimally expected little timeout messages. We just test for two of the
         * little timeout messages to see that repeated timeout handling is basically working.
         */

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xmx100M",
            "-XX:ErrorHandlerTest=14",
            "-XX:+TestUnresponsiveErrorHandler",
            "-XX:ErrorLogTimeout=16", // 16 seconds big timeout = 4 seconds per little timeout
            "-XX:-CreateCoredumpOnCrash",
            "-version");

        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

        // we should have crashed with a SIGSEGV
        output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
        output_detail.shouldMatch("# +(?:SIGSEGV|EXCEPTION_ACCESS_VIOLATION).*");

        // VM should have been aborted by WatcherThread
        output_detail.shouldMatch(".*timer expired, abort.*");

        // extract hs-err file
        String hs_err_file = output_detail.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        if (hs_err_file == null) {
            throw new RuntimeException("Did not find hs-err file in output.\n");
        }

        File f = new File(hs_err_file);
        if (!f.exists()) {
            throw new RuntimeException("hs-err file missing at "
                + f.getAbsolutePath() + ".\n");
        }

        System.out.println("Found hs_err file. Scanning...");

        FileInputStream fis = new FileInputStream(f);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String line = null;



        Pattern [] pattern = new Pattern[] {
            Pattern.compile(".*timeout occurred during error reporting in step.*"),
            Pattern.compile(".*timeout occurred during error reporting in step.*")
        };
        int currentPattern = 0;

        String lastLine = null;
        while ((line = br.readLine()) != null) {
            if (currentPattern < pattern.length) {
              if (pattern[currentPattern].matcher(line).matches()) {
                System.out.println("Found: " + line + ".");
                currentPattern ++;
              }
            }
            lastLine = line;
        }
        br.close();

        if (currentPattern < pattern.length) {
            throw new RuntimeException("hs-err file incomplete (first missing pattern: " +  currentPattern + ")");
        }

        System.out.println("OK.");

    }

}

