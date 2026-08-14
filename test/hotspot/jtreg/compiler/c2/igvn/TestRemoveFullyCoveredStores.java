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

package compiler.c2.igvn;


import compiler.lib.compile_framework.*;
import compiler.lib.ir_framework.*;
import compiler.lib.template_framework.*;
import compiler.lib.template_framework.library.*;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.scope;

import jdk.incubator.vector.*;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Utils;
import java.util.*;
import java.util.stream.IntStream;

/*
 * @test
 * @bug 8387472
 * @key randomness
 * @summary Test removal of fully covered stores and same-pattern vector stores.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          jdk.incubator.vector
 * @run driver ${test.main.class}
 */

public class TestRemoveFullyCoveredStores {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final String PACKAGE = "compiler.c2.igvn.generated";
    private static final String CLASS_NAME = "TestRemoveFullyCoveredStoresGenerated";
    private static final int RANDOM_MIXED_ARRAY_SIZE = 128;

    public static void main(String[] args) {
        final CompileFramework comp = new CompileFramework();

        comp.addJavaSourceCode(PACKAGE + "." + CLASS_NAME, generate(comp));
        comp.compile("--add-modules=jdk.incubator.vector",
                     "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");

        List<String> vmArgs = new ArrayList<>(List.of(
            "--add-modules=jdk.incubator.vector",
            "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens=jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED"
        ));

        vmArgs.addAll(Arrays.asList(args));

        String[] vmArgsArray = vmArgs.toArray(new String[0]);
        comp.invoke(PACKAGE + "." + CLASS_NAME, "main", new Object[] {vmArgsArray});
    }

    private static String generate(CompileFramework comp) {
        final Set<String> imports = Set.of(
            "java.lang.foreign.MemorySegment",
            "java.lang.foreign.ValueLayout",
            "java.util.Arrays",
            "jdk.incubator.vector.*",
            "jdk.internal.misc.Unsafe"
        );

        List<TestCase> cases = new ArrayList<>();
        cases.add(unsafeCase(CaseType.POSITIVE));
        cases.add(unsafeCase(CaseType.NEGATIVE));

        cases.add(memorySegmentCase());

        cases.addAll(vectorCases(CaseType.POSITIVE));
        cases.addAll(vectorCases(CaseType.NEGATIVE));

        List<TemplateToken> tests = new ArrayList<>();
        tests.add(sharedGeneratedCode());
        tests.add(createTestInstances(cases));

        return TestFrameworkClass.render(
            PACKAGE,
            CLASS_NAME,
            imports,
            comp.getEscapedClassPathOfCompiledClasses(),
            tests
        );
    }

    // For vector test cases.
    enum StoreOperation {
        // A later wide vector store fully covers earlier scalar array stores.
        STORE_MIXED_ARRAY_VECTOR,
        // A later wide vector store fully covers earlier vector stores.
        STORE_VECTOR,
        // Masked vector stores at the same offset with the same mask.
        // A later store fully covers the earlier stores.
        STORE_VECTOR_MASK,
        // Scatter vector stores at the same offset with the same indices.
        // A later store fully covers the earlier stores.
        STORE_VECTOR_SCATTER,
        // Masked scatter vector stores at the same offset with the same mask and
        // indices. A later store fully covers the earlier stores.
        STORE_VECTOR_SCATTER_MASK,
        // Randomly shuffled Unsafe, MemorySegment, array and vector stores.
        // Correctness-only because the IR shape is unstable.
        STORE_RANDOM_MIXED
    }

    enum CaseType {
        // Optimization is expected to remove redundant earlier stores.
        POSITIVE,
        // Optimization is not expected, the case is used for correctness only.
        NEGATIVE
    }

