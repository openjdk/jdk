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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.spi.ToolProvider;

import tests.JImageGenerator;
import tests.JImageGenerator.JLinkTask;

/*
 * @test
 * @summary Make sure that 100 modules can be linked using jlink.
 * @bug 8240567
 * @library ../lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main/othervm -Xmx1g -Xlog:init=debug -XX:+UnlockDiagnosticVMOptions -XX:+BytecodeVerificationLocal JLink100Modules
 */
public class JLink100Modules {
    private static final ToolProvider JAVAC_TOOL = ToolProvider.findFirst("javac")
            .orElseThrow(() -> new RuntimeException("javac tool not found"));
    private static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() -> new RuntimeException("jlink tool not found"));

    static void report(String command, String[] args) {
        System.out.println(command + " " + String.join(" ", Arrays.asList(args)));
    }

    static void javac(String[] args) {
        report("javac", args);
        JAVAC_TOOL.run(System.out, System.err, args);
    }

    static void jlink(String[] args) {
        report("jlink", args);
        JLINK_TOOL.run(System.out, System.err, args);
    }

    public static void main(String[] args) throws Exception {
        Path src = Paths.get("bug8240567");

        StringJoiner mainModuleInfoContent = new StringJoiner(";\n  requires ", "module bug8240567x {\n  requires ", "\n;}");

        // create 100 modules. With this naming schema up to 130 seem to work
        for (int i = 0; i < 150; i++) {
            String name = "module" + i + "x";
            Path moduleDir = Files.createDirectories(src.resolve(name));

            StringBuilder builder = new StringBuilder("module ");
            builder.append(name).append(" {");

            if (i != 0) {
                builder.append("requires module0x;");
            }

            builder.append("}\n");
            Files.writeString(moduleDir.resolve("module-info.java"), builder.toString());
            mainModuleInfoContent.add(name);
        }

        // create module reading the generated modules
        Path mainModulePath = src.resolve("bug8240567x");
        Files.createDirectories(mainModulePath);
        Path mainModuleInfo = mainModulePath.resolve("module-info.java");
        Files.writeString(mainModuleInfo, mainModuleInfoContent.toString());

        Path mainClassDir = mainModulePath.resolve("testpackage");
        Files.createDirectories(mainClassDir);

        Files.writeString(mainClassDir.resolve("JLink100ModulesTest.java"), """
                package testpackage;

                public class JLink100ModulesTest {
                    public static void main(String[] args) throws Exception {
                        System.out.println("JLink100ModulesTest started.");
                    }
                }
                """);

        String out = src.resolve("out").toString();

        javac(new String[]{
                "-d", out,
                "--module-source-path", src.toString(),
                "--module", "bug8240567x"
        });

        JImageGenerator.getJLinkTask()
                .modulePath(out)
                .output(src.resolve("out-jlink"))
                .addMods("bug8240567x")
                .call()
                .assertSuccess();

        Path bin = src.resolve("out-jlink").resolve("bin");

        // String binName = jdk.internal.util.OperatingSystem.isWindows() ? "java.exe" : "java";
        String binName = "java.exe";
        if (!Files.exists(Path.of(binName))) {
            binName = "java";
        }

        ProcessBuilder processBuilder = new ProcessBuilder(binName, "-XX:+UnlockDiagnosticVMOptions", "-XX:+BytecodeVerificationLocal", "-m", "bug8240567x/testpackage.JLink100ModulesTest");
        processBuilder.inheritIO();
        processBuilder.directory(bin.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        System.err.println(exitCode);
        if (exitCode != 0) throw new AssertionError("Exit code is not 0");
    }
}
