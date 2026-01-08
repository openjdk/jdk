/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8359412
 * @summary Test template generation with Expressions.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main template_framework.tests.TestExpression
 */

package template_framework.tests;

import java.util.List;
import java.util.Set;

import compiler.lib.template_framework.DataName;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.Expression;

/**
 * This tests the use of the {@link Expression} from the template library. This is
 * not a tutorial about how to use Expressions, rather we produce deterministic
 * output to be able to compare the generated strings to expected strings.
 *
 * If you are interested in how to use {@link Expression}s, see {@code examples/TestExpressions.java}.
 */
public class TestExpression {
    // Interface for failing tests.
    interface FailingTest {
        void run();
    }

    // We define our own types, so that we can check if subtyping works right.
    public record MyType(String name) implements CodeGenerationDataNameType {
        @Override
        public Object con() {
            return "<" + name() + ">";
        }

        @Override
        public boolean isSubtypeOf(DataName.Type other) {
            return other instanceof MyType(String n) && name().startsWith(n);
        }

        @Override
        public String toString() { return name(); }
    }
    private static final MyType myTypeA  = new MyType("MyTypeA");
    private static final MyType myTypeA1 = new MyType("MyTypeA1");
    private static final MyType myTypeB  = new MyType("MyTypeB");

    public static void main(String[] args) {
        // The following tests all pass, i.e. have no errors during rendering.
        testAsToken();
        testNest();
        testNestRandomly();
        testInfo();

        // The following tests should all fail, with an expected exception and message.
        expectIllegalArgumentException(() -> testFailingAsToken1(), "Wrong number of arguments: expected: 2 but got: 1");
        expectIllegalArgumentException(() -> testFailingAsToken2(), "Wrong number of arguments: expected: 2 but got: 3");
        expectIllegalArgumentException(() -> testFailingNest1(), "Cannot nest expressions because of mismatched types.");
    }

