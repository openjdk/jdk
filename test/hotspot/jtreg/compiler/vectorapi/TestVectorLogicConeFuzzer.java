/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8387204
 * @key randomness
 * @summary Fuzzer for the C2 vector "logic cone" (MacroLogicV) packing optimization.
 * @requires vm.compiler2.enabled
 * @requires os.simpleArch == "x64"
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @compile ../../compiler/lib/verify/Verify.java
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Random;

import jdk.test.lib.Utils;

import compiler.lib.compile_framework.CompileFramework;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;

import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Expression.Nesting;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.lib.template_framework.library.VectorType;

/**
 * Fuzzer for the vector logic-cone (MacroLogicV) optimization.
 *
 * <p>The generated tests build random expression trees using only bitwise logic
 * operations, fed by exactly three input vectors, so that C2's
 * {@code Compile::optimize_logic_cones} folds them into {@code MacroLogicV} (ternary
 * truth-table) nodes.
 *
 * <p>Predication is fuzzed too: a cone freely mixes non-predicated operations with
 * predicated ones, and a cone may use more than one mask ({@code m0}, {@code m1}).
 *
 */
public class TestVectorLogicConeFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

    // A non-predicated MacroLogicV is a pure bitwise fold, so it is inferred for every
    // integral element type; only the vector size matters (>= 128-bit, see the AVX512VL
    // requirement in Matcher::match_rule_supported_vector). Byte and short are therefore
    // covered as well, and only predication is restricted (see canPredicate below).
    private static final List<VectorType.Vector> LOGIC_TYPES = List.of(
        VectorType.BYTE_128,  VectorType.BYTE_256,  VectorType.BYTE_512,
        VectorType.SHORT_128, VectorType.SHORT_256, VectorType.SHORT_512,
        VectorType.INT_128,   VectorType.INT_256,   VectorType.INT_512,
        VectorType.LONG_128,  VectorType.LONG_256,  VectorType.LONG_512
    );

    private static final int SAMPLES_PER_TYPE = 15;

    // A predicated MacroLogicV maps to masked x86 VPTERNLOGD/Q, which is only defined for 32-
    // and 64-bit lanes; Matcher::match_rule_supported_vector_masked rejects any other element
    // type. Cones over byte and short are therefore generated non-predicated only.
    private static boolean canPredicate(VectorType.Vector t) {
        String carrier = t.elementType.carrierTypeName();
        return carrier.equals("int") || carrier.equals("long");
    }

    // Maximum number of distinct masks a single cone may use. A cone using one mask can pack
    // entirely into a predicated MacroLogicV; a cone using two masks forces the packing logic
    // to keep the differently-predicated parts apart.
    private static final int MAX_MASKS = 2;

    // Logic-only operation pool for a given vector type: the non-predicated operations plus,
    // for each of the "numMasks" masks m0..m<numMasks-1> declared in the generated method, the
    // predicated counterparts. Cones are nested from this pool at random, so they mix
    // non-predicated ops, ops sharing a mask, and ops under different masks.
    //
    // The operation set is exactly AndV, OrV, XorV and Not. Not is a unary op that C2 lowers to
    // a XorV with an all-ones vector; both encodings are generated:
    //   - all-ones in in(2): v.not() / v.lanewise(NOT, m)
    //   - all-ones in in(1): allOnes.lanewise(XOR, v [, m])
    private static List<Expression> logicOps(VectorType.Vector t, int numMasks) {
        String allOnes = t.name() + ".broadcast(" + t.speciesName + ", -1)";
        List<Expression> ops = new ArrayList<>(List.of(
            Expression.make(t, "", t, ".lanewise(VectorOperators.AND, ", t, ")"), // AndV
            Expression.make(t, "", t, ".lanewise(VectorOperators.OR, ",  t, ")"), // OrV
            Expression.make(t, "", t, ".lanewise(VectorOperators.XOR, ", t, ")"), // XorV
            Expression.make(t, "", t, ".not()"),                                  // Not, all-ones in in(2)
            Expression.make(t, allOnes + ".lanewise(VectorOperators.XOR, ", t, ")") // Not, all-ones in in(1)
        ));
        for (int i = 0; i < numMasks; i++) {
            String m = ", m" + i + ")";
            ops.add(Expression.make(t, "", t, ".lanewise(VectorOperators.AND, ", t, m)); // AndV
            ops.add(Expression.make(t, "", t, ".lanewise(VectorOperators.OR, ",  t, m)); // OrV
            ops.add(Expression.make(t, "", t, ".lanewise(VectorOperators.XOR, ", t, m)); // XorV
            ops.add(Expression.make(t, "", t, ".lanewise(VectorOperators.NOT, m" + i + ")")); // Not, all-ones in in(2)
            ops.add(Expression.make(t, allOnes + ".lanewise(VectorOperators.XOR, ", t, m)); // Not, all-ones in in(1)
        }
        return ops;
    }

    public static void main(String[] args) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("compiler.vectorapi.templated.LogicConeTemplated", generate(comp));
        comp.compile("--add-modules=jdk.incubator.vector");

        List<String> vmArgs = new ArrayList<>(List.of(
            "--add-modules=jdk.incubator.vector"
        ));
        vmArgs.addAll(Arrays.asList(args));

        comp.invoke("compiler.vectorapi.templated.LogicConeTemplated", "main",
                    new Object[] { vmArgs.toArray(new String[0]) });
    }

    public static String generate(CompileFramework comp) {
        List<TemplateToken> tests = new ArrayList<>();

        // Emit the LibraryRNG helper class used to fill the input arrays.
        tests.add(PrimitiveType.generateLibraryRNG());

        // Body shared by the compiled ($test) and reference ($reference) methods.
        var bodyTemplate = Template.make("expression", "arguments", "decls",
                (Expression expression, List<Object> arguments, List<Object> decls) -> {
            VectorType.Vector retType = (VectorType.Vector) expression.returnType;
            return scope(
                let("carrierType", retType.elementType.carrierTypeName()),
                decls,
                "#carrierType[] out = new #carrierType[1000];\n",
                expression.asToken(arguments), ".intoArray(out, 0);\n",
                "return out;\n"
            );
        });

        var testTemplate = Template.make("type", (VectorType.Vector type) -> {
            int numMasks = canPredicate(type) ? RANDOM.nextInt(1, MAX_MASKS + 1) : 0;

            // Generate a cone with at least 3 leaves so it can be fed by exactly 3 inputs.
            Expression expression;
            int attempts = 0;
            do {
                int depth = RANDOM.nextInt(3, 8); // roughly the number of logic ops in the cone
                expression = Expression.nestRandomly(type, logicOps(type, numMasks), depth, Nesting.EXACT);
            } while (expression.argumentTypes.size() < 3 && ++attempts < 50);

            String carrier = type.elementType.carrierTypeName();

            // MacroLogicV is a ternary (3-input) truth-table node, so it is only inferred
            // when the whole cone is fed by exactly 3 distinct input vectors. Feed the leaves
            // from v0, v1, v2 round-robin: this uses each input at least once and keeps the
            // two operands of every binary op distinct, avoiding self-cancelling identities
            // (e.g. v ^ v == 0) that would collapse the cone away from a MacroLogicV.
            List<Object> useArgs = new ArrayList<>();
            for (int i = 0; i < expression.argumentTypes.size(); i++) {
                var at = expression.argumentTypes.get(i);
                if (!(at instanceof VectorType.Vector)) {
                    throw new RuntimeException("unexpected argument type in logic cone: " + at);
                }
                useArgs.add("v" + (i % 3));
            }

            // Declarations shared by the compiled and the reference method: each mask once,
            // then v0..v2 loaded once from the three input arrays.
            List<Object> decls = new ArrayList<>();
            for (int i = 0; i < numMasks; i++) {
                decls.add(List.of("var m", Integer.toString(i), " = VectorMask.fromArray(",
                                  type.speciesName, ", mask_arr_", Integer.toString(i), ", 0);\n"));
            }
            for (int j = 0; j < 3; j++) {
                decls.add(List.of("var v", Integer.toString(j), " = ", type.name(),
                                  ".fromArray(", type.speciesName, ", arg_", Integer.toString(j), ", 0);\n"));
            }

            // Method arguments: the 3 input arrays followed by one array per mask.
            List<Object> defineAndFill = new ArrayList<>();
            StringBuilder passArgs = new StringBuilder("arg_0, arg_1, arg_2");
            List<Object> receiveArgs = new ArrayList<>();
            receiveArgs.add(List.of(carrier, "[] arg_0, ", carrier, "[] arg_1, ", carrier, "[] arg_2"));
            for (int j = 0; j < 3; j++) {
                String a = "arg_" + j;
                defineAndFill.add(List.of(carrier, "[] ", a, " = new ", carrier, "[1000];\n",
                                          "LibraryRNG.fill(", a, ");\n"));
            }
            for (int i = 0; i < numMasks; i++) {
                String ma = "mask_arr_" + i;
                defineAndFill.add("boolean[] " + ma + " = new boolean[1000];\nLibraryRNG.fill(" + ma + ");\n");
                passArgs.append(", ").append(ma);
                receiveArgs.add(", boolean[] " + ma);
            }

            // MacroLogicV IR matching is only asserted for non-masked cones; masked
            // cones may not pack into MacroLogicV (e.g. mixed masks or partial predication).
            Object testMethodHeader = numMasks == 0
                ? """
                @IR(applyIf = {"UseAVX", "3"}, counts = {IRNode.MACRO_LOGIC_V, " > 0 "})
                @Test
                public static Object $test(
                """
                : """
                @Test
                public static Object $test(
                """;

            return scope(
                let("leaves", expression.argumentTypes.size()),
                let("masks", numMasks),
                """
                // --- $test start (type: #type, leaves: #leaves, inputs: 3, masks: #masks) ---
                @Run(test = "$test")
                public void $run() {
                """,
                defineAndFill,
                "    Object r0 = $test(" + passArgs + ");\n",
                "    Object r1 = $reference(" + passArgs + ");\n",
                "    Verify.checkEQ(r0, r1);\n",
                """
                }

                """,
                testMethodHeader,
                receiveArgs,
                """
                ) {
                """,
                bodyTemplate.asToken(expression, useArgs, decls),
                """
                }

                @DontCompile
                public static Object $reference(
                """,
                receiveArgs,
                """
                ) {
                """,
                bodyTemplate.asToken(expression, useArgs, decls),
                """
                }
                // --- $test end ---
                """
            );
        });

        for (VectorType.Vector type : LOGIC_TYPES) {
            for (int i = 0; i < SAMPLES_PER_TYPE; i++) {
                tests.add(testTemplate.asToken(type));
            }
        }

        return TestFrameworkClass.render(
            "compiler.vectorapi.templated", "LogicConeTemplated",
            Set.of("compiler.lib.verify.*",
                   "compiler.lib.generators.*",
                   "jdk.incubator.vector.*",
                   "java.util.Random",
                   "jdk.test.lib.Utils"),
            comp.getEscapedClassPathOfCompiledClasses(),
            tests);
    }
}
