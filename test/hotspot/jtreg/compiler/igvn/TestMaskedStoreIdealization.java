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
import java.util.Collections;
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
        vmArgs.add("-XX:-StressReflectiveCode"); // Temporarily disable stress flag that causes unrelated failures.
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
                        .filter(vec -> vec.byteSize() <= maxVecByteSize && vec.elementType instanceof PrimitiveType)
                        .map(vec -> new TestPerShape(vec).generate())
                        .collect(Collectors.toList()));
        tests.add(PrimitiveType.generateLibraryRNG());

        return TestFrameworkClass.render(PACKAGE, CLASS_NAME, imports, comp.getEscapedClassPathOfCompiledClasses(), tests);
    }

    enum Operation {
        STORE_SCATTER,
        STORE_MASK,
        STORE_SCATTER_MASK,
        STORE_VECTOR_AFTER_SCATTER,
        RANDOM
    }

    record TestPerShape(VectorType.Vector vec) {
        TemplateToken generate() {
            final String testName = vec.elementType.boxedTypeName() + vec.length;

            // Select the index where we set the mask to false. The index is biased to
            // zero, as the original bug only triggered with the first element.
            final int idx = RANDOM.nextBoolean() ? RANDOM.nextInt(0, vec.length) : 0;

            var irVerification = Template.make("op", "arraySize", (Operation op, Integer arraySize) -> {
                // No IR-verification for random test cases.
                if (op == Operation.RANDOM) {
                    return scope("");
                }

                final PrimitiveType pty = (PrimitiveType) vec.elementType;
                final String ptyIR = pty.abbrev().equals("S") ? "C" : pty.abbrev();

                // Verify that the method contains two VectorStore{Masked|Scatter} nodes.
                var opVerification = Template.make(() -> {
                    if (vec.length <= 2) {
                        return scope("    // No Vector nodes are emitted for vectors of length 2 or shorter.\n");
                    }

                    if (Set.of(Operation.STORE_SCATTER, Operation.STORE_SCATTER_MASK, Operation.STORE_VECTOR_AFTER_SCATTER).contains(op) &&
                        vec.elementType.byteSize() < 4) {
                        return scope("    // StoreVectorScatter is not emitted for vectors of subword types.\n");
                    }

                    final String opIR = switch (op) {
                        case STORE_SCATTER              -> "STORE_VECTOR_SCATTER";
                        case STORE_MASK                 -> "STORE_VECTOR_MASKED";
                        case STORE_SCATTER_MASK         -> "STORE_VECTOR_SCATTER_MASKED";
                        case STORE_VECTOR_AFTER_SCATTER -> "STORE_VECTOR_SCATTER";
                        case RANDOM                     -> "";
                    };

                    final int numMatches = switch (op) {
                        case STORE_VECTOR_AFTER_SCATTER -> 1;
                        default                         -> 2;
                    };

                    return scope(
                        let("opIR", opIR),
                        let("matches", numMatches),
                        """
                            @IR(counts = {IRNode.#{opIR}, "=#{matches}"},
                                applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
                        """
                    );
                });

                return scope(
                    let("pty", vec.elementType.name()),
                    let("ptyIR", ptyIR),
                    let("idx", idx),
                    switch (op) {
                        case STORE_MASK, STORE_SCATTER_MASK ->
                        // For masked operations, depending on the generated mask and index map C2 does not manage to elide a branch from
                        // if (mask.allTrue()) {
                        //     intoArray(a, offset);
                        // } else {
                        //     intoArray(a, offset, mask);
                        // }
                        // leading to both stores of the diamond being live. This is highly profile dependent and cannot be predicted.
                        """
                            @IR(counts = {IRNode.START + "Store#{ptyIR}" + IRNode.MID + "(Memory: @aryptr:#{pty}\\\\[int:#{arraySize}\\\\]).*(:NotNull:exact\\\\[\\\\d+\\\\]).*" + IRNode.END, ">=1",
                                          IRNode.START + "Store#{ptyIR}" + IRNode.MID + "(Memory: @aryptr:#{pty}\\\\[int:#{arraySize}\\\\]).*(:NotNull:exact\\\\[\\\\d+\\\\]).*" + IRNode.END, "<=2"},
                                applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"},
                                phase = CompilePhase.BEFORE_MATCHING)
                        """;
                        case STORE_SCATTER ->
                        """
                            @IR(counts = {IRNode.START + "Store#{ptyIR}" + IRNode.MID + "(Memory: @aryptr:#{pty}\\\\[int:#{arraySize}\\\\]).*(:NotNull:exact\\\\[\\\\d+\\\\]).*" + IRNode.END, "=1"},
                                applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"},
                                phase = CompilePhase.BEFORE_MATCHING)
                        """;
                        case STORE_VECTOR_AFTER_SCATTER, RANDOM -> "";
                    },
                    opVerification.asToken()
                );
            });

            var testBody = Template.make("testCaseRandom", "op", "arraySize", (Random testCaseRandom, Operation op, Integer arraySize) -> {
                if (op == Operation.RANDOM) {
                    return scope(generateRandomTest(testCaseRandom).asToken());
                }

                var maskGeneration = Template.make(() -> scope(
                    let("idx", idx),
                    let("boxedTy", vec.elementType.boxedTypeName()),
                    let("species", vec.speciesName),
                    "        VectorMask<#boxedTy> mask = VectorMask.fromLong(#species, ",
                    testCaseRandom.nextInt(0, 10) == 0 ? testCaseRandom.nextLong() : "-1 - (1 << #idx)",
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

                var generation = switch (op) {
                    case STORE_SCATTER, STORE_VECTOR_AFTER_SCATTER -> indexMapGeneration.asToken();
                    case STORE_MASK                                -> maskGeneration.asToken();
                    case STORE_SCATTER_MASK                        ->
                        Template.make(() -> scope(indexMapGeneration.asToken(), maskGeneration.asToken())).asToken();
                    case RANDOM                                    -> throw new RuntimeException("unreachable");
                };

                var initStore  = switch (op) {
                    case STORE_SCATTER, STORE_VECTOR_AFTER_SCATTER -> "        v.intoArray(a, 0, indexMap, 0);\n";
                    case STORE_MASK                                -> "        v.intoArray(a, 0, mask);\n";
                    case STORE_SCATTER_MASK                        -> "        v.intoArray(a, 0, indexMap, 0, mask);\n";
                    case RANDOM                                    -> throw new RuntimeException("unreachable");
                };

                var keepStore = switch (op) {
                    case STORE_MASK, STORE_SCATTER, STORE_SCATTER_MASK -> "        a[#idx] = arrVal;\n";
                    case STORE_VECTOR_AFTER_SCATTER                    -> "        v.intoArray(a, 0);\n";
                    case RANDOM                                        -> throw new RuntimeException("unreachable");
                };

                var holeStore  = switch (op) {
                    case STORE_SCATTER              -> "        v.intoArray(a, 0, indexMap, 0);\n";
                    case STORE_MASK                 -> "        v.intoArray(a, 0, mask);\n";
                    case STORE_SCATTER_MASK         -> "        v.intoArray(a, 0, indexMap, 0, mask);\n";
                    case STORE_VECTOR_AFTER_SCATTER -> "";
                    case RANDOM                     -> throw new RuntimeException("unreachable");
                };

                return scope(
                    let("pty", vec.elementType.name()),
                    let("vecTy", vec.name()),
                    let("species", vec.speciesName),
                    let("idx", idx),
                    """
                            #pty[] a = new #pty[#arraySize];
                    """,
                    generation,
                    """

                            var v = #vecTy.broadcast(#species, broadcastVal);
                    """,
                    initStore,
                    keepStore,
                    holeStore,
                    """
                            return a;
                    """
                );
            });

            var testCase = Template.make("op", (Operation op) -> {
                // To get the same test body twice, use a new random instance for each generated test body with a seed fixed per test case.
                final int testCaseSeed = RANDOM.nextInt();

                String testCaseName = testName + switch (op) {
                    case STORE_SCATTER              -> "Scatter";
                    case STORE_MASK                 -> "Mask";
                    case STORE_SCATTER_MASK         -> "ScatterMask";
                    case STORE_VECTOR_AFTER_SCATTER -> "VectorAfterScatter";
                    case RANDOM                     -> "Random";
                };

                // The array size needs to be one larger than the number of lanes for scatter tests, so we can not write to one element.
                final int arraySize = switch (op) {
                    case STORE_SCATTER, STORE_SCATTER_MASK, STORE_VECTOR_AFTER_SCATTER -> vec.length + 1;
                    case STORE_MASK, RANDOM                                            -> vec.length;
                };

                return scope(
                    let("pty", vec.elementType.name()),
                    let("testCaseName", testCaseName),
                    let("boxedTy", vec.elementType.boxedTypeName()),
                    let("vecTy", vec.name()),
                    let("lanes", vec.length),
                    let("species", vec.speciesName),
                    let("idx", idx),
                    let("rngCall", vec.elementType.callLibraryRNG()),
                    let("broadcastVal", vec.elementType.con()),
                    let("arrVal", vec.elementType.con()),
                """
                    @Run(test = "test#{testCaseName}")
                    @Warmup(15_000)
                    static void run#{testCaseName}(RunInfo info) {
                        final #pty broadcastVal = #broadcastVal;
                        final #pty arrVal = #arrVal;
                        final #pty[] compiledResult = test#{testCaseName}(broadcastVal, arrVal);

                        if (!info.isWarmUp()) {
                            final #pty[] interpreterResult = reference#{testCaseName}(broadcastVal, arrVal);
                            if (!Arrays.equals(interpreterResult, compiledResult)) {
                                throw new RuntimeException("wrong result for test${testCaseName}:\\n" +
                                                           "  interpreter result: " + Arrays.toString(interpreterResult) + "\\n" +
                                                           "  compiled result: " + Arrays.toString(compiledResult));
                            }
                        }
                    }

                    @Test
                """,
                    irVerification.asToken(op, arraySize),
                """
                    static #pty[] test#{testCaseName}(#pty broadcastVal, #pty arrVal) {
                """,
                    testBody.asToken(new Random(testCaseSeed), op, arraySize),
                """
                    }

                    @DontCompile
                    static #pty[] reference#{testCaseName}(#pty broadcastVal, #pty arrVal) {
                """,
                    testBody.asToken(new Random(testCaseSeed), op, arraySize),
                """
                    }

                """
                );
            });

            return Template.make(() -> scope(
                Stream.of(Operation.class.getEnumConstants())
                         .map(op -> testCase.asToken(op))
                         .toList()
            )).asToken();
        }

        Template.ZeroArgs generateRandomTest(Random testCaseRandom) {
            final int arraySize = testCaseRandom.nextInt(vec.length + 1, 5 * vec.length);
            var maskHook = new Hook("MaskHook");
            var genRandomMask = Template.make("maskName", "vecTy", (String maskName, VectorType.Vector vecTy) -> scope(
                let("boxedTy", vecTy.elementType.boxedTypeName()),
                let("species", vecTy.speciesName),
                let("maskVal", testCaseRandom.nextLong()),
                """
                        VectorMask<#{boxedTy}> #maskName = VectorMask.fromLong(#species, #maskVal);
                """
            ));
            var genRandomIdxMap = Template.make("mapName", "maxIdx", "len", (String mapName, Integer maxIdx, Integer len) -> {
                ArrayList<Integer> possibleIndices = new ArrayList(IntStream.range(0, maxIdx).boxed().toList());
                Collections.shuffle(possibleIndices, testCaseRandom);
                return scope(
                    let("map", String.join(", ", possibleIndices.stream().limit(vec.length).map(i -> i.toString()).toList())),
                """
                        int[] #mapName = { #map };
                """
                );
            });
            var initialStore = Template.make(() -> {
                final int offset = testCaseRandom.nextInt(0, 4) == 0 ? testCaseRandom.nextInt(0, arraySize - vec.length) : 0;
                return scope(
                    let("offset", offset),
                    switch (testCaseRandom.nextInt(0,4)) {
                        case 0 -> scope(
                            """
                                    v.intoArray(a, #offset);
                            """
                            );
                        case 1 -> scope(
                            maskHook.insert(genRandomMask.asToken("initMask", vec)),
                            """
                                    v.intoArray(a, #offset, initMask);
                            """
                            );
                        case 2 -> scope(
                            maskHook.insert(genRandomIdxMap.asToken("initMap", arraySize - offset, vec.length)),
                            """
                                    v.intoArray(a, #offset, initMap, 0);
                            """
                            );
                        case 3 -> scope(
                            maskHook.insert(scope(
                                genRandomIdxMap.asToken("initMap", arraySize - offset, vec.length),
                                genRandomMask.asToken("initMask", vec)
                            )),
                            """
                                    v.intoArray(a, #offset, initMap, 0, initMask);
                            """
                            );
                        default -> throw new RuntimeException("unreachable");
                    }
                );
            });

            var storeThatShouldNotBeLost = Template.make(() -> {
                final int selection = testCaseRandom.nextInt(0,6);
                final VectorType.Vector narrowerVector = CodeGenerationDataNameType.VECTOR_VECTOR_TYPES
                                                            .stream()
                                                            .filter(v -> v.elementType == vec.elementType && v.length <= vec.length)
                                                            // Flip a coin on each reduction step to pick a random element.
                                                            .reduce(null, (l, r) -> l == null ? r : (testCaseRandom.nextBoolean() ? l : r));
                final int offset = testCaseRandom.nextInt(0, 4) == 0 ? testCaseRandom.nextInt(0, arraySize - narrowerVector.length) : 0;
                return scope(
                    let("offset", offset),
                    switch (selection) {
                        case 0, 1, 2, 3 -> scope(
                            let("vecTy", narrowerVector),
                            let("species", narrowerVector.speciesName),
                            """
                                    var vKeep = #vecTy.broadcast(#species, arrVal);
                            """
                            );
                        default -> "";
                    },
                    switch (selection) {
                        case 0 -> scope(
                            """
                                    vKeep.intoArray(a, #offset);
                            """
                            );
                        case 1 -> scope(
                            maskHook.insert(genRandomMask.asToken("keepMask", narrowerVector)),
                            """
                                    vKeep.intoArray(a, #offset, keepMask);
                            """
                            );
                        case 2 -> scope(
                            maskHook.insert(genRandomIdxMap.asToken("keepMap", arraySize - offset, narrowerVector.length)),
                            """
                                    vKeep.intoArray(a, #offset, keepMap, 0);
                            """
                            );
                        case 3 -> scope(
                            maskHook.insert(scope(
                                genRandomIdxMap.asToken("keepMap", arraySize - offset, narrowerVector.length),
                                genRandomMask.asToken("keepMask", narrowerVector)
                            )),
                            """
                                    vKeep.intoArray(a, #offset, keepMap, 0, keepMask);
                            """
                            );
                        case 4 -> scope(
                            let("idx", testCaseRandom.nextInt(0, arraySize)),
                            """
                                    a[#idx] = arrVal;
                            """
                        );
                        case 5 -> scope(
                            let("idx", testCaseRandom.nextInt(0, arraySize - vec.length)),
                            let("len", testCaseRandom.nextInt(1, vec.length + 1)),
                            """
                                    Arrays.fill(a, #idx, #idx + #len, arrVal);
                            """
                        );
                        default -> throw new RuntimeException("unreachable");
                    }
                );
            });

            var storeWithHole = Template.make(() -> {
                final int offset = testCaseRandom.nextInt(0, 4) == 0 ? testCaseRandom.nextInt(0, arraySize - vec.length) : 0;
                return scope(
                    let("offset", offset),
                    switch (testCaseRandom.nextInt(0,3)) {
                        case 0 -> scope(
                            maskHook.insert(genRandomMask.asToken("holeMask", vec)),
                            """
                                    v.intoArray(a, #offset, holeMask);
                            """
                            );
                        case 1 -> scope(
                            maskHook.insert(genRandomIdxMap.asToken("holeMap", arraySize - offset, vec.length)),
                            """
                                    v.intoArray(a, #offset, holeMap, 0);
                            """
                            );
                        case 2 -> scope(
                            maskHook.insert(scope(
                                genRandomIdxMap.asToken("holeMap", arraySize - offset, vec.length),
                                genRandomMask.asToken("holeMask", vec)
                            )),
                            """
                                    v.intoArray(a, #offset, holeMap, 0, holeMask);
                            """
                            );
                        default -> throw new RuntimeException("unreachable");
                    }
                );
            });

            return Template.make(() -> scope(
                let("pty", vec.elementType.name()),
                let("arraySize", arraySize),
                """
                        #pty[] a = new #pty[#arraySize];

                """,
                maskHook.anchor(scope(
                    // The code for generating masks and index maps goes here.
                    let("vecTy", vec.name()),
                    let("species", vec.speciesName),
                """
                        var v = #vecTy.broadcast(#species, broadcastVal);
                """,
                    initialStore.asToken(),
                    storeThatShouldNotBeLost.asToken(),
                    storeWithHole.asToken()
                )),
                """
                        return a;
                """
            ));
        }
    }
}
