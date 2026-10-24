/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=vanilla
 * @bug 8324751
 * @summary Test Speculative Aliasing checks in SuperWord
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver/timeout=200 compiler.loopopts.superword.TestAliasingFuzzer vanilla
 */

/*
 * @test id=random-flags
 * @bug 8324751
 * @summary Test Speculative Aliasing checks in SuperWord
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver/timeout=200 compiler.loopopts.superword.TestAliasingFuzzer random-flags
 */

package compiler.loopopts.superword;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jdk.test.lib.Utils;

import compiler.lib.compile_framework.*;
import compiler.lib.generators.Generators;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;

import compiler.lib.template_framework.library.TestFrameworkClass;

/**
 * Simpler test cases can be found in {@link TestAliasing}.
 *
 * We randomly generate tests to verify the behavior of the aliasing runtime checks. We feature:
 * - Different primitive types:
 *   - for access type (primitive, we can have multiple types in a single loop)
 *   - for backing type (primitive and additionally we have native memory)
 * - Different AccessScenarios:
 *   - copy (load and store)
 *   - fill (using two stores)
 * - Different Aliasing: in some cases we never alias at runtime, in other cases we might
 *   -> Should exercise both the predicate and the multiversioning approach with the
 *      aliasing runtime checks.
 * - Backing memory
 *   - Arrays: using int-index
 *   - MemorySegment (backed by primitive array or native memory):
 *     - Using long-index with MemorySegment::getAtIndex
 *     - Using byte-offset with MemorySegment::get
 * - Loop iv:
 *   - forward (counting up) and backward (counting down)
 *   - Different iv stride:
 *     - inc/dec by one, and then scale with ivScale:   for (..; i++)  { access(i * 4); }
 *     - abs(ivScale) == 1, but use iv stride instead:  for (..; i+=4) { access(i); }
 *   - type of index, invars, and bounds (see isLongIvType)
 *     - int: for array and MemorySegment
 *     - long: for MemorySegment
 * - IR rules:
 *   - Verify that verification does (not) happen as expected.
 *   - Verify that we do not use multiversioning when no aliasing is expected at runtime.
 *     -> verify that the aliasing runtime check is not overly sensitive, so that the
 *        predicate does not fail unnecessarily and we have to recompile with multiversioning.
 *
 * Possible extensions (Future Work):
 * - Access with Unsafe
 * - Backing memory with Buffers
 * - AccessScenario:
 *   - More than two accesses
 * - Improve IR rules, once more cases vectorize (see e.g. JDK-8359688)
 * - Aliasing:
 *   - MemorySegment on same backing memory, creating different MemorySegments
 *     via slicing. Possibly overlapping MemorySegments.
 *   - CONTAINER_UNKNOWN_ALIASING_NEVER: currently always has different
 *     memory and split ranges. But we could alternate between same memory
 *     and split ranges, and then different memory but overlapping ranges.
 *     This would also be never aliasing.
 * - Generate cases that would catch bugs like JDK-8369902:
 *   - Large long constants, or scales. Probably only possible for MemorySegment.
 *   - Large number of invar, and reuse of invar so that they could cancle
 *     to zero, and need to be filtered out.
 */