    public static void testAsToken() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, "]");
        Expression e2 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeB, "]");
        Expression e3 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeB, ",", myTypeA1, "]");
        Expression e4 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeB, ",", myTypeA1, ",", myTypeA, "]");

        var template = Template.make(() -> scope(
            "xx", e1.toString(), "yy\n",
            "xx", e2.toString(), "yy\n",
            "xx", e3.toString(), "yy\n",
            "xx", e4.toString(), "yy\n",
            "xx", e1.asToken(List.of("a")), "yy\n",
            "xx", e2.asToken(List.of("a", "b")), "yy\n",
            "xx", e3.asToken(List.of("a", "b", "c")), "yy\n",
            "xx", e4.asToken(List.of("a", "b", "c", "d")), "yy\n"
        ));

        String expected =
            """
            xxExpression["[", MyTypeA, "]"]yy
            xxExpression["[", MyTypeA, ",", MyTypeB, "]"]yy
            xxExpression["[", MyTypeA, ",", MyTypeB, ",", MyTypeA1, "]"]yy
            xxExpression["[", MyTypeA, ",", MyTypeB, ",", MyTypeA1, ",", MyTypeA, "]"]yy
            xx[a]yy
            xx[a,b]yy
            xx[a,b,c]yy
            xx[a,b,c,d]yy
            """;
        String code = template.render();
        checkEQ(code, expected);
    }

    public static void testFailingAsToken1() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeB, "]");
        e1.asToken(List.of("a"));
    }

    public static void testFailingAsToken2() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeB, "]");
        e1.asToken(List.of("a", "b", "c"));
    }

    public static void testNest() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, "]");
        Expression e2 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeB, "]");
        Expression e3 = Expression.make(myTypeA1, "[", myTypeA, "]");
        Expression e4 = Expression.make(myTypeA, "[", myTypeA, "x", myTypeA, "y", myTypeA, "z", myTypeA, "]");
        Expression e5 = Expression.make(myTypeA, "[", myTypeA, "u", myTypeA, "v", myTypeA, "w", myTypeA, "]");

        Expression e1e1 = e1.nest(0, e1);
        Expression e2e1 = e2.nest(0, e1);
        Expression e3e1 = e3.nest(0, e1);
        Expression e4e5 = e4.nest(1, e5);

        var template = Template.make(() -> scope(
            "xx", e1e1.toString(), "yy\n",
            "xx", e2e1.toString(), "yy\n",
            "xx", e3e1.toString(), "yy\n",
            "xx", e4e5.toString(), "yy\n",
            "xx", e1e1.asToken(List.of("a")), "yy\n",
            "xx", e2e1.asToken(List.of("a", "b")), "yy\n",
            "xx", e3e1.asToken(List.of("a")), "yy\n",
            "xx", e4e5.asToken(List.of("a", "b", "c", "d", "e", "f", "g")), "yy\n"
        ));

        String expected =
            """
            xxExpression["[[", MyTypeA, "]]"]yy
            xxExpression["[[", MyTypeA, "],", MyTypeB, "]"]yy
            xxExpression["[[", MyTypeA, "]]"]yy
            xxExpression["[", MyTypeA, "x[", MyTypeA, "u", MyTypeA, "v", MyTypeA, "w", MyTypeA, "]y", MyTypeA, "z", MyTypeA, "]"]yy
            xx[[a]]yy
            xx[[a],b]yy
            xx[[a]]yy
            xx[ax[bucvdwe]yfzg]yy
            """;
        String code = template.render();
        checkEQ(code, expected);
    }

    public static void testNestRandomly() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, "]");
        Expression e2 = Expression.make(myTypeA, "(", myTypeA, ")");
        Expression e3 = Expression.make(myTypeB, "{", myTypeA, "}");
        Expression e4 = Expression.make(myTypeA1, "<", myTypeA, ">");
        Expression e5 = Expression.make(myTypeA, "[", myTypeB, "]");

        Expression e1e2 = e1.nestRandomly(List.of(e2));
        Expression e1ex = e1.nestRandomly(List.of(e3, e2, e3));
        Expression e1e4 = e1.nestRandomly(List.of(e3, e4, e3));
        Expression e1ey = e1.nestRandomly(List.of(e3, e3));

        // 5-deep nesting of e1
        Expression deep1 = Expression.nestRandomly(myTypeA, List.of(e1, e3), 5);
        // Alternating pattern
        Expression deep2 = Expression.nestRandomly(myTypeA, List.of(e5, e3), 5);

        var template = Template.make(() -> scope(
            "xx", e1e2.toString(), "yy\n",
            "xx", e1ex.toString(), "yy\n",
            "xx", e1e4.toString(), "yy\n",
            "xx", e1ey.toString(), "yy\n",
            "xx", deep1.toString(), "yy\n",
            "xx", deep2.toString(), "yy\n",
            "xx", e1e2.asToken(List.of("a")), "yy\n",
            "xx", e1ex.asToken(List.of("a")), "yy\n",
            "xx", e1e4.asToken(List.of("a")), "yy\n",
            "xx", e1ey.asToken(List.of("a")), "yy\n",
            "xx", deep1.asToken(List.of("a")), "yy\n",
            "xx", deep2.asToken(List.of("a")), "yy\n"
        ));

        String expected =
            """
            xxExpression["[(", MyTypeA, ")]"]yy
            xxExpression["[(", MyTypeA, ")]"]yy
            xxExpression["[<", MyTypeA, ">]"]yy
            xxExpression["[", MyTypeA, "]"]yy
            xxExpression["[[[[[", MyTypeA, "]]]]]"]yy
            xxExpression["[{[{[", MyTypeB, "]}]}]"]yy
            xx[(a)]yy
            xx[(a)]yy
            xx[<a>]yy
            xx[a]yy
            xx[[[[[a]]]]]yy
            xx[{[{[a]}]}]yy
            """;
        String code = template.render();
        checkEQ(code, expected);
    }

    public static void testFailingNest1() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, "]");
        Expression e2 = Expression.make(myTypeB, "[", myTypeA, "]");
        Expression e1e2 = e1.nest(0, e2);
    }

    public static void testInfo() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, "]");
        Expression e2 = Expression.make(myTypeA, "(", myTypeA, ")");
        Expression e3 = Expression.make(myTypeA, "<", myTypeA, ">", new Expression.Info().withExceptions(Set.of("E1")));
        Expression e4 = Expression.make(myTypeA, "+", myTypeA, "-", new Expression.Info().withExceptions(Set.of("E2")));
        Expression e5 = Expression.make(myTypeA, "x", myTypeA, "y", new Expression.Info().withNondeterministicResult());
        Expression e6 = Expression.make(myTypeA, "u", myTypeA, "v", new Expression.Info().withNondeterministicResult());
        checkInfo(e1, Set.of(), true);
        checkInfo(e2, Set.of(), true);
        checkInfo(e3, Set.of("E1"), true);
        checkInfo(e4, Set.of("E2"), true);
        checkInfo(e1.nest(0, e2), Set.of(), true);
        checkInfo(e2.nest(0, e1), Set.of(), true);
        checkInfo(e1.nest(0, e3), Set.of("E1"), true);
        checkInfo(e3.nest(0, e1), Set.of("E1"), true);
        checkInfo(e3.nest(0, e4), Set.of("E1", "E2"), true);
        checkInfo(e4.nest(0, e3), Set.of("E1", "E2"), true);
        checkInfo(e5, Set.of(), false);
        checkInfo(e6, Set.of(), false);
        checkInfo(e1.nest(0, e5), Set.of(), false);
        checkInfo(e5.nest(0, e1), Set.of(), false);
        checkInfo(e5.nest(0, e6), Set.of(), false);
        checkInfo(e4.nest(0, e3).nest(0, e5), Set.of("E1", "E2"), false);
        checkInfo(e5.nest(0, e4).nest(0, e3), Set.of("E1", "E2"), false);
        checkInfo(e3.nest(0, e5).nest(0, e4), Set.of("E1", "E2"), false);
    }

    public static void checkInfo(Expression e, Set<String> exceptions, boolean isResultDeterministic) {
        if (!e.info.exceptions.equals(exceptions) ||
            e.info.isResultDeterministic != isResultDeterministic) {
            throw new RuntimeException("Info not as expected.");
        }
    }

    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
            System.out.println("\"" + code + "\"");
            System.out.println("\"" + expected + "\"");
            throw new RuntimeException("Template rendering mismatch!");
        }
    }

    public static void expectIllegalArgumentException(FailingTest test, String errorPrefix) {
        try {
            test.run();
            System.out.println("Should have thrown IllegalArgumentException with prefix: " + errorPrefix);
            throw new RuntimeException("Should have thrown!");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().startsWith(errorPrefix)) {
                System.out.println("Should have thrown with prefix: " + errorPrefix);
                System.out.println("got: " + e.getMessage());
                throw new RuntimeException("Prefix mismatch", e);
            }
        }
    }
}
