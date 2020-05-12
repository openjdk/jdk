/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8225193
 * @requires os.family != "windows"
 * @library /test/lib
 * @run main RemovingUnixDomainSocketTest
 */

import java.io.IOException;
import java.nio.file.Path;

import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.OutputAnalyzer;

public class RemovingUnixDomainSocketTest {

    private static void runJCmd(long pid) throws InterruptedException, IOException {
        JDKToolLauncher jcmd = JDKToolLauncher.createUsingTestJDK("jcmd");
        jcmd.addVMArgs(Utils.getTestJavaOpts());
        jcmd.addToolArg(Long.toString(pid));
        jcmd.addToolArg("VM.version");

        ProcessBuilder pb = new ProcessBuilder(jcmd.getCommand());
        Process jcmdProc = pb.start();

        OutputAnalyzer out = new OutputAnalyzer(jcmdProc);

        jcmdProc.waitFor();

        System.out.println(out.getStdout());
        System.err.println(out.getStderr());

        out.stderrShouldBeEmptyIgnoreVMWarnings();
    }

    public static void main(String... args) throws Exception {
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp();

            // Access to Attach Listener
            runJCmd(app.getPid());

            // Remove unix domain socket file
            var sockFile = Path.of(System.getProperty("java.io.tmpdir"),
                                   ".java_pid" + app.getPid())
                               .toFile();
            System.out.println("Remove " + sockFile.toString());
            sockFile.delete();

            // Access to Attach Listener again
            runJCmd(app.getPid());
        } finally {
            LingeredApp.stopApp(app);
        }
    }

}
