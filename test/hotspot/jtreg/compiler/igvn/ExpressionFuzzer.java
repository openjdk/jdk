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
 * @bug 8359412 8370922
 * @key randomness
 * @summary Use the template framework library to generate random expressions.
 * @modules java.base/jdk.internal.misc
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @compile ../lib/verify/Verify.java
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:CompileTaskTimeout=10000 compiler.igvn.ExpressionFuzzer
 */

package compiler.igvn;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Random;
import java.util.Collections;
import jdk.test.lib.Utils;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Operations;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;
import static compiler.lib.template_framework.library.CodeGenerationDataNameType.PRIMITIVE_TYPES;
import static compiler.lib.template_framework.library.CodeGenerationDataNameType.SCALAR_NUMERIC_TYPES;

// We generate random Expressions from primitive type operators.
//
// The goal is to generate random inputs with constrained TypeInt / TypeLong ranges / KnownBits,
// and then verify the output value, ranges and bits.
//
// Should this test fail and make a lot of noise in the CI, you have two choices:
// - Problem-list this test: but other tests may also use the same broken operators.
// - Temporarily remove the operator from {@code Operations.PRIMITIVE_OPERATIONS}.
//
// Future Work [FUTURE]:
// - Constrain also the unsigned bounds
// - Some basic IR tests to ensure that the constraints / checksum mechanics work.
//   We may even have to add some IGVN optimizations to be able to better observe things right.
// - Lower the CompileTaskTimeout, if possible. It is chosen conservatively (rather high) for now.
public class ExpressionFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static record MethodArgument(String name, CodeGenerationDataNameType type) {}
    public static record StringPair(String s0, String s1) {}

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("compiler.igvn.templated.ExpressionFuzzerInnerTest", generate(comp));

        // Compile the source file.
        comp.compile("--add-modules=jdk.incubator.vector");

        // compiler.igvn.templated.InnterTest.main(new String[] {});
        comp.invoke("compiler.igvn.templated.ExpressionFuzzerInnerTest", "main", new Object[] {new String[] {
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        }});
    }

    // Generate a Java source file as String
    public static String generate(CompileFramework comp) {
        // Generate a list of test methods.
        List<TemplateToken> tests = new ArrayList<>();

        // We are going to use some random numbers in our tests, so import some good methods for that.
        tests.add(PrimitiveType.generateLibraryRNG());

        // Create the body for the test. We use it twice: compiled and reference.
        // Execute the expression and catch expected Exceptions.
        var bodyTemplate = Template.make("expression", "arguments", "checksum", (Expression expression, List<Object> arguments, String checksum) -> scope(
            """
            try {
            """,
            "var val = ", expression.asToken(arguments), ";\n",
            "return #checksum(val);\n",
            expression.info.exceptions.stream().map(exception ->
                "} catch (" + exception + " e) { return e;\n"
            ).toList(),
            """
            } finally {
                // Just so that javac is happy if there are no exceptions to catch.
            }
            """
        ));

        // Machinery for the "checksum" method.
        //
        // We want to do output verification. We don't just want to check if the output value is correct,
        // but also if the signed/unsigned/KnownBits are correct of the TypeInt and TypeLong. For this,
        // we add some comparisons. If we get the ranges/bits wrong (too tight), then the comparisons
        // can wrongly constant fold, and we can detect that in the output array.
        List<StringPair> unsignedCmp = List.of(
            new StringPair("(val < ", ")"),
            new StringPair("(val > ", ")"),
            new StringPair("(val >= ", ")"),
            new StringPair("(val <= ", ")"),
            new StringPair("(val != ", ")"),
            new StringPair("(val == ", ")"),
            new StringPair("(val & ", ")") // Extract bits
        );

        List<StringPair> intCmp = List.of(
            new StringPair("(val < ", ")"),
            new StringPair("(val > ", ")"),
            new StringPair("(val >= ", ")"),
            new StringPair("(val <= ", ")"),
            new StringPair("(val != ", ")"),
            new StringPair("(val == ", ")"),
            new StringPair("(val & ", ")"), // Extract bits
            new StringPair("(Integer.compareUnsigned(val, ", ") > 0)"),
            new StringPair("(Integer.compareUnsigned(val, ", ") < 0)"),
            new StringPair("(Integer.compareUnsigned(val, ", ") >= 0)"),
            new StringPair("(Integer.compareUnsigned(val, ", ") <= 0)")
        );

        List<StringPair> longCmp = List.of(
            new StringPair("(val < ", ")"),
            new StringPair("(val > ", ")"),
            new StringPair("(val >= ", ")"),
            new StringPair("(val <= ", ")"),
            new StringPair("(val != ", ")"),
            new StringPair("(val == ", ")"),
            new StringPair("(val & ", ")"), // Extract bits
            new StringPair("(Long.compareUnsigned(val, ", ") > 0)"),
            new StringPair("(Long.compareUnsigned(val, ", ") < 0)"),
            new StringPair("(Long.compareUnsigned(val, ", ") >= 0)"),
            new StringPair("(Long.compareUnsigned(val, ", ") <= 0)")
        );

        var integralCmpTemplate = Template.make("type", (CodeGenerationDataNameType type) -> {
            List<StringPair> cmps = switch(type.name()) {
                case "char" -> unsignedCmp;
                case "byte", "short", "int" -> intCmp;
                case "long" -> longCmp;
                default -> throw new RuntimeException("not handled: " + type.name());
            };
            StringPair cmp = cmps.get(RANDOM.nextInt(cmps.size()));
            return scope(
                ", ", cmp.s0(), type.con(), cmp.s1()
            );
        });

        // Checksum method: returns not just the value, but also does some range / bit checks.
        //                  This gives us enhanced verification on the range / bits of the result type.
        var checksumTemplate = Template.make("expression", "checksum", (Expression expression, String checksum) -> scope(
            let("returnType", expression.returnType),
            """
            @ForceInline
            public static Object #checksum(#returnType val) {
            """,
            "return new Object[] {",
            switch(expression.returnType.name()) {
                // The integral values have signed/unsigned ranges and known bits.
                // Return val, but also some range and bits tests to see if those
                // ranges and bits are correct.
                case "byte", "short", "char", "int", "long" ->
                    List.of("val", Collections.nCopies(20, integralCmpTemplate.asToken(expression.returnType)));
                // Float/Double have no range, just return the value:
                case "float", "double", "Float16" -> "val";
                // Check if the boolean constant folded:
                case "boolean" -> "val, val == true, val == false";
                default -> throw new RuntimeException("type not supported yet: " + expression.returnType.name());
            }
            , "};\n",
            """
            }
            """
        ));

        // We need to prepare some random values to pass into the test method. We generate the values
        // once, and pass the same values into both the compiled and reference method.
        var valueTemplate = Template.make("name", "type", (String name, CodeGenerationDataNameType type) -> scope(
            "#type #name = ",
            (type instanceof PrimitiveType pt) ? pt.callLibraryRNG() : type.con(),
            ";\n"
        ));

        // At the beginning of the compiled and reference test methods we receive the arguments,
        // which have their full bottom_type (e.g. TypeInt: int). We now constrain the ranges and
        // bits, for the types that allow it.
        //
        // To ensure that both the compiled and reference method use the same constraint, we put
        // the computation in a ForceInline method.
        var constrainArgumentMethodTemplate = Template.make("name", "type", (String name, CodeGenerationDataNameType type) -> scope(
            """
            @ForceInline
            public static #type constrain_#name(#type v) {
            """,
            switch(type.name()) {
                // These currently have no type ranges / bits.
                // Booleans do have an int-range, but restricting it would just make it constant, which
                // is not very useful: we would like to keep it variable here. We already mix in variable
                // arguments and constants in the testTemplate.
                case "boolean", "float", "double", "Float16" -> "return v;\n";
                case "byte", "short", "char", "int", "long" -> List.of(
                    // Sometimes constrain the signed range
                    //   v = min(max(v, CON1), CON2)
                    (RANDOM.nextInt(2) == 0)
                    ? List.of("v = (#type)Math.min(Math.max(v, ", type.con(),"), ", type.con() ,");\n")
                    : List.of(),
                    // Sometimes constrain the bits:
                    //   v = (v & CON1) | CON2
                    // Note:
                    //   and (&): forces some bits to zero
                    //   or  (|): forces some bits to one
                    (RANDOM.nextInt(2) == 0)
                    ? List.of("v = (#type)((v & ", type.con(),") | ", type.con() ,");\n")
                    : List.of(),
                    // FUTURE: we could also constrain the unsigned bounds.
                    "return v;\n");
                default -> throw new RuntimeException("type not supported yet: " + type.name());
            },
            """
            }
            """
        ));

        var constrainArgumentTemplate = Template.make("name", (String name) -> scope(
            """
            #name = constrain_#name(#name);
            """
        ));

        // The template that generates the whole test machinery needed for testing a given expression.
        // Generates:
        // - @Test method: generate arguments and call compiled and reference test with it.
        //                 result verification (only if the result is known to be deterministic).
        //
        // - instantiate compiled and reference test methods.
        // - instantiate argument constraint methods (constrains test method arguments types).
        // - instantiate checksum method (summarizes value and bounds/bit checks).
        var testTemplate = Template.make("expression", (Expression expression) -> {
            // Fix the arguments for both the compiled and reference method.
            // We have a mix of variable and constant inputs to the expression.
            // The variable inputs are passed as method arguments to the test methods.
            List<MethodArgument> methodArguments = new ArrayList<>();
            List<Object> expressionArguments = new ArrayList<>();
            for (CodeGenerationDataNameType type : expression.argumentTypes) {
                switch (RANDOM.nextInt(2)) {
                    case 0 -> {
                        String name = $("arg" + methodArguments.size());
                        methodArguments.add(new MethodArgument(name, type));
                        expressionArguments.add(name);
                    }
                    default -> {
                        expressionArguments.add(type.con());
                    }
                }
            }
            return scope(
                let("methodArguments",
                    methodArguments.stream().map(ma -> ma.name).collect(Collectors.joining(", "))),
                let("methodArgumentsWithTypes",
                    methodArguments.stream().map(ma -> ma.type + " " + ma.name).collect(Collectors.joining(", "))),
                """
                @Test
                public static void $primitiveConTest() {
                    // In each iteration, generate new random values for the method arguments.
                """,
                methodArguments.stream().map(ma -> valueTemplate.asToken(ma.name, ma.type)).toList(),
                """
                    Object v0 = ${primitiveConTest}_compiled(#methodArguments);
                    Object v1 = ${primitiveConTest}_reference(#methodArguments);
                """,
                expression.info.isResultDeterministic ? "Verify.checkEQ(v0, v1);\n" : "// could fail - don't verify.\n",
                """
                }

                @DontInline
                public static Object ${primitiveConTest}_compiled(#methodArgumentsWithTypes) {
                """,
                // The arguments now have the bottom_type. Constrain the ranges and bits.
                methodArguments.stream().map(ma -> constrainArgumentTemplate.asToken(ma.name)).toList(),
                // Generate the body with the expression, and calling the checksum.
                bodyTemplate.asToken(expression, expressionArguments, $("checksum")),
                """
                }

                @DontCompile
                public static Object ${primitiveConTest}_reference(#methodArgumentsWithTypes) {
                """,
                methodArguments.stream().map(ma -> constrainArgumentTemplate.asToken(ma.name)).toList(),
                bodyTemplate.asToken(expression, expressionArguments, $("checksum")),
                """
                }

                """,
                methodArguments.stream().map(ma -> constrainArgumentMethodTemplate.asToken(ma.name, ma.type)).toList(),
                checksumTemplate.asToken(expression, $("checksum"))
            );
        });

        // Generate expressions with the primitive types.
        for (PrimitiveType type : PRIMITIVE_TYPES) {
            // Prmitive expressions are most important, so let's create many expressions per output type.
            for (int i = 0; i < 10; i++) {
                // The depth determines roughly how many operations are going to be used in the expression.
                int depth = RANDOM.nextInt(1, 20);
                Expression expression = Expression.nestRandomly(type, Operations.PRIMITIVE_OPERATIONS, depth);
                tests.add(testTemplate.asToken(expression));
            }
        }

        // Generate expressions with any scalar numeric types.
        for (CodeGenerationDataNameType type : SCALAR_NUMERIC_TYPES) {
            // The extended set of scalar numeric expressions (incl. special types such as Float16) are relevant
            // but don't currently warrant the same number amount of testing time, so we only create 2 cases
            // per type. Note: this still produces a lot of expressions, given that we have a lot of output
            // types, and even if the output type is "float", we can still use other types in the expression,
            // such as "float -> Float16 -> float". We can consider adjusting this arbitrary count in the future.
            for (int i = 0; i < 2; i++) {
                // The depth determines roughly how many operations are going to be used in the expression.
                int depth = RANDOM.nextInt(1, 20);
                Expression expression = Expression.nestRandomly(type, Operations.SCALAR_NUMERIC_OPERATIONS, depth);
                tests.add(testTemplate.asToken(expression));
            }
        }

        // Create the test class, which runs all tests.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.igvn.templated", "ExpressionFuzzerInnerTest",
            // Set of imports.
            Set.of("compiler.lib.verify.*",
                   "java.util.Random",
                   "jdk.test.lib.Utils",
                   "compiler.lib.generators.*",
                   "jdk.incubator.vector.Float16"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            tests);
    }
}
