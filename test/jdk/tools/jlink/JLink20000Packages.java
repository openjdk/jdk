/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.spi.ToolProvider;

import tests.JImageGenerator;

/*
 * @test
 * @summary Make sure that ~20000 packages in a uber jar can be linked using jlink. Now that
 *          pagination is in place, the limitation is on the constant pool size, not number
 *          of packages.
 * @bug 8321413
 * @library ../lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main/othervm -Xmx1g -Xlog:init=debug -XX:+UnlockDiagnosticVMOptions -XX:+BytecodeVerificationLocal JLink20000Packages
 */
public class JLink20000Packages {
    private static final ToolProvider JAVAC_TOOL = ToolProvider.findFirst("javac")
            .orElseThrow(() -> new RuntimeException("javac tool not found"));

    static void report(String command, String[] args) {
        System.out.println(command + " " + String.join(" ", Arrays.asList(args)));
    }

    static void javac(String[] args) {
        report("javac", args);
        JAVAC_TOOL.run(System.out, System.err, args);
    }

    public static void main(String[] args) throws Exception {
        Path src = Paths.get("bug8321413");
        Path imageDir = src.resolve("out-jlink");
        Path mainModulePath = src.resolve("bug8321413x");

        StringJoiner mainModuleInfoContent = new StringJoiner(";\n  exports ", "module bug8321413x {\n  exports ", ";\n}");

        for (int i = 0; i < 20000; i++) {
            String packageName = "p" + i;
            String className = "C" + i;

            Path packagePath = Files.createDirectories(mainModulePath.resolve(packageName));

            StringBuilder classContent = new StringBuilder("package ");
            classContent.append(packageName).append(";\n");
            classContent.append("class ").append(className).append(" {}\n");
            Files.writeString(packagePath.resolve(className + ".java"), classContent.toString());

            mainModuleInfoContent.add(packageName);
        }

        // create module reading the generated modules
        Path mainModuleInfo = mainModulePath.resolve("module-info.java");
        Files.writeString(mainModuleInfo, mainModuleInfoContent.toString());

        Path mainClassDir = mainModulePath.resolve("testpackage");
        Files.createDirectories(mainClassDir);

        Files.writeString(mainClassDir.resolve("JLink20000PackagesTest.java"), """
                package testpackage;

                public class JLink20000PackagesTest {
                    public static void main(String[] args) throws Exception {
                        System.out.println("JLink20000PackagesTest started.");
                    }
                }
                """);

        String out = src.resolve("out").toString();
        javac(new String[]{
                "-d", out,
                "--module-source-path", src.toString(),
                "--module", "bug8321413x"
        });

        JImageGenerator.getJLinkTask()
                .modulePath(out)
                .output(imageDir)
                .addMods("bug8321413x")
                .call()
                .assertSuccess();

        Path binDir = imageDir.resolve("bin").toAbsolutePath();
        Path bin = binDir.resolve("java");

        ProcessBuilder processBuilder = new ProcessBuilder(bin.toString(),
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+BytecodeVerificationLocal",
                "-m", "bug8321413x/testpackage.JLink20000PackagesTest");
        processBuilder.inheritIO();
        processBuilder.directory(binDir.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0)
             throw new AssertionError("JLink20000PackagesTest failed to launch");
    }
}
