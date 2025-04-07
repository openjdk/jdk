/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8353485
 * @summary Test jcmd "streaming_output" option
 * @library /test/lib
 * @run driver TestJcmdStreamingOutput
 */

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

public class TestJcmdStreamingOutput {

    private static final String CMD = "VM.version";
    private static final String OPT = "--streaming_output";
    private static final String OPT_TRUE = OPT + "=true";
    private static final String OPT_FALSE = OPT + "=false";
    private static final String STREAMING_ON = "executing command jcmd, streaming output: 1";
    private static final String STREAMING_OFF = "executing command jcmd, streaming output: 0";

    public static void main(String[] args) throws Exception {
        // The option can be at any position after process id.
        test(STREAMING_ON, (targetPid) -> {
            jcmd(targetPid, OPT_TRUE, CMD)
                .shouldHaveExitValue(0);
        });
        test(STREAMING_ON, (targetPid) -> {
            jcmd(targetPid, CMD, OPT_TRUE)
                .shouldHaveExitValue(0);
        });

        test(STREAMING_OFF, (targetPid) -> {
            jcmd(targetPid, OPT_FALSE, CMD)
                .shouldHaveExitValue(0);
        });
        test(STREAMING_OFF, (targetPid) -> {
            jcmd(targetPid, CMD, OPT_FALSE)
                .shouldHaveExitValue(0);
        });

        test(null, (targetPid) -> {
            jcmd(targetPid, CMD, OPT + "=something_wrong")
                .shouldNotHaveExitValue(0)
                .shouldContain("Unexpected option value");
        });
    }

    private static OutputAnalyzer jcmd(String... args) throws Exception {
        return JcmdBase.jcmdNoPid(null, args);
    }

    private interface JcmdAction {
        void run(String targetPid) throws Exception;
    }

    private static void test(String expectedLog, JcmdAction action) throws Exception {
        System.out.println(">> Test");
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp("-Xlog:attach=trace");
            action.run(String.valueOf(app.getPid()));
        } finally {
            LingeredApp.stopApp(app);
        }

        if (expectedLog != null) {
            new OutputAnalyzer(app.getProcessStdout(), "")
                .stdoutShouldMatch(expectedLog);
        }
        System.out.println("<< Test");
        System.out.println();
    }

}
