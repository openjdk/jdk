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
 */

import jdk.test.lib.compiler.CompilerUtils;
import tests.JImageGenerator;
import tests.Result;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/*
 * @test
 * @summary Make sure that 100 modules can be linked using jlink and dedcplication works correctlyk.
 * @bug 8311591
 * @library /test/lib
 *          ../lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.* JLinkDedupTest100Modules jdk.test.lib.compiler.CompilerUtils
 * @run main/othervm -Xmx1g -Xlog:init=debug -XX:+UnlockDiagnosticVMOptions -XX:+BytecodeVerificationLocal JLinkDedupTest100Modules
 */
public class JLinkDedupTest100Modules {

    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "dedup", "src");
    private static final Path MODS_DIR = Paths.get("mods");

    private static final String MODULE_PATH =
            Paths.get(JAVA_HOME, "jmods").toString() +
                    File.pathSeparator + MODS_DIR.toString();

    // the names of the modules in this test
    private static String[] modules = new String[] {"m1", "m2", "m3", "m4"};

    private static boolean hasJmods() {
        if (!Files.exists(Paths.get(JAVA_HOME, "jmods"))) {
            System.err.println("Test skipped. NO jmods directory");
            return false;
        }
        return true;
    }
    /*
     * Compiles all modules used by the test
     */
    public static void compileAll() throws Throwable {
        if (!hasJmods()) return;

        for (String mn : modules) {
            Path msrc = SRC_DIR.resolve(mn);
            CompilerUtils.compile(msrc, MODS_DIR,
                    "--module-source-path", SRC_DIR.toString());
        }
    }


    static void report(String command, String[] args) {
        System.out.println(command + " " + String.join(" ", Arrays.asList(args)));
    }


    public static void main(String[] args) throws Throwable {

        compileAll();
        Path src = Paths.get("bug8311591");

        JImageGenerator.getJLinkTask()
                .modulePath(MODULE_PATH)
                .output(src.resolve("out-jlink-dedup"))
                .addMods("m1")
                .addMods("m2")
                .addMods("m2")
                .addMods("m3")
                .addMods("m4")
                .call()
                .assertSuccess();

        Path binDir = src.resolve("out-jlink-dedup").resolve("bin").toAbsolutePath();
        Path bin = binDir.resolve("java");

        ProcessBuilder processBuilder = new ProcessBuilder(bin.toString(),
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+BytecodeVerificationLocal",
                "-m", "m4/p4.Main");
        processBuilder.inheritIO();
        processBuilder.directory(binDir.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0)
             throw new AssertionError("JLinkDedupTest100Modules failed to launch");

        extractJImage(src);

    }
    static void extractJImage(Path src) {
        Path binDir = src.resolve("out-jlink-dedup").toAbsolutePath();
        Path outputDir = src.resolve("dir");
        Path jimageDir = binDir.resolve("lib","modules");

        Result result = JImageGenerator.getJImageTask()
                .dir(outputDir)
                .image(jimageDir)
                .extract();

        System.out.println("REsult dir " +result.getFile());
        result.assertSuccess();

    }
}