    private static TemplateToken sharedGeneratedCode() {
        return Template.make(() -> scope(
            """
                static final Unsafe UNSAFE = Unsafe.getUnsafe();

                static final long BYTE_BASE = UNSAFE.arrayBaseOffset(byte[].class);
                static final long SHORT_BASE = UNSAFE.arrayBaseOffset(short[].class);
                static final long INT_BASE = UNSAFE.arrayBaseOffset(int[].class);
                static final long LONG_BASE = UNSAFE.arrayBaseOffset(long[].class);
                static final long FLOAT_BASE = UNSAFE.arrayBaseOffset(float[].class);
                static final long DOUBLE_BASE = UNSAFE.arrayBaseOffset(double[].class);

                static final ValueLayout.OfShort SHORT_UNALIGNED = ValueLayout.JAVA_SHORT.withByteAlignment(1);
                static final ValueLayout.OfInt INT_UNALIGNED = ValueLayout.JAVA_INT.withByteAlignment(1);
                static final ValueLayout.OfLong LONG_UNALIGNED = ValueLayout.JAVA_LONG.withByteAlignment(1);
                static final ValueLayout.OfFloat FLOAT_UNALIGNED = ValueLayout.JAVA_FLOAT.withByteAlignment(1);
                static final ValueLayout.OfDouble DOUBLE_UNALIGNED = ValueLayout.JAVA_DOUBLE.withByteAlignment(1);
            """
        )).asToken();
    }

    /**
     * Describe a generated test case.
     *
     * @param name           unique suffix of method name used for the generated test
     * @param pty            primitive array element type
     * @param arraySize      number of array elements to allocate for the generated test
     * @param irVerification optional IR annotation text for positive IR checks and
     *                       empty for correctness-only cases
     * @param body           generated test body
     */
    record TestCase(String name, String pty,
                    int arraySize, String irVerification,
                    TemplateToken body) {}

    // create all test instances
    private static TemplateToken createTestInstances(List<TestCase> cases) {
        var testCase = Template.make("c", (TestCase c) -> scope(
            let("name", c.name()),
            let("pty", c.pty()),
            let("arraySize", c.arraySize()),
            let("irVerification", c.irVerification()),
            """
                @Run(test = "test#name")
                static void run#name(RunInfo info) {
                    final #pty[] compiledResult = test#name();

                    if (!info.isWarmUp()) {
                        final #pty[] interpreterResult = reference#name();

                        if (!Arrays.equals(interpreterResult, compiledResult)) {
                            throw new RuntimeException("wrong result for test#name:\\n" +
                                                       "  interpreter result: " + Arrays.toString(interpreterResult) + "\\n" +
                                                       "  compiled result: " + Arrays.toString(compiledResult));
                        }
                    }
                }

                @Test
                #irVerification
                static #pty[] test#name() {
                    #pty[] array = new #pty[#arraySize];
            """,
            c.body(),
            """
                    return array;
                }

                @DontCompile
                static #pty[] reference#name() {
                    #pty[] array = new #pty[#arraySize];
            """,
            c.body(),
            """
                    return array;
                }

            """
        ));

        return Template.make(() -> scope(cases.stream().map(testCase::asToken)
                                              .toList())).asToken();
    }

