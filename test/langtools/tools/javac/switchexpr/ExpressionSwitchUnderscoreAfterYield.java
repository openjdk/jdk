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

/*
 * @test
 * @bug 8335136
 * @summary Underscore as parameter name in one-parameter functional types fails to compile in yield statement if not enclosed in parentheses
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.ToolBox toolbox.Task
 * @run main ExpressionSwitchUnderscoreAfterYield
 */

import toolbox.*;

import java.nio.file.Path;
import java.util.List;

public class ExpressionSwitchUnderscoreAfterYield extends TestRunner {

    private final ToolBox tb = new ToolBox();

    private final Path ROOT = Path.of(".");

    public ExpressionSwitchUnderscoreAfterYield() {
        super(System.err);
    }

    public static void main(String[] args) throws Exception {
        new ExpressionSwitchUnderscoreAfterYield().runTests();
    }

    protected void runTests() throws Exception {
        runTests(f -> {
            if (f.getName().endsWith("_ShouldFailToCompile")) {
                return new Object[]{
                        List.of(
                                FailParams.UNDERSCORE_YIELDED,
                                FailParams.ASSIGNMENT_TO_UNDERSCORE_IN_YIELD
                        )
                };
            }
            return new Object[0];
        });
    }

    @Test
    public void testUnderscoreAsParameterNameInLambda_ShouldCompileFine() throws Exception {
        var code = """
                        import java.util.function.*;
                        \s
                        public class Test {
                            public static void main(String[] args) {
                                Consumer<Object> result = switch (1) {
                                    case 1 -> {
                                        yield _ -> {};
                                    }
                                    default -> null;
                                };
                            }
                        }
                        """;
        tb.writeJavaFiles(ROOT, code);
        new toolbox.JavacTask(tb)
                .files(tb.findJavaFiles(ROOT))
                .run(Task.Expect.SUCCESS);
    }

    public record FailParams(String code, List<String> expectedDiagnosticMessage) {
        public static FailParams UNDERSCORE_YIELDED = new FailParams(
                """
                        public class Test {
                            public static void main(String[] args) {
                                Object result = switch (1) {
                                    case 1 -> {
                                        yield _;
                                    }
                                    default -> null;
                                };
                            }
                        }
                        """,
                List.of("Test.java:5:23: compiler.err.use.of.underscore.not.allowed.non.variable", "1 error")
        );

        public static FailParams ASSIGNMENT_TO_UNDERSCORE_IN_YIELD = new FailParams(
                """
                        public class Test {
                            public static void main(String[] args) {
                                Object result = switch (1) {
                                    case 1 -> {
                                        yield _ = 1;
                                    }
                                    default -> null;
                                };
                            }
                        }
                        """,
                List.of("Test.java:5:23: compiler.err.use.of.underscore.not.allowed.non.variable", "1 error")
        );
    }

    @Test
    public void testUnderscoreAsParameterNameInLambda_ShouldFailToCompile(List<FailParams> params) throws Exception {
        for (var param : params) {
            tb.writeJavaFiles(ROOT, param.code);
            Task.Result result = new JavacTask(tb)
                    .options("-XDrawDiagnostics")
                    .files(tb.findJavaFiles(ROOT))
                    .run(Task.Expect.FAIL);
            tb.checkEqual(param.expectedDiagnosticMessage, result.getOutputLines(Task.OutputKind.DIRECT));
        }
    }

}
