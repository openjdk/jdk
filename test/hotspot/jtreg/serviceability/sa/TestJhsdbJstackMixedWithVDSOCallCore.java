/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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

import java.nio.file.Path;

import jtreg.SkippedException;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.CoreUtils;

/**
 * @test
 * @bug 8376269
 * @requires (os.family == "linux") & (vm.hasSA)
 * @requires os.arch == "amd64"
 * @library /test/lib
 * @run driver TestJhsdbJstackMixedWithVDSOCallCore
 */
public class TestJhsdbJstackMixedWithVDSOCallCore {

    private static void runJstackMixed(String coreFileName) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addVMArgs(Utils.getTestJavaOpts());
        launcher.addToolArg("jstack");
        launcher.addToolArg("--mixed");
        launcher.addToolArg("--exe");
        launcher.addToolArg(JDKToolFinder.getTestJDKTool("java"));
        launcher.addToolArg("--core");
        launcher.addToolArg(coreFileName);

        ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
        Process jhsdb = pb.start();
        OutputAnalyzer out = new OutputAnalyzer(jhsdb);

        jhsdb.waitFor();

        System.out.println(out.getStdout());
        System.err.println(out.getStderr());

        out.shouldContain("vdso_gettimeofday");
    }

    private static void checkVDSODebugInfo() {
        var kernelVersion = System.getProperty("os.version");
        var vdso = Path.of("/lib", "modules", kernelVersion, "vdso", "vdso64.so");
        if (SATestUtils.getDebugInfo(vdso.toString()) == null) {
            // Skip this test if debuginfo of vDSO not found because internal
            // function of gettimeofday() would not be exported, and vDSO
            // binary might be stripped.
            throw new SkippedException("vDSO debuginfo not found (" + vdso.toString() + ")");
        }
    }

    public static void main(String... args) throws Throwable {
        if (Platform.isMusl()) {
            throw new SkippedException("This test does not work on musl libc.");
        }
        checkVDSODebugInfo();

        var app = new LingeredAppWithVDSOCall();
        app.setForceCrash(true);
        LingeredApp.startApp(app, CoreUtils.getAlwaysPretouchArg(true));
        app.waitAppTerminate();

        String crashOutput = app.getOutput().getStdout();
        String coreFileName = CoreUtils.getCoreFileLocation(crashOutput, app.getPid());
        runJstackMixed(coreFileName);
    }
}
