/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run driver compiler.loopopts.superword.TestAliasingFuzzer vanilla
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
 * @run driver compiler.loopopts.superword.TestAliasingFuzzer random-flags
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
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;

import compiler.lib.template_framework.library.TestFrameworkClass;

/**
 * Simpler test cases can be found in {@link TestAliasing}.
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
    public static final MyType myByte   = new MyType("byte",   1, con1, con2,   "ValueLayout.JAVA_BYTE");
    public static final MyType myChar   = new MyType("char",   2, con1, con2,   "ValueLayout.JAVA_CHAR_UNALIGNED");
    public static final MyType myShort  = new MyType("short",  2, con1, con2,   "ValueLayout.JAVA_SHORT_UNALIGNED");
    public static final MyType myInt    = new MyType("int",    4, con1, con2,   "ValueLayout.JAVA_INT_UNALIGNED");
    public static final MyType myLong   = new MyType("long",   8, con1, con2,   "ValueLayout.JAVA_LONG_UNALIGNED");
    public static final MyType myFloat  = new MyType("float",  4, con1F, con2F, "ValueLayout.JAVA_FLOAT_UNALIGNED");
    public static final MyType myDouble = new MyType("double", 8, con1D, con2D, "ValueLayout.JAVA_DOUBLE_UNALIGNED");
    public static final List<MyType> allTypes
        = List.of(myByte, myChar, myShort, myInt, myLong, myFloat, myDouble);

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
        MEMORY_SEGMENT_AT_INDEX,
    }

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        long t0 = System.nanoTime();
        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

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
        // p.xyz.InnterTest.main(new String[] {});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {flags});
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
            "-XX:" + randomPlusMinus(5, 1) + "LoopMultiversioningOptimizeSlowLoop",
            // Either way is ok.
            "-XX:" + randomPlusMinus(1, 1) + "UseCompactObjectHeaders"
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
        for (Aliasing aliasing : Aliasing.values()) {
            for (AccessScenario accessScenario : AccessScenario.values()) {
                testTemplateTokens.addAll(allTypes.stream().map(type ->
                    TestGenerator.makeArray(type, aliasing, accessScenario).generate()
                ).toList());
            }
        }

        // MemorySegment with getAtIndex / setAtIndex
        // There are too many combinations, so we sample some random cases.
        for (int i = 0; i < 20; i++) {
            Aliasing aliasing = sample(Arrays.asList(Aliasing.values()));
            AccessScenario accessScenario = sample(Arrays.asList(AccessScenario.values()));
            MyType containerElementType = sample(allTypes);
            MyType accessType = sample(allTypes);
            testTemplateTokens.add(
                TestGenerator.makeMemorySegmentAtIndex(
                    containerElementType, accessType, aliasing, accessScenario
                ).generate()
            );
        }

        // TODO:
        // - array, MemorySegment, Unsafe
        // - various types - do I need them from the library?
        // - various pointer shapes - different summands, count up vs down, strided etc.
        // - aliasing
        // - conversions on native / unsafe / MemorySegment
        // Tricky: IR rules. May not vectorize in all cases.. how do we handle that?
        // General strategy: one method compiled, one interpreted -> compare!
        //
        // Array index:
        //   iv: init and limit
        //   forms: scale, offset, field/args, add/sub, mul for field/args
        //   fields/args: determine safe range.
        //   Form determines bounds on init / limit.
        //   Can we just set a form, and then generate field/arg, and then determine bounds?


        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "p.xyz", "InnerTest",
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
    // The idea is that invarRest is always close to zero, with some small range [-err .. err].
    // The invar variables for invarRest must be in the range [-1, 1, 1], so that we can
    // estimate the error range from the invarRestScales.
    //
    // At runtime, we will have to generate inputs for the iv.lo/iv.hi, as well as the invar0,
    // so that the index range lays in some predetermined range [range.lo, range.hi] and the
    // ivStride:
    //
    // for (int iv = iv.lo; iv < iv.hi; iv += ivStride) {
    //     assert: range.lo <= index(iv) <= range.hi
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
    //     range.hi >= con + iv.hi * ivScale + invar0 * invar0Scale + invarRest
    //              >= con + iv.hi * ivScale + invar0 * invar0Scale + err
    //   It follows:
    //     iv.hi * ivScale <= range.hi - con - invar0 * invar0Scale - err
    //
    public static record IndexForm(int con, int ivScale, int invar0Scale, int[] invarRestScales) {
        public static IndexForm random(int numInvarRest) {
            int con = RANDOM.nextInt(-100_000, 100_000);
            int ivScale = randomScale();
            int invar0Scale = randomScale();
            int[] invarRestScales = new int[numInvarRest];
            for (int i = 0; i < invarRestScales.length; i++) {
                invarRestScales[i] = RANDOM.nextInt(-1, 2);
            }
            return new IndexForm(con, ivScale, invar0Scale, invarRestScales);
        }

        public static int randomScale() {
            int scale = switch(RANDOM.nextInt(10)) {
                case 0 -> RANDOM.nextInt(2, 5); // strided 2..4
                default -> 1; // in most cases, we do not want it to be strided
            };
            return RANDOM.nextBoolean() ? scale : -scale;
        }

        public String generate() {
            return "new IndexForm(" + con() + ", " + ivScale() + ", " + invar0Scale() + ", new int[] {" +
                   Arrays.stream(invarRestScales)
                         .mapToObj(String::valueOf)
                         .collect(Collectors.joining(", ")) +
                   "})";
        }

        public TemplateToken index(String invar0, String[] invarRest) {
            var template = Template.make(() -> body(
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
            var template = Template.make(() -> body(
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

    public static TemplateToken generateIndexForm() {
        var template = Template.make(() -> body(
            """
            private static final Random RANDOM = Utils.getRandomInstance();

            public static record IndexForm(int con, int ivScale, int invar0Scale, int[] invarRestScales) {
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
                        //   range.hi > con + iv.lo * ivScale + invar0 * invar0Scale + invarRest
                        //            > con + iv.lo * ivScale + invar0 * invar0Scale + err
                        // It follows:
                        //   invar0 * invar0Scale <  range.hi - con - iv.lo * ivScale - err
                        //   invar0 * invar0Scale <= range.hi - con - iv.lo * ivScale - err - 1
                        int rhs = range.hi() - con - ivLo * ivScale - err() - 1;
                        int invar0 = (invar0Scale > 0)
                        ?
                            // invar0 * invar0Scale <=  range.hi - con - iv.lo * ivScale - err - 1
                            // invar0               <= (range.hi - con - iv.lo * ivScale - err - 1) / invar0Scale
                            Math.floorDiv(rhs, invar0Scale) // round down division
                        :
                            // invar0 * invar0Scale <=  range.hi - con - iv.lo * ivScale - err - 1
                            // invar0               >= (range.hi - con - iv.lo * ivScale - err - 1) / invar0Scale
                            Math.floorDiv(rhs + invar0Scale + 1, invar0Scale); // round up division
                        if (range.hi() <= con + ivLo * ivScale + invar0 * invar0Scale + err()) {
                            throw new RuntimeException("sanity check failed (2)");
                        }
                        return invar0;

                    }
                }

                public int ivHiForInvar0(Range range, int invar0) {
                    if (ivScale > 0) {
                        // index(iv) is largest for iv = ivHi, so we must satisfy:
                        //   range.hi > con + iv.hi * ivScale + invar0 * invar0Scale + invarRest
                        //            > con + iv.hi * ivScale + invar0 * invar0Scale + err
                        // It follows:
                        //   iv.hi * ivScale <   range.hi - con - invar0 * invar0Scale - err
                        //   iv.hi * ivScale <=  range.hi - con - invar0 * invar0Scale - err - 1
                        //   iv.hi           <= (range.hi - con - invar0 * invar0Scale - err - 1) / ivScale
                        int rhs = range.hi() - con - invar0 * invar0Scale - err() - 1;
                        int ivHi = Math.floorDiv(rhs, ivScale); // round down division
                        if (range.hi() <= con + ivHi * ivScale + invar0 * invar0Scale + err()) {
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
        MyType containerElementType, // null means native

        // Do we count up or down, iterate over the containers forward or backward?
        boolean loopForward,

        // For all index forms: number of invariants in the rest, i.e. the [err] term.
        int numInvarRest,

        // Each access has an index form and a type.
        IndexForm[] accessIndexForm,
        MyType[] accessType,

        // The scenario.
        Aliasing aliasing,
        AccessScenario accessScenario) {

        public static TestGenerator makeArray(MyType type, Aliasing aliasing, AccessScenario accessScenario) {
            // size must be large enough for:
            //   - scale = 4
            //   - range with size / 4
            // -> need at least size 16_000 to ensure we have 1000 iterations
            // We want there to be a little variation, so alignment is not always the same.
            final int numElements = Generators.G.safeRestrict(Generators.G.ints(), 18_000, 20_000).next();
            final int containerByteSize = numElements * type.byteSize();
            final boolean loopForward = RANDOM.nextBoolean();

            final int numInvarRest = RANDOM.nextInt(5);
            var form_a = IndexForm.random(numInvarRest);
            var form_b = IndexForm.random(numInvarRest);

            return new TestGenerator(
                2,
                containerByteSize,
                ContainerKind.ARRAY,
                type,
                loopForward,
                numInvarRest,
                new IndexForm[] {form_a, form_b},
                new MyType[]    {type,   type},
                aliasing,
                accessScenario);
        }

        public static int alignUp(int value, int align) {
            return Math.ceilDiv(value, align) * align;
        }

        public static TestGenerator makeMemorySegmentAtIndex(MyType containerElementType, MyType accessType, Aliasing aliasing, AccessScenario accessScenario) {
            // size must be large enough for:
            //   - scale = 4
            //   - range with size / 4
            // -> need at least size 16_000 to ensure we have 1000 iterations
            // We want there to be a little variation, so alignment is not always the same.
            final int numAccessElements = Generators.G.safeRestrict(Generators.G.ints(), 18_000, 20_000).next();
            final int align = Math.max(accessType.byteSize(), containerElementType.byteSize());
            // We need to align up, so the size is divisible exactly by all involved type sizes.
            final int containerByteSize = alignUp(numAccessElements * accessType.byteSize(), align);
            final boolean loopForward = RANDOM.nextBoolean();

            final int numInvarRest = RANDOM.nextInt(5);
            var form_a = IndexForm.random(numInvarRest);
            var form_b = IndexForm.random(numInvarRest);

            return new TestGenerator(
                2,
                containerByteSize,
                ContainerKind.MEMORY_SEGMENT_AT_INDEX,
                containerElementType,
                loopForward,
                numInvarRest,
                new IndexForm[] {form_a, form_b},
                new MyType[]    {accessType, accessType},
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
                return body(
                    """
                    // --- $test start ---
                    """,
                    generateTestFields(invarRest, containerNames, indexFormNames),
                    """
                    // Count the run invocations.
                    private static int $iterations = 0;

                    @Run(test = "$test")
                    @Warmup(100)
                    public static void $run() {
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
            var template = Template.make(() -> body(
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
            var template = Template.make(() -> body(
                let("size", containerByteSize / type.byteSize()),
                let("name", name),
                let("type", type),
                """
                private static MemorySegment original_#name  = MemorySegment.ofArray(new #type[#size]);
                private static MemorySegment test_#name      = MemorySegment.ofArray(new #type[#size]);
                private static MemorySegment reference_#name = MemorySegment.ofArray(new #type[#size]);
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateIndexField(String name, IndexForm form) {
            var template = Template.make(() -> body(
                let("name", name),
                let("form", form.generate()),
                """
                private static IndexForm #name = #form;
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateTestFields(String[] invarRest, String[] containerNames, String[] indexFormNames) {
            var template = Template.make(() -> body(
                """
                // invarRest fields:
                """,
                Arrays.stream(invarRest).map(invar ->
                    List.of("private static int ", invar, " = 0;\n")
                ).toList(),
                """
                // Containers fields:
                """,
                Arrays.stream(containerNames).map(name ->
                    switch (containerKind) {
                        case ContainerKind.ARRAY ->
                            generateArrayField(name, containerElementType);
                        case ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
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
            var template = Template.make(() -> body(
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
            var template = Template.make(() -> body(
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
            var template = Template.make(() -> body(
                """
                // Init containers from original data:
                """,
                Arrays.stream(containerNames).map(name ->
                    switch (containerKind) {
                        case ContainerKind.ARRAY ->
                            generateContainerInitArray(name);
                        case ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
                            generateContainerInitMemorySegment(name);
                    }
                ).toList()
             ));
            return template.asToken();
        }

        private TemplateToken generateContainerAliasingAssignment(int i, String name1, String name2, String iterations) {
            var template = Template.make(() -> body(
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
            var template = Template.make(() -> body(
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
                        // TODO: consider MemorySegment on same backing, but split into different sections.
                        //       could also be done in allocation!
                    }
                ).toList()
             ));
            return template.asToken();
        }

        private TemplateToken generateRanges() {
            // TODO: handle MemorySegment case, index vs byte addressing?
            int size = containerByteSize / accessType[0].byteSize();

            if (accessIndexForm.length != 2) { throw new RuntimeException("not yet implemented"); }

            var templateSplitRanges = Template.make(() -> body(
                let("size", size),
                """
                int middle = RANDOM.nextInt(#size / 3, #size * 2 / 3);
                var r0 = new IndexForm.Range(0, middle);
                var r1 = new IndexForm.Range(middle, #size);
                if (RANDOM.nextBoolean()) {
                    var tmp = r0;
                    r0 = r1;
                    r1 = tmp;
                }
                """
            ));

            var templateWholeRanges = Template.make(() -> body(
                let("size", size),
                """
                var r0 = new IndexForm.Range(0, #size);
                var r1 = new IndexForm.Range(0, #size);
                """
            ));

            var templateRandomRanges = Template.make(() -> body(
                let("size", size),
                """
                int lo0 = RANDOM.nextInt(0, #size * 3 / 4);
                int lo1 = RANDOM.nextInt(0, #size * 3 / 4);
                var r0 = new IndexForm.Range(lo0, lo0 + #size / 4);
                var r1 = new IndexForm.Range(lo1, lo1 + #size / 4);
                """
            ));

            var templateAnyRanges = Template.make(() -> body(
                switch(RANDOM.nextInt(3)) {
                    case 0 -> templateSplitRanges.asToken();
                    case 1 -> templateWholeRanges.asToken();
                    case 2 -> templateRandomRanges.asToken();
                    default -> throw new RuntimeException("impossible");
                }
            ));

            var template = Template.make(() -> body(
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
                        templateSplitRanges.asToken(); // TODO: could improve
                    case Aliasing.CONTAINER_UNKNOWN_ALIASING_UNKNOWN ->
                        templateAnyRanges.asToken();
                }
            ));
            return template.asToken();
        }

        private TemplateToken generateBoundsAndInvariants(String[] indexFormNames, String[] invarRest) {
            var template = Template.make(() -> body(
                let("containerByteSize", containerByteSize),
                """
                // Compute loop bounds and loop invariants.
                int ivLo = RANDOM.nextInt(-1000, 1000);
                int ivHi = ivLo + #containerByteSize;
                """,
                IntStream.range(0, indexFormNames.length).mapToObj(i ->
                    Template.make(() -> body(
                        let("i", i),
                        let("form", indexFormNames[i]),
                        """
                        int invar0_#i = #form.invar0ForIvLo(r#i, ivLo);
                        ivHi = Math.min(ivHi, #form.ivHiForInvar0(r#i, invar0_#i));
                        """
                    )).asToken()
                ).toList(),
                """
                // Let's check that the range is large enough, so that the vectorized
                // main loop can even be entered.
                if (ivLo + 1000 > ivHi) { throw new RuntimeException("iv range too small: " + ivLo + " " + ivHi); }
                """,
                Arrays.stream(invarRest).map(invar ->
                    List.of(invar, " = RANDOM.nextInt(-1, 2);\n")
                ).toList(),
                """
                // Verify the bounds we just created, just to be sure there is no unexpected aliasing!
                int i = ivLo;
                """,
                IntStream.range(0, indexFormNames.length).mapToObj(i ->
                    List.of("int lo_", i, " = ", accessIndexForm[i].index("invar0_" + i, invarRest), ";\n")
                ).toList(),
                """
                i = ivHi;
                """,
                IntStream.range(0, indexFormNames.length).mapToObj(i ->
                    List.of("int hi_", i, " =  ", accessIndexForm[i].index("invar0_" + i, invarRest), ";\n")
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
                                Template.make(() -> body(
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
            var template = Template.make(() -> body(
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
            var template = Template.make(() -> body(
                switch (containerKind) {
                    case ContainerKind.ARRAY ->
                        generateIRRulesArray();
                    case ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
                        generateIRRulesMemorySegment();
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

        private TemplateToken generateIRRulesArray() {
            var template = Template.make(() -> body(
                let("T", containerElementType.letter()),
                switch (accessScenario) {
                    case COPY_LOAD_STORE ->
                        // Currently, we do not allow strided access or shuffle.
                        // Since the load and store are connected, we either vectorize both or none.
                        //
                        // JDK-8359688: it seems we only vectorize with ivScale=1, and not ivScale=-1
                        //              The issue seems to be RangeCheck elimination
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

        private TemplateToken generateIRRulesMemorySegment() {
           var template = Template.make(() -> body(
                """
                // Unfortunately, there are some issues that prevent RangeCheck elimination.
                // The cases are currently quite unpredictable, so we cannot create any IR
                // rules.
                """
                // JDK-8359688: it seems we only vectorize with ivScale=1, and not ivScale=-1
                //              The issue seems to be RangeCheck elimination
            ));
            return template.asToken();
        }

        private TemplateToken generateTestMethod(String methodName, String[] invarRest) {
            var template = Template.make(() -> body(
                let("methodName", methodName),
                let("containerElementType", containerElementType),
                // Method head / signature.
                "public static Object #methodName(",
                IntStream.range(0, numContainers).mapToObj(i ->
                    switch (containerKind) {
                        case ContainerKind.ARRAY ->
                            List.of("#containerElementType[] container_", i, ", int invar0_", i, ", ");
                        case ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
                            List.of("MemorySegment container_", i, ", int invar0_", i, ", ");
                    }
                ).toList(),
                "int ivLo, int ivHi) {\n",
                // Method loop body.
                (loopForward
                 ?  "for (int i = ivLo; i < ivHi; i++) {\n"
                 :  "for (int i = ivHi-1; i >= ivLo; i--) {\n"),
                // Loop iteration.
                switch (containerKind) {
                    case ContainerKind.ARRAY ->
                        generateTestLoopIterationArray(invarRest);
                    case ContainerKind.MEMORY_SEGMENT_AT_INDEX ->
                        generateTestLoopIterationMemorySegmentAtIndex(invarRest);
                },
                """
                    }
                    return new Object[] {
                """,
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
            var template = Template.make(() -> body(
                let("type", containerElementType),
                switch (accessScenario) {
                    case COPY_LOAD_STORE ->
                        List.of("container_0[", accessIndexForm[0].index("invar0_0", invarRest), "] = ",
                                "container_1[", accessIndexForm[1].index("invar0_1", invarRest), "];\n");
                    case FILL_STORE_STORE ->
                        List.of("container_0[", accessIndexForm[0].index("invar0_0", invarRest), "] = (#type)0x0a;\n",
                                "container_1[", accessIndexForm[1].index("invar0_1", invarRest), "] = (#type)0x0b;\n");
                }
            ));
            return template.asToken();
        }

        private TemplateToken generateTestLoopIterationMemorySegmentAtIndex(String[] invarRest) {
            var template = Template.make(() -> body(
                let("type0", accessType[0]),
                let("type1", accessType[1]),
                let("type0Layout", accessType[0].layout()),
                let("type1Layout", accessType[1].layout()),
                switch (accessScenario) {
                    case COPY_LOAD_STORE ->
                        List.of("var v = ",
                                "container_0.getAtIndex(#type0Layout, ", accessIndexForm[0].indexLong("invar0_0", invarRest), ");\n",
                                "container_1.setAtIndex(#type0Layout, ", accessIndexForm[1].indexLong("invar0_1", invarRest), ", v);\n");
                    case FILL_STORE_STORE ->
                        // TODO: improve input for misaligned cases!
                        List.of("container_0.setAtIndex(#type0Layout, ", accessIndexForm[0].indexLong("invar0_0", invarRest), ", (#type0)0x0a);\n",
                                "container_1.setAtIndex(#type0Layout, ", accessIndexForm[1].indexLong("invar0_1", invarRest), ", (#type1)0x0b);\n");
                }
            ));
            return template.asToken();
        }
    }
}