    /**
     * Test unsafe stores.
     *
     * Positive:
     *
     * Unsafe Store byte  [4, 5)
     * Unsafe Store short [3, 5)
     * Unsafe Store int   [2, 6)
     * Unsafe Store int   [0, 4)
     * Unsafe Store long  [0, 8)
     *
     * The final StoreL fully covers all previous stores.
     *
     * Negative:
     *
     * Unsafe Store long  [0, 8)
     * Unsafe Store int   [0, 4)
     * Unsafe Store int   [2, 6)
     * Unsafe Store short [3, 5)
     * Unsafe Store byte  [4, 5)
     *
     * No later store fully covers the previous Stores.
     */
    private static TestCase unsafeCase(CaseType caseType) {
        boolean positive = caseType == CaseType.POSITIVE;

        return new TestCase (
            positive ? "UnsafeStoreLongCoversPreviousStores" :
                       "UnsafeStoresDoNotCoverPreviousStores",
            "byte",
            16,
            positive ?
            """
            @IR(failOn = {IRNode.STORE_B,
                          IRNode.STORE_C,
                          IRNode.STORE_I},
                counts = {IRNode.STORE_L, ">= 1"},
                phase = CompilePhase.BEFORE_MATCHING)
            """ : "",
            Template.make(() -> scope(
                positive ?
                """
                        UNSAFE.putByte(array, BYTE_BASE + 4, (byte)0x16);
                        UNSAFE.putShort(array, BYTE_BASE + 3, (short)0x1582);
                        UNSAFE.putInt(array, BYTE_BASE + 2, 0x12345678);
                        UNSAFE.putInt(array, BYTE_BASE, 0x87654321);
                        UNSAFE.putLong(array, BYTE_BASE, 0x1122334455667788L);
                """ :
                """
                        UNSAFE.putLong(array, BYTE_BASE, 0x1122334455667788L);
                        UNSAFE.putInt(array, BYTE_BASE, 0x87654321);
                        UNSAFE.putInt(array, BYTE_BASE + 2, 0x12345678);
                        UNSAFE.putShort(array, BYTE_BASE + 3, (short)0x1582);
                        UNSAFE.putByte(array, BYTE_BASE + 4, (byte)0x16);
                """
            )).asToken()
        );
    }

    /**
     * Test Mixed MemorySegment stores.
     *
     * MemorySegment Store byte  [6, 7)
     * MemorySegment Store short [5, 7)
     * MemorySegment Store int   [3, 7)
     * MemorySegment Store int   [1, 5)
     * MemorySegment Store long  [1, 9)
     * MemorySegment Store long  [1, 9)
     * MemorySegment Store int   [1, 5)
     * MemorySegment Store int   [3, 7)
     * MemorySegment Store short [5, 7)
     * MemorySegment Store byte  [6, 7)
     *
     * Correctness-only test because the IR shape is unstable.
     */
    private static TestCase memorySegmentCase() {
        return new TestCase (
            "MemorySegmentMixedStores",
            "byte",
            16,
            "",
            Template.make(() -> scope(
                """
                        MemorySegment segment = MemorySegment.ofArray(array);
                        segment.set(ValueLayout.JAVA_BYTE, 6, (byte)0x78);
                        segment.set(SHORT_UNALIGNED, 5, (short)0x6a6b);
                        segment.set(INT_UNALIGNED, 3, 0x11223344);
                        segment.set(INT_UNALIGNED, 1, 0x12345689);
                        segment.set(LONG_UNALIGNED, 1, 0x0102030405060708L);
                        segment.set(LONG_UNALIGNED, 1, 0x1020304050607080L);
                        segment.set(INT_UNALIGNED, 1, 0x22446688);
                        segment.set(INT_UNALIGNED, 3, 0x13579344);
                        segment.set(SHORT_UNALIGNED, 5, (short)0x7a8b);
                        segment.set(ValueLayout.JAVA_BYTE, 6, (byte)0x91);
                """
            )).asToken()
        );
    }

    // Test Vector stores.
    private static List<TestCase> vectorCases(CaseType caseType) {
        List<TestCase> cases = new ArrayList<>();

        for (VectorType.Vector vec : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
            // At least two lanes needed for later tests.
            if (vec.length <= 1) {
                continue;
            }

            int idx = vec.length / 2;
            // These cases use a later 512-bit vector store to cover earlier stores.
            // They are only valid when the current species is narrower than 512 bits.
            if (vectorBitSize(vec) < 512) {
                // Vector store case.
                cases.add(vectorCase(StoreOperation.STORE_VECTOR,
                                     vec, idx, caseType));
            }

            // Array and vector store case.
            cases.add(vectorCase(StoreOperation.STORE_MIXED_ARRAY_VECTOR,
                                 vec, idx, caseType));

            // Masked vector store case.
            cases.add(vectorCase(StoreOperation.STORE_VECTOR_MASK,
                                 vec, idx, caseType));

            // Generated scatter-based cases only for int/long/float/double vectors.
            if (vec.elementType.byteSize() >= 4) {
                // Scatter vector store case.
                cases.add(vectorCase(StoreOperation.STORE_VECTOR_SCATTER,
                                     vec, idx, caseType));
                // Scatter Masked vector store case.
                cases.add(vectorCase(StoreOperation.STORE_VECTOR_SCATTER_MASK,
                                     vec, idx, caseType));
            }

            // Random mixed store case. They are correctness-only and do not distinguish
            // positive and negative cases, just for generating two different randomized
            // test patterns.
            cases.add(vectorCase(StoreOperation.STORE_RANDOM_MIXED,
                                 vec, Math.max(1, idx), caseType));
        }

        return cases;
    }

