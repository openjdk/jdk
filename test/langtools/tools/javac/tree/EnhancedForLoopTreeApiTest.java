/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify EnhancedForLoopTree variable and record-pattern accessors
 * @run main EnhancedForLoopTreeApiTest
 */
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.JavacTask;

public class EnhancedForLoopTreeApiTest {
    String[] statements = {
            "for (String word : words) { }",
            "for (Point(var x, var y) : points) { }"
    };

    public static void main(String... args) throws Exception {
        new EnhancedForLoopTreeApiTest().run();
    }

    void run() throws Exception {
        EnhancedForLoopTree variableLoop = parseEnhancedForLoop(statements[0]);
        assertEquals(true, variableLoop.getVariable() != null, "Expected variable declaration in first loop");
        assertEquals(null, variableLoop.getRecordPattern(), "Expected null record pattern in first loop");
        assertEquals(EnhancedForLoopTree.DeclarationKind.VARIABLE,
                variableLoop.getDeclarationKind(),
                "Expected VARIABLE declaration kind in first loop");

        EnhancedForLoopTree patternLoop = parseEnhancedForLoop(statements[1]);
        assertEquals(null, patternLoop.getVariable(), "Expected null variable declaration in second loop");
        assertEquals(true, patternLoop.getRecordPattern() != null, "Expected record pattern in second loop");
        assertEquals(EnhancedForLoopTree.DeclarationKind.PATTERN,
                patternLoop.getDeclarationKind(),
                "Expected PATTERN declaration kind in second loop");
    }

    EnhancedForLoopTree parseEnhancedForLoop(String statementSource) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("No system Java compiler available");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics,
                List.of("--enable-preview", "-source", Integer.toString(Runtime.version().feature())),
                null,
                List.of(new TestSourceFile(statementSource)));

        CompilationUnitTree cut = task.parse().iterator().next();
        List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.getDiagnostics()
                .stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .toList();

        if (!errors.isEmpty()) {
            throw new AssertionError("Unexpected diagnostics: " + errors);
        }

        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        StatementTree statement = method.getBody().getStatements().get(0);

        if (!(statement instanceof EnhancedForLoopTree enhancedForLoop)) {
            throw new AssertionError("Expected enhanced-for, got: " + statement.getKind());
        }

        return enhancedForLoop;
    }

    static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ", expected: " + expected + ", actual: " + actual);
        }
    }

    static class TestSourceFile extends SimpleJavaFileObject {
        private final String source =
                """
                class Test {
                    void test(java.util.List<String> words, java.util.List<Point> points) {
                        #E
                    }
                }
                record Point(int x, int y) { }
                """;

        TestSourceFile(String statement) {
            super(URI.create("mem://Test.java"), JavaFileObject.Kind.SOURCE);
            this.text = source.replace("#E", statement);
        }

        private final String text;

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
