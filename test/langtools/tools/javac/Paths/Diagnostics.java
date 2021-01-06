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

/*
 * @test
 * @bug 4884487 6295519 6236704 6429613
 * @summary Test for proper diagnostics during path manipulation operations
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jartool/sun.tools.jar
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main/timeout=180 Diagnostics
 */

// Original test: test/langtools/tools/javac/Paths/Diagnostics.sh

import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;

import sun.tools.jar.Main;

import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.Task;

public class Diagnostics extends TestRunner {
    ToolBox tb;

    public Diagnostics() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        Diagnostics d = new Diagnostics();
        d.runTests();
    }

    @Test
    public void testPathManipulation() throws Exception {
        String code = "public class Main{public static void main(String[]a){}}";
        tb.writeFile("Main.java", code);
        List<Path> files = Arrays.asList(Path.of("Main.java"));
        List<String> options = null;
        List<String> expected = null;

        // No warnings unless -Xlint:path is used
        options = Arrays.asList("-XDrawDiagnostics");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-cp", "classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        // Warn for missing elts in user-specified paths
        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-cp", "classes");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: classes", "1 warning");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-Xbootclasspath/p:classes");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: classes", "1 warning");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint", "-Xbootclasspath/a:classes");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: classes", "1 warning");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-endorseddirs", "classes");
        expected = Arrays.asList("- compiler.warn.dir.path.element.not.found: classes",
                "- compiler.warn.source.no.bootclasspath: 8", "2 warnings");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint", "-extdirs", "classes");
        expected = Arrays.asList("- compiler.warn.dir.path.element.not.found: classes",
                "- compiler.warn.source.no.bootclasspath: 8", "2 warnings");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-Xbootclasspath:classes/DefaultBootClassPath");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: classes/DefaultBootClassPath",
                "Main.java:1:8: compiler.err.cant.access: java.lang.Object, (compiler.misc.class.file.not.found: java.lang.Object)",
                "Main.java:1:43: compiler.err.cant.resolve.location: kindname.class, String, , , (compiler.misc.location: kindname.class, Main, null)",
                "2 errors", "1 warning");
        testCompileFail(files, options, expected);

        // No warning for missing elts in "system" paths
        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-J-Djava.endorsed.dirs=classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-J-Djava.ext.dirs=classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-J-Xbootclasspath/p:classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-J-Xbootclasspath/a:classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-J-Xbootclasspath:classes/DefaultBootClassPath");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        // No warning if class path element exists
        tb.createDirectories("classes");

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-cp", "classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-endorseddirs", "classes");
        expected = Arrays.asList("- compiler.warn.source.no.bootclasspath: 8", "1 warning");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-extdirs", "classes");
        expected = Arrays.asList("- compiler.warn.source.no.bootclasspath: 8", "1 warning");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-Xbootclasspath/p:classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-Xbootclasspath/a:classes");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-Xbootclasspath:classes/DefaultBootClassPath");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: classes/DefaultBootClassPath",
                "Main.java:1:8: compiler.err.cant.access: java.lang.Object, (compiler.misc.class.file.not.found: java.lang.Object)",
                "Main.java:1:43: compiler.err.cant.resolve.location: kindname.class, String, , , (compiler.misc.location: kindname.class, Main, null)",
                "2 errors", "1 warning");
        testCompileFail(files, options, expected);

        // test jar, war, zip file
        sun.tools.jar.Main jarGenerator = new sun.tools.jar.Main(System.out, System.err, "jar");
        jarGenerator.run(new String[] {"cf", "classes.jar", "Main.class"});
        tb.copyFile("classes.jar", "./classes.war");
        tb.copyFile("classes.jar", "./classes.zip");

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-cp", "classes.jar");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-cp", "classes.war");
        expected = Arrays.asList("- compiler.warn.unexpected.archive.file: classes.war", "1 warning");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-cp", "classes.zip");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        // Warn if -Xlint is used and if class path element refers to
        // regular file which doesn't look like a zip file
        tb.copyFile("classes.war", "./classes.foo");
        options = Arrays.asList("-XDrawDiagnostics", "-Xlint:path", "-cp", "classes.foo");
        expected = Arrays.asList("- compiler.warn.unexpected.archive.file: classes.foo", "1 warning");
        testCompileOK(files, options, expected);

        // No error if class path element refers to regular file which is not a zip file
        options = Arrays.asList("-XDrawDiagnostics", "-cp", "Main.java");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        // Warn if -Xlint is used and if class path element refers to
        // regular file which is not a zip file
        options = Arrays.asList("-XDrawDiagnostics", "-Xlint", "-cp", "Main.java");
        expected = Arrays.asList("- compiler.warn.invalid.archive.file: Main.java", "1 warning");
        testCompileOK(files, options, expected);

        // Test jar file class path reference recursion
        // Create MANIFEST.MF and classesRefRef.jar
        tb.writeFile("MANIFEST.MF", "Manifest-Version: 1.0\nClass-Path: classesRef.jar\n");
        jarGenerator.run(new String[] {"-c", "-f", "classesRefRef.jar", "-m", "MANIFEST.MF", "Main.class"});

        // Non-existent recursive Class-Path reference gives warning
        options = Arrays.asList("-XDrawDiagnostics", "-classpath", "classesRefRef.jar");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-Xlint", "-classpath", "classesRefRef.jar");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: "
                + Path.of(".").toAbsolutePath().normalize() + "/classesRef.jar", "1 warning");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint", "-Xbootclasspath/p:classesRefRef.jar");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        // Create a bad jar file classesRef.jar
        tb.writeFile("classesRef.jar", Path.of(".").toAbsolutePath().toString());

        // Non-jar file recursive Class-Path reference gives error
        options = Arrays.asList("-XDrawDiagnostics", "-classpath", "classesRefRef.jar");
        expected = Arrays.asList("- compiler.err.error.reading.file: "
                + Path.of(".").toAbsolutePath().normalize()
                + "/classesRef.jar, zip END header not found");
        testCompileFail(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xbootclasspath/a:classesRefRef.jar");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        // Create MANIFEST.MF and classesRef.jar
        tb.writeFile("MANIFEST.MF", "Manifest-Version: 1.0\nClass-Path: classes\n");
        jarGenerator.run(new String[] {"-c", "-f", "classesRef.jar", "-m", "MANIFEST.MF", "Main.class"});

        // Jar file recursive Class-Path reference is OK
        options = Arrays.asList("-XDrawDiagnostics", "-Xlint", "-classpath", "classesRefRef.jar");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint", "-Xbootclasspath/p:classesRefRef.jar");
        expected = Arrays.asList("");
        testCompileOK(files, options, expected);

        // Class-Path attribute followed in extdirs or endorseddirs
        tb.createDirectories("jars");
        tb.copyFile("classesRefRef.jar", "jars/.");

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint", "-extdirs", "jars");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: "
                + Path.of(".").toAbsolutePath().normalize() + "/jars/classesRef.jar",
                "- compiler.warn.source.no.bootclasspath: 8", "2 warnings");
        testCompileOK(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-endorseddirs", "jars");
        expected = Arrays.asList("- compiler.warn.path.element.not.found: "
                + Path.of(".").toAbsolutePath().normalize() + "/jars/classesRef.jar",
                "- compiler.warn.source.no.bootclasspath: 8", "2 warnings");
        testCompileOK(files, options, expected);

        // Create a bad jar file classesRef.jar
        tb.writeFile("jars/classesRef.jar", Path.of(".").toAbsolutePath().toString());

        // Bad Jar file in extdirs and endorseddirs should not be ignored
        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint", "-extdirs", "jars");
        expected = Arrays.asList("- compiler.err.error.reading.file: jars/classesRef.jar, zip END header not found",
                "- compiler.warn.source.no.bootclasspath: 8");
        testCompileFail(files, options, expected);

        options = Arrays.asList("-XDrawDiagnostics", "-source", "8", "-target", "8",
                "-Xlint:path", "-endorseddirs", "jars");
        expected = Arrays.asList("- compiler.err.error.reading.file: jars/classesRef.jar, zip END header not found",
                "- compiler.warn.source.no.bootclasspath: 8");
        testCompileFail(files, options, expected);
    }

    public void testCompileOK(List<Path> files, List<String> options, List<String> expected) {
        testCompile(Task.Expect.SUCCESS, files, options, expected);
    }

    public void testCompileFail(List<Path> files, List<String> options, List<String> expected) {
        testCompile(Task.Expect.FAIL, files, options, expected);
    }

    public void testCompile(Task.Expect result, List<Path> files, List<String> options, List<String> expectedOutput) {
        List<String> output = new JavacTask(tb, Task.Mode.CMDLINE)
                .files(files)
                .options(options)
                .run(result)
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(expectedOutput, output);
    }
}