    private static TestCase vectorCase(StoreOperation op,
                                       VectorType.Vector vec,
                                       int idx,
                                       CaseType caseType) {
        int arraySize = switch (op) {
            // The final store was fixed to SPECIES_512 to fully cover earlier stores
            // with narrower species, so the array size is based on the 512-bit species.
            case STORE_VECTOR               -> 512 * vec.length / vectorBitSize(vec);
            case STORE_VECTOR_MASK,
                 STORE_MIXED_ARRAY_VECTOR   -> vec.length;
            // Scatter index maps may access index vec.length after shifting lane indices,
            // so allocate one extra element.
            case STORE_VECTOR_SCATTER,
                 STORE_VECTOR_SCATTER_MASK  -> vec.length + 1;
            case STORE_RANDOM_MIXED         -> RANDOM_MIXED_ARRAY_SIZE;
        };

        Random testCaseRandom = new Random(RANDOM.nextInt());

        return new TestCase(vectorCaseName(op, vec, caseType),
                            vec.elementType.name(),
                            arraySize,
                            irVerification(op, vec, caseType),
                            vectorStoreBody(op, vec, caseType, idx, testCaseRandom));
    }

    private static String vectorCaseName(StoreOperation op,
                                         VectorType.Vector vec,
                                         CaseType caseType) {
        return vec.elementType.boxedTypeName() +
               vectorBitSize(vec) +
               caseType +
               switch (op) {
                   case STORE_MIXED_ARRAY_VECTOR  -> "mixedArrayVectorStore";
                   case STORE_VECTOR              -> "storeVectorCoversMaskedStoreVector";
                   case STORE_VECTOR_MASK         -> "storeVectorMaskedSameOffsetAndMask";
                   case STORE_VECTOR_SCATTER      -> "storeVectorScatterSameOffsetAndIndices";
                   case STORE_VECTOR_SCATTER_MASK -> "storeVectorScatterMaskedSameOffsetIndicesAndMask";
                   case STORE_RANDOM_MIXED        -> "randomMixedStores";
               };
    }

    private static String irVerification(StoreOperation op,
                                         VectorType.Vector vec,
                                         CaseType caseType) {
        if (caseType != CaseType.POSITIVE) {
            return "";
        }

        return switch (op) {
            case STORE_MIXED_ARRAY_VECTOR ->
                stableIR(op, vec) ?
                """
                @IR(failOn = {IRNode.STORE_I},
                    counts = {IRNode.STORE_VECTOR, "<= 1"},
                    phase = CompilePhase.BEFORE_MATCHING,
                    applyIf = {"MaxVectorSize", ">= 32"},
                    applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "sve", "true", "rvv", "true"})
                """:"";

            case STORE_VECTOR ->
                stableIR(op, vec) ?
                """
                @IR(failOn = {IRNode.STORE_VECTOR_MASKED},
                    counts = {IRNode.STORE_VECTOR, "<= 1"},
                    phase = CompilePhase.BEFORE_MATCHING,
                    applyIf = {"MaxVectorSize", ">= 64"},
                    applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
                """:"";

            case STORE_VECTOR_MASK ->
                stableIR(op, vec) ?
                """
                @IR(counts = {IRNode.STORE_VECTOR_MASKED, "<= 1"},
                    phase = CompilePhase.BEFORE_MATCHING,
                    applyIf = {"MaxVectorSize", ">= 32"},
                    applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
                """:"";

            case STORE_VECTOR_SCATTER ->
                stableIR(op, vec) ?
                """
                @IR(counts = {IRNode.STORE_VECTOR_SCATTER, "<= 1"},
                    phase = CompilePhase.BEFORE_MATCHING,
                    applyIf = {"MaxVectorSize", ">= 32"},
                    applyIfCPUFeatureOr = {"sve", "true", "avx512vl", "true", "rvv", "true"})
                """:"";

            case STORE_VECTOR_SCATTER_MASK ->
                stableIR(op, vec) ?
                """
                @IR(counts = {IRNode.STORE_VECTOR_SCATTER_MASKED, "<= 1"},
                    phase = CompilePhase.BEFORE_MATCHING,
                    applyIf = {"MaxVectorSize", ">= 32"},
                    applyIfCPUFeatureOr = {"sve", "true", "avx512vl", "true", "rvv", "true"})
                """:"";

            case STORE_RANDOM_MIXED -> "";
        };
    }

