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

public class TestTemplate {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        testSingleLine();
        testMultiLine();
        testBodyElements();
        testWithOneArguments();
        testWithTwoArguments();
        testRecursive();
        testHook();

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

    public static void testBodyElements() {
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

    public static void testHook() {
        var hook1 = new Hook("Hook1");

        var template1 = Template.make(() -> body("Hello\n"));

        var template2 = Template.make(() -> body(
            "{\n",
            hook1,
            "World\n",
            intoHook(hook1, template1.withArgs()),
            "}"
        ));

        String code = template2.withArgs().render();
        String expected =
            """
            xxx""";
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
