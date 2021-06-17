/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8263926
 * @summary JavacFileManager.hasExplicitLocation fails with NPE while compiling
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jartool/sun.tools.jar
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.JarTask
 * @run main SourceLocationNotExist
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import java.util.List;
import java.util.Arrays;

import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.JarTask;
import toolbox.Task;

public class SourceLocationNotExist extends TestRunner {
    ToolBox tb;

    private static final String libCode = """
            package test;
            public class TestUnnamedModule {
            }
            """;

    private static final String moduleInfoWithoutRequires = """
            module use {
            }
            """;

    private static final String moduleInfoWithRequires = """
            module use {
                requires test;
            }
            """;

    private static final String testUseCode = """
            import test.TestUnnamedModule;
            public class TestUse {
            }
            """;

    public static void main(String[] args) throws Exception {
        SourceLocationNotExist test = new SourceLocationNotExist();
        test.runTests();
    }

    public SourceLocationNotExist() {
        super(System.err);
        tb = new ToolBox();
    }

    @Test
    public void testSourceLocationNotExist() throws Exception {
        // Compile TestUnnamedModule.java into package test.
        Path rootPath = Paths.get(".");
        Path testPath = Paths.get("test");
        tb.writeJavaFiles(rootPath, libCode);
        new JavacTask(tb)
                .files(tb.findJavaFiles(testPath))
                .outdir(".")
                .run();

        // Update the last modified time of the file TestUnnamedModule.java.
        FileTime classFileTime = Files.getLastModifiedTime(testPath.resolve("TestUnnamedModule.class"));
        FileTime javaFileTime = FileTime.fromMillis(classFileTime.toMillis() + 100000);
        Files.setLastModifiedTime(testPath.resolve("TestUnnamedModule.java"), javaFileTime);

        // Construct the jar file: test.jar.
        new JarTask(tb).run("cf", "test.jar",
                            testPath.resolve("TestUnnamedModule.class").toString(),
                            testPath.resolve("TestUnnamedModule.java").toString());

        // Construct the module `use` without requires.
        Path usePath = Paths.get("use");
        tb.writeJavaFiles(usePath, testUseCode, moduleInfoWithoutRequires);
        List<Path> withoutRequires = Arrays.asList(tb.findJavaFiles(usePath));

        // Compile the module `use` without requires by using different options.
        // Use `-classpath` instead of `--module-path`.
        List<String> options = Arrays.asList("-classpath", "test.jar", "-d", "use", "-sourcepath", "use", "-XDrawDiagnostics");
        List<String> expected = Arrays.asList(
                "TestUnnamedModule.java:1:1: compiler.err.file.sb.on.source.or.patch.path.for.module",
                "TestUse.java:1:12: compiler.err.doesnt.exist: test",
                "2 errors");
        // Before patch JDK-8263926, this compilation make compiler crash with NPE.
        // After patch JDK-8263926, the compiler outputs the corresponding error messages.
        testCompileFail(withoutRequires, options, expected);
        // Use `--module-path` instead of `-classpath`.
        options = Arrays.asList("--module-path", "test.jar", "-d", "use", "-sourcepath", "use", "-XDrawDiagnostics");
        expected = Arrays.asList(
                "TestUse.java:1:8: compiler.err.package.not.visible: test, " +
                        "(compiler.misc.not.def.access.does.not.read: use, test, test)",
                "1 error");
        testCompileFail(withoutRequires, options, expected);

        // Remove the module `use`.
        Files.walk(usePath).map(Path::toFile).forEach(File::delete);

        // Construct the module `use` with requires.
        usePath = Paths.get("use");
        tb.writeJavaFiles(usePath, moduleInfoWithRequires, testUseCode);
        List<Path> withRequires = Arrays.asList(tb.findJavaFiles(usePath));

        // Compile the module `use` with requires by using different options.
        // Use `-classpath` instead of `--module-path`.
        options = Arrays.asList("-classpath", "test.jar", "-d", "use", "-sourcepath", "use", "-XDrawDiagnostics");
        expected = Arrays.asList("module-info.java:2:14: compiler.err.module.not.found: test", "1 error");
        testCompileFail(withRequires, options, expected);
        // Use `--module-path` instead of `-classpath`.
        options = Arrays.asList("--module-path", "test.jar", "-d", "use", "-sourcepath", "use", "-XDrawDiagnostics");
        expected = Arrays.asList("");
        testCompileOK(withRequires, options, expected);
    }

    public void testCompileOK(List<Path> files, List<String> options, List<String> expected) {
        testCompile(Task.Expect.SUCCESS, files, options, expected);
    }

    public void testCompileFail(List<Path> files, List<String> options, List<String> expected) {
        testCompile(Task.Expect.FAIL, files, options, expected);
    }

    public void testCompile(Task.Expect result, List<Path> files, List<String> options, List<String> expectedOutput) {
        List<String> output = new JavacTask(tb)
                .files(files)
                .options(options)
                .run(result)
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(expectedOutput, output);
    }
}
