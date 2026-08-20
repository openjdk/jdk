/*
 * Copyright (c) 2026, Microsoft, Inc. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8378894
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.cds.write.archived.java.heap
 * @summary Test the use of jcmd on a JVM process that's running in AOT training mode.
 * @library /test/lib
 * @build JcmdOnTrainingProcess
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar LingeredApp.jar
 *              jdk/test/lib/apps/LingeredApp
 *              jdk/test/lib/apps/LingeredApp$1
 *              jdk/test/lib/apps/LingeredApp$SteadyStateLock
 *              jdk/test/lib/process/OutputBuffer
 * @run driver JcmdOnTrainingProcess
 */

import java.io.IOException;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class JcmdOnTrainingProcess {
    public static void main(String[] args) throws Exception {
        test_VM_native_memory();
        // Add other test cases here if needed in the future.
    }

    static void test_VM_native_memory() throws Exception {
        // In training run, we should not map any read-only regions.
        OutputAnalyzer out = runJcmdOnTrainingProcess("VM.native_memory");
        out.shouldMatch("Shared class space .* readonly=0KB");
    }

    static OutputAnalyzer runJcmdOnTrainingProcess(String... cmds) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new LingeredApp();
            theApp.setUseDefaultClasspath(false);
            LingeredApp.startApp(theApp,
                                 "-cp", "LingeredApp.jar",
                                 "-XX:AOTMode=record",
                                 "-XX:AOTConfiguration=LingeredApp.aotconfig",
                                 "-XX:NativeMemoryTracking=summary");
            long pid = theApp.getPid();

            JDKToolLauncher jcmd = JDKToolLauncher.createUsingTestJDK("jcmd");
            jcmd.addToolArg(String.valueOf(pid));
            for (String cmd : cmds) {
                jcmd.addToolArg(cmd);
            }

            try {
                OutputAnalyzer output = ProcessTools.executeProcess(jcmd.getCommand());
                output.shouldHaveExitValue(0);
                return output;
            } catch (Exception e) {
                throw new RuntimeException("Test failed: " + e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Test failed: " + e);
        }
        finally {
            LingeredApp.stopApp(theApp);
        }
    }
}
