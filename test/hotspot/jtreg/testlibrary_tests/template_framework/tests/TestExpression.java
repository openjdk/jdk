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

import compiler.lib.template_framework.DataName;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
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
        // TODO: add some

        // The following tests should all fail, with an expected exception and message.
        // TODO: add some
    }

    public static void testAsToken() {
        Expression e1 = Expression.make(myTypeA, "[", myTypeA, "]");
        Expression e2 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeA, "]");
        Expression e3 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeA, ",", myTypeA, "]");
        Expression e4 = Expression.make(myTypeA, "[", myTypeA, ",", myTypeA, ",", myTypeA, ",", myTypeA, "]");

        var template = Template.make(() -> body(
            "xx", e1.asToken(List.of("a")), "yy\n",
            "xx", e2.asToken(List.of("a", "b")), "yy\n",
            "xx", e3.asToken(List.of("a", "b", "c")), "yy\n",
            "xx", e4.asToken(List.of("a", "b", "c", "d")), "yy\n"
        ));

        String expected =
            """
            xx[a]yy
            xx[a,b]yy
            xx[a,b,c]yy
            xx[a,b,c,d]yy
            """;
        String code = template.render();
        checkEQ(code, expected);
    }

    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
            System.out.println("\"" + code + "\"");
            System.out.println("\"" + expected + "\"");
            throw new RuntimeException("Template rendering mismatch!");
        }
    }
}
