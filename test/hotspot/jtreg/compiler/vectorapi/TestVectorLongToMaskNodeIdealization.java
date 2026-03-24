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

package compiler.vectorapi;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Random;

import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.*;
import compiler.lib.compile_framework.CompileFramework;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;

/*
 * @test
 * @bug 8277997 8378968
 * @key randomness
 * @summary Testing some optimizations in VectorLongToMaskNode::Ideal
 *          For now: VectorMask.fromLong(.., mask.toLong())
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @compile ../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../compiler/lib/generators/Generators.java
 * @compile ../../compiler/lib/verify/Verify.java
 * @run driver ${test.main.class}
 */
public class TestVectorLongToMaskNodeIdealization {
    private static final Random RANDOM = Utils.getRandomInstance();

    static final long[] ONES_L = new long[64];
    static { Arrays.fill(ONES_L, 1); }

    static final boolean[] TRUES = new boolean[64];
    static { Arrays.fill(TRUES, true); }

    public static void main(String[] args) {
        // Run some tests directly first.
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();

        // Then also generate some random examples.
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("compiler.vectorapi.templated.Templated", generate(comp));
        comp.compile("--add-modules=jdk.incubator.vector");
        comp.invoke("compiler.vectorapi.templated.Templated", "main", new Object[] {new String[] {
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        }});
    }

