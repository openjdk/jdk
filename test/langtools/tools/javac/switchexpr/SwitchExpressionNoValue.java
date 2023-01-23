/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8276836
 * @summary Check that switch expression with no value does not crash the compiler.
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      java.base/jdk.internal
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @build combo.ComboTestHelper
 * @compile SwitchExpressionNoValue.java
 * @run main/othervm SwitchExpressionNoValue
 */

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask;
import combo.ComboTestHelper;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Objects;
import javax.tools.Diagnostic;
import toolbox.ToolBox;

import javax.tools.JavaFileObject;

public class SwitchExpressionNoValue extends ComboInstance<SwitchExpressionNoValue> {
    protected ToolBox tb;

    SwitchExpressionNoValue() {
        super();
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<SwitchExpressionNoValue>()
                .withDimension("SWITCH_EXPRESSION", (x, method) -> x.switchExpression = method, SwitchExpression.values())
                .withDimension("EXPRESSION", (x, expression) -> x.expression = expression, Expression.values())
                .withDimension("CONTEXT", (x, context) -> x.context = context, Context.values())
                .withFilter(test -> test.context.expressionType == test.expression.expressionType &&
                                    test.context.expressionType == test.switchExpression.expressionType)
                .run(SwitchExpressionNoValue::new);
    }

    private SwitchExpression switchExpression;
    private Expression expression;
    private Context context;

    private static final String MAIN_TEMPLATE =
            """
            public class Test {
                public static void doTest() {
                    #{CONTEXT}
                }
                static int i;
                static int[] arr = new int[0];
                static void m(int i, Object o, int j) {}
            }
            """;

    @Override
    protected void doWork() throws Throwable {
        Path base = Paths.get(".");

        ComboTask task = newCompilationTask()
                .withSourceFromTemplate(MAIN_TEMPLATE, pname -> switch (pname) {
                        case "SWITCH_EXPRESSION" -> switchExpression;
                        case "EXPRESSION" -> expression;
                        case "CONTEXT" -> context;
                        default -> throw new UnsupportedOperationException(pname);
                    })
                .withOption("--enable-preview")
                .withOption("-source")
                .withOption(String.valueOf(Runtime.version().feature()));

        task.generate(result -> {
            try {
                if (result.hasErrors()) {
                    throw new AssertionError(result.diagnosticsForKind(Diagnostic.Kind.ERROR));
                }
                Iterator<? extends JavaFileObject> filesIt = result.get().iterator();
                JavaFileObject file = filesIt.next();
                if (filesIt.hasNext()) {
                    throw new IllegalStateException("More than one classfile returned!");
                }
                byte[] data = file.openInputStream().readAllBytes();
                ClassLoader inMemoryLoader = new ClassLoader() {
                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                        if ("Test".equals(name)) {
                            return defineClass(name, data, 0, data.length);
                        }
                        return super.findClass(name);
                    }
                };
                Class<?> test = Class.forName("Test", false, inMemoryLoader);
                try {
                java.lang.reflect.Method doTest = test.getDeclaredMethod("doTest");
                    doTest.invoke(null);
                    throw new AssertionError("No expected exception!");
                } catch (Throwable ex) {
                    while (ex instanceof InvocationTargetException) {
                        ex = ((InvocationTargetException) ex).getCause();
                    }
                    if (ex instanceof RuntimeException && "test".equals(ex.getMessage())) {
                        //OK
                    } else {
                        throw new IllegalStateException(ex);
                    }
                }
            } catch (Throwable ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private void assertEquals(Object o1, Object o2) {
        if (!Objects.equals(o1, o2)) {
            throw new AssertionError();
        }
    }

    public enum SwitchExpression implements ComboParameter {
        INT("switch (i) { case 0 -> throw new RuntimeException(\"test\"); default -> {if (true) throw new RuntimeException(\"test\"); else yield 0; } }", ExpressionType.INT),
        BOOLEAN("switch (i) { case 0 -> throw new RuntimeException(\"test\"); default -> {if (true) throw new RuntimeException(\"test\"); else yield true; } }", ExpressionType.BOOLEAN)
        ;
        private final String expression;
        private final ExpressionType expressionType;

        private SwitchExpression(String expression, ExpressionType expressionType) {
            this.expression = expression;
            this.expressionType = expressionType;
        }

        @Override
        public String expand(String optParameter) {
            return expression;
        }
    }

    public enum Expression implements ComboParameter {
        SIMPLE("#{SWITCH_EXPRESSION}", ExpressionType.INT),
        BINARY_SIMPLE("3 + #{SWITCH_EXPRESSION}", ExpressionType.INT),
        BINARY_LONGER1("3 + #{SWITCH_EXPRESSION} + #{SWITCH_EXPRESSION} + #{SWITCH_EXPRESSION}", ExpressionType.INT),
        BINARY_LONGER2("3 + switch (0) { default -> 0; } + #{SWITCH_EXPRESSION} + #{SWITCH_EXPRESSION}", ExpressionType.INT),
        BINARY_LONGER3("3 + #{SWITCH_EXPRESSION} + switch (0) { default -> 0; } + #{SWITCH_EXPRESSION}", ExpressionType.INT),
        BINARY_BOOLEAN("\"\".isEmpty() && #{SWITCH_EXPRESSION}", ExpressionType.BOOLEAN),
        ;
        private final String expression;
        private final ExpressionType expressionType;

        private Expression(String expression, ExpressionType expressionType) {
            this.expression = expression;
            this.expressionType = expressionType;
        }

        @Override
        public String expand(String optParameter) {
            return expression;
        }
    }

    public enum Context implements ComboParameter {
        ASSIGNMENT("i = #{EXPRESSION};", ExpressionType.INT),
        COMPOUND_ASSIGNMENT("i += #{EXPRESSION};", ExpressionType.INT),
        METHOD_INVOCATION("m(0, #{EXPRESSION}, 0);", ExpressionType.INT),
        ARRAY_DEREF("arr[#{EXPRESSION}] = 0;", ExpressionType.INT),
        IF("if (#{EXPRESSION});", ExpressionType.BOOLEAN),
        WHILE("while (#{EXPRESSION});", ExpressionType.BOOLEAN)
        ;
        private final String code;
        private final ExpressionType expressionType;
        private Context(String code, ExpressionType expressionType) {
            this.code = code;
            this.expressionType = expressionType;
        }
        @Override
        public String expand(String optParameter) {
            return code;
        }
    }

    enum ExpressionType {
        INT,
        BOOLEAN;
    }
}
