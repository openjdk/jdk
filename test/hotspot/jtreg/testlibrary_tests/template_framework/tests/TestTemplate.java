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
 * @bug 8344942
 * @summary Test some basic Template instantiations. We do not necessarily generate correct
 *          java code, we just test that the code generation deterministically creates the
 *          expected String.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main template_framework.tests.TestTemplate
 */

package template_framework.tests;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.FilledTemplate;
import compiler.lib.template_framework.UnfilledTemplate;
import compiler.lib.template_framework.Name;
import compiler.lib.template_framework.Hook;
import compiler.lib.template_framework.TemplateBinding;
import compiler.lib.template_framework.RendererException;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.setFuelCost;
import static compiler.lib.template_framework.Template.addName;
import static compiler.lib.template_framework.Template.weighNames;
import static compiler.lib.template_framework.Template.sampleName;

public class TestTemplate {
    interface FailingTest {
        void run();
    }

    private record MyPrimitive(String name) implements Name.Type {
        @Override
        public boolean isSubtypeOf(Name.Type other) {
            return other instanceof MyPrimitive(String n) && n == name();
        }

        @Override
        public String toString() { return name(); }
    }
    private static final MyPrimitive myInt = new MyPrimitive("int");
    private static final MyPrimitive myLong = new MyPrimitive("long");

    public static void main(String[] args) {
        testSingleLine();
        testMultiLine();
        testBodyTokens();
        testWithOneArgument();
        testWithTwoArguments();
        testWithThreeArguments();
        testNested();
        testHookSimple();
        testHookIsSet();
        testHookNested();
        testHookWithNestedTemplates();
        testHookRecursion();
        testDollar();
        testLet();
        testSelector();
        testRecursion();
        testFuel();
        testFuelCustom();
        testNames();
        testNames2();
        testNames3();
        testListArgument();

        expectRendererException(() -> testFailingNestedRendering(), "Nested render not allowed.");
        expectRendererException(() -> $("name"),                          "A Template method such as");
        expectRendererException(() -> let("x","y"),                       "A Template method such as");
        expectRendererException(() -> fuel(),                             "A Template method such as");
        expectRendererException(() -> setFuelCost(1.0f),                  "A Template method such as");
        expectRendererException(() -> weighNames(myInt, true),            "A Template method such as");
        expectRendererException(() -> sampleName(myInt, true),            "A Template method such as");
        expectRendererException(() -> (new Hook("abc")).isSet(),          "A Template method such as");
        expectRendererException(() -> testFailingHook(), "Hook 'Hook1' was referenced but not found!");
        expectRendererException(() -> testFailingSample(), "No variable of type 'int'.");
        expectRendererException(() -> testFailingHashtag1(), "Duplicate hashtag replacement for #a");
        expectRendererException(() -> testFailingHashtag2(), "Duplicate hashtag replacement for #a");
        expectRendererException(() -> testFailingHashtag3(), "Duplicate hashtag replacement for #a");
        expectRendererException(() -> testFailingHashtag4(), "Missing hashtag replacement for #a");
        expectRendererException(() -> testFailingBinding1(), "Duplicate 'bind' not allowed.");
        expectRendererException(() -> testFailingBinding2(), "Cannot 'get' before 'bind'.");
        expectIllegalArgumentException(() -> body(null),              "Unexpected tokens: null");
        expectIllegalArgumentException(() -> body("x", null),         "Unexpected token: null");
        expectIllegalArgumentException(() -> body(new Hook("Hook1")), "Unexpected token:");
        Hook hook1 = new Hook("Hook1");
        expectIllegalArgumentException(() -> hook1.set(null),         "Unexpected tokens: null");
        expectIllegalArgumentException(() -> hook1.set("x", null),    "Unexpected token: null");
        expectIllegalArgumentException(() -> hook1.set(hook1),        "Unexpected token:");
    }

    public static void testSingleLine() {
        var template = Template.make(() -> body("Hello World!"));
        String code = template.render();
        checkEQ(code, "Hello World!");
    }

