/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests logging generated classes for lambda
 * @library /java/nio/file
 * @modules jdk.compiler
 *          jdk.zipfs
 * @run testng LogGeneratedClassesTest
 */
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.SkipException;

import static java.nio.file.attribute.PosixFilePermissions.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class LogGeneratedClassesTest extends LUtils {
    static final Path DUMP_LAMBDA_PROXY_CLASS_FILES = Path.of("DUMP_LAMBDA_PROXY_CLASS_FILES");
    String longFQCN;

    @BeforeClass
    public void setup() throws IOException {
        final List<String> scratch = new ArrayList<>();
        scratch.clear();
        scratch.add("package com.example;");
        scratch.add("public class TestLambda {");
        scratch.add("    interface I {");
        scratch.add("        int foo();");
        scratch.add("    }");
        scratch.add("    public static void main(String[] args) {");
        scratch.add("        System.setSecurityManager(new SecurityManager());");
        scratch.add("        I lam = () -> 10;");
        scratch.add("        Runnable r = () -> {");
        scratch.add("            System.out.println(\"Runnable\");");
        scratch.add("        };");
        scratch.add("        r.run();");
        scratch.add("        System.out.println(\"Finish\");");
        scratch.add("    }");
        scratch.add("}");

        File test = new File("TestLambda.java");
        createFile(test, scratch);
        compile("-d", ".", test.getName());

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
        test = new File("LongPackageName.java");
        createFile(test, scratch);
        compile("-d", ".", test.getName());
    }

    @AfterClass
    public void cleanup() throws IOException {
        Files.delete(Paths.get("TestLambda.java"));
        Files.delete(Paths.get("LongPackageName.java"));
        TestUtil.removeAll(DUMP_LAMBDA_PROXY_CLASS_FILES);
        TestUtil.removeAll(Paths.get("notDir"));
        TestUtil.removeAll(Paths.get("dump"));
        TestUtil.removeAll(Paths.get("dumpLong"));
    }

    @Test
    public void testNotLogging() {
        TestResult tr = doExec(JAVA_CMD.getAbsolutePath(),
                               "-cp", ".",
                               "-Djava.security.manager=allow",
                               "com.example.TestLambda");
        tr.assertZero("Should still return 0");
    }

    @Test
    public void testLogging() throws IOException {
        Path testDir = Path.of("dump");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        TestResult tr = doExec(JAVA_CMD.getAbsolutePath(),
                               "-cp", "..",
                               "-Duser.dir=" + testDir.toAbsolutePath(),
                               "-Djava.security.manager=allow",
                               "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                               "com.example.TestLambda");
        // 2 our own class files. We don't care about the others
        assertEquals(Files.find(
                        dumpDir,
                        99,
                        (p, a) -> p.startsWith(dumpDir.resolve("com/example"))
                                && a.isRegularFile()).count(),
                      2, "Two lambda captured");
        tr.assertZero("Should still return 0");
    }

    @Test
    public void testDumpDirNotExist() throws IOException {
        Path testDir = Path.of("NotExist");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        TestUtil.removeAll(dumpDir);

        assertFalse(Files.exists(dumpDir));
        TestResult tr = doExec(JAVA_CMD.getAbsolutePath(),
                               "-cp", "..",
                               "-Duser.dir=" + testDir.toAbsolutePath(),
                               "-Djava.security.manager=allow",
                               "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                               "com.example.TestLambda");

        // The dump directory will be created if not exist
        assertEquals(Files.find(
                        dumpDir,
                        99,
                        (p, a) -> p.startsWith(dumpDir.resolve("com/example"))
                                && a.isRegularFile()).count(),
                    2, "Two lambda captured");
        tr.assertZero("Should still return 0");
    }

    @Test
    public void testDumpDirIsFile() throws IOException {
        Path testDir = Path.of("notDir");
        Path dumpFile = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        Files.createFile(dumpFile);
        assertTrue(Files.isRegularFile(dumpFile));
        TestResult tr = doExec(JAVA_CMD.getAbsolutePath(),
                               "-cp", "..",
                               "-Duser.dir=" + testDir.toAbsolutePath(),
                               "-Djava.security.manager=allow",
                               "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                               "com.example.TestLambda");
        assertEquals(tr.testOutput.stream()
                                  .filter(s -> s.contains("DUMP_LAMBDA_PROXY_CLASS_FILES is not a directory"))
                                  .count(),
                     1, "only show error once");
        assertTrue(tr.exitValue != 0);
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
    public void testDumpDirNotWritable() throws IOException {
        if (!Files.getFileStore(Paths.get("."))
                  .supportsFileAttributeView(PosixFileAttributeView.class)) {
            // No easy way to setup readonly directory without POSIX
            // We would like to skip the test with a cause with
            //     throw new SkipException("Posix not supported");
            // but jtreg will report failure so we just pass the test
            // which we can look at if jtreg changed its behavior
            System.out.println("WARNING: POSIX is not supported. Skipping testDumpDirNotWritable test.");
            return;
        }

        Path testDir = Path.of("readOnly");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectory(testDir);
        Files.createDirectory(dumpDir,
                              asFileAttribute(fromString("r-xr-xr-x")));
        try {
            if (isWriteableDirectory(dumpDir)) {
                // Skipping the test: it's allowed to write into read-only directory
                // (e.g. current user is super user).
                System.out.println("WARNING: The dump directory is writeable. Skipping testDumpDirNotWritable test.");
                return;
            }

            TestResult tr = doExec(JAVA_CMD.getAbsolutePath(),
                                   "-cp", "..",
                                   "-Duser.dir=" + testDir.toAbsolutePath(),
                                   "-Djava.security.manager=allow",
                                   "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                                   "com.example.TestLambda");
            assertEquals(tr.testOutput.stream()
                                      .filter(s -> s.contains("DUMP_LAMBDA_PROXY_CLASS_FILES is not writable"))
                                      .count(),
                         1, "only show error once");
            assertTrue(tr.exitValue != 0);
        } finally {
            TestUtil.removeAll(testDir);
        }
    }

    @Test
    public void testLoggingException() throws IOException {
        Path testDir = Path.of("dumpLong");
        Path dumpDir = testDir.resolve(DUMP_LAMBDA_PROXY_CLASS_FILES);
        Files.createDirectories(dumpDir.resolve("com/example/nonsense"));
        Files.createFile(dumpDir.resolve("com/example/nonsense/nonsense"));
        TestResult tr = doExec(JAVA_CMD.getAbsolutePath(),
                               "-cp", "..",
                               "-Duser.dir=" + testDir.toAbsolutePath(),
                               "-Djava.security.manager=allow",
                               "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles",
                               longFQCN);
        assertEquals(tr.testOutput.stream()
                                  .filter(s -> s.startsWith("WARNING: Exception"))
                                  .count(),
                     2, "show error each capture");
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
        assertEquals(Files.walk(dumpDir)
                .filter(filter)
                .count(), 5, "Two lambda captured failed to log");
        tr.assertZero("Should still return 0");
    }
}