    // -------------------------------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0", // Not yet optimized away
                  IRNode.VECTOR_STORE_MASK,                     "> 0", // Not yet optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Not yet optimized away
                  IRNode.VECTOR_BLEND_I,  IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "= 0", // Optimized away
                  IRNode.VECTOR_STORE_MASK,                     "= 0", // Optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Cast I->J
                  IRNode.VECTOR_BLEND_I,                        "= 0", // Not needed
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeature = {"avx512", "true"})
    // This is the original reproducer for JDK-8378968, which failed on AVX2 with a wrong result.
    public static Object test1() {
        // There was a bug here with AVX2:
        var ones = LongVector.broadcast(LongVector.SPECIES_256, 1);
        var trues_L256 = ones.compare(VectorOperators.NE, 0);
        // VectorMaskCmp #vectory<J,4>   -> (L-mask=0x0..0/0xF..F)

        var trues_I128 = VectorMask.fromLong(IntVector.SPECIES_128, trues_L256.toLong());
        // VectorStoreMask(L-mask to 0/1)
        // VectorMaskToLong
        // AndL(truncate)
        // VectorLongToMask -> 0/1
        //
        // VectorLongToMaskNode::Ideal transforms this into:
        // VectorMaskCmp #vectory<J,4>   -> (L-mask=0x0..0/0xF..F)
        // VectorMaskCastNode -> (L-mask=0x0..0/0xF..F to 0/1)
        //   But VectorMaskCastNode is not made for such mask conversion to boolean mask,
        //   and so it wrongly produces a 0x00/0xFF byte mask, instead of bytes 0x00/01.
        //   See: vector_mask_cast
        //
        // The correct transformation would have been to:
        // VectorMaskCmp #vectory<J,4>   -> (L-mask=0x0..0/0xF..F)
        // VectorStoreMask(L-mask to 0/1, i.e. 0x00/0x01 bytes)

        var zeros = IntVector.zero(IntVector.SPECIES_128);
        var m1s = zeros.lanewise(VectorOperators.NOT, trues_I128);
        // The rest of the code is:
        // VectorLoadMask (0/1 to I-mask=0x0..0/0xF..F)
        //   It expects x=0x00/0x01 bytes, and does a subtraction 0-x to get values 0x00/0xFF
        //   that are then widened to int-length.
        //   But if it instead gets 0x00/0xFF, the subtraction produces 0x00/0x01 values, which
        //   are widened to int 0x0..0/0..01 values.
        //   See: load_vector_mask
        // Blend, which expects I-mask (0x0..0/0xF..F)
        //   It looks at the 7th (uppermost) bit of every byte to determine which byte is taken.
        //   If it instead gets the 0x0..0/0x0..01 mask, it interprets both as "false".

        int[] out = new int[64];
        m1s.intoArray(out, 0);
        return out;
    }

    static final Object GOLD_TEST1 = test1();

    @Check(test = "test1")
    public static void check_test1(Object out) {
        Verify.checkEQ(GOLD_TEST1, out);
    }
    // -------------------------------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_L,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0", // Not yet optimized away
                  IRNode.VECTOR_STORE_MASK,                     "> 0", // Not yet optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Not yet optimized away: Cast Z->Z, see JDK-8379866
                  IRNode.VECTOR_BLEND_I,  IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_L,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "= 0", // Optimized away
                  IRNode.VECTOR_STORE_MASK,                     "= 0", // Optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Cast I->J
                  IRNode.VECTOR_BLEND_I,                        "= 0", // Not needed
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeature = {"avx512", "true"})
    // The original reproducer test1 could eventually constant-fold the comparison
    // with zero and trues. So let's make sure we load the data from a mutable array.
    public static Object test1b() {
        // load instead of broadcast:
        var ones = LongVector.fromArray(LongVector.SPECIES_256, ONES_L, 0);
        var trues_L256 = ones.compare(VectorOperators.NE, 0);

        var trues_I128 = VectorMask.fromLong(IntVector.SPECIES_128, trues_L256.toLong());

        var zeros = IntVector.zero(IntVector.SPECIES_128);
        var m1s = zeros.lanewise(VectorOperators.NOT, trues_I128);

        int[] out = new int[64];
        m1s.intoArray(out, 0);
        return out;
    }

    static final Object GOLD_TEST1B = test1b();

    @Check(test = "test1b")
    public static void check_test1b(Object out) {
        Verify.checkEQ(GOLD_TEST1B, out);
    }
    // -------------------------------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "= 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_Z,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "= 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0",
                  IRNode.VECTOR_STORE_MASK,                     "= 0",
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Not yet optimized Z->Z, see JDK-8379866
                  IRNode.VECTOR_BLEND_I,  IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "= 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_Z,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "= 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0",
                  IRNode.VECTOR_STORE_MASK,                     "= 0",
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Cast I->J
                  IRNode.VECTOR_BLEND_I,                        "= 0", // Not needed
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeature = {"avx512", "true"})
    // And now let's try a case where we load the mask from boolean array, so we don't
    // have the VectorStoreMask before the VectorMaskToLong.
    public static Object test1c() {
        // Load true mask from array directly.
        var trues_L256 = VectorMask.fromArray(LongVector.SPECIES_256, TRUES, 0);

        var trues_I128 = VectorMask.fromLong(IntVector.SPECIES_128, trues_L256.toLong());

        var zeros = IntVector.zero(IntVector.SPECIES_128);
        var m1s = zeros.lanewise(VectorOperators.NOT, trues_I128);

        int[] out = new int[64];
        m1s.intoArray(out, 0);
        return out;
    }

    static final Object GOLD_TEST1C = test1c();

    @Check(test = "test1c")
    public static void check_test1c(Object out) {
        Verify.checkEQ(GOLD_TEST1C, out);
    }
    // -------------------------------------------------------------------------------------

    // TODO: we can refactor this away once JDK-8369699 is integrated.
    record VectorType(PrimitiveType elementType, int length) {
        String typeName() {
            return switch(elementType.name()) {
                case "byte"   -> "ByteVector";
                case "short"  -> "ShortVector";
                case "int"    -> "IntVector";
                case "long"   -> "LongVector";
                case "float"  -> "FloatVector";
                case "double" -> "DoubleVector";
                default       -> throw new UnsupportedOperationException("Not supported: " + elementType.name());
            };
        }

        String speciesName() {
            return typeName() + ".SPECIES_" + bitSize();
        }

        int bitSize() {
            return elementType.byteSize() * length() * 8;
        }
    }

    public static final List<VectorType> VECTOR_TYPES = List.of(
        new VectorType(CodeGenerationDataNameType.bytes(), 8),
        new VectorType(CodeGenerationDataNameType.bytes(), 16),
        new VectorType(CodeGenerationDataNameType.bytes(), 32),
        new VectorType(CodeGenerationDataNameType.bytes(), 64),
        new VectorType(CodeGenerationDataNameType.shorts(), 4),
        new VectorType(CodeGenerationDataNameType.shorts(), 8),
        new VectorType(CodeGenerationDataNameType.shorts(), 16),
        new VectorType(CodeGenerationDataNameType.shorts(), 32),
        new VectorType(CodeGenerationDataNameType.ints(), 2),
        new VectorType(CodeGenerationDataNameType.ints(), 4),
        new VectorType(CodeGenerationDataNameType.ints(), 8),
        new VectorType(CodeGenerationDataNameType.ints(), 16),
        new VectorType(CodeGenerationDataNameType.longs(), 1),
        new VectorType(CodeGenerationDataNameType.longs(), 2),
        new VectorType(CodeGenerationDataNameType.longs(), 4),
        new VectorType(CodeGenerationDataNameType.longs(), 8),
        new VectorType(CodeGenerationDataNameType.floats(), 2),
        new VectorType(CodeGenerationDataNameType.floats(), 4),
        new VectorType(CodeGenerationDataNameType.floats(), 8),
        new VectorType(CodeGenerationDataNameType.floats(), 16),
        new VectorType(CodeGenerationDataNameType.doubles(), 1),
        new VectorType(CodeGenerationDataNameType.doubles(), 2),
        new VectorType(CodeGenerationDataNameType.doubles(), 4),
        new VectorType(CodeGenerationDataNameType.doubles(), 8)
    );

    private static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> tests = new ArrayList<>();

        var testTemplate = Template.make("t1", "t2", (VectorType t1, VectorType t2) -> scope(
            let("e1", t1.elementType()),
            let("e2", t2.elementType()),
            let("V1", t1.typeName()),
            let("V2", t2.typeName()),
            let("S1", t1.speciesName()),
            let("S2", t2.speciesName()),
            """
            // ------------ $test -------------
            @Test
            @Warmup(10_000)
            """,
            // Now let's generate some IR rules.
            // General Idea: if the lengths match, we can optimize. If the don't match, it is
            //               a truncation/zero extension, and we don't optimize.
            //
            // TODO: length=64 leads the AndL mask to be all-ones, and fold away immediately.
            //       We could eventually extend the optimization to handle that. See JDK-8379398.
            //
            // AVX512: expect vectorization in length range [4..64]
            //         TODO: 2-element masks are currently not properly intrinsified, see JDK-8378589.
            (t1.length() >= 4 && t2.length() >= 4)
            ?(  (t1.length() == t2.length && t1.length() != 64)
                ?   """
                    @IR(counts = {IRNode.VECTOR_LONG_TO_MASK, "= 0",  // Optimized away
                                  IRNode.VECTOR_MASK_TO_LONG, "= 0"}, // Optimized away
                        applyIfCPUFeature = {"avx512", "true"})
                    """
                :   """
                    @IR(counts = {IRNode.VECTOR_LONG_TO_MASK, "> 0",  // Cannot optimize
                                  IRNode.VECTOR_MASK_TO_LONG, "> 0"}, // Cannot optimize
                        applyIfCPUFeature = {"avx512", "true"})
                    """)
            :(   """
                 // AVX512: at least one vector length not in range [4..64] -> no IR rule.
                 """),
            // AVX2: expect vectorization if: length >= 4 and bitSize <= 256
            //       TODO: 2-element masks are currently not properly intrinsified, see JDK-8378589.
            (t1.bitSize() <= 256 && t2.bitSize() <= 256 && t1.length() >= 4 && t2.length() >= 4)
            ?(  (t1.length() == t2.length)
                ?   """
                    @IR(counts = {IRNode.VECTOR_LONG_TO_MASK, "= 0",  // Optimized away
                                  IRNode.VECTOR_MASK_TO_LONG, "= 0"}, // Optimized away
                        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
                    """
                :   """
                    @IR(counts = {IRNode.VECTOR_LONG_TO_MASK, "> 0",  // Cannot optimize
                                  IRNode.VECTOR_MASK_TO_LONG, "> 0"}, // Cannot optimize
                        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
                    """)
            :(   """
                 // AVX2: at least one vector length not: length >= 4 and bitSize <= 256 -> no IR rule.
                 """),
            """
            public static Object $test() {
                var inputs = #V1.fromArray(#S1, $INPUT, 0);
                var mask1 = inputs.compare(VectorOperators.GT, 0);

                var mask2 = VectorMask.fromLong(#S2, mask1.toLong());

                var zeros = #V2.zero(#S2);
                var m1s   = #V2.broadcast(#S2, -1);
                var res = zeros.blend(m1s, mask2);

                #e2[] out = new #e2[64];
                res.intoArray(out, 0);
                return out;
            }

            public static #e1[] $INPUT = new #e1[64];
            """,
            "static { for (int i = 0; i < $INPUT.length; i++) { $INPUT[i] = ",  t1.elementType().callLibraryRNG(), "; } }",
            """
            public static Object $GOLD = $test();

            @Check(test = "$test")
            public static void $check(Object val) {
                Verify.checkEQ($GOLD, val);
            }
            """
        ));

        tests.add(PrimitiveType.generateLibraryRNG());

        // It would take a bit long to cover all 20*20=400 combinations, but we can sample some:
        for (int i = 0; i < 20; i++) {
            VectorType t1 = VECTOR_TYPES.get(RANDOM.nextInt(VECTOR_TYPES.size()));
            VectorType t2 = VECTOR_TYPES.get(RANDOM.nextInt(VECTOR_TYPES.size()));
            tests.add(testTemplate.asToken(t1, t2));
        }

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.vectorapi.templated", "Templated",
            // List of imports.
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