    private static TemplateToken vectorStoreBody(StoreOperation op,
                                                 VectorType.Vector vec,
                                                 CaseType caseType,
                                                 int idx,
                                                 Random testCaseRandom) {
        boolean positive = caseType == CaseType.POSITIVE;
        int idx2 = (idx + 1) % vec.length;

        String maskExpr1 = testCaseRandom.nextInt(0, 10) == 0 ? testCaseRandom.nextLong() + "L" :
                                                                "~(1L << " + idx + ")";
        String maskExpr2 = positive ? maskExpr1 : "~(1L << " + idx2 + ")";

        int[] indexMap1 = IntStream.range(0, vec.length)
                                   .map(i -> i >= idx ? i + 1 : i)
                                   .toArray();
        int[] indexMap2 = IntStream.range(0, vec.length)
                                   .map(i -> i >= idx2 ? i + 1 : i)
                                   .toArray();

        String indexMapStr1 = Arrays.toString(indexMap1)
                                    .replace('[', '{')
                                    .replace(']', '}');

        String indexMapStr2 = Arrays.toString(indexMap2)
                                    .replace('[', '{')
                                    .replace(']', '}');

        var maskGeneration = Template.make(() -> scope(
            let("boxedTy", vec.elementType.boxedTypeName()),
            let("species", vec.speciesName),
            let("maskExpr1", maskExpr1),
            let("maskExpr2", maskExpr2),
            """
                    VectorMask<#boxedTy> mask1 = VectorMask.fromLong(#species, #maskExpr1);
                    VectorMask<#boxedTy> mask2 = VectorMask.fromLong(#species, #maskExpr2);
            """
        ));

        var indexMapGeneration = Template.make(() -> scope(
            let("idxMap1", indexMapStr1),
            let("idxMap2", indexMapStr2),
            """
                    final int[] indexMap1 = #idxMap1;
                    final int[] indexMap2 = #idxMap2;
            """
        ));

        var generation = switch (op) {
            case STORE_MIXED_ARRAY_VECTOR,
                 STORE_RANDOM_MIXED        -> Template.make(() -> scope()).asToken();
            case STORE_VECTOR              -> maskGeneration.asToken();
            case STORE_VECTOR_MASK         -> maskGeneration.asToken();
            case STORE_VECTOR_SCATTER      -> indexMapGeneration.asToken();
            case STORE_VECTOR_SCATTER_MASK ->
                Template.make(() -> scope(
                    indexMapGeneration.asToken(),
                    maskGeneration.asToken()
                )).asToken();
        };

        String body = switch (op) {
            case STORE_MIXED_ARRAY_VECTOR  ->
                positive ?
                """
                        array[#idx] = #bv1;
                        array[#idx2] = #bv2;
                        v3.intoArray(array, 0);
                """ :
                """
                        v3.intoArray(array, 0);
                        array[#idx] = #bv1;
                        array[#idx2] = #bv2;
                """;

            case STORE_VECTOR ->
                positive ?
                """
                        if (mask1.allTrue()) {
                            return array;
                        }
                        v1.intoArray(array, 1, mask1);
                        v2.intoArray(array, 4);
                        v2.intoArray(array, 0);
                        v3.intoArray(array, 0);
                """ :
                """
                        if (mask1.allTrue()) {
                            return array;
                        }
                        v3.intoArray(array, 0);
                        v2.intoArray(array, 0);
                        v2.intoArray(array, 4);
                        v1.intoArray(array, 1, mask1);
                """;

            case STORE_VECTOR_MASK ->
                positive ?
                """
                        if (mask1.allTrue()) {
                            return array;
                        }

                        v1.intoArray(array, 0, mask1);
                        v2.intoArray(array, 0, mask1);
                        v3.intoArray(array, 0, mask1);
                """ :
                """
                        if (mask1.allTrue() || mask2.allTrue()) {
                            return array;
                        }

                        v1.intoArray(array, 0, mask1);
                        v2.intoArray(array, 0, mask2);
                        v3.intoArray(array, 0, mask2);
                """;

            case STORE_VECTOR_SCATTER ->
                positive ?
                """
                        v1.intoArray(array, 0, indexMap1, 0);
                        v2.intoArray(array, 0, indexMap1, 0);
                        v3.intoArray(array, 0, indexMap1, 0);
                """ :
                """
                        v1.intoArray(array, 0, indexMap1, 0);
                        v2.intoArray(array, 0, indexMap2, 0);
                        v3.intoArray(array, 0, indexMap2, 0);
                """;

            case STORE_VECTOR_SCATTER_MASK ->
                positive ?
                """
                        if (mask1.allTrue()) {
                            return array;
                        }

                        v1.intoArray(array, 0, indexMap1, 0, mask1);
                        v2.intoArray(array, 0, indexMap1, 0, mask1);
                        v3.intoArray(array, 0, indexMap1, 0, mask1);
                """ :
                """
                        if (mask1.allTrue() || mask2.allTrue()) {
                            return array;
                        }

                        v1.intoArray(array, 0, indexMap1, 0, mask1);
                        v2.intoArray(array, 0, indexMap2, 0, mask2);
                        v3.intoArray(array, 0, indexMap2, 0, mask2);
                """;
            case STORE_RANDOM_MIXED -> randomMixedBody(vec, testCaseRandom);
        };

        return Template.make(() -> scope(
            let("vecTy", vec.name()),
            let("species", vec.speciesName),
            let("lateSpecies", vec.name() + ".SPECIES_512"),
            let("idx", idx),
            let("idx2", idx2),
            let("bv1", literal(vec, 12)),
            let("bv2", literal(vec, 34)),
            let("bv3", literal(vec, 56)),
            generation,
            op == StoreOperation.STORE_VECTOR ?
            """
                    #vecTy v1 = #vecTy.broadcast(#species, #bv1);
                    #vecTy v2 = #vecTy.broadcast(#species, #bv2);
                    #vecTy v3 = #vecTy.broadcast(#lateSpecies, #bv3);
            """ :
            op == StoreOperation.STORE_RANDOM_MIXED ?
            ""  :
            """
                    #vecTy v1 = #vecTy.broadcast(#species, #bv1);
                    #vecTy v2 = #vecTy.broadcast(#species, #bv2);
                    #vecTy v3 = #vecTy.broadcast(#species, #bv3);
            """,
            body)).asToken();
    }

