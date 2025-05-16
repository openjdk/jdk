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
 * @summary Demonstrate the use of Templates with the Compile Framework.
 *          It displays the use of most features in the Template Framework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main template_framework.examples.TestTutorial
 */

package template_framework.examples;

import java.util.Collections;
import java.util.List;

import compiler.lib.compile_framework.*;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.Hook;
import compiler.lib.template_framework.TemplateBinding;
import compiler.lib.template_framework.Name;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.addName;
import static compiler.lib.template_framework.Template.sampleName;
import static compiler.lib.template_framework.Template.weighNames;

import compiler.lib.template_framework.library.Hooks;

public class TestTutorial {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add java source files.
        comp.addJavaSourceCode("p.xyz.InnerTest1", generateWithListOfTokens());
        comp.addJavaSourceCode("p.xyz.InnerTest2", generateWithTemplateArguments());
        comp.addJavaSourceCode("p.xyz.InnerTest3", generateWithHashtagAndDollarReplacements());
        comp.addJavaSourceCode("p.xyz.InnerTest4", generateWithCustomHooks());
        comp.addJavaSourceCode("p.xyz.InnerTest5", generateWithLibraryHooks());
        comp.addJavaSourceCode("p.xyz.InnerTest6", generateWithRecursionAndBindingsAndFuel());
        comp.addJavaSourceCode("p.xyz.InnerTest7", generateWithNamesSimple());
        comp.addJavaSourceCode("p.xyz.InnerTest8", generateWithNames());

        // Compile the source files.
        // Hint: if you want to see the generated source code, you can enable
        //       printing of the source code that the CompileFramework receives,
        //       with -DCompileFrameworkVerbose=true
        comp.compile();

        // Object ret = p.xyz.InnerTest1.main();
        comp.invoke("p.xyz.InnerTest1", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest2", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest3", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest4", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest5", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest6", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest7", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest8", "main", new Object[] {});
    }

