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
import compiler.lib.template_framework.DataName;
import compiler.lib.template_framework.StructuralName;
import compiler.lib.template_framework.Hook;
import compiler.lib.template_framework.TemplateBinding;
import compiler.lib.template_framework.RendererException;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.flat;
import static compiler.lib.template_framework.Template.nameScope;
import static compiler.lib.template_framework.Template.hashtagScope;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.setFuelCost;
import static compiler.lib.template_framework.Template.addDataName;
import static compiler.lib.template_framework.Template.dataNames;
import static compiler.lib.template_framework.Template.addStructuralName;
import static compiler.lib.template_framework.Template.structuralNames;
import static compiler.lib.template_framework.DataName.Mutability.MUTABLE;
import static compiler.lib.template_framework.DataName.Mutability.IMMUTABLE;
import static compiler.lib.template_framework.DataName.Mutability.MUTABLE_OR_IMMUTABLE;

/**
 * The tests in this file are mostly there to ensure that the Template Rendering
 * works correctly, and not that we produce compilable Java code. Rather, we
 * produce deterministic output, and compare it to the expected String.
 * Still, this file may be helpful for learning more about how Templates
 * work and can be used.
 *
 * We assume that you have already studied {@code TestTutorial.java}.
 */
public class TestTemplate {
    // Interface for failing tests.
    interface FailingTest {
        void run();
    }

    // Define a simple type to model primitive types.
    private record MyPrimitive(String name) implements DataName.Type {
        @Override
        public boolean isSubtypeOf(DataName.Type other) {
            return other instanceof MyPrimitive(String n) && n.equals(name());
        }

        @Override
        public String toString() { return name(); }
    }
    private static final MyPrimitive myInt = new MyPrimitive("int");
    private static final MyPrimitive myLong = new MyPrimitive("long");

    // Simulate classes. Subtypes start with the name of the super type.
    private record MyClass(String name) implements DataName.Type {
        @Override
        public boolean isSubtypeOf(DataName.Type other) {
            return other instanceof MyClass(String n) && name().startsWith(n);
        }

        @Override
        public String toString() { return name(); }
    }
    private static final MyClass myClassA   = new MyClass("myClassA");
    private static final MyClass myClassA1  = new MyClass("myClassA1");
    private static final MyClass myClassA2  = new MyClass("myClassA2");
    private static final MyClass myClassA11 = new MyClass("myClassA11");
    private static final MyClass myClassB   = new MyClass("myClassB");

    // Simulate some structural types.
    private record MyStructuralType(String name) implements StructuralName.Type {
        @Override
        public boolean isSubtypeOf(StructuralName.Type other) {
            return other instanceof MyStructuralType(String n) && name().startsWith(n);
        }

        @Override
        public String toString() { return name(); }
    }
    private static final MyStructuralType myStructuralTypeA = new MyStructuralType("StructuralA");
    private static final MyStructuralType myStructuralTypeA1 = new MyStructuralType("StructuralA1");
    private static final MyStructuralType myStructuralTypeA2 = new MyStructuralType("StructuralA2");
    private static final MyStructuralType myStructuralTypeA11 = new MyStructuralType("StructuralA11");
    private static final MyStructuralType myStructuralTypeB = new MyStructuralType("StructuralB");

    public static void main(String[] args) {
        // The following tests all pass, i.e. have no errors during rendering.
        testSingleLine();
        testMultiLine();
        testBasicTokens();
        testWithOneArgument();
        testWithTwoArguments();
        testWithThreeArguments();
        testNestedTemplates();
        testHookSimple();
        testHookIsAnchored();
        testHookNested();
        testHookWithNestedTemplates();
        testHookRecursion();
        testDollar();
        testLet();
        testDollarAndHashtagBrackets();
        testSelector();
        testRecursion();
        testFuel();
        testFuelCustom();
        testDataNames0a();
        testDataNames0b();
        testDataNames0c();
        testDataNames0d();
        testDataNames1();
        testDataNames2();
        testDataNames3();
        testDataNames4();
        testDataNames5();
        testDataNames6();
        testStructuralNames0();
        testStructuralNames1();
        testStructuralNames2();
        testListArgument();
        testNestedScopes1();
        testNestedScopes2();

        // The following tests should all fail, with an expected exception and message.
        expectRendererException(() -> testFailingNestedRendering(), "Nested render not allowed.");
        expectRendererException(() -> $("name"),                          "A Template method such as");
        expectRendererException(() -> fuel(),                             "A Template method such as");
        expectRendererException(() -> setFuelCost(1.0f),                  "A Template method such as");
        expectRendererException(() -> (new Hook("abc")).isAnchored(),     "A Template method such as");
        expectRendererException(() -> testFailingDollarName1(), "Is not a valid '$' name: ''.");
        expectRendererException(() -> testFailingDollarName2(), "Is not a valid '$' name: '#abc'.");
        expectRendererException(() -> testFailingDollarName3(), "Is not a valid '$' name: 'abc#'.");
        expectRendererException(() -> testFailingDollarName4(), "A '$' name should not be null.");
        expectRendererException(() -> testFailingDollarName5(), "Is not a valid '$' replacement pattern: '$' in '$'.");
        expectRendererException(() -> testFailingDollarName6(), "Is not a valid '$' replacement pattern: '$' in 'asdf$'.");
        expectRendererException(() -> testFailingDollarName7(), "Is not a valid '$' replacement pattern: '$1' in 'asdf$1'.");
        expectRendererException(() -> testFailingDollarName8(), "Is not a valid '$' replacement pattern: '$' in 'abc$$abc'.");
        expectRendererException(() -> testFailingLetName1(), "A hashtag replacement should not be null.");
        expectRendererException(() -> testFailingHashtagName1(), "Is not a valid hashtag replacement name: ''.");
        expectRendererException(() -> testFailingHashtagName2(), "Is not a valid hashtag replacement name: 'abc#abc'.");
        expectRendererException(() -> testFailingHashtagName3(), "Is not a valid hashtag replacement name: ''.");
        expectRendererException(() -> testFailingHashtagName4(), "Is not a valid hashtag replacement name: 'xyz#xyz'.");
        expectRendererException(() -> testFailingHashtagName5(), "Is not a valid '#' replacement pattern: '#' in '#'.");
        expectRendererException(() -> testFailingHashtagName6(), "Is not a valid '#' replacement pattern: '#' in 'asdf#'.");
        expectRendererException(() -> testFailingHashtagName7(), "Is not a valid '#' replacement pattern: '#1' in 'asdf#1'.");
        expectRendererException(() -> testFailingHashtagName8(), "Is not a valid '#' replacement pattern: '#' in 'abc##abc'.");
        expectRendererException(() -> testFailingDollarHashtagName1(), "Is not a valid '#' replacement pattern: '#' in '#$'.");
        expectRendererException(() -> testFailingDollarHashtagName2(), "Is not a valid '$' replacement pattern: '$' in '$#'.");
        expectRendererException(() -> testFailingDollarHashtagName3(), "Is not a valid '#' replacement pattern: '#' in '#$name'.");
        expectRendererException(() -> testFailingDollarHashtagName4(), "Is not a valid '$' replacement pattern: '$' in '$#name'.");
        expectRendererException(() -> testFailingHook(), "Hook 'Hook1' was referenced but not found!");
        expectRendererException(() -> testFailingSample1a(),  "No Name found for DataName.FilterdSet(MUTABLE, subtypeOf(int), supertypeOf(int))");
        expectRendererException(() -> testFailingSample1b(),  "No Name found for StructuralName.FilteredSet( subtypeOf(StructuralA) supertypeOf(StructuralA))");
        expectRendererException(() -> testFailingHashtag1(), "Duplicate hashtag replacement for #a");
        expectRendererException(() -> testFailingHashtag2(), "Duplicate hashtag replacement for #a");
        expectRendererException(() -> testFailingHashtag3(), "Duplicate hashtag replacement for #a");
        expectRendererException(() -> testFailingHashtag4(), "Missing hashtag replacement for #a");
        expectRendererException(() -> testFailingHashtag5(), "Missing hashtag replacement for #a");
        expectRendererException(() -> testFailingBinding1(), "Duplicate 'bind' not allowed.");
        expectRendererException(() -> testFailingBinding2(), "Cannot 'get' before 'bind'.");
        expectIllegalArgumentException(() -> scope(null),              "Unexpected tokens: null");
        expectIllegalArgumentException(() -> scope("x", null),         "Unexpected token: null");
        expectIllegalArgumentException(() -> scope(new Hook("Hook1")), "Unexpected token:");
        expectIllegalArgumentException(() -> flat(null),              "Unexpected tokens: null");
        expectIllegalArgumentException(() -> flat("x", null),         "Unexpected token: null");
        expectIllegalArgumentException(() -> flat(new Hook("Hook1")), "Unexpected token:");
        expectIllegalArgumentException(() -> nameScope(null),              "Unexpected tokens: null");
        expectIllegalArgumentException(() -> nameScope("x", null),         "Unexpected token: null");
        expectIllegalArgumentException(() -> nameScope(new Hook("Hook1")), "Unexpected token:");
        expectIllegalArgumentException(() -> hashtagScope(null),              "Unexpected tokens: null");
        expectIllegalArgumentException(() -> hashtagScope("x", null),         "Unexpected token: null");
        expectIllegalArgumentException(() -> hashtagScope(new Hook("Hook1")), "Unexpected token:");
        Hook hook1 = new Hook("Hook1");
        expectIllegalArgumentException(() -> hook1.anchor(null),      "Unexpected tokens: null");
        expectIllegalArgumentException(() -> hook1.anchor("x", null), "Unexpected token: null");
        expectIllegalArgumentException(() -> hook1.anchor(hook1),     "Unexpected token:");
        expectIllegalArgumentException(() -> testFailingAddDataName1(), "Unexpected mutability: MUTABLE_OR_IMMUTABLE");
        expectIllegalArgumentException(() -> testFailingAddDataName2(), "Unexpected weight: ");
        expectIllegalArgumentException(() -> testFailingAddDataName3(), "Unexpected weight: ");
        expectIllegalArgumentException(() -> testFailingAddDataName4(), "Unexpected weight: ");
        expectIllegalArgumentException(() -> testFailingAddStructuralName1(), "Unexpected weight: ");
        expectIllegalArgumentException(() -> testFailingAddStructuralName2(), "Unexpected weight: ");
        expectIllegalArgumentException(() -> testFailingAddStructuralName3(), "Unexpected weight: ");
        expectUnsupportedOperationException(() -> testFailingSample2a(), "Must first call 'subtypeOf', 'supertypeOf', or 'exactOf'.");
        expectUnsupportedOperationException(() -> testFailingSample2b(), "Must first call 'subtypeOf', 'supertypeOf', or 'exactOf'.");
        expectRendererException(() -> testFailingAddNameDuplication1(), "Duplicate name:");
        expectRendererException(() -> testFailingAddNameDuplication2(), "Duplicate name:");
        expectRendererException(() -> testFailingAddNameDuplication3(), "Duplicate name:");
        expectRendererException(() -> testFailingAddNameDuplication4(), "Duplicate name:");
        expectRendererException(() -> testFailingAddNameDuplication5(), "Duplicate name:");
        expectRendererException(() -> testFailingAddNameDuplication6(), "Duplicate name:");
        expectRendererException(() -> testFailingAddNameDuplication7(), "Duplicate name:");
        expectRendererException(() -> testFailingAddNameDuplication8(), "Duplicate name:");
        expectRendererException(() -> testFailingScope1(), "Duplicate hashtag replacement for #x. previous: x1, new: x2");
        expectRendererException(() -> testFailingScope2(), "Duplicate hashtag replacement for #x. previous: x1, new: x2");
    }

