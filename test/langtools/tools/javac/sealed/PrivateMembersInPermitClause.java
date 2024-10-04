/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8338981
 * @summary Access to private classes should be permitted inside the permits clause of the enclosing top-level class
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *                     jdk.compiler/com.sun.tools.javac.main
 *                     jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.Task
 * @run main PrivateMembersInPermitClause
 */

import java.nio.file.Path;
import java.util.Objects;
import toolbox.Task;
import java.util.List;

public class PrivateMembersInPermitClause extends toolbox.TestRunner {

    private final toolbox.ToolBox tb;

    public PrivateMembersInPermitClause() {
        super(System.err);
        tb = new toolbox.ToolBox();
    }

    public static void main(String... args) throws Exception {
        new PrivateMembersInPermitClause().runTests();
    }

    public void runTests() throws Exception {
        runTests(_ -> new Object[] {});
    }

    @Test
    public void givenPrivateClassInPermitsClause_whenCompiling_thenShouldCompile() throws Exception {
        var root = Path.of("src");
        tb.writeJavaFiles(root,
            """
            sealed class S permits S.A {
                private static final class A extends S {}
            }
            """
        );

        new toolbox.JavacTask(tb)
            .files(root.resolve("S.java"))
            .run(toolbox.Task.Expect.SUCCESS);
    }

    @Test
    public void givenPrivateClassOfOtherTopLevelClassInPermitsClause_whenCompiling_thenShouldFail() throws Exception {
        var root = Path.of("src");
        tb.writeJavaFiles(root,
            """
            public class S {
                private static final class A extends S {}
            }
            """,
            """
            public sealed class T permits S.A {
                private static final class A {}
            }
            """
        );
        var expectedErrors = List.of(
            "T.java:1:32: compiler.err.report.access: S.A, private, S",
            "1 error"
        );

        var compileErrors = new toolbox.JavacTask(tb)
            .files(root.resolve("S.java"), root.resolve("T.java"))
            .options("-XDrawDiagnostics")
            .run(toolbox.Task.Expect.FAIL)
            .getOutputLines(Task.OutputKind.DIRECT);

        if (!Objects.equals(compileErrors, expectedErrors)) {
            throw new AssertionError("Expected errors: " + expectedErrors + ", but got: " + compileErrors);
        }

    }

    @Test
    public void givenPrivateClassInPermitsClauseOfInnerClass_whenCompiling_thenShouldCompile() throws Exception {
        var root = Path.of("src");
        tb.writeJavaFiles(root,
            """
            public sealed class S permits S.T.A {
                static class T {
                    private static final class A extends S {}
                }
            }
            """
        );

        new toolbox.JavacTask(tb)
            .files(root.resolve("S.java"))
            .run(toolbox.Task.Expect.SUCCESS);
    }

    @Test
    public void givenPrivateClassInPermitsClauseContainedInSiblingPrivateInnerClass_whenCompiling_thenShouldCompile() throws Exception {
        var root = Path.of("src");
        tb.writeJavaFiles(root,
            """
            public class S {
                private static class A {
                    private static class B extends C.D {}
                }
                private static class C {
                    private static class D {}
                }
            }
            """
        );

        new toolbox.JavacTask(tb)
            .files(root.resolve("S.java"))
            .run(toolbox.Task.Expect.SUCCESS);
    }

    @Test
    public void givenPrivateClassInPermitsClause_whenThanCompilingOtherClassThatReferencesPrivateClassInPermitsClause_thenShouldFail() throws Exception {
        var root = Path.of("src");
        tb.writeJavaFiles(root,
            """
            sealed class S permits S.A {
                private static final class A extends S {}
            }
            """,
            """
            sealed class T permits S.A {
            }
            """
        );

        var expectedErrors = List.of(
            "T.java:1:25: compiler.err.report.access: S.A, private, S",
            "1 error"
        );

        var compileErrors = new toolbox.JavacTask(tb)
            .files(root.resolve("S.java"), root.resolve("T.java"))
            .options("-XDrawDiagnostics")
            .run(toolbox.Task.Expect.FAIL)
            .getOutputLines(Task.OutputKind.DIRECT);

        if (!Objects.equals(compileErrors, expectedErrors)) {
            throw new AssertionError("Expected errors: " + expectedErrors + ", but got: " + compileErrors);
        }
    }
}
