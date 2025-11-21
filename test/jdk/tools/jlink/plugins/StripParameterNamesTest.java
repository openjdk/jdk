/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.spi.ToolProvider;

import jdk.test.lib.util.FileUtils;
import tests.JImageGenerator;
import tests.JImageGenerator.InMemorySourceFile;
import tests.Result;

/*
 * @test
 * @summary Test jlink strip debug plugins handle method parameter names.
 * @bug 8347007
 * @library ../../lib
 * @library /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build jdk.test.lib.util.FileUtils
 *        tests.*
 * @run junit/othervm StripParameterNamesTest
 */
public class StripParameterNamesTest {
    private static final ToolProvider JAVAC_TOOL = ToolProvider.findFirst("javac")
            .orElseThrow(() -> new RuntimeException("javac tool not found"));

    private static Path src = Path.of("src").toAbsolutePath();
    private static List<Jmod> testJmods = new ArrayList<>();

    record Jmod(Path moduleDir, boolean withDebugInfo, boolean withParameterNames) {}

    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectory(src);
        var mainClassSource = new InMemorySourceFile("test", "InspectParameterNames", """
                package test;

                public class InspectParameterNames {
                    int add(int a, int b) {
                        return a + b;
                    }

                    public static boolean hasParameterNames() throws NoSuchMethodException {
                        // Get add method in the class
                        var method = InspectParameterNames.class.getDeclaredMethod("add", int.class, int.class);

                        // Get method parameters
                        var parameters = method.getParameters();

                        // validate parameter names
                        return parameters[0].getName().equals("a") && parameters[1].getName().equals("b");
                    }

                    public static void main(String[] args) throws NoSuchMethodException {
                        System.out.println(hasParameterNames());
                    }
                }
            """);
        var moduleDir = JImageGenerator.generateSources(src, "bug8347007x", List.of(mainClassSource));
        JImageGenerator.generateModuleInfo(moduleDir, List.of("test"));
        testJmods.add(buildJmod(true, true));
        testJmods.add(buildJmod(true, false));
        testJmods.add(buildJmod(false, true));
        testJmods.add(buildJmod(false, false));
    }

    @AfterEach
    public void cleanup() throws IOException {
        FileUtils.deleteFileTreeWithRetry(Path.of("img"));
    }

    static void report(String command, List<String> args) {
        System.out.println(command + " " + String.join(" ", args));
    }

    static void javac(List<String> args) {
        report("javac", args);
        JAVAC_TOOL.run(System.out, System.err, args.toArray(new String[0]));
    }

    /**
     * Build jmods from the module source path
     */
    static Jmod buildJmod(boolean withDebugInfo, boolean withParameterNames) {
        String dirName = "jmods";
        List<String> options = new ArrayList<>();

        if (withDebugInfo) {
            options.add("-g");
            dirName += "g";
        }

        if (withParameterNames) {
            options.add("-parameters");
            dirName += "p";
        }

        Path moduleDir = Path.of(dirName).toAbsolutePath();

        options.add("-d");
        options.add(moduleDir.toString());
        options.add("--module-source-path");
        options.add(src.toString());
        options.add("--module");
        options.add("bug8347007x");

        javac(options);
        return new Jmod(moduleDir, withDebugInfo, withParameterNames);
    }

    Result buildImage(Path modulePath, Path imageDir, String... options) {
        var jlinkTask = JImageGenerator.getJLinkTask()
                .modulePath(modulePath.toString())
                .output(imageDir);

        for (var option: options) {
            jlinkTask.option(option);
        }

        return jlinkTask.addMods("bug8347007x")
                .call();
    }

    void assertHasParameterNames(Path imageDir, boolean expected) throws IOException, InterruptedException {
        Path binDir = imageDir.resolve("bin").toAbsolutePath();
        Path bin = binDir.resolve("java");

        ProcessBuilder processBuilder = new ProcessBuilder(bin.toString(),
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+BytecodeVerificationLocal",
                "-m", "bug8347007x/test.InspectParameterNames");
        processBuilder.directory(binDir.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        var output = process.inputReader().readLine();
        System.out.println(output);
        assertEquals(expected, Boolean.parseBoolean(output));
    }

    Stream<Jmod> provideTestJmods() {
        return testJmods.stream();
    }

    @ParameterizedTest
    @FieldSource("testJmods")
    public void testDefaultBehavior(Jmod jmod) throws Exception {
        var imageDir = Path.of("img");
        buildImage(jmod.moduleDir(), imageDir)
            .assertSuccess();
        var hasParameter = jmod.withParameterNames();
        assertHasParameterNames(imageDir, hasParameter);
    }

    @ParameterizedTest
    @FieldSource("testJmods")
    public void testStripDebug(Jmod jmod) throws Exception {
        var imageDir = Path.of("img");
        buildImage(jmod.moduleDir(), imageDir,
                "--strip-debug")
            .assertSuccess();
        var hasParameter = jmod.withParameterNames();
        assertHasParameterNames(imageDir, hasParameter);
    }

    @Test
    public void testBothStripOptions() throws Exception {
        var imageDir = Path.of("img");
        var jmod = testJmods.get(0);
        buildImage(jmod.moduleDir(), imageDir,
                "--strip-debug", "--strip-java-debug-attributes")
            .assertSuccess();
        assertHasParameterNames(imageDir, jmod.withParameterNames());
    }

    @ParameterizedTest
    @FieldSource("testJmods")
    public void testOnlyStripJavaDebugAttributes() throws Exception {
        var imageDir = Path.of("img");
        var jmod = testJmods.get(0);
        buildImage(jmod.moduleDir(), imageDir,
                "--strip-java-debug-attributes")
            .assertSuccess();
        assertHasParameterNames(imageDir, jmod.withParameterNames());
    }
}