public class TestAliasingFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

    public record MyType(String name, int byteSize, String con1, String con2, String layout) {
        @Override
        public String toString() { return name(); }

        public String letter() { return name().substring(0, 1).toUpperCase(); }
    }
    public static final String con1 = "0x0102030405060708L";
    public static final String con2 = "0x0910111213141516L";
    public static final String con1F = "Float.intBitsToFloat(0x01020304)";
    public static final String con2F = "Float.intBitsToFloat(0x09101112)";
    public static final String con1D = "Double.longBitsToDouble(" + con1 + ")";
    public static final String con2D = "Double.longBitsToDouble(" + con2 + ")";

    // List of primitive types for accesses and arrays.
    public static final MyType myByte   = new MyType("byte",   1, con1, con2,   "ValueLayout.JAVA_BYTE");
    public static final MyType myChar   = new MyType("char",   2, con1, con2,   "ValueLayout.JAVA_CHAR_UNALIGNED");
    public static final MyType myShort  = new MyType("short",  2, con1, con2,   "ValueLayout.JAVA_SHORT_UNALIGNED");
    public static final MyType myInt    = new MyType("int",    4, con1, con2,   "ValueLayout.JAVA_INT_UNALIGNED");
    public static final MyType myLong   = new MyType("long",   8, con1, con2,   "ValueLayout.JAVA_LONG_UNALIGNED");
    public static final MyType myFloat  = new MyType("float",  4, con1F, con2F, "ValueLayout.JAVA_FLOAT_UNALIGNED");
    public static final MyType myDouble = new MyType("double", 8, con1D, con2D, "ValueLayout.JAVA_DOUBLE_UNALIGNED");
    public static final List<MyType> primitiveTypes
        = List.of(myByte, myChar, myShort, myInt, myLong, myFloat, myDouble);

    // For native memory, we use this "fake" type. It has a byteSize of 1, since we measure the memory in bytes.
    public static final MyType myNative = new MyType("native", 1, null, null,   null);
    public static final List<MyType> primitiveTypesAndNative
        = List.of(myByte, myChar, myShort, myInt, myLong, myFloat, myDouble, myNative);

    // Do the containers (array, MemorySegment, etc) ever overlap?
    enum Aliasing {
        CONTAINER_DIFFERENT,
        CONTAINER_SAME_ALIASING_NEVER,
        CONTAINER_SAME_ALIASING_UNKNOWN,
        CONTAINER_UNKNOWN_ALIASING_NEVER,
        CONTAINER_UNKNOWN_ALIASING_UNKNOWN,
    }

    enum AccessScenario {
        COPY_LOAD_STORE,  // a[i1] = b[i2];
        FILL_STORE_STORE, // a[i1] = x; b[i2] = y;
    }

    enum ContainerKind {
        ARRAY,
        MEMORY_SEGMENT_LONG_ADR_SCALE,  // for (..; i++)  { access(i * 4); }
        MEMORY_SEGMENT_LONG_ADR_STRIDE, // for (..; i+=4) { access(i); }
        MEMORY_SEGMENT_AT_INDEX,
    }

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        long t0 = System.nanoTime();
        // Add a java source file.
        comp.addJavaSourceCode("compiler.loopopts.superword.templated.AliasingFuzzer", generate(comp));

        long t1 = System.nanoTime();
        // Compile the source file.
        comp.compile();

        long t2 = System.nanoTime();

        String[] flags = switch(args[0]) {
            case "vanilla" -> new String[] {};
            case "random-flags" -> randomFlags();
            default -> throw new RuntimeException("unknown run id=" + args[0]);
        };
        // Run the tests without any additional VM flags.
        // compiler.loopopts.superword.templated.AliasingFuzzer.main(new String[] {});
        comp.invoke("compiler.loopopts.superword.templated.AliasingFuzzer", "main", new Object[] {flags});
        long t3 = System.nanoTime();

        System.out.println("Code Generation:  " + (t1-t0) * 1e-9f);
        System.out.println("Code Compilation: " + (t2-t1) * 1e-9f);
        System.out.println("Running Tests:    " + (t3-t2) * 1e-9f);
    }

    public static String[] randomFlags() {
        // We don't want to always run with all flags, that is too expensive.
        // But let's make sure things don't completely, rot by running with some
        // random flags that are relevant.
        // We set the odds towards the "default" we are targetting.
        return new String[] {
            // Default disabled.
            "-XX:" + randomPlusMinus(1, 5) + "AlignVector",
            // Default enabled.
            "-XX:" + randomPlusMinus(5, 1) + "UseAutoVectorizationSpeculativeAliasingChecks",
            "-XX:" + randomPlusMinus(5, 1) + "UseAutoVectorizationPredicate",
            "-XX:" + randomPlusMinus(5, 1) + "ShortRunningLongLoop",
            // Either way is ok.
            "-XX:" + randomPlusMinus(1, 1) + "UseCompactObjectHeaders",
            "-XX:SuperWordAutomaticAlignment=" + RANDOM.nextInt(0,3)
        };
    }

    public static String randomPlusMinus(int plus, int minus) {
        return (RANDOM.nextInt(plus + minus) < plus) ? "+" : "-";
    }

    public static <T> T sample(List<T> list) {
        int r = RANDOM.nextInt(list.size());
        return list.get(r);
    }

    public static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> testTemplateTokens = new ArrayList<>();

        // Add some basic functionalities.
        testTemplateTokens.add(generateIndexForm());

        // Array tests
        for (int i = 0; i < 10; i++) {
            testTemplateTokens.add(TestGenerator.makeArray().generate());
        }

        // MemorySegment with getAtIndex / setAtIndex
        for (int i = 0; i < 20; i++) {
            testTemplateTokens.add(TestGenerator.makeMemorySegment().generate());
        }

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.loopopts.superword.templated", "AliasingFuzzer",
            // List of imports.
            Set.of("compiler.lib.generators.*",
                   "compiler.lib.verify.*",
                   "java.lang.foreign.*",
                   "java.util.Random",
                   "jdk.test.lib.Utils"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    // The IndexForm is used to model the index. We can use it for arrays, but also by
    // restricting the MemorySegment index to a simple index.
    //
    // Form:
    //   index = con + iv * ivScale + invar0 * invar0Scale + invarRest
    //                                                       [err]
    //
    // The index has a size >= 1, so that the index refers to a region:
    //   [index, index + size]
    //
    // The idea is that invarRest is always close to zero, with some small range [-err .. err].
    // The invar variables for invarRest must be in the range [-1, 0, 1], so that we can
    // estimate the error range from the invarRestScales.
    //
    // At runtime, we will have to generate inputs for the iv.lo/iv.hi, as well as the invar0,
    // so that the index range lays in some predetermined range [range.lo, range.hi] and the
    // ivStride:
    //
    // for (int iv = iv.lo; iv < iv.hi; iv += ivStride) {
    //     assert: range.lo <= index(iv)
    //                         index(iv) + size <= range.hi
    // }
    //
    // Since there are multiple memory accesses, we may have multiple indices to compute.
    // Since they are all in the same loop, the indices share the same iv.lo and iv.hi. Hence,
    // we fix either iv.lo or iv.hi, and compute the other via the constraints.
    //
    // Fix iv.lo, assume ivScale > 0:
    //   index(iv) is smallest for iv = iv.lo, so we must satisfy
    //     range.lo <= con + iv.lo * ivScale + invar0 * invar0Scale + invarRest
    //              <= con + iv.lo * ivScale + invar0 * invar0Scale - err
    //   It follows:
    //     invar0 * invar0Scale >= range.lo - con - iv.lo * ivScale + err
    //   This allows us to pick a invar0.
    //   Now, we can compute the largest iv.lo possible.
    //   index(iv) is largest for iv = iv.hi, so we must satisfy:
    //     range.hi >= con + iv.hi * ivScale + invar0 * invar0Scale + invarRest + size
    //              >= con + iv.hi * ivScale + invar0 * invar0Scale + err       + size
    //   It follows:
    //     iv.hi * ivScale <= range.hi - con - invar0 * invar0Scale - err - size
    //   This allows us to pick a iv.hi.
    //
    // More details can be found in the implementation below.
    //
    public static record IndexForm(int con, int ivScale, int invar0Scale, int[] invarRestScales, int size) {
        public static IndexForm random(int numInvarRest, int size, int ivStrideAbs) {
            int con = RANDOM.nextInt(-100_000, 100_000);
            int ivScale = randomScale(size / ivStrideAbs);
            int invar0Scale = randomScale(size);
            int[] invarRestScales = new int[numInvarRest];
            // Sample values [-1, 0, 1]
            for (int i = 0; i < invarRestScales.length; i++) {
                invarRestScales[i] = RANDOM.nextInt(-1, 2);
            }
            return new IndexForm(con, ivScale, invar0Scale, invarRestScales, size);
        }

        public static int randomScale(int size) {
            int scale = switch(RANDOM.nextInt(10)) {
                case 0 -> RANDOM.nextInt(1, 4 * size + 1); // any strided access
                default -> size; // in most cases, we do not want it to be strided
            };
            return RANDOM.nextBoolean() ? scale : -scale;
        }

        public String generate() {
            return "new IndexForm(" + con() + ", " + ivScale() + ", " + invar0Scale() + ", new int[] {" +
                   Arrays.stream(invarRestScales)
                         .mapToObj(String::valueOf)
                         .collect(Collectors.joining(", ")) +
                   "}, " + size() + ")";
        }

        public TemplateToken index(String invar0, String[] invarRest) {
            var template = Template.make(() -> scope(
                let("con", con),
                let("ivScale", ivScale),
                let("invar0Scale", invar0Scale),
                let("invar0", invar0),
                "#con + #ivScale * i + #invar0Scale * #invar0",
                IntStream.range(0, invarRestScales.length).mapToObj(
                    i -> List.of(" + ", invarRestScales[i], " * ", invarRest[i])
                ).toList()
            ));
            return template.asToken();
        }

        // MemorySegment need to be long-addressed, otherwise there can be int-overflow
        // in the index, and that prevents RangeCheck Elimination and Vectorization.
        public TemplateToken indexLong(String invar0, String[] invarRest) {
            var template = Template.make(() -> scope(
                let("con", con),
                let("ivScale", ivScale),
                let("invar0Scale", invar0Scale),
                let("invar0", invar0),
                "#{con}L + #{ivScale}L * i + #{invar0Scale}L * #invar0",
                IntStream.range(0, invarRestScales.length).mapToObj(
                    i -> List.of(" + ", invarRestScales[i], "L * ", invarRest[i])
                ).toList()
            ));
            return template.asToken();
        }
    }

    // Mirror the IndexForm from the generator to the test.
    public static TemplateToken generateIndexForm() {
        var template = Template.make(() -> scope(
            """
            private static final Random RANDOM = Utils.getRandomInstance();

            public static record IndexForm(int con, int ivScale, int invar0Scale, int[] invarRestScales, int size) {
                public IndexForm {
                    if (ivScale == 0 || invar0Scale == 0) {
                        throw new RuntimeException("Bad scales: " + ivScale + " " + invar0Scale);
                    }
                }

                public static record Range(int lo, int hi) {
                    public Range {
                        if (lo >= hi) { throw new RuntimeException("Bad range: " + lo + " " + hi); }
                    }
                }

                public int err() {
                    int sum = 0;
                    for (int scale : invarRestScales) { sum += Math.abs(scale); }
                    return sum;
                }

                public int invar0ForIvLo(Range range, int ivLo) {
                    if (ivScale > 0) {
                        // index(iv) is smallest for iv = ivLo, so we must satisfy:
                        //   range.lo <= con + iv.lo * ivScale + invar0 * invar0Scale + invarRest
                        //            <= con + iv.lo * ivScale + invar0 * invar0Scale - err
                        // It follows:
                        //   invar0 * invar0Scale >= range.lo - con - iv.lo * ivScale + err
                        int rhs = range.lo() - con - ivLo * ivScale + err();
                        int invar0 = (invar0Scale > 0)
                        ?
                            // invar0 * invar0Scale >=  range.lo - con - iv.lo * ivScale + err
                            // invar0               >= (range.lo - con - iv.lo * ivScale + err) / invar0Scale
                            Math.floorDiv(rhs + invar0Scale - 1, invar0Scale) // round up division
                        :
                            // invar0 * invar0Scale >=  range.lo - con - iv.lo * ivScale + err
                            // invar0               <= (range.lo - con - iv.lo * ivScale + err) / invar0Scale
                            Math.floorDiv(rhs, invar0Scale); // round down division
                        if (range.lo() > con + ivLo * ivScale + invar0 * invar0Scale - err()) {
                            throw new RuntimeException("sanity check failed (1)");
                        }
                        return invar0;
                    } else {
                        // index(iv) is largest for iv = ivLo, so we must satisfy:
                        //   range.hi >= con + iv.lo * ivScale + invar0 * invar0Scale + invarRest + size
                        //            >= con + iv.lo * ivScale + invar0 * invar0Scale + err       + size
                        // It follows:
                        //   invar0 * invar0Scale <= range.hi - con - iv.lo * ivScale - err - size
                        int rhs = range.hi() - con - ivLo * ivScale - err() - size();
                        int invar0 = (invar0Scale > 0)
                        ?
                            // invar0 * invar0Scale <= rhs
                            // invar0               <= rhs / invar0Scale
                            Math.floorDiv(rhs, invar0Scale) // round down division
                        :
                            // invar0 * invar0Scale <= rhs
                            // invar0               >= rhs / invar0Scale
                            Math.floorDiv(rhs + invar0Scale + 1, invar0Scale); // round up division
                        if (range.hi() < con + ivLo * ivScale + invar0 * invar0Scale + err() + size()) {
                            throw new RuntimeException("sanity check failed (2)");
                        }
                        return invar0;

                    }
                }

                public int ivHiForInvar0(Range range, int invar0) {
                    if (ivScale > 0) {
                        // index(iv) is largest for iv = ivHi, so we must satisfy:
                        //   range.hi >= con + iv.hi * ivScale + invar0 * invar0Scale + invarRest + size
                        //            >= con + iv.hi * ivScale + invar0 * invar0Scale + err       + size
                        // It follows:
                        //   iv.hi * ivScale <=  range.hi - con - invar0 * invar0Scale - err - size
                        //   iv.hi           <= (range.hi - con - invar0 * invar0Scale - err - size) / ivScale
                        int rhs = range.hi() - con - invar0 * invar0Scale - err() - size();
                        int ivHi = Math.floorDiv(rhs, ivScale); // round down division
                        if (range.hi() < con + ivHi * ivScale + invar0 * invar0Scale + err() + size()) {
                            throw new RuntimeException("sanity check failed (3)");
                        }
                        return ivHi;
                    } else {
                        // index(iv) is smallest for iv = ivHi, so we must satisfy:
                        //   range.lo <= con + iv.hi * ivScale + invar0 * invar0Scale + invarRest
                        //            <= con + iv.hi * ivScale + invar0 * invar0Scale - err
                        // It follows:
                        //   iv.hi * ivScale >=  range.lo - con - invar0 * invar0Scale + err
                        //   iv.hi           <= (range.lo - con - invar0 * invar0Scale + err) / ivScale
                        int rhs = range.lo() - con - invar0 * invar0Scale + err();
                        int ivHi = Math.floorDiv(rhs, ivScale); // round down division
                        if (range.lo() > con + ivHi * ivScale + invar0 * invar0Scale - err()) {
                            throw new RuntimeException("sanity check failed (4)");
                        }
                        return ivHi;

                    }
                }
            }
            """
        ));
        return template.asToken();
    }

    public static record TestGenerator(
        // The containers.
        int numContainers,
        int containerByteSize,
        ContainerKind containerKind,
        MyType containerElementType,

        // Do we count up or down, iterate over the containers forward or backward?
        boolean loopForward,
        int ivStrideAbs,
        boolean isLongIvType,

        // For all index forms: number of invariants in the rest, i.e. the [err] term.
        int numInvarRest,

        // Each access has an index form and a type.
        IndexForm[] accessIndexForm,
        MyType[] accessType,

        // The scenario.
        Aliasing aliasing,
        AccessScenario accessScenario) {

        public static TestGenerator makeArray() {
            // Sample some random parameters:
            Aliasing aliasing = sample(Arrays.asList(Aliasing.values()));
            AccessScenario accessScenario = sample(Arrays.asList(AccessScenario.values()));
            MyType type = sample(primitiveTypes);

            // size must be large enough for:
            //   - scale = 4
            //   - range with size / 4
            // -> need at least size 16_000 to ensure we have 1000 iterations
            // We want there to be a little variation, so alignment is not always the same.
            int numElements = Generators.G.safeRestrict(Generators.G.ints(), 18_000, 20_000).next();
            int containerByteSize = numElements * type.byteSize();
            boolean loopForward = RANDOM.nextBoolean();

            int numInvarRest = RANDOM.nextInt(5);
            int ivStrideAbs = 1;
            boolean isLongIvType = false; // int index
            var form0 = IndexForm.random(numInvarRest, 1, ivStrideAbs);
            var form1 = IndexForm.random(numInvarRest, 1, ivStrideAbs);

            return new TestGenerator(
                2,
                containerByteSize,
                ContainerKind.ARRAY,
                type,
                loopForward,
                ivStrideAbs,
                isLongIvType,
                numInvarRest,
                new IndexForm[] {form0, form1},
                new MyType[]    {type,   type},
                aliasing,
                accessScenario);
        }

        public static int alignUp(int value, int align) {
            return Math.ceilDiv(value, align) * align;
        }

        public static TestGenerator makeMemorySegment() {
            // Sample some random parameters:
            Aliasing aliasing = sample(Arrays.asList(Aliasing.values()));
            AccessScenario accessScenario = sample(Arrays.asList(AccessScenario.values()));
            // Backing memory can be native, access must be primitive.
            MyType containerElementType = sample(primitiveTypesAndNative);
            MyType accessType0 = sample(primitiveTypes);
            MyType accessType1 = sample(primitiveTypes);
            ContainerKind containerKind = sample(List.of(
                ContainerKind.MEMORY_SEGMENT_AT_INDEX,
                ContainerKind.MEMORY_SEGMENT_LONG_ADR_SCALE,
                ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE
            ));

            if (containerKind == ContainerKind.MEMORY_SEGMENT_AT_INDEX) {
                // The access types must be the same, it is a limitation of the index computation.
                accessType1 = accessType0;
            }

            final int minAccessSize = Math.min(accessType0.byteSize(), accessType1.byteSize());
            final int maxAccessSize = Math.max(accessType0.byteSize(), accessType1.byteSize());

            // size must be large enough for:
            //   - scale = 4
            //   - range with size / 4
            // -> need at least size 16_000 to ensure we have 1000 iterations
            // We want there to be a little variation, so alignment is not always the same.
            final int numAccessElements = Generators.G.safeRestrict(Generators.G.ints(), 18_000, 20_000).next();
            final int align = Math.max(maxAccessSize, containerElementType.byteSize());
            // We need to align up, so the size is divisible exactly by all involved type sizes.
            final int containerByteSize = alignUp(numAccessElements * maxAccessSize, align);
            final boolean loopForward = RANDOM.nextBoolean();

            final int numInvarRest = RANDOM.nextInt(5);
            int indexSize0 = accessType0.byteSize();
            int indexSize1 = accessType1.byteSize();
            if (containerKind == ContainerKind.MEMORY_SEGMENT_AT_INDEX) {
                // These are int-indeces for getAtIndex, so we index by element and not bytes.
                indexSize0 = 1;
                indexSize1 = 1;
            }

            boolean withAbsOneIvScale = containerKind == ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE;
            int ivStrideAbs = containerKind == ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE ? minAccessSize : 1;
            boolean isLongIvType = RANDOM.nextBoolean();
            var form0 = IndexForm.random(numInvarRest, indexSize0, ivStrideAbs);
            var form1 = IndexForm.random(numInvarRest, indexSize1, ivStrideAbs);

            return new TestGenerator(
                2,
                containerByteSize,
                containerKind,
                containerElementType,
                loopForward,
                ivStrideAbs,
                isLongIvType,
                numInvarRest,
                new IndexForm[] {form0, form1},
                new MyType[]    {accessType0, accessType1},
                aliasing,
                accessScenario);
        }

        public TemplateToken generate() {
            var testTemplate = Template.make(() -> {
                // Let's generate the variable names that are to be shared for the nested Templates.
                String[] invarRest = new String[numInvarRest];
                for (int i = 0; i < invarRest.length; i++) {
                    invarRest[i] = $("invar" + i);
                }
                String[] containerNames = new String[numContainers];
                for (int i = 0; i < numContainers; i++) {
                    containerNames[i] = $("container" + i);
                }
                String[] indexFormNames = new String[accessIndexForm.length];
                for (int i = 0; i < indexFormNames.length; i++) {
                    indexFormNames[i] = $("index" + i);
                }
                return scope(
                    """
                    // --- $test start ---
                    """,
                    generateTestFields(invarRest, containerNames, indexFormNames),
                    """
                    // Count the run invocations.
                    private static int $iterations = 0;

                    @Run(test = "$test")
                    @Warmup(100)
                    public static void $run(RunInfo info) {

                        // Once warmup is over (100x), repeat 10x to get reasonable coverage of the
                        // randomness in the tests.
                        int reps = info.isWarmUp() ? 10 : 1;
                        for (int r = 0; r < reps; r++) {

                            $iterations++;
                    """,
                    generateContainerInit(containerNames),
                    generateContainerAliasing(containerNames, $("iterations")),
                    generateRanges(),
                    generateBoundsAndInvariants(indexFormNames, invarRest),
                    """
                            // Run test and compare with interpreter results.
                    """,
                    generateCallMethod("result", $("test"), "test"),
                    generateCallMethod("expected", $("reference"), "reference"),
                    """
                            Verify.checkEQ(result, expected);
                        } // end reps
                    } // end $run

                    @Test
                    """,
                    generateIRRules(),
                    generateTestMethod($("test"), invarRest),
                    """
                    @DontCompile
                    """,
                    generateTestMethod($("reference"), invarRest),
                    """

                    // --- $test end ---
                    """
                );
            });
            return testTemplate.asToken();
        }

        private TemplateToken generateArrayField(String name, MyType type) {
            var template = Template.make(() -> scope(
                let("size", containerByteSize / type.byteSize()),
                let("name", name),
                let("type", type),
                """
                private static #type[] original_#name  = new #type[#size];
                private static #type[] test_#name      = new #type[#size];
                private static #type[] reference_#name = new #type[#size];
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateMemorySegmentField(String name, MyType type) {
            var template = Template.make(() -> scope(
                let("size", containerByteSize / type.byteSize()),
                let("byteSize", containerByteSize),
                let("name", name),
                let("type", type),
                (type == myNative
                 ?  """
                    private static MemorySegment original_#name  = Arena.ofAuto().allocate(#byteSize);
                    private static MemorySegment test_#name      = Arena.ofAuto().allocate(#byteSize);
                    private static MemorySegment reference_#name = Arena.ofAuto().allocate(#byteSize);
                    """
                 :  """
                    private static MemorySegment original_#name  = MemorySegment.ofArray(new #type[#size]);
                    private static MemorySegment test_#name      = MemorySegment.ofArray(new #type[#size]);
                    private static MemorySegment reference_#name = MemorySegment.ofArray(new #type[#size]);
                    """
                )
            ));
            return template.asToken();
        }

        private TemplateToken generateIndexField(String name, IndexForm form) {
            var template = Template.make(() -> scope(
                let("name", name),
                let("form", form.generate()),
                """
                private static IndexForm #name = #form;
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateTestFields(String[] invarRest, String[] containerNames, String[] indexFormNames) {
            var template = Template.make(() -> scope(
                let("ivType", isLongIvType ? "long" : "int"),
                """
                // invarRest fields:
                """,
                Arrays.stream(invarRest).map(invar ->
                    List.of("private static #ivType ", invar, " = 0;\n")
                ).toList(),
                """
                // Containers fields:
                """,
                Arrays.stream(containerNames).map(name ->
                    switch (containerKind) {
                        case ContainerKind.ARRAY ->
                            generateArrayField(name, containerElementType);
                        case ContainerKind.MEMORY_SEGMENT_AT_INDEX,
                             ContainerKind.MEMORY_SEGMENT_LONG_ADR_SCALE,
                             ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE ->
                            generateMemorySegmentField(name, containerElementType);
                    }
                ).toList(),
                """
                // Index forms for the accesses:
                """,
                IntStream.range(0, indexFormNames.length).mapToObj(i ->
                    generateIndexField(indexFormNames[i], accessIndexForm[i])
                ).toList()
            ));
            return template.asToken();
        }

        private TemplateToken generateContainerInitArray(String name) {
            var template = Template.make(() -> scope(
                let("size", containerByteSize / containerElementType.byteSize()),
                let("name", name),
                """
                System.arraycopy(original_#name, 0, test_#name, 0, #size);
                System.arraycopy(original_#name, 0, reference_#name, 0, #size);
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateContainerInitMemorySegment(String name) {
            var template = Template.make(() -> scope(
                let("size", containerByteSize / containerElementType.byteSize()),
                let("name", name),
                """
                test_#name.copyFrom(original_#name);
                reference_#name.copyFrom(original_#name);
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateContainerInit(String[] containerNames) {
            var template = Template.make(() -> scope(
                """
                // Init containers from original data:
                """,
                Arrays.stream(containerNames).map(name ->
                    switch (containerKind) {
                        case ContainerKind.ARRAY ->
                            generateContainerInitArray(name);
                        case ContainerKind.MEMORY_SEGMENT_AT_INDEX,
                             ContainerKind.MEMORY_SEGMENT_LONG_ADR_SCALE,
                             ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE ->
                            generateContainerInitMemorySegment(name);
                    }
                ).toList()
             ));
            return template.asToken();
        }

        private TemplateToken generateContainerAliasingAssignment(int i, String name1, String name2, String iterations) {
            var template = Template.make(() -> scope(
                let("i", i),
                let("name1", name1),
                let("name2", name2),
                let("iterations", iterations),
                """
                var test_#i      = (#iterations % 2 == 0) ? test_#name1      : test_#name2;
                var reference_#i = (#iterations % 2 == 0) ? reference_#name1 : reference_#name2;
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateContainerAliasing(String[] containerNames, String iterations) {
            var template = Template.make(() -> scope(
                """
                // Container aliasing:
                """,
                IntStream.range(0, containerNames.length).mapToObj(i ->
                    switch(aliasing) {
                        case Aliasing.CONTAINER_DIFFERENT ->
                            generateContainerAliasingAssignment(i, containerNames[i], containerNames[i], iterations);
                        case Aliasing.CONTAINER_SAME_ALIASING_NEVER,
                             Aliasing.CONTAINER_SAME_ALIASING_UNKNOWN ->
                            generateContainerAliasingAssignment(i, containerNames[0], containerNames[0], iterations);
                        case Aliasing.CONTAINER_UNKNOWN_ALIASING_NEVER,
                             Aliasing.CONTAINER_UNKNOWN_ALIASING_UNKNOWN ->
                            generateContainerAliasingAssignment(i, containerNames[i], containerNames[0], iterations);
                    }
                ).toList()
             ));
            return template.asToken();
        }

        private TemplateToken generateRanges() {
            int size = switch (containerKind) {
                case ContainerKind.ARRAY,
                     ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
                    // Access with element index
                    containerByteSize / accessType[0].byteSize();
                case ContainerKind.MEMORY_SEGMENT_LONG_ADR_SCALE,
                     ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE ->
                    // Access with byte offset
                    containerByteSize;
            };

            if (accessIndexForm.length != 2) { throw new RuntimeException("not yet implemented"); }

            var templateSplitRanges = Template.make(() -> scope(
                let("size", size),
                """
                int middle = RANDOM.nextInt(#size / 3, #size * 2 / 3);
                int rnd = Math.min(256, #size / 10);
                int range = #size / 3 - RANDOM.nextInt(rnd);
                """,
                (RANDOM.nextBoolean()
                 // Maximal range
                 ?  """
                    var r0 = new IndexForm.Range(0, middle);
                    var r1 = new IndexForm.Range(middle, #size);
                    """
                 // Same size range
                 // If the accesses run towards each other, and the runtime
                 // check is too relaxed, we may fail the checks even though
                 // there is no overlap. Having same size ranges makes this
                 // more likely, and we could detect it if we get multiversioning
                 // unexpectedly.
                 :  """
                    var r0 = new IndexForm.Range(middle - range, middle);
                    var r1 = new IndexForm.Range(middle, middle + range);
                    """
                ),
                """
                if (RANDOM.nextBoolean()) {
                    var tmp = r0;
                    r0 = r1;
                    r1 = tmp;
                }
                """
            ));

            var templateWholeRanges = Template.make(() -> scope(
                let("size", size),
                """
                var r0 = new IndexForm.Range(0, #size);
                var r1 = new IndexForm.Range(0, #size);
                """
            ));

            var templateRandomRanges = Template.make(() -> scope(
                let("size", size),
                """
                int lo0 = RANDOM.nextInt(0, #size * 3 / 4);
                int lo1 = RANDOM.nextInt(0, #size * 3 / 4);
                var r0 = new IndexForm.Range(lo0, lo0 + #size / 4);
                var r1 = new IndexForm.Range(lo1, lo1 + #size / 4);
                """
            ));

            var templateSmallOverlapRanges = Template.make(() -> scope(
                // Idea: same size ranges, with size "range". A small overlap,
                //       so that bad runtime checks would create wrong results.
                let("size", size),
                """
                int rnd = Math.min(256, #size / 10);
                int middle = #size / 2 + RANDOM.nextInt(-rnd, rnd);
                int range = #size / 3 - RANDOM.nextInt(rnd);
                int overlap = RANDOM.nextInt(-rnd, rnd);
                var r0 = new IndexForm.Range(middle - range + overlap, middle + overlap);
                var r1 = new IndexForm.Range(middle, middle + range);
                if (RANDOM.nextBoolean()) {
                    var tmp = r0;
                    r0 = r1;
                    r1 = tmp;
                }
                """
                // Can this go out of bounds? Assume worst case on lower end:
                //   middle         - range          + overlap
                //   (size/2 - rnd) - (size/3 - rnd) - rnd
                //   size/6 - rnd
                // -> safe with rnd = size/10
            ));

            var templateAnyRanges = Template.make(() -> scope(
                switch(RANDOM.nextInt(4)) {
                    case 0 -> templateSplitRanges.asToken();
                    case 1 -> templateWholeRanges.asToken();
                    case 2 -> templateRandomRanges.asToken();
                    case 3 -> templateSmallOverlapRanges.asToken();
                    default -> throw new RuntimeException("impossible");
                }
            ));

            var template = Template.make(() -> scope(
                """
                // Generate ranges:
                """,
                switch(aliasing) {
                    case Aliasing.CONTAINER_DIFFERENT ->
                        templateAnyRanges.asToken();
                    case Aliasing.CONTAINER_SAME_ALIASING_NEVER ->
                        templateSplitRanges.asToken();
                    case Aliasing.CONTAINER_SAME_ALIASING_UNKNOWN ->
                        templateAnyRanges.asToken();
                    case Aliasing.CONTAINER_UNKNOWN_ALIASING_NEVER ->
                        templateSplitRanges.asToken();
                    case Aliasing.CONTAINER_UNKNOWN_ALIASING_UNKNOWN ->
                        templateAnyRanges.asToken();
                }
            ));
            return template.asToken();
        }

        private TemplateToken generateBoundsAndInvariants(String[] indexFormNames, String[] invarRest) {
            // We want there to be at least 1000 iterations.
            final int minIvRange = ivStrideAbs * 1000;

            var template = Template.make(() -> scope(
                let("containerByteSize", containerByteSize),
                """
                // Compute loop bounds and loop invariants.
                int ivLo = RANDOM.nextInt(-1000, 1000);
                int ivHi = ivLo + #containerByteSize;
                """,
                IntStream.range(0, indexFormNames.length).mapToObj(i ->
                    Template.make(() -> scope(
                        let("i", i),
                        let("form", indexFormNames[i]),
                        """
                        int invar0_#i = #form.invar0ForIvLo(r#i, ivLo);
                        ivHi = Math.min(ivHi, #form.ivHiForInvar0(r#i, invar0_#i));
                        """
                    )).asToken()
                ).toList(),
                let("minIvRange", minIvRange),
                """
                // Let's check that the range is large enough, so that the vectorized
                // main loop can even be entered.
                if (ivLo + #minIvRange > ivHi) { throw new RuntimeException("iv range too small: " + ivLo + " " + ivHi); }
                """,
                Arrays.stream(invarRest).map(invar ->
                    List.of(invar, " = RANDOM.nextInt(-1, 2);\n")
                ).toList(),
                """
                // Verify the bounds we just created, just to be sure there is no unexpected aliasing!
                int i = ivLo;
                """,
                IntStream.range(0, indexFormNames.length).mapToObj(i ->
                    List.of("int lo_", i, " = (int)(", accessIndexForm[i].index("invar0_" + i, invarRest), ");\n")
                ).toList(),
                """
                i = ivHi;
                """,
                IntStream.range(0, indexFormNames.length).mapToObj(i ->
                    List.of("int hi_", i, " =  (int)(", accessIndexForm[i].index("invar0_" + i, invarRest), ");\n")
                ).toList(),
                switch(aliasing) {
                    case Aliasing.CONTAINER_SAME_ALIASING_NEVER,
                         Aliasing.CONTAINER_UNKNOWN_ALIASING_NEVER -> // could fail in the future if we make it smarter
                        List.of(
                        """
                        // Bounds should not overlap.
                        if (false
                        """,
                        IntStream.range(0, indexFormNames.length).mapToObj(i1 ->
                            IntStream.range(0, i1).mapToObj(i2 ->
                                Template.make(() -> scope(
                                    let("i1", i1),
                                    let("i2", i2),
                                    // i1 < i2 or i1 > i2
                                    """
                                    || (lo_#i1 < lo_#i2 && lo_#i1 < hi_#i2 && hi_#i1 < lo_#i2 && hi_#i1 < hi_#i2)
                                    || (lo_#i1 > lo_#i2 && lo_#i1 > hi_#i2 && hi_#i1 > lo_#i2 && hi_#i1 > hi_#i2)
                                    """
                                )).asToken()
                            ).toList()
                        ).toList(),
                        """
                        ) {
                            // pass
                        } else {
                            throw new RuntimeException("bounds overlap!");
                        }
                        """);
                    case Aliasing.CONTAINER_DIFFERENT,
                         Aliasing.CONTAINER_SAME_ALIASING_UNKNOWN,
                         Aliasing.CONTAINER_UNKNOWN_ALIASING_UNKNOWN ->
                        """
                        // Aliasing unknown, cannot verify bounds.
                        """;
                }
            ));
            return template.asToken();
        }


        private TemplateToken generateCallMethod(String output, String methodName, String containerPrefix) {
            var template = Template.make(() -> scope(
                let("output", output),
                let("methodName", methodName),
                "var #output = #methodName(",
                IntStream.range(0, numContainers).mapToObj(i ->
                    List.of(containerPrefix, "_", i, ", invar0_", i, ", ")
                ).toList(),
                "ivLo, ivHi);\n"
            ));
            return template.asToken();
        }

        private TemplateToken generateIRRules() {
            var template = Template.make(() -> scope(
                switch (containerKind) {
                    case ContainerKind.ARRAY ->
                        generateIRRulesArray();
                    case ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
                        generateIRRulesMemorySegmentAtIndex();
                    case ContainerKind.MEMORY_SEGMENT_LONG_ADR_SCALE ->
                        generateIRRulesMemorySegmentLongAdrScale();
                    case ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE ->
                        generateIRRulesMemorySegmentLongAdrStride();
                },
                // In same scnearios, we know that a aliasing runtime check will never fail.
                // That means if we have UseAutoVectorizationPredicate enabled, that predicate
                // will never fail, and we will not have to do multiversioning.
                switch(aliasing) {
                    case Aliasing.CONTAINER_DIFFERENT,
                         Aliasing.CONTAINER_SAME_ALIASING_NEVER,
                         Aliasing.CONTAINER_UNKNOWN_ALIASING_NEVER ->
                            """
                            // Aliasing check should never fail at runtime, so the predicate
                            // should never fail, and we do not have to use multiversioning.
                            // Failure could have a few causes:
                            // - issues with doing RCE / missing predicates
                            //   -> other loop-opts need to be fixed
                            // - predicate fails: recompile with multiversioning
                            //   -> logic in runtime check may be wrong
                            @IR(counts = {".*multiversion.*", "= 0"},
                                phase = CompilePhase.PRINT_IDEAL,
                                applyIf = {"UseAutoVectorizationPredicate", "true"},
                                applyIfPlatform = {"64-bit", "true"},
                                applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
                            """;
                   case Aliasing.CONTAINER_SAME_ALIASING_UNKNOWN,
                         Aliasing.CONTAINER_UNKNOWN_ALIASING_UNKNOWN ->
                            """
                            // Aliasing unknown, we may use the predicate or multiversioning.
                            """;
                }
            ));
            return template.asToken();
        }

        // Regular array-accesses are vectorized quite predictably, and we can create nice
        // IR rules - even for cases where we do not expect vectorization.
        private TemplateToken generateIRRulesArray() {
            var template = Template.make(() -> scope(
                let("T", containerElementType.letter()),
                switch (accessScenario) {
                    case COPY_LOAD_STORE ->
                        // Currently, we do not allow strided access or shuffle.
                        // Since the load and store are connected, we either vectorize both or none.
                        (accessIndexForm[0].ivScale() == accessIndexForm[1].ivScale() &&
                         Math.abs(accessIndexForm[0].ivScale()) == 1)
                        ?   """
                            // Good ivScales, vectorization expected.
                            @IR(counts = {IRNode.LOAD_VECTOR_#T, "> 0",
                                          IRNode.STORE_VECTOR,   "> 0"},
                                applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                                              "AlignVector", "false"},
                                applyIfPlatform = {"64-bit", "true"},
                                applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
                            """
                        :   """
                            // Bad ivScales, no vectorization expected.
                            @IR(counts = {IRNode.LOAD_VECTOR_#T, "= 0",
                                          IRNode.STORE_VECTOR,   "= 0"},
                                applyIfPlatform = {"64-bit", "true"},
                                applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
                            """;
                    case FILL_STORE_STORE ->
                        // Currently, we do not allow strided access.
                        // We vectorize any contiguous pattern. Possibly only one is vectorized.
                        (Math.abs(accessIndexForm[0].ivScale()) == 1 ||
                         Math.abs(accessIndexForm[1].ivScale()) == 1)
                        ?   """
                            // Good ivScales, vectorization expected.
                            @IR(counts = {IRNode.LOAD_VECTOR_#T, "= 0",
                                          IRNode.STORE_VECTOR,   "> 0"},
                                applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                                              "AlignVector", "false"},
                                applyIfPlatform = {"64-bit", "true"},
                                applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
                            """
                        :   """
                            // Bad ivScales, no vectorization expected.
                            @IR(counts = {IRNode.LOAD_VECTOR_#T, "= 0",
                                          IRNode.STORE_VECTOR,   "= 0"},
                                applyIfPlatform = {"64-bit", "true"},
                                applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
                            """;
                }
            ));
            return template.asToken();
        }

        private TemplateToken generateIRRulesMemorySegmentAtIndex() {
           var template = Template.make(() -> scope(
                """
                // Unfortunately, there are some issues that prevent RangeCheck elimination.
                // The cases are currently quite unpredictable, so we cannot create any IR
                // rules - sometimes there are vectors sometimes not.
                """
                // JDK-8359688: it seems we only vectorize with ivScale=1, and not ivScale=-1
                //              The issue seems to be RangeCheck elimination
            ));
            return template.asToken();
        }

        private TemplateToken generateIRRulesMemorySegmentLongAdrStride() {
           var template = Template.make(() -> scope(
                """
                // Unfortunately, there are some issues that prevent RangeCheck elimination.
                // The cases are currently quite unpredictable, so we cannot create any IR
                // rules - sometimes there are vectors sometimes not.
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateIRRulesMemorySegmentLongAdrScale() {
           var template = Template.make(() -> scope(
                """
                // Unfortunately, there are some issues that prevent RangeCheck elimination.
                // The cases are currently quite unpredictable, so we cannot create any IR
                // rules - sometimes there are vectors sometimes not.
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateTestMethod(String methodName, String[] invarRest) {
            var template = Template.make(() -> scope(
                let("methodName", methodName),
                let("containerElementType", containerElementType),
                let("ivStrideAbs", ivStrideAbs),
                let("ivType", isLongIvType ? "long" : "int"),
                // Method head / signature.
                "public static Object #methodName(",
                IntStream.range(0, numContainers).mapToObj(i ->
                    switch (containerKind) {
                        case ContainerKind.ARRAY ->
                            List.of("#containerElementType[] container_", i, ", #ivType invar0_", i, ", ");
                        case ContainerKind.MEMORY_SEGMENT_AT_INDEX,
                             ContainerKind.MEMORY_SEGMENT_LONG_ADR_SCALE,
                             ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE ->
                            List.of("MemorySegment container_", i, ", #ivType invar0_", i, ", ");
                    }
                ).toList(),
                "#ivType ivLo, #ivType ivHi) {\n",
                // Method loop body.
                (loopForward
                 ?  "for (#ivType i = ivLo; i < ivHi; i+=#ivStrideAbs) {\n"
                 :  "for (#ivType i = ivHi-#ivStrideAbs; i >= ivLo; i-=#ivStrideAbs) {\n"),
                // Loop iteration.
                switch (containerKind) {
                    case ContainerKind.ARRAY ->
                        generateTestLoopIterationArray(invarRest);
                    case ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
                        generateTestLoopIterationMemorySegmentAtIndex(invarRest);
                    case ContainerKind.MEMORY_SEGMENT_LONG_ADR_SCALE,
                         ContainerKind.MEMORY_SEGMENT_LONG_ADR_STRIDE ->
                        generateTestLoopIterationMemorySegmentLongAdr(invarRest);
                },
                """
                    }
                    return new Object[] {
                """,
                // Return a list of all containers that are involved in the test.
                // The caller can then compare the results of the test and reference method.
                IntStream.range(0, numContainers).mapToObj(i ->
                    "container_" + i
                ).collect(Collectors.joining(", ")), "\n",
                """
                    };
                }
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateTestLoopIterationArray(String[] invarRest) {
            var template = Template.make(() -> scope(
                let("type", containerElementType),
                switch (accessScenario) {
                    case COPY_LOAD_STORE ->
                        List.of("container_0[", accessIndexForm[0].index("invar0_0", invarRest), "] = ",
                                "container_1[", accessIndexForm[1].index("invar0_1", invarRest), "];\n");
                    case FILL_STORE_STORE ->
                        List.of("container_0[", accessIndexForm[0].index("invar0_0", invarRest), "] = (#type)0x0102030405060708L;\n",
                                "container_1[", accessIndexForm[1].index("invar0_1", invarRest), "] = (#type)0x1112131415161718L;\n");
                }
            ));
            return template.asToken();
        }

        private TemplateToken generateTestLoopIterationMemorySegmentAtIndex(String[] invarRest) {
            var template = Template.make(() -> scope(
                let("type0", accessType[0]),
                let("type1", accessType[1]),
                let("type0Layout", accessType[0].layout()),
                let("type1Layout", accessType[1].layout()),
                switch (accessScenario) {
                    case COPY_LOAD_STORE ->
                        // Conversion not implemented, index bound computation is too limited for this currently.
                        List.of("var v = ",
                                "container_0.getAtIndex(#type0Layout, ", accessIndexForm[0].indexLong("invar0_0", invarRest), ");\n",
                                "container_1.setAtIndex(#type1Layout, ", accessIndexForm[1].indexLong("invar0_1", invarRest), ", v);\n");
                    case FILL_STORE_STORE ->
                        List.of("container_0.setAtIndex(#type0Layout, ", accessIndexForm[0].indexLong("invar0_0", invarRest), ", (#type0)0x0102030405060708L);\n",
                                "container_1.setAtIndex(#type1Layout, ", accessIndexForm[1].indexLong("invar0_1", invarRest), ", (#type1)0x1112131415161718L);\n");
                }
            ));
            return template.asToken();
        }

        private TemplateToken generateTestLoopIterationMemorySegmentLongAdr(String[] invarRest) {
            var template = Template.make(() -> scope(
                let("type0", accessType[0]),
                let("type1", accessType[1]),
                let("type0Layout", accessType[0].layout()),
                let("type1Layout", accessType[1].layout()),
                switch (accessScenario) {
                    case COPY_LOAD_STORE ->
                        // We allow conversions here.
                        List.of("#type1 v = (#type1)",
                                "container_0.get(#type0Layout, ", accessIndexForm[0].indexLong("invar0_0", invarRest), ");\n",
                                "container_1.set(#type1Layout, ", accessIndexForm[1].indexLong("invar0_1", invarRest), ", v);\n");
                    case FILL_STORE_STORE ->
                        List.of("container_0.set(#type0Layout, ", accessIndexForm[0].indexLong("invar0_0", invarRest), ", (#type0)0x0102030405060708L);\n",
                                "container_1.set(#type1Layout, ", accessIndexForm[1].indexLong("invar0_1", invarRest), ", (#type1)0x1112131415161718L);\n");
                }
            ));
            return template.asToken();
        }
    }
}
