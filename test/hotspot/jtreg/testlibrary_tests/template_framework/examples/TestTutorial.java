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
import compiler.lib.template_framework.DataName;
import compiler.lib.template_framework.StructuralName;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.transparentScope;
import static compiler.lib.template_framework.Template.hashtagScope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.addDataName;
import static compiler.lib.template_framework.Template.dataNames;
import static compiler.lib.template_framework.Template.addStructuralName;
import static compiler.lib.template_framework.Template.structuralNames;
import static compiler.lib.template_framework.DataName.Mutability.MUTABLE;
import static compiler.lib.template_framework.DataName.Mutability.IMMUTABLE;
import static compiler.lib.template_framework.DataName.Mutability.MUTABLE_OR_IMMUTABLE;

import compiler.lib.template_framework.library.Hooks;

public class TestTutorial {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add Java source files.
        comp.addJavaSourceCode("p.xyz.InnerTest1",  generateWithListOfTokens());
        comp.addJavaSourceCode("p.xyz.InnerTest2",  generateWithTemplateArguments());
        comp.addJavaSourceCode("p.xyz.InnerTest3",  generateWithHashtagAndDollarReplacements());
        comp.addJavaSourceCode("p.xyz.InnerTest3b", generateWithHashtagAndDollarReplacements2());
        comp.addJavaSourceCode("p.xyz.InnerTest3c", generateWithHashtagAndDollarReplacements3());
        comp.addJavaSourceCode("p.xyz.InnerTest4",  generateWithCustomHooks());
        comp.addJavaSourceCode("p.xyz.InnerTest5",  generateWithLibraryHooks());
        comp.addJavaSourceCode("p.xyz.InnerTest6",  generateWithRecursionAndBindingsAndFuel());
        comp.addJavaSourceCode("p.xyz.InnerTest7",  generateWithDataNamesSimple());
        comp.addJavaSourceCode("p.xyz.InnerTest8",  generateWithDataNamesForFieldsAndVariables());
        comp.addJavaSourceCode("p.xyz.InnerTest9a", generateWithScopes1());
        comp.addJavaSourceCode("p.xyz.InnerTest9b", generateWithScopes2());
        comp.addJavaSourceCode("p.xyz.InnerTest10", generateWithDataNamesForFuzzing());
        comp.addJavaSourceCode("p.xyz.InnerTest11", generateWithStructuralNamesForMethods());

        // Compile the source files.
        // Hint: if you want to see the generated source code, you can enable
        //       printing of the source code that the CompileFramework receives,
        //       with -DCompileFrameworkVerbose=true
        //       The code may not be nicely formatted, especially regarding
        //       indentation. You might consider dumping the generated code
        //       into an IDE or other auto-formatting tool.
        comp.compile();

