/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8384557
 * @summary Test to make sure jps works correctly when -XX:AltTempDir is set.
 * @library /test/lib
 * @requires os.family == "linux"
 * @modules jdk.jartool/sun.tools.jar
 * @build jdk.test.lib.apps.LingeredApp
 * @run main/othervm TestJpsTempDir
 */

// Test that jps finds hsperfdata file in -XX:AltTempDir.

import jdk.test.lib.apps.LingeredApp;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.FileUtils;

public class TestJpsTempDir {

    public static void main(java.lang.String[] unused) throws Exception {
        Path clientTmpDir = Files.createTempDirectory(Path.of("/tmp"), "c");
        String tmpdirString = "-XX:AltTempDir=" + clientTmpDir.toString();

        LingeredAppForJps app = new LingeredAppForJps();

        try {
            // Start LingeredApp with AltTempDir
            List<String> vmArgs = new ArrayList<>(List.of(JpsHelper.getVmArgs()));
            vmArgs.add(tmpdirString);
            LingeredApp.startApp(app, vmArgs.toArray(String[]::new));

            // Pass to jps (adds -J)
            List<String> jpsArgs = new ArrayList<>();
            jpsArgs.add(tmpdirString);

            OutputAnalyzer output = JpsHelper.jps(jpsArgs, null);
            output.shouldContain(app.getProcessName());
            output.shouldContain(Long.toString(app.getPid()));
            output.shouldHaveExitValue(0);
        } finally {
            LingeredApp.stopApp(app);
            FileUtils.deleteFileTreeWithRetry(clientTmpDir);
        }
    }
}
