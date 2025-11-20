/*
 * Copyright (c) 2025, Microsoft, Inc. All rights reserved.
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
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.cds.write.archived.java.heap
 * @summary Sanity test for Jcmd AOT.end_recording command
 * @library /test/lib
 * @build JcmdAOTEndRecordingTest
 * @run driver JcmdAOTEndRecordingTest
 */

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.IOException;

public class JcmdAOTEndRecordingTest {
    public static void main(String[] args)  throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new LingeredApp();
            theApp.setUseDefaultClasspath(false);
            LingeredApp.startApp(theApp);
            long pid = theApp.getPid();

            JDKToolLauncher jcmd = JDKToolLauncher.createUsingTestJDK("jcmd");
            jcmd.addToolArg(String.valueOf(pid));
            jcmd.addToolArg("AOT.end_recording");

            try {
                OutputAnalyzer output = ProcessTools.executeProcess(jcmd.getCommand());
                output.shouldContain("AOT.end_recording is unsupported");
            } catch (Exception e) {
                throw new RuntimeException("Test failed: " + e);
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Test failed: " + e);
        }
        finally {
            LingeredApp.stopApp(theApp);
        }
    }
}