/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8158123 8161906 8162713
 * @summary tests for module declarations
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main ModuleInfoTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class ModuleInfoTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        ModuleInfoTest t = new ModuleInfoTest();
        t.runTests();
    }

    /**
     * Check error message if module declaration not in module-info.java.
     */
    @Test
    public void testModuleDeclNotInModuleJava(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeFile(src.resolve("M.java"), "module M { }");
        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("M.java:1:1: compiler.err.module.decl.sb.in.module-info.java"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that a package private class can be put in module-info.java.
     */
    @Test
    public void testNotModuleDeclInModuleJava_1(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeFile(src.resolve("module-info.java"), "class C { }");
        new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    /**
     * Verify that a public class cannot be put in module-info.java.
     */
    @Test
    public void testNotModuleDeclInModuleJava_2(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeFile(src.resolve("module-info.java"), "public class C { }");
        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:8: compiler.err.class.public.should.be.in.file: C"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that only one module decl can be put in module-info.java.
     */
    @Test
    public void testSingleModuleDecl(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module M1 { } /*...*/ module M2 { }");
        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:14: compiler.err.expected: token.end-of-input"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that missing requires are reported.
     */
    @Test
    public void testRequiresNotFound(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module M1 { requires M2; }");
        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:22: compiler.err.module.not.found: M2"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that missing exports are reported.
     */
    @Test
    public void testExportsNotFound(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module M1 { exports p to M2; }");
        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:26: compiler.err.module.not.found: M2"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that a simple loop is detected.
     */
    @Test
    public void testRequiresSelf(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module M { requires M; }");
        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:21: compiler.err.cyclic.requires: M"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that a multi-module loop is detected.
     */
    @Test
    public void testRequiresLoop(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeFile(src_m1.resolve("module-info.java"), "module m1 { requires m2; }");
        Path src_m2 = src.resolve("m2");
        tb.writeFile(src_m2.resolve("module-info.java"), "module m2 { requires m3; }");
        Path src_m3 = src.resolve("m3");
        tb.writeFile(src_m3.resolve("module-info.java"), "module m3 { requires m1; }");

        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:22: compiler.err.cyclic.requires: m3"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that a multi-module loop is detected.
     */
    @Test
    public void testRequiresTransitiveLoop(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeFile(src_m1.resolve("module-info.java"), "module m1 { requires m2; }");
        Path src_m2 = src.resolve("m2");
        tb.writeFile(src_m2.resolve("module-info.java"), "module m2 { requires transitive m3; }");
        Path src_m3 = src.resolve("m3");
        tb.writeFile(src_m3.resolve("module-info.java"), "module m3 { requires m1; }");

        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:33: compiler.err.cyclic.requires: m3"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that duplicate requires are detected.
     */
    @Test
    public void testDuplicateRequires(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeFile(src_m1.resolve("module-info.java"), "module m1 { }");
        Path src_m2 = src.resolve("m2");
        tb.writeFile(src_m2.resolve("module-info.java"), "module m2 { requires m1; requires m1; }");

        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:35: compiler.err.duplicate.requires: m1"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that duplicate requires are detected.
     */
    @Test
    public void testDuplicateRequiresTransitiveStatic(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeFile(src_m1.resolve("module-info.java"), "module m1 { }");
        Path src_m2 = src.resolve("m2");
        tb.writeFile(src_m2.resolve("module-info.java"), "module m2 { requires transitive m1; requires static m1; }");

        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:53: compiler.err.duplicate.requires: m1"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that duplicate exported packages are detected correctly.
     */
    @Test
    public void testConflictingExports_packages(Path base) throws Exception {
        verifyConflictingExports_packages(base,
                                          "exports p; exports q;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "exports p; exports p;",
                                          "module-info.java:1:32: compiler.err.conflicting.exports: p");
        verifyConflictingExports_packages(base,
                                          "exports p; opens p;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "exports p; exports p to m2;",
                                          "module-info.java:1:32: compiler.err.conflicting.exports: p");
        verifyConflictingExports_packages(base,
                                          "exports p; opens p to m2;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p; exports p;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p; opens p;",
                                          "module-info.java:1:28: compiler.err.conflicting.opens: p");
        verifyConflictingExports_packages(base,
                                          "opens p; exports p to m2;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p; opens p to m2;",
                                          "module-info.java:1:28: compiler.err.conflicting.opens: p");
        verifyConflictingExports_packages(base,
                                          "exports p to m2; exports p;",
                                          "module-info.java:1:38: compiler.err.conflicting.exports: p");
        verifyConflictingExports_packages(base,
                                          "exports p to m2; opens p;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "exports p to m2; exports p to m2;",
                                          "module-info.java:1:43: compiler.err.conflicting.exports.to.module: m2");
        verifyConflictingExports_packages(base,
                                          "exports p to m2; opens p to m2;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p to m2; exports p;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p to m2; opens p;",
                                          "module-info.java:1:34: compiler.err.conflicting.opens: p");
        verifyConflictingExports_packages(base,
                                          "opens p to m2; exports p to m2;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p to m2; opens p to m2;",
                                          "module-info.java:1:34: compiler.err.conflicting.opens: p");
        verifyConflictingExports_packages(base,
                                          "exports p to m2; exports p to m3;",
                                          "module-info.java:1:38: compiler.err.conflicting.exports: p");
        verifyConflictingExports_packages(base,
                                          "exports p to m2; opens p to m3;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p to m2; exports p to m3;",
                                          null);
        verifyConflictingExports_packages(base,
                                          "opens p to m2; opens p to m3;",
                                          "module-info.java:1:34: compiler.err.conflicting.opens: p");
    }

    private void verifyConflictingExports_packages(Path base, String code, String expected) throws Exception {
        Files.createDirectories(base);
        tb.cleanDirectory(base);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                          "module m1 { " + code + " }",
                          "package p; public class P {}",
                          "package q; public class Q {}");
        tb.writeJavaFiles(src.resolve("m2"),
                          "module m2 { requires m1; }");
        tb.writeJavaFiles(src.resolve("m3"),
                          "module m3 { requires m1; }");

        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(expected != null ? Task.Expect.FAIL : Task.Expect.SUCCESS)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (expected != null && !log.contains(expected))
            throw new Exception("expected output not found, actual output: " + log);
    }

    /**
     * Verify that duplicate exported packages are detected.
     */
    @Test
    public void testConflictingExports_modules(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeFile(src_m1.resolve("module-info.java"), "module m1 { }");
        Path src_m2 = src.resolve("m2");
        tb.writeFile(src_m2.resolve("module-info.java"), "module m2 { exports p to m1, m1; }");

        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:30: compiler.err.conflicting.exports.to.module: m1"))
            throw new Exception("expected output not found");
    }

    /**
     * Verify that annotations are not permitted at
     * any of the module names or the package names.
     */
    @Test
    public void testAnnotations(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1.sub");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String code = "module @m1.@sub { " +
                "requires @p1.@p2; " +
                "exports @p1.@p2; " +
                "exports @p1.@p2 to @m2.@sub; " +
                "exports @p1.@p2 to @m2.@sub, @m3.@sub; " +
                "uses @p1.@Interface; " +
                "provides @p1.@Interface with @p2.@Concrete; " +
                "}";
        String[] splittedCode = code.split("@");
        int length = splittedCode.length;
        String anno = "@Anno ";

        for (int i = 1; i < length; i++) {
            String preAnno = String.join("", Arrays.copyOfRange(splittedCode, 0, i));
            String postAnno = String.join("", Arrays.copyOfRange(splittedCode, i, length));
            String moduleInfo = preAnno + anno + postAnno;
            tb.writeFile(src_m1.resolve("module-info.java"), moduleInfo);

            String log = new JavacTask(tb)
                    .options("-XDrawDiagnostics", "--module-source-path", src.toString())
                    .outdir(classes)
                    .files(findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutput(Task.OutputKind.DIRECT);

            String expect_prefix = "(?s)^module\\-info\\.java:\\d+:\\d+: ";
            String expect_message = "compiler\\.err\\.expected: token\\.identifier";
            String expect_suffix = ".*";
            String expect = expect_prefix + expect_message + expect_suffix;
            if (!log.matches(expect))
                throw new Exception("expected output not found for: " + moduleInfo + "; actual: " + log);
        }
    }
}