    private static String randomMixedBody(VectorType.Vector vec, Random random) {
        int arraySize = RANDOM_MIXED_ARRAY_SIZE;
        int vectorSize = vec.length;
        int maxVectorOffset = arraySize - vectorSize;
        int elemBytes = vec.elementType.byteSize();
        List<String> ops = new ArrayList<>();

        // Array stores.
        for (int i = 0; i < 2; i++) {
            int limit = (i == 0) ? vectorSize : arraySize;
            int offset = random.nextInt(0, limit);
            ops.add("        array[" + offset + "] = " + randomLiteral(vec, random) + ";");
        }

        // Unsafe stores.
        for (int i = 0; i < 2; i++) {
            int limit = (i == 0) ? vectorSize : arraySize;
            int offset = random.nextInt(0, limit);
            ops.add(randomUnsafeStore(offset, vec, randomLiteral(vec, random)));
        }

        // MemorySegment stores.
        for (int i = 0; i < 2; i++) {
            int byteLimit = ((i == 0) ? vectorSize : arraySize) * elemBytes;
            int byteOffset = random.nextInt(0, byteLimit - elemBytes + 1);
            ops.add(randomSegmentStore(byteOffset, vec, randomLiteral(vec, random)));
        }

        // Vector stores.
        ops.add(randomVectorStore(0, randomLiteral(vec, random)));
        ops.add(randomVectorStore(random.nextInt(0, maxVectorOffset + 1),
                                  randomLiteral(vec, random)));

        // Masked vector stores.
        ops.add(randomMaskedVectorStore(0, randomLiteral(vec, random),
                                        randomMask(random, vectorSize)));
        ops.add(randomMaskedVectorStore(random.nextInt(0, maxVectorOffset + 1),
                                        randomLiteral(vec, random),
                                        randomMask(random, vectorSize)));

        // Scatter vector store.
        int scatterOffset1 = 0;
        int[] scatterIndexMap1 = randomIndexMap(random, vectorSize,
                                                arraySize, scatterOffset1);
        ops.add(randomScatterVectorStore(scatterOffset1, scatterIndexMap1,
                                         randomLiteral(vec, random)));

        // Scatter masked vector store.
        int scatterOffset2 = random.nextInt(0, maxVectorOffset + 1);
        int[] scatterIndexMap2 = randomIndexMap(random, vectorSize,
                                                arraySize, scatterOffset2);
        ops.add(randomScatterMaskedVectorStore(scatterOffset2, scatterIndexMap2,
                                               randomLiteral(vec, random),
                                               randomMask(random, vectorSize)));

        Collections.shuffle(ops, random);

        return """
                       MemorySegment segment = MemorySegment.ofArray(array);

                       #ops
               """.replace("#ops", String.join("\n", ops));
    }