        comp.invoke("p.xyz.InnerTest1",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest2",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest3",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest3b", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest3c", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest4",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest5",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest6",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest7",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest8",  "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest9a", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest9b", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest10", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest11", "main", new Object[] {});
    }

    // This example shows the use of various Tokens.
    public static String generateWithListOfTokens() {
        // A Template is essentially a function / lambda that produces a
        // scope, which contains a list of Tokens that are concatenated.
        var templateClass = Template.make(() -> scope(
            // The "scope" arguments are a sequence of "Tokens".
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
        var templateHello = Template.make(() -> scope(
            """
            System.out.println("Hello");
            """
        ));

        // A Template with a single Integer argument.
        var templateCompare = Template.make("arg", (Integer arg) -> scope(
            "System.out.println(", arg, ");\n",  // capture arg via lambda argument
            "System.out.println(#arg);\n",       // capture arg via hashtag replacement
            "System.out.println(#{arg});\n",     // capture arg via hashtag replacement with brackets
            // It would have been optimal to use Java String Templates to format
            // argument values into Strings. However, since these are not (yet)
            // available, the Template Framework provides two alternative ways of
            // formatting Strings:
            // 1) By appending to the comma-separated list of Tokens passed to scope().
            //    Appending as a Token works whenever one has a reference to the Object
            //    in Java code. But often, this is rather cumbersome and looks awkward,
            //    given all the additional quotes and commands required. Hence, it
            //    is encouraged to only use this method when necessary.
            // 2) By hashtag replacements inside a single string. One can either
            //    use "#arg" directly, or use brackets "#{arg}". When possible, one
            //    should prefer avoiding the brackets, as they create additional
            //    noise. However, there are cases where they are useful, for
            //    example "#TYPE_CON" would be parsed as a hashtag replacement
            //    for the hashtag name "TYPE_CON", whereas "#{TYPE}_CON" is
            //    parsed as hashtag name "TYPE", followed by literal string "_CON".
            //    See also: generateWithHashtagAndDollarReplacements2
            //    There are two ways to define the value of a hashtag replacement:
            //    a) Capturing Template arguments as Strings.
            //    b) Using a "let" definition (see examples further down).
            // Which one should be preferred is a code style question. Generally, we
            // prefer the use of hashtag replacements because that allows easy use of
            // multiline strings (i.e. text blocks).
            "if (#arg != ", arg, ") { throw new RuntimeException(\"mismatch\"); }\n"
        ));

        // A Template that creates the body of the Class and main method, and then
        // uses the two Templates above inside it.
        var templateClass = Template.make(() -> scope(
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
    //
    // Important: hashtag replacements are always constrained to a single template
    //            and are not available in any nested templates. Hashtag replacements
    //            are only there to facilitate string templating within the limited
    //            scope of a template. You may consider it like a "local variable"
    //            for code generation purposes only.
    //            If you need to pass some value to a nested Template, consider using
    //            a Template argument, and capturing that Template argument.
    public static String generateWithHashtagAndDollarReplacements() {
        var template1 = Template.make("x", (Integer x) -> scope(
            // We have the "#x" hashtag replacement from the argument capture above.
            // Additionally, we can define "#con" as a hashtag replacement from let:
            let("con", 3 * x),
            // In the code below, we use "var" as a local variable. But if we were
            // to instantiate this template twice, the names could conflict. Hence,
            // we automatically rename the names that have a $ prepended with
            // var_1, var_2, etc.
            """
            int $var = #con;
            System.out.println("T1: #x, #con, " + $var);
            """
        ));

        var template2 = Template.make("x", (Integer x) -> scope(
            // Sometimes it can be helpful to not just create a hashtag replacement
            // with let, but also to capture the variable to use it as lambda parameter.
            let("y", 11 * x, y -> scope(
                """
                System.out.println("T2: #x, #y");
                """,
                template1.asToken(y)
            ))
        ));

        // This template generates an int variable and assigns it a value.
        // Together with template4, we see that each template has a unique renaming
        // for a $-name replacement.
        var template3 = Template.make("name", "value", (String name, Integer value) -> scope(
            """
            int #name = #value; // Note: $var is not #name
            """
        ));

        var template4 = Template.make(() -> scope(
            """
            // We will define the variable $var:
            """,
            // We can capture the $-name programmatically, and pass it to other templates:
            template3.asToken($("var"), 42),
            """
            if ($var != 42) { throw new RuntimeException("Wrong value!"); }
            """
        ));

        var templateClass = Template.make(() -> scope(
            // The Template Framework API only guarantees that every Template use
            // has a unique ID. When using the Templates, all we need is that
            // variables from different Template uses do not conflict. But it can
            // be helpful to understand how the IDs are produced. The implementation
            // simply gives the first Template use the ID=1, and increments from there.
            //
            // In this example, the templateClass is the first Template use, and
            // has ID=1. We never use a dollar replacement here, so the code will
            // not show any "_1".
            """
            package p.xyz;

            public class InnerTest3 {
                public static void main() {
            """,
                    // Second Template use: ID=2 -> var_2
                    template1.asToken(1),
                    // Third Template use: ID=3 -> var_3
                    template1.asToken(7),
                    // Fourth Template use with template2, no use of dollar, so
                    // no "_4" shows up in the generated code. Internally, it
                    // calls template1, which is the fifth Template use, with
                    // ID = 5 -> var_5
                    template2.asToken(2),
                    // Sixth and Seventh Template use -> var_7
                    template2.asToken(5),
                    // Eighth Template use with template4 -> var_8.
                    // Ninth Template use with internal call to template3,
                    // The local "$var" turns to "var_9", but the Template
                    // argument captured value = "var_8" from the outer
                    // template use of $("var").
                    template4.asToken(),
            """
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // In some cases, you may want to transform string arguments. You may
    // be working with types "int" and "long", and want to create names like
    // "INT_CON" and "LONG_CON".
    public static String generateWithHashtagAndDollarReplacements2() {
        // Let us define some final static variables of a specific type.
        var template1 = Template.make("type", (String type) -> scope(
            // The type (e.g. "int") is lower case, let us create the upper case "INT_CON" from it.
            let("TYPE", type.toUpperCase()),
            """
            static final #type #{TYPE}_CON = 42;
            """
        ));

        // Let's write a simple class to demonstrate that this works, i.e. produces compilable code.
        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest3b {
            """,
            template1.asToken("int"),
            template1.asToken("long"),
            """
                public static void main() {
                    if (INT_CON != 42 || LONG_CON != 42) {
                        throw new RuntimeException("Wrong result!");
                    }
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // We already have used "scope" multiple times, but not explained it yet.
    // So far, we have seen "scope" mostly in the context of Template scopes, but they
    // can be used in many contexts as we will see below. They can also be used on
    // their own and in the use of "let", as we will show right now.
    //
    // Scopes are even more relevant for DataNames and Structural names.
    // See: generateWithDataNamesForFieldsAndVariables
    // See: generateWithScopes1
    // See: generateWithScopes2
    public static String generateWithHashtagAndDollarReplacements3() {

        var template1 = Template.make(() -> scope(
            // We can use scopes to limit the liveness of hashtag replacements.
            scope(
                let("x", 3), // does not escape
                """
                static int v1_3 = #x;
                """
            ),
            scope(
                let("x", 5), // does not escape
                """
                static int v1_5 = #x;
                """
            ),
            // Using "scope" does not just limit the liveness / availability
            // of hashtag replacements, but also of DataNames, StructuralNames,
            // and setFuelCost. We can use "hashtagScope" to only limit hashtag
            // replacements.
            hashtagScope(
                let("x", 7), // does not escape
                """
                static int v1_7 = #x;
                """
            ),
            // Using "transparentScope" means the scope is transparent, and the hashtag
            // replacements escape the scope.
            transparentScope(
                let("x", 11), // escapes the "transparentScope".
                """
                static int v1_11a = #x;
                """
            ),
            // The hashtag replacement from the "transparentScope" escaped, and is
            // still available.
            """
            static int v1_11b = #x;
            """
        ));

        var template2 = Template.make("x", (Integer x) -> scope(
            // We can map a list of values to a list of scopes. Using a scope that is
            // non-transparent for hashtag replacements means that we can reuse the same
            // hashtag key when looping / streaming over multiple values.
            List.of(3, 5, 7).stream().map(y -> scope(
                let("y", y), // does not escape -> allows reuse of hashtag key "y".
                """
                static int v2_#{x}_#{y} = #x * #y;
                """
            )).toList()
        ));

        var template3 = Template.make("x", (Integer x) -> scope(
            // When using a "let" that captures the value in a lambda argument, we have
            // to choose what kind of scope we generate. In most cases "scope" or
            // "hashtagScope" are the best, because they limit the hashtag replacement
            // of "y" to the same scope as the lambda argument.
            let("y", x * 11, y -> scope(
                """
                static int v3a_#{x} = #y;
                """
            )),
            // But in rare cases, we may want "y" and some nested "z" to escape.
            let("y", x * 11, y -> transparentScope(
                let("z", y * 2),
                """
                static int v3b_#{x} = #y - #z;
                """
            )),
            // Because of the "transparentScope", "y" and "z" have escaped.
            """
            static int v3c_#{x} = #y - #z;
            """,
            // Side note: We can simulate a "let" without lambda with a "let" that has a lambda.
            // That is not very useful, but a similar trick can be used for other queries, that
            // only provide a lambda version, and where we only want to use the hashtag replacement.
            //
            // Below we see the standard use of "let", where we add a hashtag replacement for "a"
            // for the rest of the enclosing scope. We then also use a lambda version of "let"
            // with a transparent scope, which means that "b" escapes that scope and is also
            // available in the enclosing scope. In the implementation of the framework, we
            // actually use a "transparentScope", so the standard "let" is really just syntactic
            // sugar for the lambda "let" with "transparentScope".
            let("a", -x),
            let("b", -x, b -> transparentScope()),
            """
            static int v3d_#{x} = #a + #b;
            """
        ));

        // Let's write a simple class to demonstrate that this works, i.e. produces compilable code.
        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest3c {
            """,
            template1.asToken(),
            template2.asToken(1),
            template2.asToken(2),
            template3.asToken(2),
            """
                public static void main() {
                    if (v1_3 != 3 ||
                        v1_5 != 5 ||
                        v1_7 != 7 ||
                        v1_11a != 11 ||
                        v1_11b != 11 ||
                        v2_1_3 != 3 ||
                        v2_1_5 != 5 ||
                        v2_1_7 != 7 ||
                        v2_2_3 != 6 ||
                        v2_2_5 != 10 ||
                        v2_2_7 != 14 ||
                        v3a_2 != 22 ||
                        v3b_2 != -22 ||
                        v3c_2 != -22 ||
                        v3d_2 != -4) {
                        throw new RuntimeException("Wrong result!");
                    }
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // In this example, we look at the use of Hooks. They allow us to reach back, to outer
    // scopes. For example, we can reach out from inside a method body to a hook anchored at
    // the top of the class, and insert a field.
    //
    // When we insert to a hook, we have 3 relevant scopes:
    // - Anchor scope: the scope defined at "hook.anchor(scope(...))"
    // - Insertion scope: the scope that is inserted, see "hook.insert(scope(...))"
    // - Caller scope: the scope we insert from.
    //
    // The choice of transparency of an insertion scope (the scope that is inserted) is quite
    // important. A common use case is to insert a DataName.
    // See: generateWithDataNamesForFieldsAndVariables
    // See: generateWithScopes1
    // See: generateWithScopes2
    public static String generateWithCustomHooks() {
        // We can define a custom hook.
        // Note: generally we prefer using the pre-defined CLASS_HOOK and METHOD_HOOK from the library,
        //       whenever possible. See also the example after this one.
        var myHook = new Hook("MyHook");

        var template1 = Template.make("name", "value", (String name, Integer value) -> scope(
            """
            public static int #name = #value;
            """
        ));

        var template2 = Template.make("x", (Integer x) -> scope(
            """
            // Let us go back to where we anchored the hook with anchor() (see 'templateClass' below) and define a field
            // named $field1 there.
            """,
            myHook.insert(scope( // <- insertion scope
                """
                public static int $field1 = #x;
                """
                // Note that we were able to use the dollar replacement "$field1" and the hashtag
                // replacement "#x" inside the scope that is inserted to myHook.
            )),
            """
            // We can do that by inserting a scope like above, or by inserting a template, like below.
            //
            // Which method is used is up to the user. General guidance is if the same code may also
            // be inserted elsewhere, one should lean towards inserting templates. But in many cases
            // it is nice to see the inserted code directly, and to be able to use hashtag replacements
            // from the outer scope directly, without having to route them via template arguments,
            // as we have to do below.
            """,
            // <- caller scope
            myHook.insert(template1.asToken($("field2"), x)),
            """
            System.out.println("$field1: " + $field1);
            System.out.println("$field2: " + $field2);
            if ($field1 != #x) { throw new RuntimeException("Wrong value 1!"); }
            if ($field2 != #x) { throw new RuntimeException("Wrong value 2!"); }
            """
        ));

        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest4 {
            """,
            // We anchor a Hook outside the main method, but inside the Class.
            // Anchoring a Hook requires the definition of an inner scope,
            // aka the "anchor scope", spanning the braces of the "anchor" call.
            // Any Hook.insert that happens inside this scope goes to the top of
            // that scope.
            myHook.anchor(scope( // <- anchor scope
                // Any Hook.insert goes here.
                //
                // <-------- field1_X = 5 -----------------+
                //           field2_X = 5                  |
                //                                         |
                // <-------- field1_Y = 7 ------------+    |
                //           field2_Y = 7             |    |
                //                                    |    |
                """
                public static void main() {
                """, //                               ^    ^
                    template2.asToken(5), // -------- | ---+
                    template2.asToken(7), // ---------+
                """
                }
                """
            )), // The Hook scope ends here.
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // We saw the use of custom hooks above, but now we look at the use of CLASS_HOOK and METHOD_HOOK.
    // By convention, we use the CLASS_HOOK for class scopes, and METHOD_HOOK for method scopes.
    // Whenever we open a class scope, we should anchor a CLASS_HOOK for that scope, and whenever we
    // open a method, we should anchor a METHOD_HOOK. Conversely, this allows us to check if we are
    // inside a class or method scope by querying "isAnchored". This convention helps us when building
    // a large library of Templates. But if you are writing your own self-contained set of Templates,
    // you do not have to follow this convention.
    //
    // Hooks are "re-entrant", that is we can anchor the same hook inside a scope that we already
    // anchored it previously. The "Hook.insert" always goes to the innermost anchoring of that
    // hook. There are cases where "re-entrant" Hooks are helpful such as nested classes, where
    // there is a class scope inside another class scope. Similarly, we can nest lambda bodies
    // inside method bodies, so also METHOD_HOOK can be used in such a "re-entrant" way.
    public static String generateWithLibraryHooks() {

        var templateMethodBody = Template.make(() -> scope(
            """
            // Let's define a local variable $var and a static field $field.
            // Since we are inserting them at the anchor before the code below,
            // they will already be available:
            System.out.println("$field: " + $field);
            System.out.println("$var: " + $var);
            """,
            Hooks.CLASS_HOOK.insert(scope(
                """
                static { System.out.println("Defining static field $field"); }
                public static int $field = 5;
                """
            )),
            Hooks.METHOD_HOOK.insert(scope(
                """
                System.out.println("Defining local variable $var");
                int $var = 11;
                """
            )),
            """
            if ($field * $var != 55) { throw new RuntimeException("Wrong value!"); }
            """
            // Note: we have used "scope" for the "insert" scope. This is fine here as
            // we are only working with code and hashtags, but not with DataNames. If
            // we were to also "addDataName" inside the insert scope, we would have to
            // make sure that the scope is transparent for DataNames, so that they can
            // escape to the anchor scope, and can be available to the caller of the
            // insertion. One might want to use "transparentScope" for the insertion scope.
            // See: generateWithDataNamesForFieldsAndVariables.
            // See: generateWithScopes1
            // See: generateWithScopes2
        ));

        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest5 {
            """,
            // Class Hook for fields.
            Hooks.CLASS_HOOK.anchor(scope(
                """
                public static void main() {
                """,
                // Method Hook for local variables, and earlier computations.
                Hooks.METHOD_HOOK.anchor(scope(
                    """
                    // This is the beginning of the "main" method body.
                    System.out.println("Welcome to main!");
                    """,
                    templateMethodBody.asToken(),
                    """
                    System.out.println("Going to call other...");
                    other();
                    """
                )),
                """
                }

                private static void other() {
                """,
                // Have a separate method hook for other, so that it can insert
                // its own local variables.
                Hooks.METHOD_HOOK.anchor(scope(
                    """
                    System.out.println("Welcome to other!");
                    """,
                    templateMethodBody.asToken(),
                    """
                    System.out.println("Done with other.");
                    """
                )),
                """
                }
                """
            )),
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
        var binding1 = new TemplateBinding<Template.OneArg<Integer>>();
        var template1 = Template.make("depth", (Integer depth) -> scope(
            let("fuel", fuel()),
            """
            System.out.println("At depth #depth with fuel #fuel.");
            """,
            // We cannot yet use template1 directly, as it is being defined.
            // So we use binding1 instead.
            // For every recursion depth, some fuel is automatically subtracted
            // so that the fuel slowly depletes with the depth.
            // We keep the recursion going until the fuel is depleted.
            //
            // Note: if we forget to check the fuel(), the renderer causes a
            //       StackOverflowException, because the recursion never ends.
            (fuel() > 0) ? binding1.get().asToken(depth + 1)
                        : "System.out.println(\"Fuel depleted.\");\n",
            """
            System.out.println("Exit depth #depth.");
            """
        ));
        binding1.bind(template1);

        var templateClass = Template.make(() -> scope(
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

    // Below, we introduce the concept of "DataNames". Code generation often involves defining
    // fields and variables, which are then available inside a defined scope. "DataNames" can
    // be registered at a certain scope with addDataName. This "DataName" is then available
    // in this scope, and in any nested scope, including nested Templates. This allows us to
    // add some fields and variables in one Template, and later on, in another Template, we
    // can access these fields and variables again with "dataNames()".
    //
    // Here are a few use-cases:
    // - You are writing some inner Template, and would like to access a random field or
    //   variable from an outer Template. Luckily, the outer Templates have added their
    //   fields and variables, and you can now access them with "dataNames()". You can
    //   count them, get a list of them, or sample a random one.
    // - You are writing some outer Template, and would like to generate a variable that
    //   an inner Template could read from or even write to. You can "addDataName" the
    //   variable, and the inner Template can then find that variable in "dataNames()".
    //   If the inner Template wants to find a random field or variable, it may sample
    //   from "dataNodes()", and with some probability, it would sample your variable.
    //
    // A "DataName" captures the name of the field or variable in a String. It also
    // stores the type of the field or variable, as well as its "mutability", i.e.
    // an indication if the field or variable is only for reading, or if writing to
    // it is also allowed. If a field or variable is final, we must make sure that the
    // "DataName" is immutable, otherwise we risk that some Template attempts to generate
    // code that writes to the final field or variable, and then we get a compilation
    // error from "javac" later on.
    //
    // To get started, we show an example where all DataNames have the same type, and where
    // all Names are mutable. For simplicity, our type represents the primitive int type.
    //
    // Note: the template library contains a lot of types that model the Java types,
    //       such as primitive types ({@code PrimitiveType}). The following examples
    //       give insight into how those types work. If you are just interested in
    //       how to use the predefined types, then you can find other examples in
    //       {@code examples/TestPrimitiveTypes.java}.
    private record MySimpleInt() implements DataName.Type {
        // The type is only subtype of itself. This is relevant when sampling or weighing
        // DataNames, because we do not just sample from the given type, but also its subtypes.
        @Override
        public boolean isSubtypeOf(DataName.Type other) {
            return other instanceof MySimpleInt();
        }

        // The name of the type can later be accessed, and used in code. We are working
        // with ints, so that is what we return.
        @Override
        public String name() { return "int"; }
    }
    private static final MySimpleInt mySimpleInt = new MySimpleInt();

    // In this example, we generate 3 fields, and add their names to the
    // current scope. We can then sample some of these DataNames, which
    // gives us one of those fields each time. We increment those randomly
    // chosen fields. At the end, we print all three fields.
    public static String generateWithDataNamesSimple() {
        var templateClass = Template.make(() -> scope(
            // Let us define the names for the three fields.
            // We make all DataNames mutable, and with the same weight of 1,
            // so that they have equal probability of being sampled.
            // Note: the default weight is 1, so we can also omit the weight.
            //
            // Also note that DataNames are only available once they are defined:
            //
            // Nothing defined, yet: dataNames() = {}
            addDataName($("f1"), mySimpleInt, MUTABLE, 1),
            // Only now dataNames() contains f1: dataNames() = {f1}
            addDataName($("f2"), mySimpleInt, MUTABLE, 1),
            // dataNames() = {f1, f2}
            addDataName($("f3"), mySimpleInt, MUTABLE), // omit weight, default is 1.
            // dataNames() = {f1, f2, f3}
            """
            package p.xyz;

            public class InnerTest7 {
                // Let us define some fields.
                public static int $f1 = 0;
                public static int $f2 = 0;
                public static int $f3 = 0;

                public static void main() {
                    // Let us now sample a random field and assign its name to
                    // the hashtag replacement "a".
                    """,
                    dataNames(MUTABLE).exactOf(mySimpleInt).sampleAndLetAs("a"),
                    """
                    // We can now access the field, and increment it.
                    #a += 42;
                    // If we are also interested in the type of the field, we can do:
                    """,
                    dataNames(MUTABLE).exactOf(mySimpleInt).sampleAndLetAs("b", "bType"),
                    """
                    #b += 7;
                    // In some cases, we may want to capture the DataName directly, which
                    // requires capturing the value in a lambda that creates an inner scope:
                    """,
                    dataNames(MUTABLE).exactOf(mySimpleInt).sample((DataName dn) -> scope(
                        let("c", dn.name()),
                        """
                        #c += 12;
                        """
                    )),
                    """
                    // Now, we can print all three fields, and see which
                    // ones were incremented.
                    System.out.println("f1: " + $f1);
                    System.out.println("f2: " + $f2);
                    System.out.println("f3: " + $f3);
                    // Make sure they add up to the correct sum.
                    if ($f1 + $f2 + $f3 != 42 + 7 + 12) { throw new RuntimeException("wrong result!"); }
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // In the example above, we could have easily kept track of the three fields ourselves,
    // and would not have had to rely on the Template Framework's DataNames for this. However,
    // with more complicated examples, this gets more and more difficult, if not impossible.
    //
    // In the example below, we make the scenario a little more realistic. We work with an
    // int and a long type. In the main method, we add some fields and local variables, and
    // register their DataNames. When sampling from the main method, we should be able to see
    // both fields and variables that we just registered. But from another method, we should
    // only see the fields, but the local variables from main should not be sampled.
    //
    // Let us now define the wrapper for primitive types such as int and long.
    private record MyPrimitive(String name) implements DataName.Type {
        @Override
        public boolean isSubtypeOf(DataName.Type other) {
            return other instanceof MyPrimitive(String n) && n.equals(name());
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

    public static String generateWithDataNamesForFieldsAndVariables() {
        // Define a static field.
        // Note: it is very important that we use a "transparentScope" for the template here,
        //       so that the DataName can escape to outer scopes, so that it is available to
        //       everything that follows the DataName definition in the outer scope.
        //       (We could also use "hashtagScope", since those are also transparent for
        //        names. But it is not great style, because template boundaries are
        //        non-transparent for hashtags and setFuelCost anyway. So we might as
        //        well just use "transparentScope".)
        var templateStaticField = Template.make("type", (DataName.Type type) -> transparentScope(
            addDataName($("field"), type, MUTABLE), // escapes template because of "transparentScope"
            // Note: since we have overridden MyPrimitive::toString, we can use
            //       the type directly as "#type" in the template, which then
            //       gets hashtag replaced with "int" or "long".
            """
            public static #type $field = 0;
            """
        ));

        // Define a local variable.
        // Note: it is very important that we use a "transparentScope" for the template here,
        //       so that the DataName can escape to outer scopes.
        var templateLocalVariable = Template.make("type", (DataName.Type type) -> transparentScope(
            addDataName($("var"), type, MUTABLE), // escapes template because of "transparentScope"
            """
            #type $var = 0;
            """
        ));

        // Sample a random field or variable, from those that are available at
        // the current scope.
        var templateSample = Template.make("type", (DataName.Type type) -> scope(
            dataNames(MUTABLE).exactOf(type).sampleAndLetAs("name"),
            // Note: we could also sample from MUTABLE_OR_IMMUTABLE, we will
            //       cover the concept of mutability in an example further down.
            """
            System.out.println("Sampling type #type: #name = " + #name);
            """
        ));

        // Check how many fields and variables are available at the current scope.
        var templateStatus = Template.make(() -> scope(
            dataNames(MUTABLE).exactOf(myInt).count(ints -> scope(
                dataNames(MUTABLE).exactOf(myLong).count(longs -> scope(
                    // We have now captured the values as Java variables, and can
                    // use them inside the scope in some "let" definitions.
                    let("ints", ints),
                    let("longs", longs),
                    // Note: we could also count the MUTABLE_OR_IMMUTABLE, we will
                    //       cover the concept of mutability in an example further down.
                    """
                    System.out.println("Status: #ints ints, #longs longs.");
                    """
                ))
            )),
            // In a real code generation case, we would most likely want to
            // have the count as a Java variable so that one can take conditional
            // action based on the value. For that we have to capture the count
            // with a lambda and inner scope as above. If we only need to have
            // the count as a hashtag replacement, we can also use the following
            // trick:
            dataNames(MUTABLE).exactOf(myInt).count(c -> transparentScope(let("ints", c))),
            dataNames(MUTABLE).exactOf(myLong).count(c -> transparentScope(let("longs", c))),
            // Because of the "transparentScope", the hashtag replacements escape.
            """
            System.out.println("Status: #ints ints, #longs longs.");
            """
        ));

        // Definition of the main method body.
        var templateMain = Template.make(() -> scope(
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
        var templateOther = Template.make(() -> scope(
            """
            System.out.println("Starting inside other...");
            """,
            // We should see the fields defined in the main body,
            // one int and one long field.
            templateStatus.asToken(),
            // Sampling 5 random int and 5 random long DataNames. We should
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
        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest8 {
            """,
            // Class Hook for fields.
            Hooks.CLASS_HOOK.anchor(scope(
                """
                public static void main() {
                """,
                // Method Hook for local variables.
                Hooks.METHOD_HOOK.anchor(scope(
                    """
                    // This is the beginning of the "main" method body.
                    System.out.println("Welcome to main!");
                    """,
                    templateMain.asToken(),
                    """
                    System.out.println("Going to call other...");
                    other();
                    """
                )),
                """
                }

                private static void other() {
                """,
                // Have a separate method hook for other, where it could insert
                // its own local variables (but happens not to).
                Hooks.METHOD_HOOK.anchor(scope(
                    """
                    System.out.println("Welcome to other!");
                    """,
                    templateOther.asToken(),
                    """
                    System.out.println("Done with other.");
                    """
                )),
                """
                }
                """
            )),
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    public static String generateWithScopes1() {

        // For the examples below, we need a convenient way of asserting the state
        // of the available DataNames.
        var templateVerify = Template.make("count", "hasAny", "toList", (Integer count, Boolean hasAny, String toList) -> scope(
            dataNames(MUTABLE).exactOf(myInt).count(c -> transparentScope(let("count2", c))),
            dataNames(MUTABLE).exactOf(myInt).hasAny(h -> transparentScope(let("hasAny2", h))),
            dataNames(MUTABLE).exactOf(myInt).toList(list -> transparentScope(
                let("toList2", String.join(", ", list.stream().map(DataName::name).toList()))
            )),
            """
            if (#count != #count2 ||
                #hasAny != #hasAny2 ||
                !"#toList".equals("#toList2")) {
                throw new RuntimeException("verify failed");
            }
            """
        ));

        var templateMain = Template.make(() -> scope(
            "// Start with nothing:\n",
            templateVerify.asToken(0, false, ""),
            "// Add v1:\n",
            addDataName("v1", myInt, MUTABLE),
            "int v1 = 1;\n",
            "// Check that it is visible:\n",
            templateVerify.asToken(1, true, "v1"),
            "// Add v2:\n",
            addDataName("v2", myInt, MUTABLE),
            "int v2 = 2;\n",
            "// Check that both are visible:\n",
            templateVerify.asToken(2, true, "v1, v2"),

            "// Create a local scope:\n",
            "{\n", scope( // for consistency, we model the code and template scope together.
                "// Add v3:\n",
                addDataName("v3", myInt, MUTABLE),
                "int v3 = 3;\n",
                "// Check that all are visible:\n",
                templateVerify.asToken(3, true, "v1, v2, v3")
            ), "}\n",
            "// But after the scope, v3 is no longer available:\n",
            templateVerify.asToken(2, true, "v1, v2"),

            "// Now let's create a list of variables.\n",
            List.of(4, 5, 6).stream().map(i -> hashtagScope(
                // The hashtagScope allows hashtag replacements to be local,
                // and DataNames to escape, so we can use them afterwards.
                let("i", i),
                addDataName("v" + i, myInt, MUTABLE),
                "int v#i = #i;\n"
            )).toList(),
            templateVerify.asToken(5, true, "v1, v2, v4, v5, v6"),

            "// Let's multiply all variables by a factor of 2, using forEach:\n",
            dataNames(MUTABLE).exactOf(myInt).forEach(dn -> scope(
                let("v", dn.name()),
                "#v *= 2;\n"
            )),
            "// We can also capture the name (v) and type of the DataName:\n",
            dataNames(MUTABLE).exactOf(myInt).forEach("v", "type", dn -> scope(
                "#v *= 2;\n"
            )),
            "// Yet another option is using toList, but here that is more cumbersome:\n",
            dataNames(MUTABLE).exactOf(myInt).toList(list -> scope(
                list.stream().map(dn -> scope(
                    let("v", dn.name()),
                    "#v *= 2;\n"
                )).toList()
            )),

            """
            // We verify the result again.
            """,
            templateVerify.asToken(5, true, "v1, v2, v4, v5, v6"),
            """
            if (v1 != 1 * 8 ||
                v2 != 2 * 8 ||
                v4 != 4 * 8 ||
                v5 != 5 * 8 ||
                v6 != 6 * 8) {
                throw new RuntimeException("wrong value!");
            }
            """,

            "// Let us copy each variable:\n",
            dataNames(MUTABLE).exactOf(myInt).forEach("v", "type", dn -> hashtagScope(
                // Note that we need a hashtagScope here, so that we can reuse "v" and
                // "type" as hashtag replacements in each iteration, but still let the
                // copied DataNames escape.
                addDataName(dn.name() + "_copy", myInt, MUTABLE),
                "#type #{v}_copy = #v;\n"
            )),
            templateVerify.asToken(10, true, "v1, v2, v4, v5, v6, v1_copy, v2_copy, v4_copy, v5_copy, v6_copy")
        ));

        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest9a {
            """,
            Hooks.CLASS_HOOK.anchor(scope(
            """
                public static void main() {
            """,
                Hooks.METHOD_HOOK.anchor(scope(
                    templateMain.asToken()
                )),
            """
                }
            """
            )),
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    public static String generateWithScopes2() {

        // In this section, we will look at some subtle facts about the behavior of
        // transparent scopes around hook insertion. This is intended for expert users
        // so feel free to skip it until you extensively use hook insertion.
        // More info can also be found in the Javadocs of the Hook class.

        // Helper method to check that the expected DataNames are available.
        var templateVerify = Template.make("toList", (String toList) -> scope(
            dataNames(MUTABLE).exactOf(myInt).toList(list -> transparentScope(
                let("toList2", String.join(", ", list.stream().map(DataName::name).toList()))
            )),
            """
            if (!"#toList".equals("#toList2")) {
                throw new RuntimeException("verify failed: '#toList' vs '#toList2'.");
            }
            """
        ));

        var myHook = new Hook("MyHook");

        var templateMain = Template.make(() -> scope(
            // Start with nothing:
            templateVerify.asToken(""),
            addDataName("v1", myInt, MUTABLE),
            templateVerify.asToken("v1"),
            // Non-transparent hook anchor:
            myHook.anchor(scope(
                templateVerify.asToken("v1"),
                addDataName("v2", myInt, MUTABLE),
                templateVerify.asToken("v1, v2"),
                // Insert a non-transparent scope: nothing escapes.
                myHook.insert(scope(
                    // Note that at the anchor insertion point, v2 is not yet
                    // available, because it is added after the anchoring.
                    templateVerify.asToken("v1"),
                    let("x3", 42),
                    addDataName("v3", myInt, MUTABLE),
                    templateVerify.asToken("v1, v3")
                )),
                // Note: x3 and v3 do not escape.
                let("x3", 7), // we can define it again.
                templateVerify.asToken("v1, v2"),
                // While not letting hashtags escape may be helpful, it is probably
                // not very helpful if the DataNames don't escape. For example, if
                // we are inserting some variable at an outer scope, we would like
                // it to be available for the rest of the scope.
                // That's where a transparent scope can be helpful.
                myHook.insert(transparentScope(
                    // At the anchoring, still only v1 is available.
                    templateVerify.asToken("v1"),
                    let("x4", 42), // escapes to caller scope
                    addDataName("v4", myInt, MUTABLE), // escapes to anchor scope
                    templateVerify.asToken("v1, v4")
                )),
                // x4 escapes to the caller out here, and not to the anchor scope.
                "// x4: #x4\n",
                // And v4 escapes to the anchor scope, which is available from here too.
                // Interesting detail: the ordering in the list indicates that v1
                // is from the outermost scope of the template, v4 is located at the
                // anchor scope, and v2 is located inside the anchor scope, and
                // thus comes last.
                templateVerify.asToken("v1, v4, v2"),
                // In most practical cases we probably don't want to let the hashtag
                // escape, because they just represent something local. So we can
                // use a hashtagScope, so that DataNames escape, but not hashtags.
                myHook.insert(hashtagScope(
                    // Note: both v1 and v4 are now available at the anchoring, since
                    // v1 was inserted outside the anchoring scope, and v4 was just
                    // inserted to the anchoring scope.
                    templateVerify.asToken("v1, v4"),
                    let("x5", 42), // local, does not escape.
                    addDataName("v5", myInt, MUTABLE), // escapes to anchor scope
                    templateVerify.asToken("v1, v4, v5")
                )),
                let("x5", 7), // we can define it again.
                templateVerify.asToken("v1, v4, v5, v2")
            )),
            // We left the non-transparent anchoring scope which does not let anything escape
            templateVerify.asToken("v1"),

            // Let us now do something that probably should never be done. But still
            // we want to demonstrate it for educational purposes: transparent anchoring
            // scopes.
            myHook.anchor(transparentScope(
                templateVerify.asToken("v1"),
                // For one, this means that DataName escape the scope directly.
                addDataName("v6", myInt, MUTABLE),
                templateVerify.asToken("v1, v6"),
                // But also if we insert to the anchoring scope, DataNames don't just
                // escape from the anchoring scope, but further out to the enclosing
                // scope.
                myHook.insert(transparentScope(
                    templateVerify.asToken("v1, v6"),
                    addDataName("v7", myInt, MUTABLE),
                    templateVerify.asToken("v1, v6, v7")
                )),
                templateVerify.asToken("v1, v6, v7"),
                let("x6", 42) // escapes the anchor scope
            )),
            // We left the transparent anchoring scope which lets the DataNames and
            // hashtags escape.
            "// x6: #x6\n",
            templateVerify.asToken("v1, v6, v7")
        ));

        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest9b {
            """,
            Hooks.CLASS_HOOK.anchor(scope(
            """
                public static void main() {
            """,
                Hooks.METHOD_HOOK.anchor(scope(
                    templateMain.asToken()
                )),
            """
                }
            """
            )),
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }
    // There are two more concepts to understand more deeply with DataNames.
    //
    // One is the use of mutable and immutable DataNames.
    // In some cases, we only want to sample DataNames that are mutable, because
    // we want to store to a field or variable. We have to make sure that we
    // do not generate code that tries to store to a final field or variable.
    // In other cases, we only want to load, and we do not care if the
    // fields or variables are final or non-final.
    //
    // Another concept is subtyping of DataName Types. With primitive types, this
    // is irrelevant, but with instances of Objects, this becomes relevant.
    // We may want to load an object of any field or variable of a certain
    // class, or any subclass. When a value is of a given class, we can only
    // store it to fields and variables of that class or any superclass.
    //
    // Let us look at an example that demonstrates these two concepts.
    //
    // First, we define a DataName Type that represents different classes, that
    // may or may not be in a subtype relation. Subtypes start with the name
    // of the super type.
    private record MyClass(String name) implements DataName.Type {
        @Override
        public boolean isSubtypeOf(DataName.Type other) {
            return other instanceof MyClass(String n) && name().startsWith(n);
        }

        @Override
        public String toString() { return name(); }
    }
    private static final MyClass myClassA   = new MyClass("MyClassA");
    private static final MyClass myClassA1  = new MyClass("MyClassA1");
    private static final MyClass myClassA2  = new MyClass("MyClassA2");
    private static final MyClass myClassA11 = new MyClass("MyClassA11");
    private static final MyClass myClassB   = new MyClass("MyClassB");
    private static final List<MyClass> myClassList = List.of(myClassA, myClassA1, myClassA2, myClassA11, myClassB);

    public static String generateWithDataNamesForFuzzing() {
        // This template is used to insert a DataName (field) into an outer scope, hence we must use
        // "transparentScope" instead of "scope".
        var templateStaticField = Template.make("type", "mutable", (DataName.Type type, Boolean mutable) -> transparentScope(
            addDataName($("field"), type, mutable ? MUTABLE : IMMUTABLE), // Escapes the template.
            let("isFinal", mutable ? "" : "final"),
            """
            public static #isFinal #type $field = new #type();
            """
        ));

        var templateLoad = Template.make("type", (DataName.Type type) -> scope(
            // We only load from the field, so we do not need a mutable one,
            // we can load from final and non-final fields.
            // We want to find any field from which we can read the value and store
            // it in our variable v of our given type. Hence, we can take a field
            // of the given type or any subtype thereof.
            dataNames(MUTABLE_OR_IMMUTABLE).subtypeOf(type).sampleAndLetAs("field"),
            """
            #type $v = #field;
            System.out.println("#field: " + $v);
            """
        ));

        var templateStore = Template.make("type", (DataName.Type type) -> scope(
            // We are storing to a field, so it better be non-final, i.e. mutable.
            // We want to store a new instance of our given type to a field. This
            // field must be of the given type or any supertype.
            dataNames(MUTABLE).supertypeOf(type).sampleAndLetAs("field"),
            """
            #field = new #type();
            """
        ));

        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest10 {
                // First, we define our classes.
                public static class MyClassA {}
                public static class MyClassA1 extends MyClassA {}
                public static class MyClassA2 extends MyClassA {}
                public static class MyClassA11 extends MyClassA1 {}
                public static class MyClassB {}

                // Now, we define a list of static fields. Some of them are final, others not.
                """,
                // We must create a CLASS_HOOK and insert the fields to it. Otherwise,
                // addDataName is restricted to the scope of the templateStaticField. But
                // with the insertion to CLASS_HOOK, the addDataName goes through the scope
                // of the templateStaticField out to the scope of the CLASS_HOOK.
                Hooks.CLASS_HOOK.anchor(scope(
                    myClassList.stream().map(c ->
                        (Object)Hooks.CLASS_HOOK.insert(templateStaticField.asToken(c, true))
                    ).toList(),
                    myClassList.stream().map(c ->
                        (Object)Hooks.CLASS_HOOK.insert(templateStaticField.asToken(c, false))
                    ).toList(),
                    """

                    public static void main() {
                        // All fields are still in their initial state.
                        """,
                        myClassList.stream().map(templateLoad::asToken).toList(),
                        """
                        // Now let us mutate some fields.
                        """,
                        myClassList.stream().map(templateStore::asToken).toList(),
                        """
                        // And now some fields are different than before.
                        """,
                        myClassList.stream().map(templateLoad::asToken).toList(),
                        """
                    }
                    """
                )),
            """
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.render();
    }

    // "DataNames" are useful for modeling fields and variables. They hold data,
    // and we can read and write to them, they may be mutable or immutable.
    // We now introduce another set of "Names", the "StructuralNames". They are
    // useful for modeling method names and class names, and possibly more. Anything
    // that has a fixed name in the Java code, for which mutability is inapplicable.
    // Some use-cases for "StructuralNames":
    // - Method names. The Type could represent the signature of the static method
    //                 or the class of the non-static method.
    // - Class names. Type could represent the signature of the constructor, so
    //                that we could instantiate random instances.
    // - try/catch blocks. If a specific Exception is caught in the scope, we could
    //                     register that Exception, and in the inner scope we can
    //                     check if there is any "StructuralName" for an Exception
    //                     and its subtypes - if so, we know the exception would be
    //                     caught.
    //
    // Let us look at an example with Method names. But for simplicity, we assume they
    // all have the same signature: they take two int arguments and return an int.
    //
    // Should you ever work on a test where there are methods with different signatures,
    // then you would have to very carefully study and design the subtype relation between
    // methods. You may want to read up about covariance and contravariance. This
    // example ignores all of that, because we only have "(II)I" methods.
    private record MyMethodType() implements StructuralName.Type {
        @Override
        public boolean isSubtypeOf(StructuralName.Type other) {
            return other instanceof MyMethodType();
        }

        @Override
        public String name() { return "<not used, don't worry>"; }
    }
    private static final MyMethodType myMethodType = new MyMethodType();

    public static String generateWithStructuralNamesForMethods() {
        // Define a method, which takes two ints, returns the result of op.
        var templateMethod = Template.make("op", (String op) -> transparentScope(
            // Register the method name, so we can later sample.
            addStructuralName($("methodName"), myMethodType), // escapes the template because of "transparentScope"
            """
            public static int $methodName(int a, int b) {
                return a #op b;
            }
            """
        ));

        var templateSample = Template.make(() -> scope(
            // Sample a random method, and retrieve its name.
            structuralNames().exactOf(myMethodType).sampleAndLetAs("methodName"),
            """
            System.out.println("Calling #methodName with inputs 7 and 11");
            System.out.println("  result: " + #methodName(7, 11));
            """
        ));

        var templateClass = Template.make(() -> scope(
            """
            package p.xyz;

            public class InnerTest11 {
                // Let us define some methods that we can sample from later.
            """,
            // We must anchor a CLASS_HOOK here, and insert the method definitions to that hook.
            Hooks.CLASS_HOOK.anchor(scope(
                // If we directly nest the templateMethod, then the addStructuralName goes to the nested
                // scope, and is not available at the class scope, i.e. it is not visible
                // for sampleStructuralName outside of the templateMethod.
                // DO NOT DO THIS, the nested addStructuralName will not be visible:
                "// We cannot sample from the following methods:\n",
                templateMethod.asToken("+"),
                templateMethod.asToken("-"),
                // However, if we insert to the CLASS_HOOK, then the Renderer makes the
                // scope of the inserted templateMethod transparent, and the addStructuralName
                // goes out to the scope of the CLASS_HOOK (but no further than that).
                // RATHER, DO THIS to ensure the addStructuralName is visible:
                Hooks.CLASS_HOOK.insert(templateMethod.asToken("*")),
                Hooks.CLASS_HOOK.insert(templateMethod.asToken("|")),
                Hooks.CLASS_HOOK.insert(templateMethod.asToken("&")),
                """

                    public static void main() {
                        // Now, we call some random methods, but only those that were inserted
                        // to the CLASS_HOOK.
                        """,
                        Collections.nCopies(10, templateSample.asToken()),
                        """
                    }
                }
                """
            ))
        ));

        // Render templateClass to String.
        return templateClass.render();
    }
}
