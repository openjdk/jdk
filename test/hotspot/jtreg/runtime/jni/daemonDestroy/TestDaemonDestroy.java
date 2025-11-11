/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8290732
 * @comment Test uses custom launcher that attempts to destroy the VM on both
 *          a daemon and non-daemon thread. The result should be the same in
 *          both cases.
 * @requires vm.flagless
 * @requires !jdk.static
 * @library /test/lib
 * @build Main
 * @run main/native TestDaemonDestroy
 * @run main/native TestDaemonDestroy daemon
 */

// Logic copied from SigTestDriver

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TestDaemonDestroy {

    public static void main(String[] args) throws IOException {
        Path launcher = Paths.get(Utils.TEST_NATIVE_PATH)
            .resolve("daemonDestroy" + (Platform.isWindows() ? ".exe" : ""))
            .toAbsolutePath();

        System.out.println("Launcher = " + launcher +
                           (Files.exists(launcher) ? " (exists)" : " (missing)"));

        List<String> cmd = new ArrayList<>();
        cmd.add(launcher.toString());
        cmd.add("-Djava.class.path=" + Utils.TEST_CLASS_PATH);
        if (args.length > 0) {
            cmd.add("daemon");
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);

        // Need to add libjvm location to LD_LIBRARY_PATH
        String envVar = Platform.sharedLibraryPathVariableName();
        pb.environment().merge(envVar, Platform.jvmLibDir().toString(),
                               (x, y) -> y + File.pathSeparator + x);

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);
        oa.shouldNotContain("Error: T1 isAlive");
        oa.shouldContain("T1 finished");
        oa.reportDiagnosticSummary();
    }
}