    private static String randomUnsafeStore(int elementOffset,
                                            VectorType.Vector vec,
                                            String value) {
        String byteOffset = arrayBase(vec) + " + " +
                            ((long) elementOffset * vec.elementType.byteSize());

        return switch (vec.elementType.name()) {
            case "byte"   -> "        UNSAFE.putByte(array, " + byteOffset + ", " + value + ");";
            case "short"  -> "        UNSAFE.putShort(array, " + byteOffset + ", " + value + ");";
            case "int"    -> "        UNSAFE.putInt(array, " + byteOffset + ", " + value + ");";
            case "long"   -> "        UNSAFE.putLong(array, " + byteOffset + ", " + value + ");";
            case "float"  -> "        UNSAFE.putFloat(array, " + byteOffset + ", " + value + ");";
            case "double" -> "        UNSAFE.putDouble(array, " + byteOffset + ", " + value + ");";
            default       ->
               throw new RuntimeException("unsupported vector element type: " +
                                          vec.elementType.name());
        };
    }

    private static String arrayBase(VectorType.Vector vec) {
        return switch (vec.elementType.name()) {
            case "byte"   -> "BYTE_BASE";
            case "short"  -> "SHORT_BASE";
            case "int"    -> "INT_BASE";
            case "long"   -> "LONG_BASE";
            case "float"  -> "FLOAT_BASE";
            case "double" -> "DOUBLE_BASE";
            default       ->
                throw new RuntimeException("unsupported vector element type: " +
                                           vec.elementType.name());
        };
    }

    private static String randomSegmentStore(int byteOffset, VectorType.Vector vec,
                                             String value) {
        return "        segment.set(" + layoutFor(vec) + ", " + byteOffset + ", " + value + ");";
    }

    private static String randomVectorStore(int offset, String value) {
        return "        #vecTy.broadcast(#species, " + value + ").intoArray(array, " + offset + ");";
    }

