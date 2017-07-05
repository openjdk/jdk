/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;

/*
 * @test
 * @library /test/lib/share/classes
 * @library /testlibrary
 * @build jdk.test.lib.*
 * @build jdk.test.lib.apps.*
 * @run main TestStackTrace
 */
public class TestStackTrace {

    public static void main(String[] args) throws Exception {
        if (!Platform.shouldSAAttach()) {
            System.out.println("SA attach not expected to work - test skipped.");
            return;
        }

        LingeredApp app = null;
        try {
            List<String> vmArgs = new ArrayList<String>();
            vmArgs.add("-XX:+UsePerfData");
            vmArgs.addAll(Utils.getVmOptions());
            app = LingeredApp.startApp(vmArgs);

            System.out.println("Attaching sun.jvm.hotspot.tools.StackTrace to " + app.getPid());
            ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                    "-XX:+UsePerfData",
                    "sun.jvm.hotspot.tools.StackTrace",
                    Long.toString(app.getPid()));
            OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
            System.out.println(output.getOutput());

            output.shouldHaveExitValue(0);
            output.shouldContain("Debugger attached successfully.");
            output.stderrShouldNotMatch("[E|e]xception");
            output.stderrShouldNotMatch("[E|e]rror");
        } finally {
            LingeredApp.stopApp(app);
        }
     }

}
