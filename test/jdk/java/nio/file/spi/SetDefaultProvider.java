/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 7006126 8142968 8178380 8183320 8210112 8266345 8263940 8331467 8346573
 * @modules jdk.jartool jdk.jlink
 * @library /test/lib
 * @build testfsp/* testapp/* CustomSystemClassLoader
 * @run junit SetDefaultProvider
 * @summary Runs tests with -Djava.nio.file.spi.DefaultFileSystemProvider set on
 *          the command line to override the default file system provider
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class SetDefaultProvider {

    private static final String SET_DEFAULT_FSP =
        "-Djava.nio.file.spi.DefaultFileSystemProvider=testfsp.TestProvider";

    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private static final String TESTFSP = "testfsp";
    private static final String TESTAPP = "testapp";
    private static final String TESTAPP_MAIN = TESTAPP + ".Main";

    // directory containing testfsp class files
    private static String TESTFSP_CLASSES;

    // directory containing testapp class files
    private static String TESTAPP_CLASSES;

    @BeforeAll
    static void setup() {
        TESTFSP_CLASSES = classes(TESTFSP);
        TESTAPP_CLASSES = classes(TESTAPP);
    }

    /**
     * Test file system provider exploded on the class path.
     */
    @Test
    void testFspOnClassPath1() throws Exception {
        exec(SET_DEFAULT_FSP,
                "-cp", ofClasspath(TESTFSP_CLASSES, TESTAPP_CLASSES),
                TESTAPP_MAIN);
    }

    /**
     * Test file system provider in JAR file on the class path.
     */
    @Test
    void testFspOnClassPath2() throws Exception {
        String jarFile = createJar("fsp.jar", TESTFSP_CLASSES);
        exec(SET_DEFAULT_FSP,
                "-cp", ofClasspath(jarFile, TESTAPP_CLASSES),
                TESTAPP_MAIN);
    }

    /**
     * Test file system provider in exploded module on the module path.
     */
    @Test
    void testFspOnModulePath1() throws Exception {
        exec(SET_DEFAULT_FSP,
                "-p", TESTFSP_CLASSES,
                "--add-modules", TESTFSP,
                "-cp", TESTAPP_CLASSES,
                TESTAPP_MAIN);
    }

    /**
     * Test file system provider in modular JAR on the module path.
     */
    @Test
    void testFspOnModulePath2() throws Exception {
        String jarFile = createJar("fsp.jar", TESTFSP_CLASSES);
        exec(SET_DEFAULT_FSP,
                "-p", jarFile,
                "--add-modules", TESTFSP,
                "-cp", TESTAPP_CLASSES,
                TESTAPP_MAIN);
    }

    /**
     * Test file system provider linked into run-time image.
     */
    @Test
    void testFspInRuntimeImage() throws Exception {
        String image = "image";

        ToolProvider jlink = ToolProvider.findFirst("jlink").orElseThrow();
        String[] jlinkCmd = {
                "--module-path", TESTFSP_CLASSES,
                "--add-modules", TESTFSP,
                "--output", image
        };
        int exitCode = jlink.run(System.out, System.err, jlinkCmd);
        assertEquals(0, exitCode);

        String[] javaCmd = {
                Path.of(image, "bin", "java").toString(),
                SET_DEFAULT_FSP,
                "--add-modules", TESTFSP,
                "-cp", TESTAPP_CLASSES,
                TESTAPP_MAIN
        };
        var pb = new ProcessBuilder(javaCmd);
        ProcessTools.executeProcess(pb)
                .outputTo(System.out)
                .errorTo(System.err)
                .shouldHaveExitValue(0);
    }

    /**
     * Test file system provider on class path, application in exploded module on module path.
     */
    @Test
    void testAppOnModulePath1() throws Exception {
        exec(SET_DEFAULT_FSP,
                "-p", TESTAPP_CLASSES,
                "-cp", TESTFSP_CLASSES,
                "-m", TESTAPP + "/" + TESTAPP_MAIN);
    }

    /**
     * Test file system provider on class path, application in modular JAR on module path.
     */
    @Test
    void testAppOnModulePath2() throws Exception {
        String jarFile = createJar("testapp.jar", TESTAPP_CLASSES);
        exec(SET_DEFAULT_FSP,
                "-cp", TESTFSP_CLASSES,
                "-p", jarFile,
                "-m", TESTAPP + "/" + TESTAPP_MAIN);
    }

    /**
     * Test file system provider on class path, application in modular JAR on module path
     * that is patched with exploded patch.
     */
    @Test
    void testPatchedAppOnModulePath1() throws Exception {
        Path patchdir = createTempDirectory("patch");
        Files.createFile(patchdir.resolve("aoo.properties"));
        exec(SET_DEFAULT_FSP,
                "--patch-module", TESTAPP + "=" + patchdir,
                "-p", TESTAPP_CLASSES,
                "-cp", TESTFSP_CLASSES,
                "-m", TESTAPP + "/" + TESTAPP_MAIN);
    }

    /**
     * Test file system provider on class path, application in modular JAR on module path
     * that is patched with patch in JAR file.
     */
    @Test
    void testPatchedAppOnModulePath2() throws Exception {
        Path patchdir = createTempDirectory("patch");
        Files.createFile(patchdir.resolve("app.properties"));
        String jarFile = createJar("patch.jar", patchdir.toString());
        exec(SET_DEFAULT_FSP,
                "--patch-module", TESTAPP + "=" + jarFile,
                "-p", TESTAPP_CLASSES,
                "-cp", TESTFSP_CLASSES,
                "-m", TESTAPP + "/" + TESTAPP_MAIN);
    }

    /**
     * Test file system provider on class path in conjunction with a custom system
     * class loader that uses the file system API during its initialization.
     */
    @Test
    void testCustomSystemClassLoader() throws Exception {
        String testClasses = System.getProperty("test.classes");
        exec(SET_DEFAULT_FSP,
                "-Djava.system.class.loader=CustomSystemClassLoader",
                "-cp", ofClasspath(testClasses, TESTFSP_CLASSES, TESTAPP_CLASSES),
                TESTAPP_MAIN);
    }

    /**
     * Returns the directory containing the classes for the given module.
     */
    private static String classes(String mn) {
        String mp = System.getProperty("jdk.module.path");
        return Arrays.stream(mp.split(File.pathSeparator))
                .map(e -> Path.of(e, mn))
                .filter(Files::isDirectory)
                .findAny()
                .map(Path::toString)
                .orElseThrow();
    }

    /**
     * Returns a class path from the given paths.
     */
    private String ofClasspath(String... paths) {
        return String.join(File.pathSeparator, paths);
    }

    /**
     * Creates a JAR file from the contains of the given directory.
     */
    private String createJar(String jar, String dir) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("--create");
        args.add("--file=" + jar);
        args.add("-C");
        args.add(dir);
        args.add(".");
        int ret = JAR_TOOL.run(System.err, System.err, args.toArray(new String[0]));
        assertEquals(ret, 0);
        return jar;
    }

    /**
     * Create a temporary directory with the given prefix in the current directory.
     */
    private static Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(Path.of("."), prefix);
    }

    /**
     * Invokes the java launcher with the given arguments, throws if the non-0 is returned.
     */
    private void exec(String... args) throws Exception {
        ProcessTools.executeTestJava(args)
                .outputTo(System.err)
                .errorTo(System.err)
                .shouldHaveExitValue(0);
    }
}