    public static void testSingleLine() {
        var template = Template.make(() -> scope("Hello World!"));
        String code = template.render();
        checkEQ(code, "Hello World!");
    }

    public static void testMultiLine() {
        var template = Template.make(() -> scope(
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

    public static void testBasicTokens() {
        // We can fill the scope with Objects of different types, and they get concatenated.
        // Lists get flattened into the scope.
        var template = Template.make(() -> scope(
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
        var template1 = Template.make("a", (String a) -> scope("start #a end"));
        checkEQ(template1.render("x"), "start x end");
        checkEQ(template1.render("a"), "start a end");
        checkEQ(template1.render("" ), "start  end");

        // Capture String argument via typed lambda argument.
        var template2 = Template.make("a", (String a) -> scope("start ", a, " end"));
        checkEQ(template2.render("x"), "start x end");
        checkEQ(template2.render("a"), "start a end");
        checkEQ(template2.render("" ), "start  end");

        // Capture Integer argument via String name.
        var template3 = Template.make("a", (Integer a) -> scope("start #a end"));
        checkEQ(template3.render(0  ), "start 0 end");
        checkEQ(template3.render(22 ), "start 22 end");
        checkEQ(template3.render(444), "start 444 end");

        // Capture Integer argument via templated lambda argument.
        var template4 = Template.make("a", (Integer a) -> scope("start ", a, " end"));
        checkEQ(template4.render(0  ), "start 0 end");
        checkEQ(template4.render(22 ), "start 22 end");
        checkEQ(template4.render(444), "start 444 end");

        // Test Strings with backslashes:
        var template5 = Template.make("a", (String a) -> scope("start #a " + a + " end"));
        checkEQ(template5.render("/"), "start / / end");
        checkEQ(template5.render("\\"), "start \\ \\ end");
        checkEQ(template5.render("\\\\"), "start \\\\ \\\\ end");
    }

    public static void testWithTwoArguments() {
        // Capture 2 String arguments via String names.
        var template1 = Template.make("a1", "a2", (String a1, String a2) -> scope("start #a1 #a2 end"));
        checkEQ(template1.render("x", "y"), "start x y end");
        checkEQ(template1.render("a", "b"), "start a b end");
        checkEQ(template1.render("",  "" ), "start   end");

        // Capture 2 String arguments via typed lambda arguments.
        var template2 = Template.make("a1", "a2", (String a1, String a2) -> scope("start ", a1, " ", a2, " end"));
        checkEQ(template2.render("x", "y"), "start x y end");
        checkEQ(template2.render("a", "b"), "start a b end");
        checkEQ(template2.render("",  "" ), "start   end");

        // Capture 2 Integer arguments via String names.
        var template3 = Template.make("a1", "a2", (Integer a1, Integer a2) -> scope("start #a1 #a2 end"));
        checkEQ(template3.render(0,   1  ), "start 0 1 end");
        checkEQ(template3.render(22,  33 ), "start 22 33 end");
        checkEQ(template3.render(444, 555), "start 444 555 end");

        // Capture 2 Integer arguments via templated lambda arguments.
        var template4 = Template.make("a1", "a2", (Integer a1, Integer a2) -> scope("start ", a1, " ", a2, " end"));
        checkEQ(template4.render(0,   1  ), "start 0 1 end");
        checkEQ(template4.render(22,  33 ), "start 22 33 end");
        checkEQ(template4.render(444, 555), "start 444 555 end");
    }

    public static void testWithThreeArguments() {
        // Capture 3 String arguments via String names.
        var template1 = Template.make("a1", "a2", "a3", (String a1, String a2, String a3) -> scope("start #a1 #a2 #a3 end"));
        checkEQ(template1.render("x", "y", "z"), "start x y z end");
        checkEQ(template1.render("a", "b", "c"), "start a b c end");
        checkEQ(template1.render("",  "", "" ),  "start    end");

        // Capture 3 String arguments via typed lambda arguments.
        var template2 = Template.make("a1", "a2", "a3", (String a1, String a2, String a3) -> scope("start ", a1, " ", a2, " ", a3, " end"));
        checkEQ(template1.render("x", "y", "z"), "start x y z end");
        checkEQ(template1.render("a", "b", "c"), "start a b c end");
        checkEQ(template1.render("",  "", "" ),  "start    end");

        // Capture 3 Integer arguments via String names.
        var template3 = Template.make("a1", "a2", "a3", (Integer a1, Integer a2, Integer a3) -> scope("start #a1 #a2 #a3 end"));
        checkEQ(template3.render(0,   1  , 2  ), "start 0 1 2 end");
        checkEQ(template3.render(22,  33 , 44 ), "start 22 33 44 end");
        checkEQ(template3.render(444, 555, 666), "start 444 555 666 end");

        // Capture 2 Integer arguments via templated lambda arguments.
        var template4 = Template.make("a1", "a2", "a3", (Integer a1, Integer a2, Integer a3) -> scope("start ", a1, " ", a2, " ", a3, " end"));
        checkEQ(template3.render(0,   1  , 2  ), "start 0 1 2 end");
        checkEQ(template3.render(22,  33 , 44 ), "start 22 33 44 end");
        checkEQ(template3.render(444, 555, 666), "start 444 555 666 end");
    }

    public static void testNestedTemplates() {
        var template1 = Template.make(() -> scope("proton"));

        var template2 = Template.make("a1", "a2", (String a1, String a2) -> scope(
            "electron #a1\n",
            "neutron #a2\n"
        ));

        var template3 = Template.make("a1", "a2", (String a1, String a2) -> scope(
            "Universe ", template1.asToken(), " {\n",
                template2.asToken("up", "down"),
                template2.asToken(a1, a2),
            "}\n"
        ));

        var template4 = Template.make(() -> scope(
            template3.asToken("low", "high"),
            "{\n",
                template3.asToken("42", "24"),
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

        var template1 = Template.make(() -> scope("Hello\n"));

        var template2 = Template.make(() -> scope(
            "{\n",
            hook1.anchor(
                "World\n",
                // Note: "Hello" from the template below will be inserted
                // above "World" above.
                hook1.insert(template1.asToken())
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

    public static void testHookIsAnchored() {
        var hook1 = new Hook("Hook1");

        var template0 = Template.make(() -> scope("isAnchored: ", hook1.isAnchored(), "\n"));

        var template1 = Template.make(() -> scope("Hello\n", template0.asToken()));

        var template2 = Template.make(() -> scope(
            "{\n",
            template0.asToken(),
            hook1.anchor(
                "World\n",
                template0.asToken(),
                hook1.insert(template1.asToken())
            ),
            template0.asToken(),
            "}"
        ));

        String code = template2.render();
        String expected =
            """
            {
            isAnchored: false
            Hello
            isAnchored: true
            World
            isAnchored: true
            isAnchored: false
            }""";
        checkEQ(code, expected);
    }

    public static void testHookNested() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("a", (String a) -> scope("x #a x\n"));

        // Test nested use of hooks in the same template.
        var template2 = Template.make(() -> scope(
            "{\n",
            hook1.anchor(), // empty
            "zero\n",
            hook1.anchor(
                template1.asToken("one"),
                template1.asToken("two"),
                hook1.insert(template1.asToken("intoHook1a")),
                hook1.insert(template1.asToken("intoHook1b")),
                template1.asToken("three"),
                hook1.anchor(
                    template1.asToken("four"),
                    hook1.insert(template1.asToken("intoHook1c")),
                    template1.asToken("five")
                ),
                template1.asToken("six"),
                hook1.anchor(), // empty
                template1.asToken("seven"),
                hook1.insert(template1.asToken("intoHook1d")),
                template1.asToken("eight"),
                hook1.anchor(
                    template1.asToken("nine"),
                    hook1.insert(template1.asToken("intoHook1e")),
                    template1.asToken("ten")
                ),
                template1.asToken("eleven")
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

        var template1 = Template.make("a", (String a) -> scope("x #a x\n"));

        var template2 = Template.make("b", (String b) -> scope(
            "{\n",
            template1.asToken(b + "A"),
            hook1.insert(template1.asToken(b + "B")),
            hook2.insert(template1.asToken(b + "C")),
            template1.asToken(b + "D"),
            hook1.anchor(
                template1.asToken(b + "E"),
                hook1.insert(template1.asToken(b + "F")),
                hook2.insert(template1.asToken(b + "G")),
                template1.asToken(b + "H"),
                hook2.anchor(
                    template1.asToken(b + "I"),
                    hook1.insert(template1.asToken(b + "J")),
                    hook2.insert(template1.asToken(b + "K")),
                    template1.asToken(b + "L")
                ),
                template1.asToken(b + "M"),
                hook1.insert(template1.asToken(b + "N")),
                hook2.insert(template1.asToken(b + "O")),
                template1.asToken(b + "O")
            ),
            template1.asToken(b + "P"),
            hook1.insert(template1.asToken(b + "Q")),
            hook2.insert(template1.asToken(b + "R")),
            template1.asToken(b + "S"),
            "}\n"
        ));

        // Test use of hooks across templates.
        var template3 = Template.make(() -> scope(
            "{\n",
            "base-A\n",
            hook1.anchor(
                "base-B\n",
                hook2.anchor(
                    "base-C\n",
                    template2.asToken("sub-"),
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

        var template1 = Template.make("a", (String a) -> scope("x #a x\n"));

        var template2 = Template.make("b", (String b) -> scope(
            "<\n",
            template1.asToken(b + "A"),
            hook1.insert(template1.asToken(b + "B")), // sub-B is rendered before template2.
            template1.asToken(b + "C"),
            "inner-hook-start\n",
            hook1.anchor(
                "inner-hook-end\n",
                template1.asToken(b + "E"),
                hook1.insert(template1.asToken(b + "E")),
                template1.asToken(b + "F")
            ),
            ">\n"
        ));

        // Test use of hooks across templates.
        var template3 = Template.make(() -> scope(
            "{\n",
            "hook-start\n",
            hook1.anchor(
                "hook-end\n",
                hook1.insert(template2.asToken("sub-")),
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

        var template1 = Template.make("a", (String a) -> scope("x $name #a x\n"));

        var template2 = Template.make("a", (String a) -> scope(
            "{\n",
            "y $name #a y\n",
            template1.asToken($("name")),
            "}\n"
        ));

        var template3 = Template.make(() -> scope(
            "{\n",
            "$name\n",
            "$name", "\n",
            "z $name z\n",
            "z$name z\n",
            template1.asToken("name"),     // does not capture -> literal "$name"
            template1.asToken("$name"),    // does not capture -> literal "$name"
            template1.asToken($("name")),  // capture replacement name "name_1"
            hook1.anchor(
                "$name\n"
            ),
            "break\n",
            hook1.anchor(
                "one\n",
                hook1.insert(template1.asToken($("name"))),
                "two\n",
                template1.asToken($("name")),
                "three\n",
                hook1.insert(template2.asToken($("name"))),
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

        var template1 = Template.make("a", (String a) -> scope(
            "{\n",
            "y #a y\n",
            let("b", "<" + a + ">"),
            "y #b y\n",
            "}\n"
        ));

        var template2 = Template.make("a", (Integer a) -> let("b", a * 10, b ->
            scope(
                let("c", b * 3),
                "abc = #a #b #c\n"
            )
        ));

        var template3 = Template.make(() -> scope(
            "{\n",
            let("x", "abc"),
            template1.asToken("alpha"),
            "break\n",
            "x1 = #x\n",
            hook1.anchor(
                "x2 = #x\n", // leaks inside
                template1.asToken("beta"),
                let("y", "one"),
                "y1 = #y\n"
            ),
            "break\n",
            "y2 = #y\n", // leaks outside
            "break\n",
            template2.asToken(5),
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

    public static void testDollarAndHashtagBrackets() {
        var template1 = Template.make(() -> scope(
            let("xyz", "abc"),
            let("xyz_", "def"),
            let("xyz_klm", "ghi"),
            let("klm", "jkl"),
            """
            no bracket: #xyz #xyz_klm #xyz_#klm
            no bracket: $var $var_two $var_$two
            with bracket: #{xyz} #{xyz_klm} #{xyz}_#{klm}
            with bracket: ${var} ${var_two} ${var}_${two}
            """
        ));

        String code = template1.render();
        String expected =
            """
            no bracket: abc ghi defjkl
            no bracket: var_1 var_two_1 var__1two_1
            with bracket: abc ghi abc_jkl
            with bracket: var_1 var_two_1 var_1_two_1
            """;
        checkEQ(code, expected);
    }

    public static void testSelector() {
        var template1 = Template.make("a", (String a) -> scope(
            "<\n",
            "x #a x\n",
            ">\n"
        ));

        var template2 = Template.make("a", (String a) -> scope(
            "<\n",
            "y #a y\n",
            ">\n"
        ));

        var template3 = Template.make("a", (Integer a) -> scope(
            "[\n",
            "z #a z\n",
            // Select which template should be used:
            a > 0 ? template1.asToken("A_" + a)
                  : template2.asToken("B_" + a),
            "]\n"
        ));

        var template4 = Template.make(() -> scope(
            "{\n",
            template3.asToken(-1),
            "break\n",
            template3.asToken(0),
            "break\n",
            template3.asToken(1),
            "break\n",
            template3.asToken(2),
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
        var binding1 = new TemplateBinding<Template.OneArg<Integer>>();

        var template1 = Template.make("i", (Integer i) -> scope(
            "[ #i\n",
            // We cannot yet use the template1 directly, as it is being defined.
            // So we use binding1 instead.
            i < 0 ? "done\n" : binding1.get().asToken(i - 1),
            "] #i\n"
        ));
        binding1.bind(template1);

        var template2 = Template.make(() -> scope(
            "{\n",
            // Now, we can use template1 normally, as it is already defined.
            template1.asToken(3),
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
        var template1 = Template.make(() -> scope(
            let("f", fuel()),

            "<#f>\n"
        ));

        // Binding allows use of template2 inside template2, via the Binding indirection.
        var binding2 = new TemplateBinding<Template.OneArg<Integer>>();
        var template2 = Template.make("i", (Integer i) -> scope(
            let("f", fuel()),

            "[ #i #f\n",
            template1.asToken(),
            fuel() <= 60.f ? "done" : binding2.get().asToken(i - 1),
            "] #i #f\n"
        ));
        binding2.bind(template2);

        var template3 = Template.make(() -> scope(
            "{\n",
            template2.asToken(3),
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
        var template1 = Template.make(() -> scope(
            setFuelCost(2.0f),
            let("f", fuel()),

            "<#f>\n"
        ));

        // Binding allows use of template2 inside template2, via the Binding indirection.
        var binding2 = new TemplateBinding<Template.OneArg<Integer>>();
        var template2 = Template.make("i", (Integer i) -> scope(
            setFuelCost(3.0f),
            let("f", fuel()),

            "[ #i #f\n",
            template1.asToken(),
            fuel() <= 5.f ? "done\n" : binding2.get().asToken(i - 1),
            "] #i #f\n"
        ));
        binding2.bind(template2);

        var template3 = Template.make(() -> scope(
            setFuelCost(5.0f),
            let("f", fuel()),

            "{ #f\n",
            template2.asToken(3),
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

    public static void testDataNames0a() {
        var template = Template.make(() -> scope(
            // When a DataName is added, it is immediately available afterwards.
            // This may seem trivial, but it requires that either both "add" and
            // "sample" happen in lambda execution, or in token evaluation.
            // Otherwise, one can float above the other, and lead to unintuitive
            // behavior.
            addDataName("x", myInt, MUTABLE),
            dataNames(MUTABLE).exactOf(myInt).sampleAndLetAs("v"),
            "sample: #v."
        ));

        String code = template.render();
        checkEQ(code, "sample: x.");
    }

    public static void testDataNames0b() {
        // Test that the scope keeps local DataNames only for the scope, but that
        // we can see DataNames of outer scopes.
        var template = Template.make(() -> scope(
            // Outer scope DataName:
            addDataName("x", myInt, MUTABLE),
            dataNames(MUTABLE).exactOf(myInt).sample((DataName dn) -> scope(
                let("name1", dn.name()),
                "sample: #name1.\n",
                // We can also see the outer DataName:
                dataNames(MUTABLE).exactOf(myInt).sampleAndLetAs("name2"),
                "sample: #name2.\n",
                // Local DataName:
                addDataName("y", myLong, MUTABLE),
                dataNames(MUTABLE).exactOf(myLong).sampleAndLetAs("name3"),
                "sample: #name3.\n"
            )),
            // We can still see the outer scope DataName:
            dataNames(MUTABLE).exactOf(myInt).sampleAndLetAs("name4"),
            "sample: #name4.\n",
            // But we cannot see the DataNames that are local to the inner scope.
            // So here, we will always see "z", and never "y".
            addDataName("z", myLong, MUTABLE),
            dataNames(MUTABLE).exactOf(myLong).sampleAndLetAs("name5"),
            "sample: #name5.\n"
        ));

        String code = template.render();
        String expected =
            """
            sample: x.
            sample: x.
            sample: y.
            sample: x.
            sample: z.
            """;
        checkEQ(code, expected);
    }

    public static void testDataNames0c() {
        // Test that hashtag replacements that are local to inner scopes are
        // only visible to inner scopes, but dollar replacements are the same
        // for the whole Template.
        var template = Template.make(() -> scope(
            let("global", "GLOBAL"),
            "g: #global. $a\n",
            // Create a dummy DataName soe we can create the scope.
            addDataName("x", myInt, MUTABLE),
            dataNames(MUTABLE).exactOf(myInt).sample((DataName dn) -> scope(
                "g: #global. $b\n",
                let("local", "LOCAL1"),
                "l: #local. $c\n"
            )),
            "g: #global. $d\n",
            // Open the scope again just to see if we can create the local again there.
            dataNames(MUTABLE).exactOf(myInt).sample((DataName dn) -> scope(
                "g: #global. $e\n",
                let("local", "LOCAL2"),
                "l: #local. $f\n"
            )),
            // We can now use the "local" hashtag replacement again, since it
            // was previously only defined in an inner scope.
            let("local", "LOCAL3"),
            "g: #global. $g\n",
            "l: #local. $h\n"
        ));

        String code = template.render();
        String expected =
            """
            g: GLOBAL. a_1
            g: GLOBAL. b_1
            l: LOCAL1. c_1
            g: GLOBAL. d_1
            g: GLOBAL. e_1
            l: LOCAL2. f_1
            g: GLOBAL. g_1
            l: LOCAL3. h_1
            """;
        checkEQ(code, expected);
    }

    public static void testDataNames0d() {
        var template = Template.make(() -> scope(
            addDataName("x", myInt, MUTABLE),
            addDataName("y", myInt, MUTABLE),
            addDataName("z", myInt, MUTABLE),
            addDataName("a", myLong, MUTABLE),
            addDataName("b", myLong, MUTABLE),
            addDataName("c", myLong, MUTABLE),
            dataNames(MUTABLE).exactOf(myInt).forEach((DataName dn) -> scope(
                let("name", dn.name()),
                let("type", dn.type()),
                "listI: #name #type.\n"
            )),
            dataNames(MUTABLE).exactOf(myLong).forEach((DataName dn) -> scope(
                let("name", dn.name()),
                let("type", dn.type()),
                "listL: #name #type.\n"
            ))
        ));

        String code = template.render();
        String expected =
            """
            listI: x int.
            listI: y int.
            listI: z int.
            listL: a long.
            listL: b long.
            listL: c long.
            """;
        checkEQ(code, expected);
    }
    public static void testDataNames1() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> scope(
            "[",
            dataNames(MUTABLE_OR_IMMUTABLE).exactOf(myInt).hasAny(),
            ", ",
            dataNames(MUTABLE_OR_IMMUTABLE).exactOf(myInt).count(),
            ", names: {",
            String.join(", ", dataNames(MUTABLE_OR_IMMUTABLE).exactOf(myInt).toList().stream().map(DataName::name).toList()),
            "}]\n"
        ));

        var template2 = Template.make("name", "type", (String name, DataName.Type type) -> scope(
            addDataName(name, type, MUTABLE),
            "define #type #name\n",
            template1.asToken()
        ));

        var template3 = Template.make(() -> scope(
            "<\n",
            hook1.insert(template2.asToken($("name"), myInt)),
            "$name = 5\n",
            ">\n"
        ));

        var template4 = Template.make(() -> scope(
            "{\n",
            template1.asToken(),
            hook1.anchor(
                template1.asToken(),
                "something\n",
                template3.asToken(),
                "more\n",
                template1.asToken(),
                "more\n",
                template2.asToken($("name"), myInt),
                "more\n",
                template1.asToken()
            ),
            template1.asToken(),
            "}\n"
        ));

        String code = template4.render();
        String expected =
            """
            {
            [false, 0, names: {}]
            define int name_4
            [true, 1, names: {name_4}]
            [false, 0, names: {}]
            something
            <
            name_4 = 5
            >
            more
            [true, 1, names: {name_4}]
            more
            define int name_1
            [true, 2, names: {name_4, name_1}]
            more
            [true, 1, names: {name_4}]
            [false, 0, names: {}]
            }
            """;
        checkEQ(code, expected);
    }

    public static void testDataNames2() {
        var hook1 = new Hook("Hook1");

        var template0 = Template.make("type", "mutability", (DataName.Type type, DataName.Mutability mutability) -> scope(
            "  #mutability: [",
            dataNames(mutability).exactOf(myInt).hasAny(),
            ", ",
            dataNames(mutability).exactOf(myInt).count(),
            ", names: {",
            String.join(", ", dataNames(mutability).exactOf(myInt).toList().stream().map(DataName::name).toList()),
            "}]\n"
        ));

        var template1 = Template.make("type", (DataName.Type type) -> scope(
            "[#type:\n",
            template0.asToken(type, MUTABLE),
            template0.asToken(type, IMMUTABLE),
            template0.asToken(type, MUTABLE_OR_IMMUTABLE),
            "]\n"
        ));

        var template2 = Template.make("name", "type", (String name, DataName.Type type) -> scope(
            addDataName(name, type, MUTABLE),
            "define mutable #type #name\n",
            template1.asToken(type)
        ));

        var template3 = Template.make("name", "type", (String name, DataName.Type type) -> scope(
            addDataName(name, type, IMMUTABLE),
            "define immutable #type #name\n",
            template1.asToken(type)
        ));

        var template4 = Template.make("type", (DataName.Type type) -> scope(
            "{ $store\n",
            hook1.insert(template2.asToken($("name"), type)),
            "$name = 5\n",
            "} $store\n"
        ));

        var template5 = Template.make("type", (DataName.Type type) -> scope(
            "{ $load\n",
            hook1.insert(template3.asToken($("name"), type)),
            "blackhole($name)\n",
            "} $load\n"
        ));

        var template6 = Template.make("type", (DataName.Type type) -> scope(
            dataNames(MUTABLE).exactOf(type).sampleAndLetAs("v"),
            "{ $sample\n",
            "#v = 7\n",
            "} $sample\n"
        ));

        var template7 = Template.make("type", (DataName.Type type) -> scope(
            dataNames(MUTABLE_OR_IMMUTABLE).exactOf(type).sampleAndLetAs("v"),
            "{ $sample\n",
            "blackhole(#v)\n",
            "} $sample\n"
        ));

        var template8 = Template.make(() -> scope(
            "class $X {\n",
            template1.asToken(myInt),
            hook1.anchor(
                "begin $scope\n",
                template1.asToken(myInt),
                "start with immutable\n",
                template5.asToken(myInt),
                "then load from it\n",
                template7.asToken(myInt),
                template1.asToken(myInt),
                "now make something mutable\n",
                template4.asToken(myInt),
                "then store to it\n",
                template6.asToken(myInt),
                template1.asToken(myInt)
            ),
            template1.asToken(myInt),
            "}\n"
        ));

        String code = template8.render();
        String expected =
            """
            class X_1 {
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [false, 0, names: {}]
              MUTABLE_OR_IMMUTABLE: [false, 0, names: {}]
            ]
            define immutable int name_10
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [true, 1, names: {name_10}]
              MUTABLE_OR_IMMUTABLE: [true, 1, names: {name_10}]
            ]
            define mutable int name_21
            [int:
              MUTABLE: [true, 1, names: {name_21}]
              IMMUTABLE: [true, 1, names: {name_10}]
              MUTABLE_OR_IMMUTABLE: [true, 2, names: {name_10, name_21}]
            ]
            begin scope_1
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [false, 0, names: {}]
              MUTABLE_OR_IMMUTABLE: [false, 0, names: {}]
            ]
            start with immutable
            { load_10
            blackhole(name_10)
            } load_10
            then load from it
            { sample_16
            blackhole(name_10)
            } sample_16
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [true, 1, names: {name_10}]
              MUTABLE_OR_IMMUTABLE: [true, 1, names: {name_10}]
            ]
            now make something mutable
            { store_21
            name_21 = 5
            } store_21
            then store to it
            { sample_27
            name_21 = 7
            } sample_27
            [int:
              MUTABLE: [true, 1, names: {name_21}]
              IMMUTABLE: [true, 1, names: {name_10}]
              MUTABLE_OR_IMMUTABLE: [true, 2, names: {name_10, name_21}]
            ]
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [false, 0, names: {}]
              MUTABLE_OR_IMMUTABLE: [false, 0, names: {}]
            ]
            }
            """;
        checkEQ(code, expected);
    }

    public static void testDataNames3() {
        var hook1 = new Hook("Hook1");

        var template0 = Template.make("type", "mutability", (DataName.Type type, DataName.Mutability mutability) -> scope(
            "  #mutability: [",
            dataNames(mutability).exactOf(myInt).hasAny(),
            ", ",
            dataNames(mutability).exactOf(myInt).count(),
            ", names: {",
            String.join(", ", dataNames(mutability).exactOf(myInt).toList().stream().map(DataName::name).toList()),
            "}]\n"
        ));

        var template1 = Template.make("type", (DataName.Type type) -> scope(
            "[#type:\n",
            template0.asToken(type, MUTABLE),
            template0.asToken(type, IMMUTABLE),
            template0.asToken(type, MUTABLE_OR_IMMUTABLE),
            "]\n"
        ));

        var template2 = Template.make(() -> scope(
            "class $Y {\n",
            template1.asToken(myInt),
            hook1.anchor(
                "begin $scope\n",
                template1.asToken(myInt),
                "define mutable $v1\n",
                addDataName($("v1"), myInt, MUTABLE),
                template1.asToken(myInt),
                "define immutable $v2\n",
                addDataName($("v2"), myInt, IMMUTABLE),
                template1.asToken(myInt)
            ),
            template1.asToken(myInt),
            "}\n"
        ));

        String code = template2.render();
        String expected =
            """
            class Y_1 {
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [false, 0, names: {}]
              MUTABLE_OR_IMMUTABLE: [false, 0, names: {}]
            ]
            begin scope_1
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [false, 0, names: {}]
              MUTABLE_OR_IMMUTABLE: [false, 0, names: {}]
            ]
            define mutable v1_1
            [int:
              MUTABLE: [true, 1, names: {v1_1}]
              IMMUTABLE: [false, 0, names: {}]
              MUTABLE_OR_IMMUTABLE: [true, 1, names: {v1_1}]
            ]
            define immutable v2_1
            [int:
              MUTABLE: [true, 1, names: {v1_1}]
              IMMUTABLE: [true, 1, names: {v2_1}]
              MUTABLE_OR_IMMUTABLE: [true, 2, names: {v1_1, v2_1}]
            ]
            [int:
              MUTABLE: [false, 0, names: {}]
              IMMUTABLE: [false, 0, names: {}]
              MUTABLE_OR_IMMUTABLE: [false, 0, names: {}]
            ]
            }
            """;
        checkEQ(code, expected);
    }

    public static void testDataNames4() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("type", (DataName.Type type) -> scope(
            "[#type:\n",
            "  exact: ",
            dataNames(MUTABLE).exactOf(type).hasAny(),
            ", ",
            dataNames(MUTABLE).exactOf(type).count(),
            ", {",
            String.join(", ", dataNames(MUTABLE).exactOf(type).toList().stream().map(DataName::name).toList()),
            "}\n",
            "  subtype: ",
            dataNames(MUTABLE).subtypeOf(type).hasAny(),
            ", ",
            dataNames(MUTABLE).subtypeOf(type).count(),
            ", {",
            String.join(", ", dataNames(MUTABLE).subtypeOf(type).toList().stream().map(DataName::name).toList()),
            "}\n",
            "  supertype: ",
            dataNames(MUTABLE).supertypeOf(type).hasAny(),
            ", ",
            dataNames(MUTABLE).supertypeOf(type).count(),
            ", {",
            String.join(", ", dataNames(MUTABLE).supertypeOf(type).toList().stream().map(DataName::name).toList()),
            "}\n",
            "]\n"
        ));

        List<DataName.Type> types = List.of(myClassA, myClassA1, myClassA2, myClassA11, myClassB);
        var template2 = Template.make(() -> scope(
            "DataNames:\n",
            types.stream().map(t -> template1.asToken(t)).toList()
        ));

        var template3 = Template.make("type", (DataName.Type type) -> scope(
            dataNames(MUTABLE).subtypeOf(type).sampleAndLetAs("name1"),
            "Sample #type: #name1\n",
            dataNames(MUTABLE).subtypeOf(type).sampleAndLetAs("name2", "type2"),
            "Sample #type: #name2 #type2\n",
            dataNames(MUTABLE).subtypeOf(type).sample((DataName dn) -> scope(
                let("name3", dn.name()),
                let("type3", dn.type()),
                let("dn", dn), // format the whole DataName with toString
                "Sample #type: #name3 #type3 #dn\n"
            ))
        ));

        var template4 = Template.make(() -> scope(
            "class $W {\n",
            template2.asToken(),
            hook1.anchor(
                "Create name for myClassA11, should be visible for the super classes\n",
                addDataName($("v1"), myClassA11, MUTABLE),
                template3.asToken(myClassA11),
                template3.asToken(myClassA1),
                template3.asToken(myClassA),
                "Create name for myClassA, should never be visible for the sub classes\n",
                addDataName($("v2"), myClassA, MUTABLE),
                template3.asToken(myClassA11),
                template3.asToken(myClassA1),
                template2.asToken()
            ),
            template2.asToken(),
            "}\n"
        ));

        String code = template4.render();
        String expected =
            """
            class W_1 {
            DataNames:
            [myClassA:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassA1:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassA2:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassA11:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassB:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            Create name for myClassA11, should be visible for the super classes
            Sample myClassA11: v1_1
            Sample myClassA11: v1_1 myClassA11
            Sample myClassA11: v1_1 myClassA11 DataName[name=v1_1, type=myClassA11, mutable=true, weight=1]
            Sample myClassA1: v1_1
            Sample myClassA1: v1_1 myClassA11
            Sample myClassA1: v1_1 myClassA11 DataName[name=v1_1, type=myClassA11, mutable=true, weight=1]
            Sample myClassA: v1_1
            Sample myClassA: v1_1 myClassA11
            Sample myClassA: v1_1 myClassA11 DataName[name=v1_1, type=myClassA11, mutable=true, weight=1]
            Create name for myClassA, should never be visible for the sub classes
            Sample myClassA11: v1_1
            Sample myClassA11: v1_1 myClassA11
            Sample myClassA11: v1_1 myClassA11 DataName[name=v1_1, type=myClassA11, mutable=true, weight=1]
            Sample myClassA1: v1_1
            Sample myClassA1: v1_1 myClassA11
            Sample myClassA1: v1_1 myClassA11 DataName[name=v1_1, type=myClassA11, mutable=true, weight=1]
            DataNames:
            [myClassA:
              exact: true, 1, {v2_1}
              subtype: true, 2, {v1_1, v2_1}
              supertype: true, 1, {v2_1}
            ]
            [myClassA1:
              exact: false, 0, {}
              subtype: true, 1, {v1_1}
              supertype: true, 1, {v2_1}
            ]
            [myClassA2:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: true, 1, {v2_1}
            ]
            [myClassA11:
              exact: true, 1, {v1_1}
              subtype: true, 1, {v1_1}
              supertype: true, 2, {v1_1, v2_1}
            ]
            [myClassB:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            DataNames:
            [myClassA:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassA1:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassA2:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassA11:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [myClassB:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            }
            """;
        checkEQ(code, expected);
    }

    // Test duplicate names in safe cases.
    public static void testDataNames5() {
        var hook1 = new Hook("Hook1");
        var hook2 = new Hook("Hook2");

        // It is safe in separate Hook scopes.
        var template1 = Template.make(() -> scope(
            hook1.anchor(
                addDataName("name1", myInt, MUTABLE)
            ),
            hook1.anchor(
                addDataName("name1", myInt, MUTABLE)
            )
        ));

        // It is safe in separate Template scopes.
        var template2 = Template.make(() -> scope(
            addDataName("name2", myInt, MUTABLE)
        ));
        var template3 = Template.make(() -> scope(
            template2.asToken(),
            template2.asToken()
        ));

        var template4 = Template.make(() -> scope(
            // The following is not safe, it would collide
            // with (1), because it would be inserted to the
            // hook1.anchor in template5, and hence be available
            // inside the scope where (1) is available.
            // See: testFailingAddNameDuplication8
            // addDataName("name", myInt, MUTABLE),
            hook2.anchor(
                // (2) This one is added second. Since it is
                //     inside the hook2.anchor, it does not go
                //     out to the hook1.anchor, and is not
                //     available inside the scope of (1).
                addDataName("name3", myInt, MUTABLE)
            )
        ));
        var template5 = Template.make(() -> scope(
            hook1.anchor(
                // (1) this is the first one we add.
                addDataName("name3", myInt, MUTABLE)
            )
        ));

        // Put it all together into a single test.
        var template6 = Template.make(() -> scope(
            template1.asToken(),
            template3.asToken(),
            template5.asToken()
        ));

        String code = template1.render();
        String expected = "";
        checkEQ(code, expected);
    }

    public static void testDataNames6() {
        var template = Template.make(() -> scope(
            addDataName("x", myInt, IMMUTABLE),
            "int x = 5;\n",
            // A DataName can be captured, and used to define a new one with the same type.
            // It is important that the new DataName can escape the hashtagScope, so we have
            // access to it later.
            // Using "scope", it does not escape.
            dataNames(IMMUTABLE).exactOf(myInt).sample(dn -> scope(
                addDataName("a", dn.type(), MUTABLE),
                let("v1", "a"),
                "int #v1 = x + 1;\n"
            )),
            // Using "flat", is is available.
            dataNames(IMMUTABLE).exactOf(myInt).sample(dn -> flat(
                addDataName("b", dn.type(), MUTABLE),
                let("v2", "b"),
                "int #v2 = x + 2;\n"
            )),
            // Using "nameScope", it does not escape.
            dataNames(IMMUTABLE).exactOf(myInt).sample(dn -> nameScope(
                addDataName("c", dn.type(), MUTABLE),
                let("v3", "c"),
                "int #v3 = x + 3;\n"
            )),
            // Using "hashtagScope", is is available.
            dataNames(IMMUTABLE).exactOf(myInt).sample(dn -> hashtagScope(
                addDataName("d", dn.type(), MUTABLE),
                let("v4", "d"),
                "int #v4 = x + 4;\n"
            )),
            dataNames(MUTABLE_OR_IMMUTABLE).exactOf(myInt).forEach("name", "type", dn -> scope(
                "available: #name #type.\n"
            )),
            // Check that hashtags escape correctly too.
            "hashtag v2: #v2.\n",
            "hashtag v3: #v3.\n",
            let("v1", "aaa"),
            let("v4", "ddd")
        ));

        String code = template.render();
        String expected =
            """
            int x = 5;
            int a = x + 1;
            int b = x + 2;
            int c = x + 3;
            int d = x + 4;
            available: x int.
            available: b int.
            available: d int.
            hashtag v2: b.
            hashtag v3: c.
            """;
        checkEQ(code, expected);
    }

    public static void testStructuralNames0() {
        var template = Template.make(() -> scope(
            // When a StructuralName is added, it is immediately available afterwards.
            // This may seem trivial, but it requires that either both "add" and
            // "sample" happen in lambda execution, or in token evaluation.
            // Otherwise, one can float above the other, and lead to unintuitive
            // behavior.
            addStructuralName("x", myStructuralTypeA),
            structuralNames().exactOf(myStructuralTypeA).sampleAndLetAs("v"),
            "sample: #v."
        ));

        String code = template.render();
        checkEQ(code, "sample: x.");
    }

    public static void testStructuralNames1() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("type", (StructuralName.Type type) -> scope(
            "[#type:\n",
            "  exact: ",
            structuralNames().exactOf(type).hasAny(),
            ", ",
            structuralNames().exactOf(type).count(),
            ", {",
            String.join(", ", structuralNames().exactOf(type).toList().stream().map(StructuralName::name).toList()),
            "}\n",
            "  subtype: ",
            structuralNames().subtypeOf(type).hasAny(),
            ", ",
            structuralNames().subtypeOf(type).count(),
            ", {",
            String.join(", ", structuralNames().subtypeOf(type).toList().stream().map(StructuralName::name).toList()),
            "}\n",
            "  supertype: ",
            structuralNames().supertypeOf(type).hasAny(),
            ", ",
            structuralNames().supertypeOf(type).count(),
            ", {",
            String.join(", ", structuralNames().supertypeOf(type).toList().stream().map(StructuralName::name).toList()),
            "}\n",
            "]\n"
        ));

        List<StructuralName.Type> types = List.of(myStructuralTypeA,
                                                  myStructuralTypeA1,
                                                  myStructuralTypeA2,
                                                  myStructuralTypeA11,
                                                  myStructuralTypeB);
        var template2 = Template.make(() -> scope(
            "StructuralNames:\n",
            types.stream().map(t -> template1.asToken(t)).toList()
        ));

        var template3 = Template.make("type", (StructuralName.Type type) -> scope(
            structuralNames().subtypeOf(type).sampleAndLetAs("name1"),
            "Sample #type: #name1\n",
            structuralNames().subtypeOf(type).sampleAndLetAs("name2", "type2"),
            "Sample #type: #name2 #type2\n",
            structuralNames().subtypeOf(type).sample((StructuralName sn) -> scope(
                let("name3", sn.name()),
                let("type3", sn.type()),
                let("sn", sn), // format the whole StructuralName with toString
                "Sample #type: #name3 #type3 #sn\n"
            ))
        ));

        var template4 = Template.make(() -> scope(
            "class $Q {\n",
            template2.asToken(),
            hook1.anchor(
                "Create name for myStructuralTypeA11, should be visible for the supertypes\n",
                addStructuralName($("v1"), myStructuralTypeA11),
                template3.asToken(myStructuralTypeA11),
                template3.asToken(myStructuralTypeA1),
                template3.asToken(myStructuralTypeA),
                "Create name for myStructuralTypeA, should never be visible for the subtypes\n",
                addStructuralName($("v2"), myStructuralTypeA),
                template3.asToken(myStructuralTypeA11),
                template3.asToken(myStructuralTypeA1),
                template2.asToken()
            ),
            template2.asToken(),
            "}\n"
        ));

        String code = template4.render();
        String expected =
            """
            class Q_1 {
            StructuralNames:
            [StructuralA:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralA1:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralA2:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralA11:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralB:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            Create name for myStructuralTypeA11, should be visible for the supertypes
            Sample StructuralA11: v1_1
            Sample StructuralA11: v1_1 StructuralA11
            Sample StructuralA11: v1_1 StructuralA11 StructuralName[name=v1_1, type=StructuralA11, weight=1]
            Sample StructuralA1: v1_1
            Sample StructuralA1: v1_1 StructuralA11
            Sample StructuralA1: v1_1 StructuralA11 StructuralName[name=v1_1, type=StructuralA11, weight=1]
            Sample StructuralA: v1_1
            Sample StructuralA: v1_1 StructuralA11
            Sample StructuralA: v1_1 StructuralA11 StructuralName[name=v1_1, type=StructuralA11, weight=1]
            Create name for myStructuralTypeA, should never be visible for the subtypes
            Sample StructuralA11: v1_1
            Sample StructuralA11: v1_1 StructuralA11
            Sample StructuralA11: v1_1 StructuralA11 StructuralName[name=v1_1, type=StructuralA11, weight=1]
            Sample StructuralA1: v1_1
            Sample StructuralA1: v1_1 StructuralA11
            Sample StructuralA1: v1_1 StructuralA11 StructuralName[name=v1_1, type=StructuralA11, weight=1]
            StructuralNames:
            [StructuralA:
              exact: true, 1, {v2_1}
              subtype: true, 2, {v1_1, v2_1}
              supertype: true, 1, {v2_1}
            ]
            [StructuralA1:
              exact: false, 0, {}
              subtype: true, 1, {v1_1}
              supertype: true, 1, {v2_1}
            ]
            [StructuralA2:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: true, 1, {v2_1}
            ]
            [StructuralA11:
              exact: true, 1, {v1_1}
              subtype: true, 1, {v1_1}
              supertype: true, 2, {v1_1, v2_1}
            ]
            [StructuralB:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            StructuralNames:
            [StructuralA:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralA1:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralA2:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralA11:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            [StructuralB:
              exact: false, 0, {}
              subtype: false, 0, {}
              supertype: false, 0, {}
            ]
            }
            """;
        checkEQ(code, expected);
    }

    public static void testStructuralNames2() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make("type", (StructuralName.Type type) -> scope(
            "[#type: ",
            structuralNames().exactOf(type).hasAny(),
            ", ",
            structuralNames().exactOf(type).count(),
            ", names: {",
            String.join(", ", structuralNames().exactOf(type).toList().stream().map(StructuralName::name).toList()),
            "}]\n"
        ));

        var template2 = Template.make("name", "type", (String name, StructuralName.Type type) -> scope(
            addStructuralName(name, type),
            "define #type #name\n"
        ));

        var template3 = Template.make("type", (StructuralName.Type type) -> scope(
            "{ $access\n",
            hook1.insert(template2.asToken($("name"), type)),
            "$name = 5\n",
            "} $access\n"
        ));

        var template4 = Template.make("type", (StructuralName.Type type) -> scope(
            structuralNames().exactOf(type).sampleAndLetAs("v"),
            "{ $sample\n",
            "blackhole(#v)\n",
            "} $sample\n"
        ));

        var template8 = Template.make(() -> scope(
            "class $X {\n",
            template1.asToken(myStructuralTypeA),
            template1.asToken(myStructuralTypeB),
            hook1.anchor(
                "begin $scope\n",
                template1.asToken(myStructuralTypeA),
                template1.asToken(myStructuralTypeB),
                "start with A\n",
                template3.asToken(myStructuralTypeA),
                "then access it\n",
                template4.asToken(myStructuralTypeA),
                template1.asToken(myStructuralTypeA),
                template1.asToken(myStructuralTypeB),
                "now make a B\n",
                template3.asToken(myStructuralTypeB),
                "then access to it\n",
                template4.asToken(myStructuralTypeB),
                template1.asToken(myStructuralTypeA),
                template1.asToken(myStructuralTypeB)
            ),
            template1.asToken(myStructuralTypeA),
            template1.asToken(myStructuralTypeB),
            "}\n"
        ));

        String code = template8.render();
        String expected =
            """
            class X_1 {
            [StructuralA: false, 0, names: {}]
            [StructuralB: false, 0, names: {}]
            define StructuralA name_6
            define StructuralB name_11
            begin scope_1
            [StructuralA: false, 0, names: {}]
            [StructuralB: false, 0, names: {}]
            start with A
            { access_6
            name_6 = 5
            } access_6
            then access it
            { sample_8
            blackhole(name_6)
            } sample_8
            [StructuralA: true, 1, names: {name_6}]
            [StructuralB: false, 0, names: {}]
            now make a B
            { access_11
            name_11 = 5
            } access_11
            then access to it
            { sample_13
            blackhole(name_11)
            } sample_13
            [StructuralA: true, 1, names: {name_6}]
            [StructuralB: true, 1, names: {name_11}]
            [StructuralA: false, 0, names: {}]
            [StructuralB: false, 0, names: {}]
            }
            """;
        checkEQ(code, expected);
    }

    record MyItem(DataName.Type type, String op) {}

    public static void testListArgument() {
        var template1 = Template.make("item", (MyItem item) -> scope(
            let("type", item.type()),
            let("op", item.op()),
            "#type apply #op\n"
        ));

        var template2 = Template.make("list", (List<MyItem> list) -> scope(
            "class $Z {\n",
            // Use template1 for every item in the list.
            list.stream().map(item -> template1.asToken(item)).toList(),
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

        String code = template2.render(list);
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

    public static void testNestedScopes1() {
        var listDataNames = Template.make(() -> scope(
            "dataNames: {",
            dataNames(MUTABLE).exactOf(myInt).forEach("name", "type", (DataName dn) -> scope(
                "#name #type; "
            )),
            "}\n"
        ));

        var template = Template.make("x", (String x) -> scope(
            "$start\n",
            addDataName("vx", myInt, MUTABLE),
            "x: #x.\n",
            listDataNames.asToken(),
            // A "flat" nesting essencially does nothing but create
            // a list of tokens. It passes through names and hashtags.
            "open flat:\n",
            flat(
                "$flat\n",
                let("y", "YYY"),
                addDataName("vy", myInt, MUTABLE),
                "x: #x.\n",
                "y: #y.\n",
                listDataNames.asToken()
            ),
            "close flat.\n",
            "x: #x.\n",
            "y: #y.\n",
            listDataNames.asToken(),
            // A "hashtagScope" nesting makes hashtags local, but names
            // escape the nesting.
            "open hashtagScope:\n",
            hashtagScope(
                "$hashtagScope\n",
                let("z", "ZZZ1"),
                "z: #z.\n",
                addDataName("vz", myInt, MUTABLE),
                listDataNames.asToken()
            ),
            "close hashtagScope.\n",
            let("z", "ZZZ2"), // we can define it again outside.
            "z: #z.\n",
            listDataNames.asToken(),
            // We can also use hashtagScopes for loops.
            List.of("a", "b", "c").stream().map(str -> hashtagScope(
                "$hashtagScope\n",
                let("str", str), // the hashtag is local to every element
                "str: #str.\n",
                addDataName("v_" + str, myInt, MUTABLE),
                listDataNames.asToken()
            )).toList(),
            "finish str list.\n",
            listDataNames.asToken(),
            // A "nameScope" nesting makes names local, but hashtags
            // escape the nesting.
            "open nameScope:\n",
            nameScope(
                "$nameScope\n",
                let("p", "PPP"),
                "p: #p.\n",
                addDataName("vp", myInt, MUTABLE),
                listDataNames.asToken()
            ),
            "close hashtagScope.\n",
            "p: #p.\n",
            listDataNames.asToken(),
            // A "scope" nesting makes names and hashtags local
            "open scope:\n",
            scope(
                "$scope\n",
                let("q", "QQQ1"),
                "q: #q.\n",
                addDataName("vq", myInt, MUTABLE),
                listDataNames.asToken()
            ),
            "close scope.\n",
            let("q", "QQQ2"),
            "q: #q.\n",
            listDataNames.asToken()
        ));

        String code = template.render("XXX");
        String expected =
            """
            start_1
            x: XXX.
            dataNames: {vx int; }
            open flat:
            flat_1
            x: XXX.
            y: YYY.
            dataNames: {vx int; vy int; }
            close flat.
            x: XXX.
            y: YYY.
            dataNames: {vx int; vy int; }
            open hashtagScope:
            hashtagScope_1
            z: ZZZ1.
            dataNames: {vx int; vy int; vz int; }
            close hashtagScope.
            z: ZZZ2.
            dataNames: {vx int; vy int; vz int; }
            hashtagScope_1
            str: a.
            dataNames: {vx int; vy int; vz int; v_a int; }
            hashtagScope_1
            str: b.
            dataNames: {vx int; vy int; vz int; v_a int; v_b int; }
            hashtagScope_1
            str: c.
            dataNames: {vx int; vy int; vz int; v_a int; v_b int; v_c int; }
            finish str list.
            dataNames: {vx int; vy int; vz int; v_a int; v_b int; v_c int; }
            open nameScope:
            nameScope_1
            p: PPP.
            dataNames: {vx int; vy int; vz int; v_a int; v_b int; v_c int; vp int; }
            close hashtagScope.
            p: PPP.
            dataNames: {vx int; vy int; vz int; v_a int; v_b int; v_c int; }
            open scope:
            scope_1
            q: QQQ1.
            dataNames: {vx int; vy int; vz int; v_a int; v_b int; v_c int; vq int; }
            close scope.
            q: QQQ2.
            dataNames: {vx int; vy int; vz int; v_a int; v_b int; v_c int; }
            """;
        checkEQ(code, expected);
    }

    public static void testNestedScopes2() {
        var listDataNames = Template.make(() -> scope(
            "dataNames: {",
            dataNames(MUTABLE).exactOf(myInt).forEach("name", "type", (DataName dn) -> scope(
                "#name #type; "
            )),
            "}\n"
        ));

        var template = Template.make(() -> scope(
            // Define some global variables.
            List.of("a", "b", "c").stream().map(str -> hashtagScope(
                let("var", "g_" + str),
                addDataName("g_" + str, myInt, MUTABLE),
                "def global #var.\n"
            )).toList(),
            listDataNames.asToken(),
            scope(
                "open scope:\n",
                // Define some variables.
                List.of("i", "j", "k").stream().map(str -> hashtagScope(
                    let("var", "v_" + str),
                    addDataName("v_" + str, myInt, MUTABLE),
                    "def #var.\n"
                )).toList(),
                listDataNames.asToken(),
                scope(
                    "open inner scope:\n",
                    addDataName("v_local", myInt, MUTABLE),
                    "def v_local.\n",
                    listDataNames.asToken(),
                    "close inner scope.\n"
                ),
                listDataNames.asToken(),
                "close scope.\n"
            ),
            listDataNames.asToken()
        ));

        String code = template.render();
        String expected =
            """
            def global g_a.
            def global g_b.
            def global g_c.
            dataNames: {g_a int; g_b int; g_c int; }
            open scope:
            def v_i.
            def v_j.
            def v_k.
            dataNames: {g_a int; g_b int; g_c int; v_i int; v_j int; v_k int; }
            open inner scope:
            def v_local.
            dataNames: {g_a int; g_b int; g_c int; v_i int; v_j int; v_k int; v_local int; }
            close inner scope.
            dataNames: {g_a int; g_b int; g_c int; v_i int; v_j int; v_k int; }
            close scope.
            dataNames: {g_a int; g_b int; g_c int; }
            """;
        checkEQ(code, expected);
    }

    public static void testFailingNestedRendering() {
        var template1 = Template.make(() -> scope(
            "alpha\n"
        ));

        var template2 = Template.make(() -> scope(
            "beta\n",
            // Nested "render" call not allowed!
            template1.render(),
            "gamma\n"
        ));

        String code = template2.render();
    }

    public static void testFailingDollarName1() {
        var template1 = Template.make(() -> scope(
            let("x", $("")) // empty string not allowed
        ));
        String code = template1.render();
    }

    public static void testFailingDollarName2() {
        var template1 = Template.make(() -> scope(
            let("x", $("#abc")) // "#" character not allowed
        ));
        String code = template1.render();
    }

    public static void testFailingDollarName3() {
        var template1 = Template.make(() -> scope(
            let("x", $("abc#")) // "#" character not allowed
        ));
        String code = template1.render();
    }

    public static void testFailingDollarName4() {
        var template1 = Template.make(() -> scope(
            let("x", $(null)) // Null input to dollar
        ));
        String code = template1.render();
    }

    public static void testFailingDollarName5() {
        var template1 = Template.make(() -> scope(
            "$" // empty dollar name
        ));
        String code = template1.render();
    }

    public static void testFailingDollarName6() {
        var template1 = Template.make(() -> scope(
            "asdf$" // empty dollar name
        ));
        String code = template1.render();
    }

    public static void testFailingDollarName7() {
        var template1 = Template.make(() -> scope(
            "asdf$1" // Bad pattern after dollar
        ));
        String code = template1.render();
    }

    public static void testFailingDollarName8() {
        var template1 = Template.make(() -> scope(
            "abc$$abc" // empty dollar name
        ));
        String code = template1.render();
    }

    public static void testFailingLetName1() {
        var template1 = Template.make(() -> scope(
            let(null, $("abc")) // Null input for hashtag name
        ));
        String code = template1.render();
    }

    public static void testFailingHashtagName1() {
        // Empty Template argument
        var template1 = Template.make("", (String x) -> scope(
        ));
        String code = template1.render("abc");
    }

    public static void testFailingHashtagName2() {
        // "#" character not allowed in template argument
        var template1 = Template.make("abc#abc", (String x) -> scope(
        ));
        String code = template1.render("abc");
    }

    public static void testFailingHashtagName3() {
        var template1 = Template.make(() -> scope(
            // Empty let hashtag name not allowed
            let("", "abc")
        ));
        String code = template1.render();
    }

    public static void testFailingHashtagName4() {
        var template1 = Template.make(() -> scope(
            // "#" character not allowed in let hashtag name
            let("xyz#xyz", "abc")
        ));
        String code = template1.render();
    }

    public static void testFailingHashtagName5() {
        var template1 = Template.make(() -> scope(
            "#" // empty hashtag name
        ));
        String code = template1.render();
    }

    public static void testFailingHashtagName6() {
        var template1 = Template.make(() -> scope(
            "asdf#" // empty hashtag name
        ));
        String code = template1.render();
    }

    public static void testFailingHashtagName7() {
        var template1 = Template.make(() -> scope(
            "asdf#1" // Bad pattern after hashtag
        ));
        String code = template1.render();
    }

    public static void testFailingHashtagName8() {
        var template1 = Template.make(() -> scope(
            "abc##abc" // empty hashtag name
        ));
        String code = template1.render();
    }

    public static void testFailingDollarHashtagName1() {
        var template1 = Template.make(() -> scope(
            "#$" // empty hashtag name
        ));
        String code = template1.render();
    }

    public static void testFailingDollarHashtagName2() {
        var template1 = Template.make(() -> scope(
            "$#" // empty dollar name
        ));
        String code = template1.render();
    }

    public static void testFailingDollarHashtagName3() {
        var template1 = Template.make(() -> scope(
            "#$name" // empty hashtag name
        ));
        String code = template1.render();
    }

    public static void testFailingDollarHashtagName4() {
        var template1 = Template.make(() -> scope(
            "$#name" // empty dollar name
        ));
        String code = template1.render();
    }

    public static void testFailingHook() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> scope(
            "alpha\n"
        ));

        var template2 = Template.make(() -> scope(
            "beta\n",
            // Use hook without hook1.anchor
            hook1.insert(template1.asToken()),
            "gamma\n"
        ));

        String code = template2.render();
    }

    public static void testFailingSample1a() {
        var template1 = Template.make(() -> scope(
            // No DataName added yet.
            dataNames(MUTABLE).exactOf(myInt).sampleAndLetAs("v"),
            "v is #v\n"
        ));

        String code = template1.render();
    }

    public static void testFailingSample1b() {
        var template1 = Template.make(() -> scope(
            // No StructuralName added yet.
            structuralNames().exactOf(myStructuralTypeA).sampleAndLetAs("v"),
            "v is #v\n"
        ));

        String code = template1.render();
    }

    public static void testFailingSample2a() {
        var template1 = Template.make(() -> scope(
            // no type restriction
            dataNames(MUTABLE).sampleAndLetAs("v"),
            "v is #v\n"
        ));

        String code = template1.render();
    }

    public static void testFailingSample2b() {
        var template1 = Template.make(() -> scope(
            // no type restriction
            structuralNames().sampleAndLetAs("v"),
            "v is #v\n"
        ));

        String code = template1.render();
    }

    public static void testFailingHashtag1() {
        // Duplicate hashtag definition from arguments.
        var template1 = Template.make("a", "a", (String _, String _) -> scope(
            "nothing\n"
        ));

        String code = template1.render("x", "y");
    }

    public static void testFailingHashtag2() {
        var template1 = Template.make("a", (String _) -> scope(
            // Duplicate hashtag name
            let("a", "x"),
            "nothing\n"
        ));

        String code = template1.render("y");
    }

    public static void testFailingHashtag3() {
        var template1 = Template.make(() -> scope(
            let("a", "x"),
            // Duplicate hashtag name
            let("a", "y"),
            "nothing\n"
        ));

        String code = template1.render();
    }

    public static void testFailingHashtag4() {
        var template1 = Template.make(() -> scope(
            // Missing hashtag name definition
            "#a\n"
        ));

        String code = template1.render();
    }

    public static void testFailingHashtag5() {
        var template1 = Template.make(() -> scope(
            "use before definition: #a\n",
            // let is a token, and is only evaluated after
            // the string above, and so the string above fails.
            let("a", "x")
        ));

        String code = template1.render();
    }

    public static void testFailingBinding1() {
        var binding = new TemplateBinding<Template.ZeroArgs>();
        var template1 = Template.make(() -> scope(
            "nothing\n"
        ));
        binding.bind(template1);
        // Duplicate bind
        binding.bind(template1);
    }

    public static void testFailingBinding2() {
        var binding = new TemplateBinding<Template.ZeroArgs>();
        var template1 = Template.make(() -> scope(
            "nothing\n",
            // binding was never bound.
            binding.get()
        ));
        // Should have bound the binding here.
        String code = template1.render();
    }

    public static void testFailingAddDataName1() {
        var template1 = Template.make(() -> scope(
            // Must pick either MUTABLE or IMMUTABLE.
            addDataName("name", myInt, MUTABLE_OR_IMMUTABLE)
        ));
        String code = template1.render();
    }

    public static void testFailingAddDataName2() {
        var template1 = Template.make(() -> scope(
            // weight out of bounds [0..1000]
            addDataName("name", myInt, MUTABLE, 0)
        ));
        String code = template1.render();
    }

    public static void testFailingAddDataName3() {
        var template1 = Template.make(() -> scope(
            // weight out of bounds [0..1000]
            addDataName("name", myInt, MUTABLE, -1)
        ));
        String code = template1.render();
    }

    public static void testFailingAddDataName4() {
        var template1 = Template.make(() -> scope(
            // weight out of bounds [0..1000]
            addDataName("name", myInt, MUTABLE, 1001)
        ));
        String code = template1.render();
    }

    public static void testFailingAddStructuralName1() {
        var template1 = Template.make(() -> scope(
            // weight out of bounds [0..1000]
            addStructuralName("name", myStructuralTypeA, 0)
        ));
        String code = template1.render();
    }

    public static void testFailingAddStructuralName2() {
        var template1 = Template.make(() -> scope(
            // weight out of bounds [0..1000]
            addStructuralName("name", myStructuralTypeA, -1)
        ));
        String code = template1.render();
    }

    public static void testFailingAddStructuralName3() {
        var template1 = Template.make(() -> scope(
            // weight out of bounds [0..1000]
            addStructuralName("name", myStructuralTypeA, 1001)
        ));
        String code = template1.render();
    }

    // Duplicate name in the same scope, name identical -> expect RendererException.
    public static void testFailingAddNameDuplication1() {
        var template1 = Template.make(() -> scope(
            addDataName("name", myInt, MUTABLE),
            addDataName("name", myInt, MUTABLE)
        ));
        String code = template1.render();
    }

    // Duplicate name in the same scope, names have different mutability -> expect RendererException.
    public static void testFailingAddNameDuplication2() {
        var template1 = Template.make(() -> scope(
            addDataName("name", myInt, MUTABLE),
            addDataName("name", myInt, IMMUTABLE)
        ));
        String code = template1.render();
    }

    // Duplicate name in the same scope, names have different type -> expect RendererException.
    public static void testFailingAddNameDuplication3() {
        var template1 = Template.make(() -> scope(
            addDataName("name", myInt, MUTABLE),
            addDataName("name", myLong, MUTABLE)
        ));
        String code = template1.render();
    }

    // Duplicate name in the same scope, name identical -> expect RendererException.
    public static void testFailingAddNameDuplication4() {
        var template1 = Template.make(() -> scope(
            addStructuralName("name", myStructuralTypeA),
            addStructuralName("name", myStructuralTypeA)
        ));
        String code = template1.render();
    }

    // Duplicate name in the same scope, names have different type -> expect RendererException.
    public static void testFailingAddNameDuplication5() {
        var template1 = Template.make(() -> scope(
            addStructuralName("name", myStructuralTypeA),
            addStructuralName("name", myStructuralTypeB)
        ));
        String code = template1.render();
    }

    // Duplicate name in inner Template, name identical -> expect RendererException.
    public static void testFailingAddNameDuplication6() {
        var template1 = Template.make(() -> scope(
            addDataName("name", myInt, MUTABLE)
        ));
        var template2 = Template.make(() -> scope(
            addDataName("name", myInt, MUTABLE),
            template1.asToken()
        ));
        String code = template2.render();
    }

    // Duplicate name in Hook scope, name identical -> expect RendererException.
    public static void testFailingAddNameDuplication7() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> scope(
            addDataName("name", myInt, MUTABLE),
            hook1.anchor(
                addDataName("name", myInt, MUTABLE)
            )
        ));
        String code = template1.render();
    }

    // Duplicate name in Hook.insert, name identical -> expect RendererException.
    public static void testFailingAddNameDuplication8() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> scope(
            addDataName("name", myInt, MUTABLE)
        ));

        var template2 = Template.make(() -> scope(
            hook1.anchor(
                addDataName("name", myInt, MUTABLE),
                hook1.insert(template1.asToken())
            )
        ));
        String code = template2.render();
    }

    public static void testFailingScope1() {
        var template = Template.make(() -> scope(
            flat(
                let("x", "x1") // escapes
            ),
            let("x", "x2") // second definition
        ));
        String code = template.render();
    }

    public static void testFailingScope2() {
        var template = Template.make(() -> scope(
            nameScope(
                let("x", "x1") // escapes
            ),
            let("x", "x2") // second definition
        ));
        String code = template.render();
    }

    public static void expectRendererException(FailingTest test, String errorPrefix) {
        try {
            test.run();
            System.out.println("Should have thrown RendererException with prefix: " + errorPrefix);
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

    public static void expectUnsupportedOperationException(FailingTest test, String errorPrefix) {
        try {
            test.run();
            System.out.println("Should have thrown UnsupportedOperationException with prefix: " + errorPrefix);
            throw new RuntimeException("Should have thrown!");
        } catch(UnsupportedOperationException e) {
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
