/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8210408
 * @summary Test unnamed module to find resource bundles exported from a named
 *          module.
 * @library /test/lib
 *          ..
 * @build jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Utils
 *        jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.process.ProcessTools
 *        ModuleTestUtil
 * @run main UnNamedTest
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

public class UnNamedTest {
    private static final Path SRC_DIR = Paths.get(Utils.TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get(Utils.TEST_CLASSES, "mods");

    private static final List<String> LOCALE_LIST = List.of("de", "fr", "ja",
            "zh-tw", "en", "de");

    public static void main(String[] args) throws Throwable {
        ModuleTestUtil.prepareModule(SRC_DIR, MODS_DIR, "bundles", ".properties");
        compileCmd();
        runCmd();
    }

    private static void compileCmd() throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("javac");
        launcher.addToolArg("-d")
                .addToolArg(Utils.TEST_CLASSES)
                .addToolArg(Paths.get(Utils.TEST_SRC, "Main.java").toString());

        int exitCode = ProcessTools.executeCommand(launcher.getCommand())
                                   .getExitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Compile of the test failed. "
                    + "Unexpected exit code: " + exitCode);
        }
    }

    private static void runCmd() throws Throwable {
        // access resource bundles that are exported private unconditionally.
        List<String> args = List.of(
                "-ea", "-esa",
                "-cp", Utils.TEST_CLASSES,
                "--module-path", MODS_DIR.toString(),
                "--add-modules", "bundles",
                "Main");
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                Stream.concat(args.stream(), LOCALE_LIST.stream()).toList());
        // Evaluate process status
        int exitCode = ProcessTools.executeCommand(pb).getExitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Execution of the test1 failed. "
                    + "Unexpected exit code: " + exitCode);
        }

        // --add-exports can't open resources
        List<String> argsWithOpens = List.of(
                "-ea", "-esa",
                "-cp", Utils.TEST_CLASSES,
                "--module-path", MODS_DIR.toString(),
                "--add-modules", "bundles",
                "--add-opens", "bundles/jdk.test.internal.resources=ALL-UNNAMED",
                "Main");
        pb = ProcessTools.createTestJavaProcessBuilder(
                Stream.concat(argsWithOpens.stream(), LOCALE_LIST.stream()).toList());

        // Evaluate process status
        exitCode = ProcessTools.executeCommand(pb).getExitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Execution of the test2 failed. "
                    + "Unexpected exit code: " + exitCode);
        }
    }
}