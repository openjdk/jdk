/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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


import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.spi.ToolProvider;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 6274264 6274241 5070281
 * @summary test retransformClasses
 *
 * @modules java.instrument
 * @library /test/lib
 * @build bootreporter.StringIdCallback bootreporter.StringIdCallbackReporter
 * @enablePreview
 * @comment When this test is compiled by jtreg, it also compiles everything under this test's
 *          source directory. One such class under asmlib/Instrumentor.java uses ClassFile API
 *          which is a PreviewFeature. Hence we enablePreview for this test
 * @run driver RetransformApp roleDriver
 */
public class RetransformApp {

    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
            .orElseThrow(() -> new RuntimeException("jar tool not found")
            );

    private static final ToolProvider JAVAC_TOOL = ToolProvider.findFirst("javac")
            .orElseThrow(() -> new RuntimeException("javac tool not found")
            );

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if (!"roleDriver".equals(args[0])) {
                throw new Exception("unexpected program argument: " + args[0]);
            }
            // launch the RetransformApp java process after creating the necessary
            // infrastructure
            System.out.println("creating agent jar");
            final Path agentJar = createAgentJar();
            System.out.println("launching app, with javaagent jar: " + agentJar);
            launchApp(agentJar);
        } else {
            System.err.println("running app");
            new RetransformApp().run(System.out);
        }
    }

    private static Path createAgentJar() throws Exception {
        final Path outputDir = Files.createTempDirectory(Path.of("."), "RetransformApp");
        final Path bootClassesDir = outputDir.resolve("bootclasses");
        compileBootpathClasses(bootClassesDir);
        final Path agentClasses = outputDir.resolve("agentclasses");
        compileAgentClasses(agentClasses);

        final String manifest = """
                Manifest-Version: 1.0
                Premain-Class: RetransformAgent
                Can-Retransform-Classes: true
                """
                + "Boot-Class-Path: " + bootClassesDir.toString().replace(File.separatorChar, '/')
                + "\n";
        System.err.println("manifest is:\n" + manifest);
        final Path manifestFile = Files.writeString(Path.of("agentmanifest.mf"), manifest);
        final Path agentJarFile = Path.of("RetransformAgent.jar").toAbsolutePath();
        final String[] jarCmdArgs = {"cvfm",
                agentJarFile.toString(), manifestFile.toString(),
                "-C", agentClasses.toAbsolutePath().toString(), "."};
        System.out.println("invoking jar with args: " + Arrays.toString(jarCmdArgs));
        final int exitCode = JAR_TOOL.run(System.out, System.err, jarCmdArgs);
        if (exitCode != 0) {
            throw new Exception("jar command failed with exit code: " + exitCode);
        }
        return agentJarFile;
    }

    private static void compileBootpathClasses(final Path destDir) throws Exception {
        // directory containing this current test file
        final String testSrc = System.getProperty("test.src");
        if (testSrc == null) {
            throw new Exception("test.src system property isn't set");
        }
        final Path bootReporterSourceDir = Path.of(testSrc)
                .resolve("bootreporter").toAbsolutePath();
        // this directory must be present
        if (!Files.isDirectory(bootReporterSourceDir)) {
            throw new Exception(bootReporterSourceDir.toString() + " is missing or not a directory");
        }
        final String[] javacCmdArgs = {"-d", destDir.toString(),
                bootReporterSourceDir.resolve("StringIdCallback.java").toString(),
                bootReporterSourceDir.resolve("StringIdCallbackReporter.java").toString()
        };
        System.out.println("invoking javac with args: " + Arrays.toString(javacCmdArgs));
        final int exitCode = JAVAC_TOOL.run(System.out, System.err, javacCmdArgs);
        if (exitCode != 0 ){
            throw new Exception("javac command failed with exit code: " + exitCode);
        }
    }

    private static void compileAgentClasses(final Path destDir) throws Exception {
        // directory containing this current test file
        final String testSrc = System.getProperty("test.src");
        if (testSrc == null) {
            throw new Exception("test.src system property isn't set");
        }
        final Path agentJavaSrc = Path.of(testSrc).resolve("RetransformAgent.java")
                .toAbsolutePath();
        // this file must be present
        if (!Files.isRegularFile(agentJavaSrc)) {
            throw new Exception(agentJavaSrc.toString() + " is missing or not a file");
        }
        final Path instrumentorJavaSrc = Path.of(testSrc)
                .resolve("asmlib", "Instrumentor.java").toAbsolutePath();
        // this file must be present
        if (!Files.isRegularFile(instrumentorJavaSrc)) {
            throw new Exception(instrumentorJavaSrc.toString() + " is missing or not a file");
        }
        final String currentRuntimeVersion = String.valueOf(Runtime.version().feature());
        final String[] javacCmdArgs = {"-d", destDir.toString(),
                // enable preview since asmlib/Instrumentor.java uses ClassFile API PreviewFeature
                "--enable-preview", "--release", currentRuntimeVersion,
                agentJavaSrc.toString(), instrumentorJavaSrc.toString()};
        System.out.println("invoking javac with args: " + Arrays.toString(javacCmdArgs));
        final int exitCode = JAVAC_TOOL.run(System.out, System.err, javacCmdArgs);
        if (exitCode != 0 ){
            throw new Exception("javac command failed with exit code: " + exitCode);
        }
    }

    private static void launchApp(final Path agentJar) throws Exception {
        final OutputAnalyzer oa = ProcessTools.executeLimitedTestJava(
                "--enable-preview", // due to usage of ClassFile API PreviewFeature in the agent
                "-XX:+UnlockDiagnosticVMOptions", "-XX:-CheckIntrinsics",
                "-javaagent:" + agentJar.toString(),
                RetransformApp.class.getName());
        oa.shouldHaveExitValue(0);
        // make available stdout/stderr in the logs, even in case of successful completion
        oa.reportDiagnosticSummary();
    }

    int foo(int x) {
        return x * x;
    }

    public void run(PrintStream out) throws Exception {
        out.println("start");
        for (int i = 0; i < 4; i++) {
            if (foo(3) != 9) {
                throw new Exception("ERROR: unexpected application behavior");
            }
        }
        out.println("undo");
        RetransformAgent.undo();
        for (int i = 0; i < 4; i++) {
            if (foo(3) != 9) {
                throw new Exception("ERROR: unexpected application behavior");
            }
        }
        out.println("end");
        if (RetransformAgent.succeeded()) {
            out.println("Instrumentation succeeded.");
        } else {
            throw new Exception("ERROR: Instrumentation failed.");
        }
    }
}