    // This example shows the use of various Tokens.
    public static String generateWithListOfTokens() {
        // A Template is essentially a function / lambda that produces a
        // token body, which is a list of Tokens that are concatenated.
        var templateClass = Template.make(() -> body(
            // The "body" method is filled by a sequence of "Tokens".
            // These can be Strings and multi-line Strings, but also
            // boxed primitives.
            """
            package p.xyz;

            public class InnerTest1 {
                public static void main() {
                    System.out.println("Hello World!");
            """,
            "int a = ", 1, ";\n",
            "float b = ", 1.5f, ";\n",
            // Special Float values are "smartly" formatted!
            "float nan = ", Float.POSITIVE_INFINITY, ";\n",
            "boolean c = ", true, ";\n",
            // Lists of Tokens are also allowed:
            List.of("int ", "d = 5", ";\n"),
            // We can also stream / map over an existing list, or one created on
            // the fly:
            List.of(3, 5, 7, 11).stream().map(i -> "System.out.println(" + i + ");\n").toList(),
            """
                    System.out.println(a + " " + b + " " + nan + " " + c + " " + d);
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // This example shows the use of Templates, with and without arguments.
    public static String generateWithTemplateArguments() {
        // A Template with no arguments.
        var templateHello = Template.make(() -> body(
            """
            System.out.println("Hello");
            """
        ));

        // A Template with a single Integer argument.
        var templateCompare = Template.make("arg", (Integer arg) -> body(
            "System.out.println(", arg, ");\n",  // capture arg via lambda argument
            "System.out.println(#arg);\n",       // capture arg via hashtag replacement
            // The Template Framework allows two ways of formatting Strings, either
            // by appending to the comma-separated list of Tokens, or by hashtag
            // replacements. Appending as a Token works whenever one has a reference
            // to the Object in Java code. But often, this is rather cumbersome and
            // looks awkward, given all the additional quotes and commands required.
            // Optimal would have been Java String Templates, but since those do not
            // currently exist, we use hashtag replacements. These can be either
            // defined by capturing arguments as string names, or by using a "let" definition,
            // see further down for examples. Which one should be preferred is a code
            // style question. Generally, we prefer the use of hashtag replacements
            // because that allows easy use of multiline strings (i.e. text blocks).
            "if (#arg != ", arg, ") { throw new RuntimeException(\"mismatch\"); }\n"
        ));

        // A Template that creates the body of the Class and main method, and then
        // uses the two Templates above inside it.
        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest2 {
                public static void main() {
            """,
                    templateHello.asToken(),
                    templateCompare.asToken(7),
                    templateCompare.asToken(42),
            """
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // Example with hashtag replacements (arguments and let), and $-name renamings.
    // Note: hashtag replacements are a workaround for the missing string templates.
    //       If we had string templates, we could just capture the typed lambda
    //       arguments, and use them directly in the String via string templating.
    public static String generateWithHashtagAndDollarReplacements() {
        var template1 = Template.make("x", (Integer x) -> body(
            // We have the "#x" hashtag replacement from the argument capture above.
            // Additionally, we can define "#con" as a hashtag replacement from let:
            let("con", 3 * x),
            // In the code below, we use "var" as a local variable. But if we were
            // to instantiate this template twice, the names could conflict. Hence,
            // we automatically rename the names that have a $ prepended.
            """
            int $var = #con;
            System.out.println("T1: #x, #con, " + $var);
            """
        ));

        var template2 = Template.make("x", (Integer x) ->
            // Sometimes it can be helpful to not just create a hashtag replacement
            // with let, but also to capture the variable.
            let("y", 11 * x, y ->
                body(
                    """
                    System.out.println("T2: #x, #y");
                    """,
                    template1.asToken(y)
                )
            )
        );

        // This template generates an int variable and assigns it a value.
        // Together with template4, we see that each template has a unique renaming
        // for a $-name replacement.
        var template3 = Template.make("name", "value", (String name, Integer value) -> body(
            """
            int #name = #value; // Note: $var is not #name
            """
        ));

        var template4 = Template.make(() -> body(
            """
            // We will define the variable $var:
            """,
            // We can capture the $-name programmatically, and pass it to other templates:
            template3.asToken($("var"), 42),
            """
            if ($var != 42) { throw new RuntimeException("Wrong value!"); }
            """
        ));

        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest3 {
                public static void main() {
            """,
                    template1.asToken(1),
                    template1.asToken(7),
                    template2.asToken(2),
                    template2.asToken(5),
                    template4.asToken(),
            """
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // In this example, we look at the use of Hooks. They allow us to reach back, to outer
    // scopes. For example, we can reach out from inside a method body to a hook set at
    // the top of the class, and insert a field.
    public static String generateWithCustomHooks() {
        // We can define a custom hook.
        // Note: generally we prefer using the pre-defined CLASS_HOOK and METHOD_HOOK from the library,
        //       whenever possible. See also the example after this one.
        var myHook = new Hook("MyHook");

        var template1 = Template.make("name", "value", (String name, Integer value) -> body(
            """
            public static int #name = #value;
            """
        ));

        var template2 = Template.make("x", (Integer x) -> body(
            """
            // Let us go back to the hook, and define a field named $field...
            """,
            myHook.insert(template1.asToken($("field"), x)),
            """
            System.out.println("$field: " + $field);
            if ($field != #x) { throw new RuntimeException("Wrong value!"); }
            """
        ));

        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest4 {
            """,
            // We set a Hook outside the main method, but inside the Class.
            // The Hook is set for the Tokens inside the set braces.
            // As long as the hook is set, we can insert code into the hook,
            // here we can define static fields for example.
            myHook.set(
                """
                public static void main() {
                """,
                    template2.asToken(5),
                    template2.asToken(7),
                """
                }
                """
            ),
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // We saw the use of custom hooks above, but now we look at the use of CLASS_HOOK and METHOD_HOOK
    // from the Template Library.
    public static String generateWithLibraryHooks() {
        var templateStaticField = Template.make("name", "value", (String name, Integer value) -> body(
            """
            static { System.out.println("Defining static field #name"); }
            public static int #name = #value;
            """
        ));

        var templateLocalVariable = Template.make("name", "value", (String name, Integer value) -> body(
            """
            System.out.println("Defining local variable #name");
            int #name = #value;
            """
        ));

        var templateMethodBody = Template.make(() -> body(
            """
            // Let's define a local variable $var and a static field $field.
            """,
            Hooks.CLASS_HOOK.insert(templateStaticField.asToken($("field"), 5)),
            Hooks.METHOD_HOOK.insert(templateLocalVariable.asToken($("var"), 11)),
            """
            System.out.println("$field: " + $field);
            System.out.println("$var: " + $var);
            if ($field * $var != 55) { throw new RuntimeException("Wrong value!"); }
            """
        ));

        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest5 {
            """,
            // Class Hook for fields.
            Hooks.CLASS_HOOK.set(
                """
                public static void main() {
                """,
                // Method Hook for local variables, and earlier computations.
                Hooks.METHOD_HOOK.set(
                    """
                    // This is the beginning of the "main" method body.
                    System.out.println("Welcome to main!");
                    """,
                    templateMethodBody.asToken(),
                    """
                    System.out.println("Going to call other...");
                    other();
                    """
                ),
                """
                }

                private static void other() {
                """,
                // Have a separate method hook for other, so that it can insert
                // its own local variables.
                Hooks.METHOD_HOOK.set(
                    """
                    System.out.println("Welcome to other!");
                    """,
                    templateMethodBody.asToken(),
                    """
                    System.out.println("Done with other.");
                    """
                ),
                """
                }
                """
            ),
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // This example shows the use of bindings to allow cyclic references of Templates,
    // allowing recursive template generation. We also show the use of fuel to limit
    // recursion.
    public static String generateWithRecursionAndBindingsAndFuel() {
        // Binding allows the use of template1 inside of template1, via the binding indirection.
        var binding1 = new TemplateBinding<Template.OneArgs<Integer>>();
        var template1 = Template.make("depth", (Integer depth) -> body(
            let("fuel", fuel()),
            """
            System.out.println("At depth #depth with fuel #fuel.");
            """,
            // We cannot yet use template1 directly, as it is being defined.
            // So we use binding1 instead.
            // For every recursion depth, some fuel is automatically subtracted
            // so that the fuel slowly depletes with the depth.
            // We keep the recursion going until the fuel is depleted.
            (fuel() > 0) ? binding1.get().asToken(depth + 1)
                        : "System.out.println(\"Fuel depleted.\");\n",
            """
            System.out.println("Exit depth #depth.");
            """
        ));
        binding1.bind(template1);

        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest6 {
                public static void main() {
                    System.out.println("Welcome to main!");
                    """,
                    template1.asToken(0),
                    """
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // Below, we introduce the concept of "Names". Code generation often involves defining
    // fields and variables, which are then available inside a defined scope, and can be
    // sampled in any nested scope. To allow the use of names for multiple applications
    // (e.g. fields, variables, methods, etc.), we define a Name, which captures the String
    // representation to be used in code, as well as its type and if it is mutable.
    //
    // To get started, we show an example where all Names have the same type, and where
    // all Names are mutable. For simplicity, our type represents the primitive int type.
    private record MySimpleType() implements Name.Type {
        // The type is only subtype of itself. This is relevant when sampling or weighing
        // Names, because we do not just sample from the given type, but also its subtypes.
        @Override
        public boolean isSubtypeOf(Name.Type other) {
            return other instanceof MySimpleType();
        }

        // The name of the type can later be accessed, and used in code. We are working
        // with ints, so that is what we return.
        @Override
        public String name() { return "int"; }
    }
    private static final MySimpleType mySimpleType = new MySimpleType();

    // In this Example, we generate 3 fields, and add their names to the
    // current scope. In a nested Template, we can then sample one of these
    // Names, which gives us one of the fields. We increment that randomly
    // chosen field. At the end, we print all three fields.
    public static String generateWithNamesSimple() {
        var templateMain = Template.make(() -> body(
            // Sample a random Name, i.e. field, and assign its name to
            // the hashtag replacement "#f".
            // We are picking a mutable Name, because we are not just
            // reading but also writing to the field.
            let("f", sampleName(mySimpleType, true).name()),
            """
            // Let us now sample a random field #f, and increment it.
            #f += 42;
            """
        ));

        var templateClass = Template.make(() -> body(
            // Let us define the names for the three fields.
            // We can then sample from these names in a nested Template.
            // We make all Names mutable, and with the same weight of 1,
            // so that they have equal probability of being sampled.
            addName(new Name($("f1"), mySimpleType, true, 1)),
            addName(new Name($("f2"), mySimpleType, true, 1)),
            addName(new Name($("f3"), mySimpleType, true, 1)),
            """
            package p.xyz;

            public class InnerTest7 {
                // Let us define a some fields.
                public static int $f1;
                public static int $f2;
                public static int $f3;

                public static void main() {
                    // Let us now call the nested template that samples
                    // a random field and increments it.
                    """,
                    templateMain.asToken(),
                    """
                    // Now, we can print all three fields, and see which
                    // one was incremented.
                    System.out.println("f1: " + $f1);
                    System.out.println("f2: " + $f2);
                    System.out.println("f3: " + $f3);
                    // We have two zeros, and one 42.
                    if ($f1 + $f2 + $f3 != 42) { throw new RuntimeException("wrong result!"); }
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // In the example above, we could have easily kept track of the three fields ourselves,
    // and would not have had to rely on the Template Framework's Names for this. However,
    // with more complicated examples, this gets more and more difficult, if not impossible.
    //
    // In the example below, we make the scenario a little more realistic. We work with an
    // int and a long type. In the main method, we add some fields and local variables, and
    // register their Names. When sampling from the main method, we should be able to see
    // both fields and variables that we just registered. But from another method, we should
    // only see the fields, but the local variables from main should not be sampled.
    //
    // Let us now define the wrapper for primitive types such as int and long.
    private record MyPrimitive(String name) implements Name.Type {
        @Override
        public boolean isSubtypeOf(Name.Type other) {
            return other instanceof MyPrimitive(String n) && n == name();
        }

        // Note: the name method is automatically overridden by the record
        //       field accessor.
        // But we would like to also directly use the type in the templates,
        // hence we let "toString" return "int" or "long".
        @Override
        public String toString() { return name(); }
    }
    private static final MyPrimitive myInt = new MyPrimitive("int");
    private static final MyPrimitive myLong = new MyPrimitive("long");

    public static String generateWithNames() {
        // Define a static field.
        var templateStaticField = Template.make("type", (Name.Type type) -> body(
            addName(new Name($("field"), type, true, 1)),
            // Note: since we have overridden MyPrimitive::toString, we can use
            //       the type directly as "#type" in the template, which then
            //       gets hashtag replaced with "int" or "long".
            """
            public static #type $field = 0;
            """
        ));

        // Define a local variable.
        var templateLocalVariable = Template.make("type", (Name.Type type) -> body(
            addName(new Name($("var"), type, true, 1)),
            """
            #type $var = 0;
            """
        ));

        // Sample a random field or variable, from those that are available at
        // the current scope.
        var templateSample = Template.make("type", (Name.Type type) -> body(
            let("name", sampleName(type, true).name()),
            """
            System.out.println("Sampling type #type: #name = " + #name);
            """
        ));

        // Check how many fields and variables are available at the current scope.
        var templateStatus = Template.make(() -> body(
            let("ints", weighNames(myInt, true)),
            let("longs", weighNames(myLong, true)),
            """
            System.out.println("Status: #ints ints, #longs longs.");
            """
        ));

        // Definition of the main method body.
        var templateMain = Template.make(() -> body(
            """
            System.out.println("Starting inside main...");
            """,
            // Check the initial status, there should be nothing available.
            templateStatus.asToken(),
            // Define some local variables. We place them at the beginning of
            // the method, by using the METHOD_HOOK.
            Hooks.METHOD_HOOK.insert(templateLocalVariable.asToken(myInt)),
            Hooks.METHOD_HOOK.insert(templateLocalVariable.asToken(myLong)),
            // Define some static fields. We place them at the top of the class,
            // by using the CLASS_HOOK.
            Hooks.CLASS_HOOK.insert(templateStaticField.asToken(myInt)),
            Hooks.CLASS_HOOK.insert(templateStaticField.asToken(myLong)),
            // If we check the status now, we should see two int and two
            // long names, corresponding to our two fields and variables.
            templateStatus.asToken(),
            // Now, we sample 5 int and 5 long names. We should get a mix
            // of fields and variables. We have access to the fields because
            // we inserted them to the class scope. We have access to the
            // variables because we inserted them to the current method
            // body.
            Collections.nCopies(5, templateSample.asToken(myInt)),
            Collections.nCopies(5, templateSample.asToken(myLong)),
            // The status should not have changed since we last checked.
            templateStatus.asToken(),
            """
            System.out.println("Finishing inside main.");
            """
        ));

        // Definition of another method's body. It is in the same class
        // as the main method, so it has access to the same static fields.
        var templateOther = Template.make(() -> body(
            """
            System.out.println("Starting inside other...");
            """,
            // We should see the fields defined in the main body,
            // one int and one long field.
            templateStatus.asToken(),
            // Sampling 5 random int and 5 random long Names. We should
            // only see the fields, and not the local variables from main.
            Collections.nCopies(5, templateSample.asToken(myInt)),
            Collections.nCopies(5, templateSample.asToken(myLong)),
            // The status should not have changed since we last checked.
            templateStatus.asToken(),
            """
            System.out.println("Finishing inside other.");
            """
        ));

        // Finally, we put it all together in a class.
        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest8 {
            """,
            // Class Hook for fields.
            Hooks.CLASS_HOOK.set(
                """
                public static void main() {
                """,
                // Method Hook for local variables.
                Hooks.METHOD_HOOK.set(
                    """
                    // This is the beginning of the "main" method body.
                    System.out.println("Welcome to main!");
                    """,
                    templateMain.asToken(),
                    """
                    System.out.println("Going to call other...");
                    other();
                    """
                ),
                """
                }

                private static void other() {
                """,
                // Have a separate method hook for other, where it could insert
                // its own local variables (but happens not to).
                Hooks.METHOD_HOOK.set(
                    """
                    System.out.println("Welcome to other!");
                    """,
                    templateOther.asToken(),
                    """
                    System.out.println("Done with other.");
                    """
                ),
                """
                }
                """
            ),
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }
}
