/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8213362
 * @comment Test JLI_Launch for tools distributed outside JDK
 * @library /test/lib
 * @run main/native JliLaunchTest
 */

import java.util.Map;
import jdk.test.lib.Utils;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JliLaunchTest {
    public static void main(String[] args) throws IOException {
        Path launcher = Paths.get(System.getProperty("test.nativepath"),
            "JliLaunchTest" + (Platform.isWindows() ? ".exe" : ""));
        System.out.println("Launcher = " + launcher + (Files.exists(launcher) ? " (exists)" : " (not exists)"));
        ProcessBuilder pb = new ProcessBuilder(launcher.toString(), "--version");
        Map<String, String> env = pb.environment();
        // On windows, the DLL should be in JDK/bin, else in JDK/lib.
        String libdir = Paths.get(Utils.TEST_JDK).resolve(Platform.isWindows() ? "bin" : "lib")
            .toAbsolutePath().toString();
        String pathEnvVar = Platform.sharedLibraryPathVariableName();
        env.compute(pathEnvVar, (k, v) -> (v == null) ? libdir : libdir + File.pathSeparator + v);

        OutputAnalyzer outputf = new OutputAnalyzer(pb.start());
        outputf.shouldHaveExitValue(0);
    }
}

