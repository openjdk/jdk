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
 * @summary Test some basic Template instantiations. We do not necessarily generate correct
 *          java code, we just test that the code generation deterministically creates the
 *          expected String.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.tests.TestTemplate
 */

package template_framework.tests;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Random;

import jdk.test.lib.Utils;

import compiler.lib.template_framework.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.intoHook;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.let;

public class TestTemplate {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        testSingleLine();
        testMultiLine();
        testBodyTokens();
        testWithOneArguments();
        testWithTwoArguments();
        testRecursive();
        testHookSimple();
        testHookNested();
        testHookWithNestedTemplates();
        testHookRecursion();
        testNames();
        testLet();
        testSelector();

        //testClassInstantiator();
        //testRepeat();
        //testDispatch();
        //testClassInstantiatorAndDispatch();
        //testChoose();
        //testFieldsAndVariables();
        //testFieldsAndVariablesDispatch();
        //testIntCon();
        //testLongCon();
        //testFuel();
        //testRecursiveCalls();
    }

    public static void testSingleLine() {
        var template = Template.make(() -> body("Hello World!"));
        String code = template.withArgs().render();
        checkEQ(code, "Hello World!");
    }

    public static void testMultiLine() {
        var template = Template.make(() -> body(
            """
            Code on more
            than a single line
            """
        ));
        String code = template.withArgs().render();
        String expected =
            """
            Code on more
            than a single line
            """;
        checkEQ(code, expected);
    }

    public static void testBodyTokens() {
        // We can fill the body with Objects of different types, and they get concatenated.
        // Lists get flattened into the body.
        var template = Template.make(() -> body(
            "start ",
            Integer.valueOf(1),
            Long.valueOf(2),
            Double.valueOf(3.4),
            Float.valueOf(5.6f),
            List.of(" ", 1, " and ", 2),
            " end"
        ));
        String code = template.withArgs().render();
        checkEQ(code, "start 123.45.6 1 and 2 end");
    }

    public static void testWithOneArguments() {
        // Capture String argument via String name.
        var template1 = Template.make("a", (String a) -> body("start #a end"));
        checkEQ(template1.withArgs("x").render(), "start x end");
        checkEQ(template1.withArgs("a").render(), "start a end");
        checkEQ(template1.withArgs("" ).render(), "start  end");

        // Capture String argument via typed lambda argument.
        var template2 = Template.make("a", (String a) -> body("start ", a, " end"));
        checkEQ(template2.withArgs("x").render(), "start x end");
        checkEQ(template2.withArgs("a").render(), "start a end");
        checkEQ(template2.withArgs("" ).render(), "start  end");

        // Capture Integer argument via String name.
        var template3 = Template.make("a", (Integer a) -> body("start #a end"));
        checkEQ(template3.withArgs(0  ).render(), "start 0 end");
        checkEQ(template3.withArgs(22 ).render(), "start 22 end");
        checkEQ(template3.withArgs(444).render(), "start 444 end");

        // Capture Integer argument via templated lambda argument.
        var template4 = Template.make("a", (Integer a) -> body("start ", a, " end"));
        checkEQ(template4.withArgs(0  ).render(), "start 0 end");
        checkEQ(template4.withArgs(22 ).render(), "start 22 end");
        checkEQ(template4.withArgs(444).render(), "start 444 end");
    }

    public static void testWithTwoArguments() {
        // Capture 2 String arguments via String names.
        var template1 = Template.make("a1", "a2", (String a1, String a2) -> body("start #a1 #a2 end"));
        checkEQ(template1.withArgs("x", "y").render(), "start x y end");
        checkEQ(template1.withArgs("a", "b").render(), "start a b end");
        checkEQ(template1.withArgs("",  "" ).render(), "start   end");

        // Capture 2 String arguments via typed lambda arguments.
        var template2 = Template.make("a1", "a2", (String a1, String a2) -> body("start ", a1, " ", a2, " end"));
        checkEQ(template2.withArgs("x", "y").render(), "start x y end");
        checkEQ(template2.withArgs("a", "b").render(), "start a b end");
        checkEQ(template2.withArgs("",  "" ).render(), "start   end");

        // Capture 2 Integer arguments via String names.
        var template3 = Template.make("a1", "a2", (Integer a1, Integer a2) -> body("start #a1 #a2 end"));
        checkEQ(template3.withArgs(0,   1  ).render(), "start 0 1 end");
        checkEQ(template3.withArgs(22,  33 ).render(), "start 22 33 end");
        checkEQ(template3.withArgs(444, 555).render(), "start 444 555 end");

        // Capture 2 Integer arguments via templated lambda arguments.
        var template4 = Template.make("a1", "a2", (Integer a1, Integer a2) -> body("start ", a1, " ", a2, " end"));
        checkEQ(template4.withArgs(0,   1  ).render(), "start 0 1 end");
        checkEQ(template4.withArgs(22,  33 ).render(), "start 22 33 end");
        checkEQ(template4.withArgs(444, 555).render(), "start 444 555 end");
    }

    public static void testRecursive() {
        var template1 = Template.make(() -> body("proton"));

        var template2 = Template.make("a1", "a2", (String a1, String a2) -> body(
            "electron #a1\n",
            "neutron #a2\n"
        ));

        var template3 = Template.make("a1", "a2", (String a1, String a2) -> body(
            "Universe ", template1.withArgs(), " {\n",
                template2.withArgs("up", "down"),
                template2.withArgs(a1, a2),
            "}\n"
        ));

        var template4 = Template.make(() -> body(
            template3.withArgs("low", "high"),
            "{\n",
                template3.withArgs("42", "24"),
            "}"
        ));

        String code = template4.withArgs().render();
        String expected =
            """
            Universe proton {
            electron up
            neutron down
            electron low
            neutron high
            }
            {
            Universe proton {
            electron up
            neutron down
            electron 42
            neutron 24
            }
            }""";
        checkEQ(code, expected);
    }

    public static void testHookSimple() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> body("Hello\n"));

        var template2 = Template.make(() -> body(
            "{\n",
            hook1.set(
                "World\n",
                intoHook(hook1, template1.withArgs())
	    ),
            "}"
        ));

        String code = template2.withArgs().render();
        String expected =
            """
            {
            Hello
            World
            }""";
        checkEQ(code, expected);
    }

    public static void testHookNested() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("a", (String a) -> body("x #a x\n"));

        // Test nested use of hooks in the same template.
        var template2 = Template.make(() -> body(
            "{\n",
            hook1.set(), // empty
            "zero\n",
            hook1.set(
                template1.withArgs("one"),
                template1.withArgs("two"),
                intoHook(hook1, template1.withArgs("intoHook1a")),
                intoHook(hook1, template1.withArgs("intoHook1b")),
                template1.withArgs("three"),
                hook1.set(
                    template1.withArgs("four"),
                    intoHook(hook1, template1.withArgs("intoHook1c")),
                    template1.withArgs("five")
	        ),
                template1.withArgs("six"),
                hook1.set(), // empty
                template1.withArgs("seven"),
                intoHook(hook1, template1.withArgs("intoHook1d")),
                template1.withArgs("eight"),
                hook1.set(
                    template1.withArgs("nine"),
                    intoHook(hook1, template1.withArgs("intoHook1e")),
                    template1.withArgs("ten")
	        ),
                template1.withArgs("eleven")
	    ),
            "}"
        ));

        String code = template2.withArgs().render();
        String expected =
            """
            {
            zero
            x intoHook1a x
            x intoHook1b x
            x intoHook1d x
            x one x
            x two x
            x three x
            x intoHook1c x
            x four x
            x five x
            x six x
            x seven x
            x eight x
            x intoHook1e x
            x nine x
            x ten x
            x eleven x
            }""";
        checkEQ(code, expected);
    }

    public static void testHookWithNestedTemplates() {
        var hook1 = new Hook("Hook1");
        var hook2 = new Hook("Hook2");

        var template1 = Template.make("a", (String a) -> body("x #a x\n"));

        var template2 = Template.make("b", (String b) -> body(
            "{\n",
            template1.withArgs(b + "A"),
            intoHook(hook1, template1.withArgs(b + "B")),
            intoHook(hook2, template1.withArgs(b + "C")),
            template1.withArgs(b + "D"),
            hook1.set(
                template1.withArgs(b + "E"),
                intoHook(hook1, template1.withArgs(b + "F")),
                intoHook(hook2, template1.withArgs(b + "G")),
                template1.withArgs(b + "H"),
                hook2.set(
                    template1.withArgs(b + "I"),
                    intoHook(hook1, template1.withArgs(b + "J")),
                    intoHook(hook2, template1.withArgs(b + "K")),
                    template1.withArgs(b + "L")
                ),
                template1.withArgs(b + "M"),
                intoHook(hook1, template1.withArgs(b + "N")),
                intoHook(hook2, template1.withArgs(b + "O")),
                template1.withArgs(b + "O")
            ),
            template1.withArgs(b + "P"),
            intoHook(hook1, template1.withArgs(b + "Q")),
            intoHook(hook2, template1.withArgs(b + "R")),
            template1.withArgs(b + "S"),
            "}\n"
        ));

        // Test use of hooks across templates.
        var template3 = Template.make(() -> body(
            "{\n",
            "base-A\n",
            hook1.set(
                "base-B\n",
                hook2.set(
                    "base-C\n",
                    template2.withArgs("sub-"),
                    "base-D\n"
	        ),
                "base-E\n"
	    ),
            "base-F\n",
            "}\n"
        ));

        String code = template3.withArgs().render();
        String expected =
            """
            {
            base-A
            x sub-B x
            x sub-Q x
            base-B
            x sub-C x
            x sub-G x
            x sub-O x
            x sub-R x
            base-C
            {
            x sub-A x
            x sub-D x
            x sub-F x
            x sub-J x
            x sub-N x
            x sub-E x
            x sub-H x
            x sub-K x
            x sub-I x
            x sub-L x
            x sub-M x
            x sub-O x
            x sub-P x
            x sub-S x
            }
            base-D
            base-E
            base-F
            }
            """;
        checkEQ(code, expected);
    }

    public static void testHookRecursion() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("a", (String a) -> body("x #a x\n"));

        var template2 = Template.make("b", (String b) -> body(
            "<\n",
            template1.withArgs(b + "A"),
            intoHook(hook1, template1.withArgs(b + "B")), // sub-B is rendered before template2.
            template1.withArgs(b + "C"),
            "inner-hook-start\n",
            hook1.set(
                "inner-hook-end\n",
                template1.withArgs(b + "E"),
                intoHook(hook1, template1.withArgs(b + "E")),
                template1.withArgs(b + "F")
            ),
            ">\n"
        ));

        // Test use of hooks across templates.
        var template3 = Template.make(() -> body(
            "{\n",
            "hook-start\n",
            hook1.set(
                "hook-end\n",
                intoHook(hook1, template2.withArgs("sub-")),
                "base-C\n"
	    ),
            "base-D\n",
            "}\n"
        ));

        String code = template3.withArgs().render();
        String expected =
            """
            {
            hook-start
            x sub-B x
            <
            x sub-A x
            x sub-C x
            inner-hook-start
            x sub-E x
            inner-hook-end
            x sub-E x
            x sub-F x
            >
            hook-end
            base-C
            base-D
            }
            """;
        checkEQ(code, expected);
    }

    public static void testNames() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("a", (String a) -> body("x $name #a x\n"));

        var template2 = Template.make("a", (String a) -> body(
            "{\n",
            "y $name #a y\n",
            template1.withArgs($("name")),
            "}\n"
        ));

        var template3 = Template.make(() -> body(
            "{\n",
            "$name\n",
            "$name", "\n",
            "z $name z\n",
            "z$name z\n",
            template1.withArgs("name"),     // does not capture -> literal "$name"
            template1.withArgs("$name"),    // does not capture -> literal "$name"
            template1.withArgs($("name")),  // capture replacement name "name_1"
            hook1.set(
                "$name\n"
            ),
            "break\n",
            hook1.set(
                "one\n",
                intoHook(hook1, template1.withArgs($("name"))),
                "two\n",
                template1.withArgs($("name")),
                "three\n",
                intoHook(hook1, template2.withArgs($("name"))),
                "four\n"
            ),
            "}\n"
        ));

        String code = template3.withArgs().render();
        String expected =
            """
            {
            name_1
            name_1
            z name_1 z
            zname_1 z
            x name_2 name x
            x name_3 $name x
            x name_4 name_1 x
            name_1
            break
            x name_5 name_1 x
            {
            y name_7 name_1 y
            x name_8 name_7 x
            }
            one
            two
            x name_6 name_1 x
            three
            four
            }
            """;
        checkEQ(code, expected);
    }

    public static void testLet() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("a", (String a) -> body(
            "{\n",
            "y #a y\n",
            let("b", "<" + a + ">"),
            "y #b y\n",
            "}\n"
        ));

        var template2 = Template.make("a", (Integer a) -> let("b", a * 10, b ->
            body(
                let("c", b * 3),
                "abc = #a #b #c\n"
            )
        ));

        var template3 = Template.make(() -> body(
            "{\n",
            let("x", "abc"),
            template1.withArgs("alpha"),
            "break\n",
            "x1 = #x\n",
            hook1.set(
                "x2 = #x\n", // leaks inside
                template1.withArgs("beta"),
                let("y", "one"),
                "y1 = #y\n"
            ),
            "break\n",
            "y2 = #y\n", // leaks outside
            "break\n",
            template2.withArgs(5),
            "}\n"
        ));

        String code = template3.withArgs().render();
        String expected =
            """
            {
            {
            y alpha y
            y <alpha> y
            }
            break
            x1 = abc
            x2 = abc
            {
            y beta y
            y <beta> y
            }
            y1 = one
            break
            y2 = one
            break
            abc = 5 50 150
            }
            """;
        checkEQ(code, expected);
    }

    public static void testSelector() {
        var template1 = Template.make("a", (String a) -> body(
            "<\n",
            "x #a x\n",
            ">\n"
        ));

        var template2 = Template.make("a", (String a) -> body(
            "<\n",
            "y #a y\n",
            ">\n"
        ));

        var template3 = Template.make("a", (Integer a) -> body(
            "[\n",
            "z #a z\n",
            // Select which template should be used:
            a > 0 ? template1.withArgs("A_" + a)
                  : template2.withArgs("B_" + a),
            "]\n"
        ));

        var template4 = Template.make(() -> body(
            "{\n",
            template3.withArgs(-1),
            "break\n",
            template3.withArgs(0),
            "break\n",
            template3.withArgs(1),
            "break\n",
            template3.withArgs(2),
            "}\n"
        ));

        String code = template4.withArgs().render();
        String expected =
            """
            {
            [
            z -1 z
            <
            y B_-1 y
            >
            ]
            break
            [
            z 0 z
            <
            y B_0 y
            >
            ]
            break
            [
            z 1 z
            <
            x A_1 x
            >
            ]
            break
            [
            z 2 z
            <
            x A_2 x
            >
            ]
            }
            """;
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