    public static void testMultiLine() {
        var template = Template.make(() -> body(
            """
            Code on more
            than a single line
            """
        ));
        String code = template.render();
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
            Integer.valueOf(1), 1,
            Long.valueOf(2), 2L,
            Double.valueOf(3.4), 3.4,
            Float.valueOf(5.6f), 5.6f,
            List.of(" ", 1, " and ", 2),
            " end"
        ));
        String code = template.render();
        checkEQ(code, "start 112L2L3.43.45.6f5.6f 1 and 2 end");
    }

    public static void testWithOneArgument() {
        // Capture String argument via String name.
        var template1 = Template.make("a", (String a) -> body("start #a end"));
        checkEQ(template1.fillWith("x").render(), "start x end");
        checkEQ(template1.fillWith("a").render(), "start a end");
        checkEQ(template1.fillWith("" ).render(), "start  end");

        // Capture String argument via typed lambda argument.
        var template2 = Template.make("a", (String a) -> body("start ", a, " end"));
        checkEQ(template2.fillWith("x").render(), "start x end");
        checkEQ(template2.fillWith("a").render(), "start a end");
        checkEQ(template2.fillWith("" ).render(), "start  end");

        // Capture Integer argument via String name.
        var template3 = Template.make("a", (Integer a) -> body("start #a end"));
        checkEQ(template3.fillWith(0  ).render(), "start 0 end");
        checkEQ(template3.fillWith(22 ).render(), "start 22 end");
        checkEQ(template3.fillWith(444).render(), "start 444 end");

        // Capture Integer argument via templated lambda argument.
        var template4 = Template.make("a", (Integer a) -> body("start ", a, " end"));
        checkEQ(template4.fillWith(0  ).render(), "start 0 end");
        checkEQ(template4.fillWith(22 ).render(), "start 22 end");
        checkEQ(template4.fillWith(444).render(), "start 444 end");

        // Test Strings with backslashes:
        var template5 = Template.make("a", (String a) -> body("start #a " + a + " end"));
        checkEQ(template5.fillWith("/").render(), "start / / end");
        checkEQ(template5.fillWith("\\").render(), "start \\ \\ end");
        checkEQ(template5.fillWith("\\\\").render(), "start \\\\ \\\\ end");
    }

    public static void testWithTwoArguments() {
        // Capture 2 String arguments via String names.
        var template1 = Template.make("a1", "a2", (String a1, String a2) -> body("start #a1 #a2 end"));
        checkEQ(template1.fillWith("x", "y").render(), "start x y end");
        checkEQ(template1.fillWith("a", "b").render(), "start a b end");
        checkEQ(template1.fillWith("",  "" ).render(), "start   end");

        // Capture 2 String arguments via typed lambda arguments.
        var template2 = Template.make("a1", "a2", (String a1, String a2) -> body("start ", a1, " ", a2, " end"));
        checkEQ(template2.fillWith("x", "y").render(), "start x y end");
        checkEQ(template2.fillWith("a", "b").render(), "start a b end");
        checkEQ(template2.fillWith("",  "" ).render(), "start   end");

        // Capture 2 Integer arguments via String names.
        var template3 = Template.make("a1", "a2", (Integer a1, Integer a2) -> body("start #a1 #a2 end"));
        checkEQ(template3.fillWith(0,   1  ).render(), "start 0 1 end");
        checkEQ(template3.fillWith(22,  33 ).render(), "start 22 33 end");
        checkEQ(template3.fillWith(444, 555).render(), "start 444 555 end");

        // Capture 2 Integer arguments via templated lambda arguments.
        var template4 = Template.make("a1", "a2", (Integer a1, Integer a2) -> body("start ", a1, " ", a2, " end"));
        checkEQ(template4.fillWith(0,   1  ).render(), "start 0 1 end");
        checkEQ(template4.fillWith(22,  33 ).render(), "start 22 33 end");
        checkEQ(template4.fillWith(444, 555).render(), "start 444 555 end");
    }

    public static void testWithThreeArguments() {
        // Capture 3 String arguments via String names.
        var template1 = Template.make("a1", "a2", "a3", (String a1, String a2, String a3) -> body("start #a1 #a2 #a3 end"));
        checkEQ(template1.fillWith("x", "y", "z").render(), "start x y z end");
        checkEQ(template1.fillWith("a", "b", "c").render(), "start a b c end");
        checkEQ(template1.fillWith("",  "", "" ).render(),  "start    end");

        // Capture 3 String arguments via typed lambda arguments.
        var template2 = Template.make("a1", "a2", "a3", (String a1, String a2, String a3) -> body("start ", a1, " ", a2, " ", a3, " end"));
        checkEQ(template1.fillWith("x", "y", "z").render(), "start x y z end");
        checkEQ(template1.fillWith("a", "b", "c").render(), "start a b c end");
        checkEQ(template1.fillWith("",  "", "" ).render(),  "start    end");

        // Capture 3 Integer arguments via String names.
        var template3 = Template.make("a1", "a2", "a3", (Integer a1, Integer a2, Integer a3) -> body("start #a1 #a2 #a3 end"));
        checkEQ(template3.fillWith(0,   1  , 2  ).render(), "start 0 1 2 end");
        checkEQ(template3.fillWith(22,  33 , 44 ).render(), "start 22 33 44 end");
        checkEQ(template3.fillWith(444, 555, 666).render(), "start 444 555 666 end");

        // Capture 2 Integer arguments via templated lambda arguments.
        var template4 = Template.make("a1", "a2", "a3", (Integer a1, Integer a2, Integer a3) -> body("start ", a1, " ", a2, " ", a3, " end"));
        checkEQ(template3.fillWith(0,   1  , 2  ).render(), "start 0 1 2 end");
        checkEQ(template3.fillWith(22,  33 , 44 ).render(), "start 22 33 44 end");
        checkEQ(template3.fillWith(444, 555, 666).render(), "start 444 555 666 end");
    }

    public static void testNested() {
        var template1 = Template.make(() -> body("proton"));

        var template2 = Template.make("a1", "a2", (String a1, String a2) -> body(
            "electron #a1\n",
            "neutron #a2\n"
        ));

        var template3 = Template.make("a1", "a2", (String a1, String a2) -> body(
            "Universe ", template1, " {\n",
                template2.fillWith("up", "down"),
                template2.fillWith(a1, a2),
            "}\n"
        ));

        var template4 = Template.make(() -> body(
            template3.fillWith("low", "high"),
            "{\n",
                template3.fillWith("42", "24"),
            "}"
        ));

        String code = template4.render();
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
                hook1.insert(template1)
            ),
            "}"
        ));

        String code = template2.render();
        String expected =
            """
            {
            Hello
            World
            }""";
        checkEQ(code, expected);
    }

    public static void testHookIsSet() {
        var hook1 = new Hook("Hook1");

        var template0 = Template.make(() -> body("isSet: ", hook1.isSet(), "\n"));

        var template1 = Template.make(() -> body("Hello\n", template0));

        var template2 = Template.make(() -> body(
            "{\n",
            template0,
            hook1.set(
                "World\n",
                template0,
                hook1.insert(template1)
            ),
            template0,
            "}"
        ));

        String code = template2.render();
        String expected =
            """
            {
            isSet: false
            Hello
            isSet: true
            World
            isSet: true
            isSet: false
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
                template1.fillWith("one"),
                template1.fillWith("two"),
                hook1.insert(template1.fillWith("intoHook1a")),
                hook1.insert(template1.fillWith("intoHook1b")),
                template1.fillWith("three"),
                hook1.set(
                    template1.fillWith("four"),
                    hook1.insert(template1.fillWith("intoHook1c")),
                    template1.fillWith("five")
                ),
                template1.fillWith("six"),
                hook1.set(), // empty
                template1.fillWith("seven"),
                hook1.insert(template1.fillWith("intoHook1d")),
                template1.fillWith("eight"),
                hook1.set(
                    template1.fillWith("nine"),
                    hook1.insert(template1.fillWith("intoHook1e")),
                    template1.fillWith("ten")
                ),
                template1.fillWith("eleven")
            ),
            "}"
        ));

        String code = template2.render();
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
            template1.fillWith(b + "A"),
            hook1.insert(template1.fillWith(b + "B")),
            hook2.insert(template1.fillWith(b + "C")),
            template1.fillWith(b + "D"),
            hook1.set(
                template1.fillWith(b + "E"),
                hook1.insert(template1.fillWith(b + "F")),
                hook2.insert(template1.fillWith(b + "G")),
                template1.fillWith(b + "H"),
                hook2.set(
                    template1.fillWith(b + "I"),
                    hook1.insert(template1.fillWith(b + "J")),
                    hook2.insert(template1.fillWith(b + "K")),
                    template1.fillWith(b + "L")
                ),
                template1.fillWith(b + "M"),
                hook1.insert(template1.fillWith(b + "N")),
                hook2.insert(template1.fillWith(b + "O")),
                template1.fillWith(b + "O")
            ),
            template1.fillWith(b + "P"),
            hook1.insert(template1.fillWith(b + "Q")),
            hook2.insert(template1.fillWith(b + "R")),
            template1.fillWith(b + "S"),
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
                    template2.fillWith("sub-"),
                    "base-D\n"
                ),
                "base-E\n"
            ),
            "base-F\n",
            "}\n"
        ));

        String code = template3.render();
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
            template1.fillWith(b + "A"),
            hook1.insert(template1.fillWith(b + "B")), // sub-B is rendered before template2.
            template1.fillWith(b + "C"),
            "inner-hook-start\n",
            hook1.set(
                "inner-hook-end\n",
                template1.fillWith(b + "E"),
                hook1.insert(template1.fillWith(b + "E")),
                template1.fillWith(b + "F")
            ),
            ">\n"
        ));

        // Test use of hooks across templates.
        var template3 = Template.make(() -> body(
            "{\n",
            "hook-start\n",
            hook1.set(
                "hook-end\n",
                hook1.insert(template2.fillWith("sub-")),
                "base-C\n"
            ),
            "base-D\n",
            "}\n"
        ));

        String code = template3.render();
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

    public static void testDollar() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("a", (String a) -> body("x $name #a x\n"));

        var template2 = Template.make("a", (String a) -> body(
            "{\n",
            "y $name #a y\n",
            template1.fillWith($("name")),
            "}\n"
        ));

        var template3 = Template.make(() -> body(
            "{\n",
            "$name\n",
            "$name", "\n",
            "z $name z\n",
            "z$name z\n",
            template1.fillWith("name"),     // does not capture -> literal "$name"
            template1.fillWith("$name"),    // does not capture -> literal "$name"
            template1.fillWith($("name")),  // capture replacement name "name_1"
            hook1.set(
                "$name\n"
            ),
            "break\n",
            hook1.set(
                "one\n",
                hook1.insert(template1.fillWith($("name"))),
                "two\n",
                template1.fillWith($("name")),
                "three\n",
                hook1.insert(template2.fillWith($("name"))),
                "four\n"
            ),
            "}\n"
        ));

        String code = template3.render();
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
            template1.fillWith("alpha"),
            "break\n",
            "x1 = #x\n",
            hook1.set(
                "x2 = #x\n", // leaks inside
                template1.fillWith("beta"),
                let("y", "one"),
                "y1 = #y\n"
            ),
            "break\n",
            "y2 = #y\n", // leaks outside
            "break\n",
            template2.fillWith(5),
            "}\n"
        ));

        String code = template3.render();
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
            a > 0 ? template1.fillWith("A_" + a)
                  : template2.fillWith("B_" + a),
            "]\n"
        ));

        var template4 = Template.make(() -> body(
            "{\n",
            template3.fillWith(-1),
            "break\n",
            template3.fillWith(0),
            "break\n",
            template3.fillWith(1),
            "break\n",
            template3.fillWith(2),
            "}\n"
        ));

        String code = template4.render();
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

    public static void testRecursion() {
        // Binding allows use of template1 inside template1, via the Binding indirection.
        var binding1 = new TemplateBinding<UnfilledTemplate.OneArgs<Integer>>();

        var template1 = Template.make("i", (Integer i) -> body(
            "[ #i\n",
            // We cannot yet use the template1 directly, as it is being defined.
            // So we use binding1 instead.
            i < 0 ? "done\n" : binding1.get().fillWith(i - 1),
            "] #i\n"
        ));
        binding1.bind(template1);

        var template2 = Template.make(() -> body(
            "{\n",
            // Now, we can use template1 normally, as it is already defined.
            template1.fillWith(3),
            "}\n"
        ));

        String code = template2.render();
        String expected =
            """
            {
            [ 3
            [ 2
            [ 1
            [ 0
            [ -1
            done
            ] -1
            ] 0
            ] 1
            ] 2
            ] 3
            }
            """;
        checkEQ(code, expected);
    }

    public static void testFuel() {
        var template1 = Template.make(() -> body(
            let("f", fuel()),

            "<#f>\n"
        ));

        // Binding allows use of template2 inside template2, via the Binding indirection.
        var binding2 = new TemplateBinding<UnfilledTemplate.OneArgs<Integer>>();
        var template2 = Template.make("i", (Integer i) -> body(
            let("f", fuel()),

            "[ #i #f\n",
            template1,
            fuel() <= 60.f ? "done" : binding2.get().fillWith(i - 1),
            "] #i #f\n"
        ));
        binding2.bind(template2);

        var template3 = Template.make(() -> body(
            "{\n",
            template2.fillWith(3),
            "}\n"
        ));

        String code = template3.render();
        String expected =
            """
            {
            [ 3 90.0f
            <80.0f>
            [ 2 80.0f
            <70.0f>
            [ 1 70.0f
            <60.0f>
            [ 0 60.0f
            <50.0f>
            done] 0 60.0f
            ] 1 70.0f
            ] 2 80.0f
            ] 3 90.0f
            }
            """;
        checkEQ(code, expected);
    }

    public static void testFuelCustom() {
        var template1 = Template.make(() -> body(
            setFuelCost(2.0f),
            let("f", fuel()),

            "<#f>\n"
        ));

        // Binding allows use of template2 inside template2, via the Binding indirection.
        var binding2 = new TemplateBinding<UnfilledTemplate.OneArgs<Integer>>();
        var template2 = Template.make("i", (Integer i) -> body(
            setFuelCost(3.0f),
            let("f", fuel()),

            "[ #i #f\n",
            template1,
            fuel() <= 5.f ? "done\n" : binding2.get().fillWith(i - 1),
            "] #i #f\n"
        ));
        binding2.bind(template2);

        var template3 = Template.make(() -> body(
            setFuelCost(5.0f),
            let("f", fuel()),

            "{ #f\n",
            template2.fillWith(3),
            "} #f\n"
        ));

        String code = template3.render(20.0f);
        String expected =
            """
            { 20.0f
            [ 3 15.0f
            <12.0f>
            [ 2 12.0f
            <9.0f>
            [ 1 9.0f
            <6.0f>
            [ 0 6.0f
            <3.0f>
            [ -1 3.0f
            <0.0f>
            done
            ] -1 3.0f
            ] 0 6.0f
            ] 1 9.0f
            ] 2 12.0f
            ] 3 15.0f
            } 20.0f
            """;
        checkEQ(code, expected);
    }

    public static void testNames() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> body(
            "[", weighNames(myInt, true), "]\n"
        ));

        var template2 = Template.make("name", "type", (String name, Name.Type type) -> body(
            addName(new Name(name, type, true, 1)),
            "define #type #name\n",
            template1
        ));

        var template3 = Template.make(() -> body(
            "<\n",
            hook1.insert(template2.fillWith($("name"), myInt)),
            "$name = 5\n",
            ">\n"
        ));

        var template4 = Template.make(() -> body(
            "{\n",
            template1,
            hook1.set(
                template1,
                "something\n",
                template3,
                "more\n",
                template1,
                "more\n",
                template2.fillWith($("name"), myInt),
                "more\n",
                template1
            ),
            template1,
            "}\n"
        ));

        String code = template4.render();
        String expected =
            """
            {
            [0L]
            define int name_4
            [1L]
            [0L]
            something
            <
            name_4 = 5
            >
            more
            [1L]
            more
            define int name_1
            [2L]
            more
            [1L]
            [0L]
            }
            """;
        checkEQ(code, expected);
    }

    public static void testNames2() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("type", (Name.Type type) -> body(
            "[#type: ", weighNames(type, true), " and ", weighNames(type, false), "]\n"
        ));


        var template2 = Template.make("name", "type", (String name, Name.Type type) -> body(
            addName(new Name(name, type, true, 1)),
            "define mutable #type #name\n",
            template1.fillWith(type)
        ));

        var template3 = Template.make("name", "type", (String name, Name.Type type) -> body(
            addName(new Name(name, type, false, 1)),
            "define immutable #type #name\n",
            template1.fillWith(type)
        ));

        var template4 = Template.make("type", (Name.Type type) -> body(
            "{ $store\n",
            hook1.insert(template2.fillWith($("name"), type)),
            "$name = 5\n",
            "} $store\n"
        ));

        var template5 = Template.make("type", (Name.Type type) -> body(
            "{ $load\n",
            hook1.insert(template3.fillWith($("name"), type)),
            "blackhole($name)\n",
            "} $load\n"
        ));

        var template6 = Template.make("type", (Name.Type type) -> body(
            let("v", sampleName(type, true).name()),
            "{ $sample\n",
            "#v = 7\n",
            "} $sample\n"
        ));

        var template7 = Template.make("type", (Name.Type type) -> body(
            let("v", sampleName(type, false).name()),
            "{ $sample\n",
            "blackhole(#v)\n",
            "} $sample\n"
        ));

        var template8 = Template.make(() -> body(
            "class $X {\n",
            template1.fillWith(myInt),
            hook1.set(
                "begin $body\n",
                template1.fillWith(myInt),
                "start with immutable\n",
                template5.fillWith(myInt),
                "then load from it\n",
                template7.fillWith(myInt),
                template1.fillWith(myInt),
                "now make something mutable\n",
                template4.fillWith(myInt),
                "then store to it\n",
                template6.fillWith(myInt),
                template1.fillWith(myInt)
            ),
            template1.fillWith(myInt),
            "}\n"
        ));

        String code = template8.render();
        String expected =
            """
            class X_1 {
            [int: 0L and 0L]
            define immutable int name_4
            [int: 0L and 1L]
            define mutable int name_9
            [int: 1L and 2L]
            begin body_1
            [int: 0L and 0L]
            start with immutable
            { load_4
            blackhole(name_4)
            } load_4
            then load from it
            { sample_7
            blackhole(name_4)
            } sample_7
            [int: 0L and 1L]
            now make something mutable
            { store_9
            name_9 = 5
            } store_9
            then store to it
            { sample_12
            name_9 = 7
            } sample_12
            [int: 1L and 2L]
            [int: 0L and 0L]
            }
            """;
        checkEQ(code, expected);
    }

    public static void testNames3() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("type", (Name.Type type) -> body(
            "[#type: ", weighNames(type, true), " and ", weighNames(type, false), "]\n"
        ));

        // Example that shows that addName runs before any code gets generated.
        // To avoid this behaviour, you have to wrap the addName in their own template.
        var template2 = Template.make(() -> body(
            "class $Y {\n",
            template1.fillWith(myInt),
            hook1.set(
                "begin $body\n",
                template1.fillWith(myInt),
                "define mutable\n",
                addName(new Name($("v1"), myInt, true, 1)),
                template1.fillWith(myInt),
                "define immutable\n",
                addName(new Name($("v1"), myInt, false, 1)),
                template1.fillWith(myInt)
            ),
            template1.fillWith(myInt),
            "}\n"
        ));

        String code = template2.render();
        String expected =
            """
            class Y_1 {
            [int: 0L and 0L]
            begin body_1
            [int: 0L and 0L]
            define mutable
            [int: 1L and 1L]
            define immutable
            [int: 1L and 2L]
            [int: 0L and 0L]
            }
            """;
        checkEQ(code, expected);
    }

    record MyItem(Name.Type type, String op) {}

    public static void testListArgument() {
        var template1 = Template.make("item", (MyItem item) -> body(
            let("type", item.type()),
            let("op", item.op()),
            "#type apply #op\n"
        ));

        var template2 = Template.make("list", (List<MyItem> list) -> body(
            "class $Z {\n",
            // Use template1 for every item in the list.
            list.stream().map(item -> template1.fillWith(item)).toList(),
            "}\n"
        ));

        List<MyItem> list = List.of(new MyItem(myInt, "+"),
                                    new MyItem(myInt, "-"),
                                    new MyItem(myInt, "*"),
                                    new MyItem(myInt, "/"),
                                    new MyItem(myLong, "+"),
                                    new MyItem(myLong, "-"),
                                    new MyItem(myLong, "*"),
                                    new MyItem(myLong, "/"));

        String code = template2.fillWith(list).render();
        String expected =
            """
            class Z_1 {
            int apply +
            int apply -
            int apply *
            int apply /
            long apply +
            long apply -
            long apply *
            long apply /
            }
            """;
        checkEQ(code, expected);
    }

    public static void testFailingNestedRendering() {
        var template1 = Template.make(() -> body(
            "alpha\n"
        ));

        var template2 = Template.make(() -> body(
            "beta\n",
            // Nested "render" call not allowed!
            template1.render(),
            "gamma\n"
        ));

        String code = template2.render();
    }

    public static void testFailingHook() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> body(
            "alpha\n"
        ));

        var template2 = Template.make(() -> body(
            "beta\n",
            // Use hook without hook1.set
            hook1.insert(template1),
            "gamma\n"
        ));

        String code = template2.render();
    }

    public static void testFailingSample() {
        var template1 = Template.make(() -> body(
            let("v", sampleName(myInt, true).name()),
            "v is #v\n"
        ));

        String code = template1.render();
    }

    public static void testFailingHashtag1() {
        var template1 = Template.make("a", "a", (String _, String _) -> body(
            "nothing\n"
        ));

        String code = template1.fillWith("x", "y").render();
    }

    public static void testFailingHashtag2() {
        var template1 = Template.make("a", (String _) -> body(
            let("a", "x"),
            "nothing\n"
        ));

        String code = template1.fillWith("y").render();
    }

    public static void testFailingHashtag3() {
        var template1 = Template.make(() -> body(
            let("a", "x"),
            let("a", "y"),
            "nothing\n"
        ));

        String code = template1.render();
    }

    public static void testFailingHashtag4() {
        var template1 = Template.make(() -> body(
            "#a\n"
        ));

        String code = template1.render();
    }

    public static void testFailingBinding1() {
        var binding = new TemplateBinding<FilledTemplate.ZeroArgs>();
        var template1 = Template.make(() -> body(
            "nothing\n"
        ));
        binding.bind(template1);
        binding.bind(template1);
    }

    public static void testFailingBinding2() {
        var binding = new TemplateBinding<FilledTemplate.ZeroArgs>();
        var template1 = Template.make(() -> body(
            "nothing\n",
            binding.get()
        ));
        String code = template1.render();
    }

    public static void expectRendererException(FailingTest test, String errorPrefix) {
        try {
            test.run();
            System.out.println("Should have thrown with prefix: " + errorPrefix);
            throw new RuntimeException("Should have thrown!");
        } catch(RendererException e) {
            if (!e.getMessage().startsWith(errorPrefix)) {
                System.out.println("Should have thrown with prefix: " + errorPrefix);
                System.out.println("got: " + e.getMessage());
                throw new RuntimeException("Prefix mismatch", e);
            }
        }
    }

    public static void expectIllegalArgumentException(FailingTest test, String errorPrefix) {
        try {
            test.run();
            System.out.println("Should have thrown with prefix: " + errorPrefix);
            throw new RuntimeException("Should have thrown!");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().startsWith(errorPrefix)) {
                System.out.println("Should have thrown with prefix: " + errorPrefix);
                System.out.println("got: " + e.getMessage());
                throw new RuntimeException("Prefix mismatch", e);
            }
        }
    }

    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
            System.out.println("\"" + code + "\"");
            System.out.println("\"" + expected + "\"");
            throw new RuntimeException("Template rendering mismatch!");
        }
    }
}
