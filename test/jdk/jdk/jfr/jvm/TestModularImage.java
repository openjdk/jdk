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
package jdk.jfr.jvm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.spi.ToolProvider;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @key jfr
 * @summary Checks that a JDK image with and without the jdk.jfr module behaves
 *          as expected
 * @requires vm.hasJFR
 * @library /test/lib
 * @run driver jdk.jfr.jvm.TestModularImage
 */
public class TestModularImage {
    private static final String STARTED_RECORDING = "Started recording";
    private static final String HELLO_WORLD = "hello, world";
    private static final String ERROR_LINE1 = "Error occurred during initialization of boot layer";
    private static final String ERROR_LINE2 = "java.lang.module.FindException: Module jdk.jfr not found";

    private static final ToolProvider javac = find("javac");
    private static final ToolProvider jlink = find("jlink");

    private static final Path out = Path.of("out");
    private static final Path src = out.resolve("src");
    private static final Path classes = out.resolve("classes");

    private static final Path testJDK = Path.of(System.getProperty("test.jdk"));
    private static final Path jmods = testJDK.resolve("jmods");

    private static final String modulePath = jmods.toString() + File.pathSeparator + classes.toString();

    public static void main(String[] args) throws Exception {
        preparseSourceTree();
        compileSourceCode();

        // Jcmd for the current JVM where jdk.attach module is available
        String currentJcmd = JDKToolFinder.getJDKTool("jcmd");
        currentJcmd = Path.of(currentJcmd).normalize().toAbsolutePath().toString();

        // Image 1: Should be able to start JFR if jdk.jfr module is present
        Path javaBin1 = jlink("hello.world,jdk.jfr", "with-jfr");
        testCommandLineWithJFR(javaBin1);
        testJcmdWithJFR(javaBin1, currentJcmd);

        // Image 2: Should fail if jdk.jfr module is not present
        Path javaBin2 = jlink("hello.world", "without-jfr");
        testCommandLineWithoutJFR(javaBin2);
        testJcmdWithoutJFR(javaBin2, currentJcmd);
    }

    private static void testCommandLineWithJFR(Path binPath) throws Exception {
        var result = java(binPath, "-XX:StartFlightRecording", "--module", "hello.world/hello.Main");
        result.shouldNotContain(ERROR_LINE1);
        result.shouldNotContain(ERROR_LINE2);
        result.shouldContain(HELLO_WORLD);
        result.shouldContain(STARTED_RECORDING);
        result.shouldHaveExitValue(0);
    }

    private static void testJcmdWithJFR(Path binPath, String jcmd) throws Exception {
        var result = java(binPath, "--module", "hello.world/hello.Main", jcmd);
        result.shouldContain(HELLO_WORLD);
        result.shouldNotContain(ERROR_LINE1);
        result.shouldNotContain(ERROR_LINE2);
        result.shouldContain(STARTED_RECORDING);
        result.shouldHaveExitValue(0);
    }

    private static void testCommandLineWithoutJFR(Path binPath) throws Exception {
        var result = java(binPath, "-XX:StartFlightRecording", "--module", "hello.world/hello.Main");
        result.shouldContain(ERROR_LINE1);
        result.shouldContain(ERROR_LINE2);
        result.shouldNotContain(HELLO_WORLD);
        result.shouldNotContain(STARTED_RECORDING);
        result.shouldHaveExitValue(1);
    }

    private static void testJcmdWithoutJFR(Path binPath, String jcmd) throws Exception {
        OutputAnalyzer result = java(binPath, "--module", "hello.world/hello.Main", jcmd);
        result.shouldContain(HELLO_WORLD);
        result.shouldContain("Module jdk.jfr not found.");
        result.shouldContain("Flight Recorder can not be enabled.");
        result.shouldNotContain(STARTED_RECORDING);
        result.shouldHaveExitValue(0);
    }

    private static ToolProvider find(String tool) {
        return ToolProvider.findFirst(tool).orElseThrow(() -> new RuntimeException("No " + tool));
    }

    private static void preparseSourceTree() throws IOException {
        String main =
        """
        package hello;
        import java.io.ByteArrayOutputStream;
        public class Main {
          public static void main(String... args) throws Exception {
            System.out.println("hello, world!");
            if (args.length > 0) {
              long pid = ProcessHandle.current().pid();
              String jcmd = args[0];
              String[] cmds = { jcmd, Long.toString(pid), "JFR.start" };
              Process process = new ProcessBuilder(cmds).redirectErrorStream(true).start();
              process.waitFor();
              var baos = new ByteArrayOutputStream();
              process.getInputStream().transferTo(baos);
              System.out.println(baos.toString());
              System.exit(process.exitValue());
            }
          }
        }
        """;
        String moduleInfo = "module hello.world {}";
        Path helloWorld = src.resolve("hello.world");
        Files.createDirectories(helloWorld.resolve("hello"));
        Files.write(helloWorld.resolve("module-info.java"), moduleInfo.getBytes());
        Files.write(helloWorld.resolve("hello").resolve("Main.java"), main.getBytes());
    }

    private static void compileSourceCode() {
        javac.run(System.out, System.err,
            "--module-source-path", src.toString(),
            "--module", "hello.world",
            "-d", classes.toString());
    }

    private static Path jlink(String modules, String output) {
        jlink.run(System.out, System.err,
            "--add-modules", modules,
            "--module-path", modulePath,
            "--output", output);
        return Path.of(output).resolve("bin").toAbsolutePath();
    }

    private static OutputAnalyzer java(Path jvm, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        String java = Platform.isWindows() ? "java.exe" : "java";
        List<String> arguments = new ArrayList<>();
        arguments.add(jvm.resolve(java).toString());
        arguments.addAll(Arrays.asList(args));
        pb.command(arguments);
        pb.directory(jvm.toFile());
        System.out.println("Executing: java " + String.join(" ", args));
        OutputAnalyzer result = ProcessTools.executeProcess(pb);
        System.out.println("--- Output ----" + "-".repeat(65));
        System.out.println(result.getOutput());
        System.out.println("-".repeat(80));
        return result;
    }
}