    private static String randomMaskedVectorStore(int offset, String value,
                                                  long mask) {
        return "        #vecTy.broadcast(#species, " + value + ").intoArray(array, " + offset +
               ", VectorMask.fromLong(#species, " + mask + "L));";
    }

    private static String randomScatterVectorStore(int offset, int[] indexMap,
                                                   String value) {
        return "        #vecTy.broadcast(#species, " + value + ").intoArray(array, " + offset +
               ", " + intArrayLiteral(indexMap) + ", 0);";
    }

    private static String randomScatterMaskedVectorStore(int offset, int[] indexMap,
                                                         String value, long mask) {
        return "        #vecTy.broadcast(#species, " + value + ").intoArray(array, " + offset +
               ", " + intArrayLiteral(indexMap) + ", 0, VectorMask.fromLong(#species, " +
               mask + "L));";
    }

    private static String intArrayLiteral(int[] values) {
        return "new int[] " + Arrays.toString(values)
                                    .replace('[', '{')
                                    .replace(']', '}');
    }

    private static int[] randomIndexMap(Random random, int vectorSize,
                                        int arraySize, int offset) {
        int[] indexMap = new int[vectorSize];

        for (int i = 0; i < vectorSize; i++) {
            indexMap[i] = random.nextInt(0, arraySize - offset);
        }

        return indexMap;
    }

    private static long randomMask(Random random, int vectorSize) {
        return ~(1L << random.nextInt(Math.min(vectorSize, Long.SIZE)));
    }

    private static String layoutFor(VectorType.Vector vec) {
        return switch (vec.elementType.name()) {
            case "byte"   -> "ValueLayout.JAVA_BYTE";
            case "short"  -> "SHORT_UNALIGNED";
            case "int"    -> "INT_UNALIGNED";
            case "long"   -> "LONG_UNALIGNED";
            case "float"  -> "FLOAT_UNALIGNED";
            case "double" -> "DOUBLE_UNALIGNED";
            default ->
                throw new RuntimeException("unsupported vector element type: " +
                                           vec.elementType.name());
        };
    }

    private static String randomLiteral(VectorType.Vector vec, Random random) {
        return switch (vec.elementType.name()) {
            case "byte"   -> "(byte)0x" + Integer.toHexString(random.nextInt() & 0xff);
            case "short"  -> "(short)0x" + Integer.toHexString(random.nextInt() & 0xffff);
            case "int"    -> "0x" + Integer.toHexString(random.nextInt());
            case "long"   -> "0x" + Long.toHexString(random.nextLong()) + "L";
            case "float"  -> random.nextInt(-1000, 1000) + ".0f";
            case "double" -> random.nextInt(-1000, 1000) + ".0d";
            default       ->
                throw new RuntimeException("unsupported vector element type: " +
                                           vec.elementType.name());
        };
    }

    private static String literal(VectorType.Vector vec, int value) {
        return switch (vec.elementType.name()) {
            case "byte"   -> "(byte)" + value;
            case "short"  -> "(short)" + value;
            case "int"    -> Integer.toString(value);
            case "long"   -> value + "L";
            case "float"  -> value + ".0f";
            case "double" -> value + ".0d";
            default       ->
                throw new RuntimeException("unsupported vector element type: " +
                                           vec.elementType.name());
        };
    }

    private static int vectorBitSize(VectorType.Vector vec) {
        return vec.byteSize() * 8;
    }

    // Not all generated cases have a stable IR shape. Only selected stable shapes
    // are checked with IR verification.
    private static boolean stableIR(StoreOperation op, VectorType.Vector vec) {
        return switch (op) {
            case STORE_VECTOR_SCATTER, STORE_VECTOR_SCATTER_MASK ->
                vec.elementType.name().equals("long") &&
                vectorBitSize(vec) == 256;

            case STORE_VECTOR_MASK, STORE_MIXED_ARRAY_VECTOR, STORE_VECTOR ->
                vec.elementType.name().equals("int") &&
                vectorBitSize(vec) == 256;

            default -> false;
        };
    }
}
