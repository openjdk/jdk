/**
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test the recording and checking of dependency hashes
 * @author Andrei Eremeev
 * @library /lib/testlibrary
 * @modules java.base/jdk.internal.module
 *          jdk.jlink/jdk.tools.jlink
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.compiler
 * @ignore
 * @build jdk.testlibrary.ProcessTools jdk.testlibrary.OutputAnalyzer CompilerUtils
 * @run main HashesTest
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

public class HashesTest {

    private final Path jdkHome = Paths.get(System.getProperty("test.jdk"));
    private final Path stdJmods = jdkHome.resolve("jmods");
    private final Path testSrc = Paths.get(System.getProperty("test.src"));
    private final Path modSrc = testSrc.resolve("src");
    private final Path newModSrc = testSrc.resolve("newsrc");
    private final Path classes = Paths.get("classes");
    private final Path jmods = Paths.get("jmods");

    public static void main(String[] args) throws Exception {
        new HashesTest().run();
    }

    private void run() throws Exception {
        if (!Files.exists(stdJmods)) {
            return;
        }
        Files.createDirectories(jmods);
        Path m1Classes = classes.resolve("m1");
        Path m2Classes = classes.resolve("m2");
        Path m3Classes = classes.resolve("not_matched");
        // build the second module
        compileClasses(modSrc, m2Classes);
        runJmod(m2Classes.toString(), m2Classes.getFileName().toString());

        // build the third module
        compileClasses(modSrc, m3Classes);
        runJmod(m3Classes.toString(), m3Classes.getFileName().toString());

        compileClasses(modSrc, m1Classes, "-mp", jmods.toString());
        runJmod(m1Classes.toString(), m1Classes.getFileName().toString(),
                "--modulepath", jmods.toString(), "--hash-dependencies", "m2");
        runJava(0, "-mp", jmods.toString(), "-m", "m1/org.m1.Main");

        deleteDirectory(m3Classes);
        Files.delete(jmods.resolve("not_matched.jmod"));

        // build the new third module
        compileClasses(newModSrc, m3Classes);
        runJmod(m3Classes.toString(), m3Classes.getFileName().toString());
        runJava(0, "-mp", jmods.toString(), "-m", "m1/org.m1.Main");

        deleteDirectory(m2Classes);
        Files.delete(jmods.resolve("m2.jmod"));

        compileClasses(newModSrc, m2Classes);
        runJmod(m2Classes.toString(), m2Classes.getFileName().toString());

        runJava(1, "-mp", jmods.toString(), "-m", "m1/org.m1.Main");

        if (jdk.tools.jlink.internal.Main.run(new String[]{
                "--modulepath", stdJmods.toString() + File.pathSeparator + jmods.toString(),
                "--addmods", "m1", "--output", "myimage"}, new PrintWriter(System.out)) == 0) {
            throw new AssertionError("Expected failure. rc = 0");
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void runJava(int expectedExitCode, String... args) throws Exception {
        OutputAnalyzer analyzer = ProcessTools.executeTestJava(args);
        if (analyzer.getExitValue() != expectedExitCode) {
            throw new AssertionError("Expected exit code: " + expectedExitCode +
                    ", got: " + analyzer.getExitValue());
        }
    }

    private void compileClasses(Path src, Path output, String... options) throws IOException {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, options);
        Collections.addAll(args, "-d", output.toString());
        args.add(src.toString());
        System.out.println("javac options: " + args.stream().collect(Collectors.joining(" ")));
        if (!CompilerUtils.compile(src.resolve(output.getFileName()), output, options)) {
            throw new AssertionError("Compilation failure. See log.");
        }
    }

    private void runJmod(String cp, String modName, String... options) {
        List<String> args = new ArrayList<>();
        args.add("create");
        Collections.addAll(args, options);
        Collections.addAll(args, "--class-path", cp,
                jmods + File.separator + modName + ".jmod");
        int rc = jdk.tools.jmod.Main.run(args.toArray(new String[args.size()]), System.out);
        System.out.println("jmod options: " + args.stream().collect(Collectors.joining(" ")));
        if (rc != 0) {
            throw new AssertionError("Jmod failed: rc = " + rc);
        }
    }
}
