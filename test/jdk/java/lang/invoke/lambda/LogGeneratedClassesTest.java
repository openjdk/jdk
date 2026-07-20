/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8023524 8304846
 * @requires vm.flagless
 * @library /test/lib/
 * @library /java/nio/file
 * @modules jdk.compiler
 *          jdk.zipfs
 * @run junit LogGeneratedClassesTest
 * @summary tests logging generated classes for lambda
 */
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileStore;
import java.nio.file.attribute.PosixFileAttributeView;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;

import static java.nio.file.attribute.PosixFilePermissions.*;
import static jdk.test.lib.process.ProcessTools.*;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class LogGeneratedClassesTest {
    static final Path DUMP_LAMBDA_PROXY_CLASS_FILES = Path.of("DUMP_LAMBDA_PROXY_CLASS_FILES");
    static final Path CLASSES = Path.of("classes").toAbsolutePath();
    static String longFQCN;

    @BeforeAll
    public static void setup() throws IOException {
        final List<String> scratch = new ArrayList<>();
        scratch.clear();
        scratch.add("package com.example;");
        scratch.add("public class TestLambda {");
        scratch.add("    interface I {");
        scratch.add("        int foo();");
        scratch.add("    }");
        scratch.add("    public static void main(String[] args) {");
        scratch.add("        I lam = () -> 10;");
        scratch.add("        Runnable r = () -> {");
        scratch.add("            System.out.println(\"Runnable\");");
        scratch.add("        };");
        scratch.add("        r.run();");
        scratch.add("        System.out.println(\"Finish\");");
        scratch.add("    }");
        scratch.add("}");

        Path testLambda = Path.of("TestLambda.java");
        Files.write(testLambda, scratch, Charset.defaultCharset());

        scratch.remove(0);
        scratch.remove(0);
        scratch.add(0, "public class LongPackageName {");
        StringBuilder sb = new StringBuilder("com.example.");
        // longer than 255 which exceed max length of most filesystems
        for (int i = 0; i < 30; i++) {
            sb.append("nonsense.");
        }
        sb.append("enough");
        longFQCN = sb.toString() + ".LongPackageName";
        sb.append(";");
        sb.insert(0, "package ");
        scratch.add(0, sb.toString());
        Path lpnTest = Path.of("LongPackageName.java");
        Files.write(lpnTest, scratch, Charset.defaultCharset());

        CompilerUtils.compile(Path.of("."), CLASSES);
    }

    @AfterAll
    public static void cleanup() throws IOException {
        Files.delete(Paths.get("TestLambda.java"));
        Files.delete(Paths.get("LongPackageName.java"));
        TestUtil.removeAll(DUMP_LAMBDA_PROXY_CLASS_FILES);
        TestUtil.removeAll(Paths.get("notDir"));
        TestUtil.removeAll(Paths.get("dump"));
        TestUtil.removeAll(Paths.get("dumpLong"));
    }

    @Test
    public void testNotLogging() throws Exception {
        ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
                               "-cp", CLASSES.toString(),
                               "com.example.TestLambda");
        executeProcess(pb).shouldHaveExitValue(0);
    }

    @Test
    public void testLogging() throws Exception {
        Path testDir = Path.of("dump");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
                               "-cp", CLASSES.toString(),
                               "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                               "com.example.TestLambda").directory(testDir.toFile());
        executeProcess(pb).shouldHaveExitValue(0);

        // 2 our own class files. We don't care about the others
        assertEquals(2, Files.find(
                dumpDir,
                99,
                (p, a) -> p.startsWith(dumpDir.resolve("com/example"))
                        && a.isRegularFile()).count(), "Two lambda captured");
    }

    @Test
    public void testDumpDirNotExist() throws Exception {
        Path testDir = Path.of("NotExist");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        TestUtil.removeAll(dumpDir);
        assertFalse(Files.exists(dumpDir));

        ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
                                "-cp", CLASSES.toString(),
                                "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                                "com.example.TestLambda").directory(testDir.toFile());
        executeProcess(pb).shouldHaveExitValue(0);

        // The dump directory will be created if not exist
        assertEquals(2, Files.find(
                dumpDir,
                99,
                (p, a) -> p.startsWith(dumpDir.resolve("com/example"))
                        && a.isRegularFile()).count(), "Two lambda captured");
    }

    @Test
    public void testDumpDirIsFile() throws Exception {
        Path testDir = Path.of("notDir");
        Path dumpFile = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        Files.createFile(dumpFile);
        assertTrue(Files.isRegularFile(dumpFile));
        ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
                                "-cp", CLASSES.toString(),
                                "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                                "com.example.TestLambda").directory(testDir.toFile());
        executeProcess(pb)
                .shouldContain("DUMP_LAMBDA_PROXY_CLASS_FILES is not a directory")
                .shouldNotHaveExitValue(0);
    }

    private static boolean isWriteableDirectory(Path p) {
        if (!Files.isDirectory(p)) {
            return false;
        }
        Path test = p.resolve(Paths.get("test"));
        try {
            Files.createFile(test);
            assertTrue(Files.exists(test));
            return true;
        } catch (IOException e) {
            assertFalse(Files.exists(test));
            return false;
        } finally {
            if (Files.exists(test)) {
                try {
                    Files.delete(test);
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }
    }

    @Test
    public void testDumpDirNotWritable() throws Exception {
        FileStore fs;
        try {
            fs = Files.getFileStore(Paths.get("."));
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "WARNING: IOException occurred: " + e + ", Skipping testDumpDirNotWritable test.");
            return;
        }
        Assumptions.assumeFalse(!fs.supportsFileAttributeView(PosixFileAttributeView.class), "WARNING: POSIX is not supported. Skipping testDumpDirNotWritable test."); // No easy way to setup readonly directory without POSIX

        Path testDir = Path.of("readOnly");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        Files.createDirectory(dumpDir,
                              asFileAttribute(fromString("r-xr-xr-x")));
        try {
            Assumptions.assumeFalse(isWriteableDirectory(dumpDir), "WARNING: The dump directory is writeable. Skipping testDumpDirNotWritable test."); // Skipping the test: it's allowed to write into read-only directory
            // (e.g. current user is super user).

            ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
                                   "-cp", CLASSES.toString(),
                                   "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                                   "com.example.TestLambda").directory(testDir.toFile());
            executeProcess(pb)
                    .shouldContain("DUMP_LAMBDA_PROXY_CLASS_FILES is not writable")
                    .shouldNotHaveExitValue(0);
        } finally {
            TestUtil.removeAll(testDir);
        }
    }

    @Test
    public void testLoggingException() throws Exception {
        Path testDir = Path.of("dumpLong");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectories(dumpDir.resolve("com/example/nonsense"));
        Files.createFile(dumpDir.resolve("com/example/nonsense/nonsense"));
        ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
                               "-cp", CLASSES.toString(),
                               "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                               longFQCN).directory(testDir.toFile());
        OutputAnalyzer outputAnalyzer = executeProcess(pb);
        outputAnalyzer.shouldHaveExitValue(0);
        assertEquals(2, outputAnalyzer.asLines().stream()
                .filter(s -> s.startsWith("WARNING: Exception"))
                .count(), "show error each capture");
        // dumpLong/DUMP_LAMBDA_PROXY_CLASS_FILES/com/example/nonsense/nonsense
        Path dumpPath = dumpDir.resolve("com/example/nonsense");
        Predicate<Path> filter = p -> p.getParent() == null || dumpPath.startsWith(p) || p.startsWith(dumpPath);
        boolean debug = true;
        if (debug) {
           Files.walk(dumpDir)
                .forEachOrdered(p -> {
                    if (filter.test(p)) {
                        System.out.println("accepted: " + p.toString());
                    } else {
                        System.out.println("filtered out: " + p.toString());
                    }
                 });
        }
        assertEquals(5, Files.walk(dumpDir)
                .filter(filter)
                .count(), "Two lambda captured failed to log");
    }
}
