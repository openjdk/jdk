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

/**
 * @test
 * @bug 8387073
 * @key randomness
 * @summary Narrower stores preceding masked vector stores must not be eliminated.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.igvn;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.*;

import jdk.incubator.vector.VectorShape;

import jdk.test.lib.Utils;
import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import compiler.lib.template_framework.library.*;

public class TestMaskedStoreIdealization {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final String PACKAGE = "compiler.igvn.generated";
    private static final String CLASS_NAME = "TestMaskedStoreIdealizationGenerated";

    public static void main(String[] args) {
        final CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode(PACKAGE + "." + CLASS_NAME, generate(comp));
        comp.compile("--add-modules=jdk.incubator.vector");

        List<String> vmArgs = new ArrayList<>(List.of(
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED"
        ));
        vmArgs.addAll(Arrays.asList(args)); // Forward args
        String[] vmArgsArray = vmArgs.toArray(new String[0]);

        comp.invoke(PACKAGE + "." + CLASS_NAME, "main", new Object[] { vmArgsArray });
    }

    private static String generate(CompileFramework comp) {
        final Set<String> imports = Set.of("java.util.Arrays",
                                           "java.util.Random",
                                           "jdk.incubator.vector.*",
                                           "jdk.test.lib.Utils",
                                           "compiler.lib.generators.*");

        // The preferred vector shape is the largest possible vector size.
        final int maxVecByteSize = VectorShape.preferredShape().vectorBitSize() / 8;

        final List<TemplateToken> tests = new ArrayList<>();
        // Add tests only for the vector shapes that
        tests.addAll(CodeGenerationDataNameType.VECTOR_VECTOR_TYPES
                        .stream()
                        .filter(vec -> vec.byteSize() <= maxVecByteSize)
                        .map(vec -> new TestPerShape(vec).generate())
                        .collect(Collectors.toList()));
        tests.add(PrimitiveType.generateLibraryRNG());

        return TestFrameworkClass.render(PACKAGE, CLASS_NAME, imports, comp.getEscapedClassPathOfCompiledClasses(), tests);
    }

    enum Operation {
        STORE_SCATTER,
        STORE_MASK,
        STORE_SCATTER_MASK
    }

    record TestPerShape(VectorType.Vector vec) {
        TemplateToken generate() {
            String testName = vec.elementType.boxedTypeName() + vec.length;

            // Select the index where we set the mask to false. The index is biased to
            // zero, as the original bug only triggered with the first element.
            final int idx = RANDOM.nextBoolean() ? RANDOM.nextInt(0, vec.length) : 0;

            var maskGeneration = Template.make(() -> scope(
                let("idx", idx),
                let("boxedTy", vec.elementType.boxedTypeName()),
                let("species", vec.speciesName),
                "        VectorMask<#boxedTy> mask = VectorMask.fromLong(#species, ",
                // 1
                RANDOM.nextInt(0, 10) == 0 ? RANDOM.nextLong() : "-1 - (1 << #idx)",
                ");\n"
            ));

            var indexMapGeneration = Template.make(() -> {
                // For the scatter tests, the array is one larger than the number of lanes so we can
                // map indices starting at idx to the next index, omitting idx.
                int[] indexMap = IntStream.range(0, vec.length)
                                          .map(i -> i >= idx ? i + 1 : i)
                                          .toArray();
                String indexMapStr = Arrays.toString(indexMap)
                                           .replace('[', '{')
                                           .replace(']', '}');
                return scope(
                    let("idx", idx),
                    let("len", vec.length),
                    let("idxMap", indexMapStr),
                    """
                            final int[] indexMap = #{idxMap};
                    """
                );
            });

            var irVerification = Template.make("op", "arraySize", (Operation op, Integer arraySize) -> {
                String ptyIR = vec.elementType.abbrev().equals("S") ? "C" : vec.elementType.abbrev();

                // Verify that the method contains two VectorStore{Masked|Scatter} nodes.
                var opVerification = Template.make(() -> {
                    // The nodes are only emitted with more than two lanes.
                    if (vec.length <= 2) {
                        return scope("");
                    }

                    String opIR = switch (op) {
                        case STORE_SCATTER      -> "STORE_VECTOR_SCATTER";
                        case STORE_MASK         -> "STORE_VECTOR_MASKED";
                        case STORE_SCATTER_MASK -> "STORE_VECTOR_SCATTER_MASKED";
                    };

                    // x64 does not emit specialised scatter nodes/instructions for subword types.
                    String cpuFeatures = "\"sve\", \"true\"";
                    if (op == Operation.STORE_MASK || vec.elementType.byteSize() >= 4) {
                        cpuFeatures = cpuFeatures + ", \"avx512\", \"true\"";
                    }

                    return scope(
                        let("opIR", opIR),
                        let("cpuFeatures", cpuFeatures),
                        """
                            @IR(counts = {IRNode.#{opIR}, "= 2"},
                                applyIfCPUFeatureOr = { #cpuFeatures })
                        """
                    );
                });

                return scope(
                    let("pty", vec.elementType.name()),
                    let("ptyIR", ptyIR),
                    let("idx", idx),
                    """
                        @IR(counts = {IRNode.START + "Store#{ptyIR}" + IRNode.MID + "(Memory: @aryptr:#{pty}\\\\[int:#{arraySize}\\\\]).*(:NotNull:exact\\\\[\\\\d+\\\\]).*" + IRNode.END, "= 1"},
                            applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"},
                            phase = CompilePhase.BEFORE_MATCHING)
                    """,
                    opVerification.asToken()
                );
            });

            var run = Template.make("testCaseName", (String testCaseName) -> scope(
                let("pty", vec.elementType.name()),
                let("boxedTy", vec.elementType.boxedTypeName()),
                let("vecTy", vec.name()),
                let("lanes", vec.length),
                let("species", vec.speciesName),
                let("idx", idx),
                let("rngCall", vec.elementType.callLibraryRNG()),
                """
                    @Run(test = "test#{testCaseName}", mode = RunMode.STANDALONE)
                    static void run#{testCaseName}() {
                        final #pty broadcastVal = #rngCall;
                        final #pty arrVal = #rngCall;

                        final #pty[] interpreterResult = test#{testCaseName}(broadcastVal, arrVal);
                        for (int i = 0; i < 10_000; i++) {
                            test#{testCaseName}(broadcastVal, arrVal);
                        }
                        final #pty[] compiledResult = test#{testCaseName}(broadcastVal, arrVal);
                        if (!Arrays.equals(interpreterResult, compiledResult)) {
                            throw new RuntimeException("wrong result:\\n" +
                                                       "  interpreter result: " + Arrays.toString(interpreterResult) + "\\n" +
                                                       "  compiled result: " + Arrays.toString(compiledResult));
                        }
                    }
                """
            ));

            var test = Template.make("testCaseName", "op", (String testCaseName, Operation op) -> {
                var generation = switch (op) {
                    case STORE_SCATTER      -> indexMapGeneration.asToken();
                    case STORE_MASK         -> maskGeneration.asToken();
                    case STORE_SCATTER_MASK ->
                        Template.make(() -> scope(indexMapGeneration.asToken(), maskGeneration.asToken())).asToken();
                };

                var intoArray = switch (op) {
                    case STORE_SCATTER      -> "        v.intoArray(a, 0, indexMap, 0);\n";
                    case STORE_MASK         -> "        v.intoArray(a, 0, mask);\n";
                    case STORE_SCATTER_MASK -> "        v.intoArray(a, 0, indexMap, 0, mask);\n";
                };

                // The array size needs to be one larger than the number of lanes for scatter tests, so we can not write to one element.
                final int arraySize = switch (op) {
                    case STORE_SCATTER, STORE_SCATTER_MASK -> vec.length + 1;
                    case STORE_MASK                        -> vec.length;
                };

                return scope(
                    let("pty", vec.elementType.name()),
                    let("boxedTy", vec.elementType.boxedTypeName()),
                    let("vecTy", vec.name()),
                    let("arraySize", arraySize),
                    let("species", vec.speciesName),
                    let("idx", idx),
                    """
                        @Test
                    """,
                    irVerification.asToken(op, arraySize),
                    """
                        static #pty[] test#{testCaseName}(final #pty broadcastVal, final #pty arrVal){
                            #pty[] a = new #pty[#arraySize];
                    """,
                    generation,
                    """

                            var v = #vecTy.broadcast(#species, broadcastVal);
                    """,
                    intoArray,
                    """

                            a[#idx] = arrVal;

                    """,
                    intoArray,
                    """
                            return a;
                        }

                    """
                );
            });

            var testCase = Template.make("op", (Operation op) -> {
                String testCaseName = testName + switch (op) {
                    case STORE_SCATTER      -> "Scatter";
                    case STORE_MASK         -> "Mask";
                    case STORE_SCATTER_MASK -> "ScatterMask";
                };
                return scope(
                    run.asToken(testCaseName),
                    test.asToken(testCaseName, op)
                );
            });

            return Template.make(() -> scope(
                Stream.of(Operation.STORE_SCATTER, Operation.STORE_MASK, Operation.STORE_SCATTER_MASK)
                         .map(op -> testCase.asToken(op))
                         .toList()
            )).asToken();
        }
    }

}
