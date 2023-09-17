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

/*
 * @test
 * @summary Make sure that 100 modules can be linked using jlink.
 * @bug 8240567
 * @library ../lib
 * @modules java.base/jdk.internal.jimage
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
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

    static void report(String command, String[] args) {
        System.out.println(command + " " + String.join(" ", Arrays.asList(args)));
    }

    static void javac(String[] args) {
        report("javac", args);
        JAVAC_TOOL.run(System.out, System.err, args);
    }

    public static void main(String[] args) throws Exception {
        Path src = Paths.get("bug8240567");

        StringJoiner mainModuleInfoContent = new StringJoiner(";\n  requires ", "module bug8240567x {\n  requires ", ";\n}");

        for (int i = 0; i < 1_000; i++) {
            String name = "module" + i + "x";
            Path moduleDir = Files.createDirectories(src.resolve(name));

            StringBuilder moduleInfoContent = new StringBuilder("module ");
            moduleInfoContent.append(name).append(" {\n");
            if (i != 0) {
                moduleInfoContent.append("  requires module0x;\n");
            }
            moduleInfoContent.append("}\n");
            Files.writeString(moduleDir.resolve("module-info.java"), moduleInfoContent.toString());

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

        Path binDir = src.resolve("out-jlink").resolve("bin").toAbsolutePath();
        Path bin = binDir.resolve("java");

        ProcessBuilder processBuilder = new ProcessBuilder(bin.toString(),
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+BytecodeVerificationLocal",
                "-m", "bug8240567x/testpackage.JLink100ModulesTest");
        processBuilder.inheritIO();
        processBuilder.directory(binDir.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0)
             throw new AssertionError("JLink100ModulesTest failed to launch");
    }
}
