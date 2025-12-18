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
 * @bug 8369699
 * @key randomness
 * @summary Test the Template Library's expression generation for the Vector API.
 * @modules jdk.incubator.vector
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../compiler/lib/generators/Generators.java
 * @compile ../../compiler/lib/verify/Verify.java
 * @run main ${test.main.class}
 */

package compiler.vectorapi;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Random;
import jdk.test.lib.Utils;
import java.util.stream.IntStream;

import compiler.lib.compile_framework.CompileFramework;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Operations;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.VectorType;

/**
 * TODO: desc
 */
public class VectorExpressionFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile("--add-modules=jdk.incubator.vector");

        // p.xyz.InnterTest.main(new String[] {});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        }});
    }

    // TODO: desc
    record TestArgument(
        Object defineAndFill,
        Object passMethodArgument,
        Object receiveMethodArgument,
        Object use) {}

    // Generate a source Java file as String
    public static String generate(CompileFramework comp) {
        // Generate a list of test methods.
        List<TemplateToken> tests = new ArrayList<>();

        // We are going to use some random numbers in our tests, so import some good methods for that.
        tests.add(PrimitiveType.generateLibraryRNG());

        // Example 1:
        // To start simple, we just call the expression with the same constant arguments
        // every time. We can still compare the "gold" value optained via interpreter with
        // later results optained from the compiled method.
        var template1 = Template.make("type", (VectorType.Vector type) -> {
            // The depth determines roughly how many operations are going to be used in the expression.
            int depth = RANDOM.nextInt(1, 10);
            Expression expression = Expression.nestRandomly(type, Operations.ALL_OPERATIONS, depth);
            List<Object> expressionArguments = expression.argumentTypes.stream().map(CodeGenerationDataNameType::con).toList();
            return scope(
                """
                // --- $test start ---
                // Using $GOLD
                // type: #type

                static final Object $GOLD = $test();

                @Test
                public static Object $test() {
                    try {
                """,
                "        return ", expression.asToken(expressionArguments), ";\n",
                expression.info.exceptions.stream().map(exception ->
                    "        } catch (" + exception + " e) { return e;\n"
                ).toList(),
                """
                    } finally {
                        // Just javac is happy if there are no exceptions to catch.
                    }
                }

                @Check(test = "$test")
                public static void $check(Object result) {
                """,
                expression.info.isResultDeterministic
                    ? "    Verify.checkEQ(result, $GOLD);\n"
                    : "    // result not deterministic - don't verify.\n",
                """
                }

                // --- $test end   ---
                """
            );
        });

        // Example 2:
        // Now a more complicated case. We want:
        // - For every invocation of the test method, we want to have different inputs for the arguments.
        // - We check correctness with a reference method that does the same but runs in the interpreter.
        // - Input values are delivered via fields or array loads.
        var template2Body = Template.make("expression", "arguments", (Expression expression, List<Object> arguments) -> scope(
            """
            try {
            """,
            "return ", expression.asToken(arguments), ";\n",
            expression.info.exceptions.stream().map(exception ->
                "} catch (" + exception + " e) { return e;\n"
            ).toList(),
            """
            } finally {
                // Just javac is happy if there are no exceptions to catch.
            }
            """
        ));

        var template2 = Template.make("type", (VectorType.Vector type) -> {
            // The depth determines roughly how many operations are going to be used in the expression.
            int depth = RANDOM.nextInt(1, 10);
            Expression expression = Expression.nestRandomly(type, Operations.ALL_OPERATIONS, depth);

            List<TestArgument> arguments = new ArrayList<>();
            for (int i = 0; i < expression.argumentTypes.size(); i++) {
                String name = "arg_" + i;
                CodeGenerationDataNameType argumentType = expression.argumentTypes.get(i);
                switch(RANDOM.nextInt(4)) {
                    case 0 -> {
                        // Use the constant directly, no argument passing needed.
                        // To make the logic of passing arguments easy, we just pass null and receive an unused argument anyway.
                        arguments.add(new TestArgument(
                            "",
                            "null",
                            "Object unused_" + i,
                            argumentType.con()
                        ));
                    }
                    case 1 -> {
                        // Create the constant outside, and pass it.
                        arguments.add(new TestArgument(
                            List.of(argumentType.name(), " ", name, " = ", argumentType.con(), ";\n"),
                            name,
                            List.of(argumentType.name(), " ", name),
                            name
                        ));
                    }
                    default -> {
                        if (argumentType instanceof PrimitiveType t) {
                            // We can use the LibraryRGN to create a new value for the primitive in each
                            // invocation. We have to make sure to call the LibraryRNG in the "defineAndFill",
                            // so we get the same value for both test and reference. If we called LibraryRNG
                            // for "use", we would get separate values, which is not helpful.
                            arguments.add(new TestArgument(
                                List.of(t.name(), " ", name, " = ", t.callLibraryRNG(), ";\n"),
                                name,
                                List.of(t.name(), " ", name),
                                name
                            ));
                        } else if (argumentType instanceof VectorType.Vector t) {
                            PrimitiveType et = t.elementType;
                            arguments.add(new TestArgument(
                                List.of(et.name(), "[] ", name, " = new ", et.name(), "[1000];\n",
                                        "LibraryRNG.fill(", name,");\n"),
                                name,
                                List.of(et.name(), "[] ", name),
                                List.of(t.name(), ".fromArray(", t.speciesName, ", ", name, ", 0)")
                            ));
                        } else {
                            // We don't know anything special how to create different values
                            // each time, so let's just crate the same constant each time.
                            arguments.add(new TestArgument(
                                List.of(argumentType.name(), " ", name, " = ", argumentType.con(), ";\n"),
                                name,
                                List.of(argumentType.name(), " ", name),
                                name
                            ));
                        }
                    }
                }
            }

            // "join" together the arguments to make a comma separated list.
            List<Object> passArguments = IntStream.range(0, arguments.size() * 2 - 1).mapToObj(i ->
                (i % 2 == 0) ? arguments.get(i / 2).passMethodArgument() : ", "
            ).toList();
            List<Object> receiveArguments = IntStream.range(0, arguments.size() * 2 - 1).mapToObj(i ->
                (i % 2 == 0) ? arguments.get(i / 2).receiveMethodArgument() : ", "
            ).toList();

            List<Object> useArguments = arguments.stream().map(TestArgument::use).toList();

            return scope(
                """
                // --- $test start ---
                // type: #type

                @Run(test = "$test")
                public void $run() {
                """,
                arguments.stream().map(TestArgument::defineAndFill).toList(),
                """
                Object v0 = $test(
                """,
                passArguments,
                """
                );
                Object v1 = $reference(
                """,
                passArguments,
                """
                );
                """,
                expression.info.isResultDeterministic
                    ? "Verify.checkEQ(v0, v1);\n"
                    : "// result not deterministic - don't verify.\n",
                """
                }

                @Test
                public static Object $test(
                """,
                receiveArguments,
                """
                ) {
                """,
                template2Body.asToken(expression, useArguments),
                """
                }

                @DontCompile
                public static Object $reference(
                """,
                receiveArguments,
                """
                ) {
                """,
                template2Body.asToken(expression, useArguments),
                """
                }
                // --- $test end   ---
                """
            );
        });


//
//        var defineArray = Template.make("type", "name", "size", (Type type, String name, Integer size) -> scope(
//            """
//            public static #type[] #name = fill(new #type[#size]);
//            """
//        ));
//
//        // Example 2:
//        // We use the expression to iterate over arrays, loading from a set of input arrays,
//        // and storing to an output array.
//        var template2 = Template.make("type", (VectorAPIType type)-> {
//            int size = 1000;
//            Expression expression = Expression.make(type, Type.ALL_BUILTIN_TYPES, 4);
//            List<Type> types = expression.argTypes();
//            List<TemplateWithArgs> arrayDefinitions = new ArrayList<>();
//            List<Object> args = new ArrayList<>();
//            for (int i = 0; i < types.size(); i++) {
//                String name = $("array") + "_" + i;
//                Type argType = types.get(i);
//                PrimitiveType elementType = null;
//                if (argType instanceof PrimitiveType t) {
//                    elementType = t;
//                    args.add(name + "[0]");
//                } else if (argType instanceof VectorAPIType vt) {
//                    elementType = vt.elementType;
//                    args.add(vt.vectorType + ".fromArray(" + vt.species + ", " + name + ", 0)");
//                } else if (argType instanceof VectorAPIType.MaskType mt) {
//                    elementType = Type.booleans();
//                    args.add("VectorMask.fromArray(" + mt.vectorType.species + ", " + name + ", 0)");
//                } else if (argType instanceof VectorAPIType.ShuffleType st) {
//                    elementType = Type.ints();
//                    args.add("VectorShuffle.fromArray(" + st.vectorType.species + ", " + name + ", 0)");
//                } else {
//                    throw new RuntimeException("Not handled: " + argType);
//                }
//                arrayDefinitions.add(defineArray.asToken(elementType, name, size));
//            }
//            return scope(
//                let("size", size),
//                let("elementType", type.elementType),
//                """
//                // --- $test start ---
//                // Using $GOLD
//                // type: #type
//                // elementType: #elementType
//                """,
//                Library.CLASS_HOOK.set(
//                    """
//                    // Input arrays:
//                    """,
//                    arrayDefinitions,
//                    """
//
//                    static final Object $GOLD = $test();
//
//                    @Test
//                    public static Object $test() {
//                        try {
//                            #elementType[] out = new #elementType[#size];
//                    """,
//                    "        ", expression.asToken(args), ".intoArray(out, 0);\n",
//                    """
//                            return out;
//                    """,
//                    expression.exceptions().stream().map(exception ->
//                        "} catch (" + exception + " e) { return e;\n"
//                    ).toList(),
//                    """
//                        } finally {
//                            // Just javac is happy if there are no exceptions to catch.
//                        }
//                    }
//
//                    @Check(test = "$test")
//                    public static void $check(Object result) {
//                        Verify.checkEQ(result, $GOLD);
//                    }
//
//                    // --- $test end   ---
//                    """
//                )
//            );
//        });
//
//        // TODO: add scalar output case for reductions
//        // TODO: add register stress test
//        // TODO: add load/store stress test
//
//        // Now use the templates and add them into the IRTestClass.
//        List<TemplateWithArgs> templates = new ArrayList<>();
//        templates.add(Library.arrayFillMethods());
//        for (VectorAPIType type : Type.VECTOR_API_VECTOR_TYPES) {
//            for (int i = 0; i < 2; i++) { templates.add(template1.asToken(type)); }
//            for (int i = 0; i < 2; i++) { templates.add(template2.asToken(type)); }
//        }
//        return IRTestClass.TEMPLATE.asToken(info, templates).render();

        for (VectorType.Vector type : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
            tests.add(template1.asToken(type));
            tests.add(template2.asToken(type));
        }

        // Create the test class, which runs all tests.
        return TestFrameworkClass.render(
            // package and class name.
            "p.xyz", "InnerTest",
            // Set of imports.
            Set.of("compiler.lib.verify.*",
                   "java.util.Random",
                   "jdk.test.lib.Utils",
                   "compiler.lib.generators.*",
                   "jdk.incubator.vector.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            tests);
    }
}
