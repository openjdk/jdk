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
 * @test
 * @bug 8324751
 * @summary Test Speculative Aliasing checks in SuperWord
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver compiler.loopopts.superword.TestAliasingFuzzer
 */

package compiler.loopopts.superword;

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

    public record MyType(String name, int byteSize) {
        @Override
        public String toString() { return name(); }

        public String letter() { return name().substring(0, 1).toUpperCase(); }
    }
    public static final MyType myByte   = new MyType("byte", 1);
    public static final MyType myChar   = new MyType("char", 2);
    public static final MyType myShort  = new MyType("short", 2);
    public static final MyType myInt    = new MyType("int", 4);
    public static final MyType myLong   = new MyType("long", 8);
    public static final MyType myFloat  = new MyType("float", 4);
    public static final MyType myDouble = new MyType("double", 8);
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
        // Run the tests without any additional VM flags.
        // p.xyz.InnterTest.main(new String[] {});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {}});
        long t3 = System.nanoTime();

        System.out.println("Code Generation:  " + (t1-t0) * 1e-9f);
        System.out.println("Code Compilation: " + (t2-t1) * 1e-9f);
        System.out.println("Running Tests:    " + (t3-t2) * 1e-9f);
    }

    public static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> testTemplateTokens = new ArrayList<>();

        // Add some basic functionalities.
        testTemplateTokens.add(generateIntIndexForm());

        for (Aliasing aliasing : Aliasing.values()) {
            testTemplateTokens.addAll(allTypes.stream().map(t -> generateArray(t, aliasing)).toList());
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
            List.of("compiler.lib.generators.*",
                    "compiler.lib.verify.*",
                    "java.util.Random",
                    "jdk.test.lib.Utils"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    // The IntIndexForm is used to model the int-index. We can use it for arrays, but also by
    // restricting the MemorySegment index to a simple int-index.
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
    public static record IntIndexForm(int con, int ivScale, int invar0Scale, int[] invarRestScales) {
        public String generate() {
            return "new IntIndexForm(" + con() + ", " + ivScale() + ", " + invar0Scale() + ", new int[] {" +
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
    }

    public static TemplateToken generateIntIndexForm() {
        var template = Template.make(() -> body(
            """
            private static final Random RANDOM = Utils.getRandomInstance();

            public static record IntIndexForm(int con, int ivScale, int invar0Scale, int[] invarRestScales) {
                public IntIndexForm {
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
                        if (invar0Scale > 0) {
                            return (rhs + invar0Scale - 1) / invar0Scale; // round up division
                        } else {
                            throw new RuntimeException("not implemented 1");
                        }
                    } else {
                        throw new RuntimeException("not implemented 2");
                    }
                }

                public int ivHiForInvar0(Range range, int invar0) {
                    if (ivScale > 0) {
                        // index(iv) is largest for iv = ivHi, so we must satisfy:
                        //   range.hi > con + iv.hi * ivScale + invar0 * invar0Scale + invarRest
                        //            > con + iv.hi * ivScale + invar0 * invar0Scale + err
                        // It follows:
                        //   iv.hi * ivScale < range.hi - con - invar0 * invar0Scale - err
                        int rhs = range.hi() - con - invar0 * invar0Scale - err();
                        return (rhs - 1) / ivScale; // round down division
                    } else {
                        throw new RuntimeException("not implemented 2");
                    }
                }
            }
            """
        ));
        return template.asToken();
    }

    public static TemplateToken generateArray(MyType type, Aliasing aliasing) {
        final int size = Generators.G.safeRestrict(Generators.G.ints(), 10_000, 20_000).next();
        // TODO: random forms
        var form_a = new IntIndexForm(42, 1, 1, new int[] {1, 2, 3});
        var form_b = new IntIndexForm(42, 1, 1, new int[] {1, 2, 3});

        var templateSplitRanges = Template.make(() -> body(
            let("size", size),
            """
            int middle = RANDOM.nextInt(#size / 3, #size * 2 / 3);
            var r1 = new IntIndexForm.Range(0, middle);
            var r2 = new IntIndexForm.Range(middle, #size);
            if (RANDOM.nextBoolean()) {
                var tmp = r1;
                r1 = r2;
                r2 = tmp;
            }
            """
        ));

        var templateWholeRanges = Template.make(() -> body(
            let("size", size),
            """
            // Whole ranges
            var r1 = new IntIndexForm.Range(0, #size);
            var r2 = new IntIndexForm.Range(0, #size);
            """
        ));

        var templateRandomRanges = Template.make(() -> body(
            let("size", size),
            """
            // Random ranges
            int lo1 = RANDOM.nextInt(0, #size * 3 / 4);
            int lo2 = RANDOM.nextInt(0, #size * 3 / 4);
            var r1 = new IntIndexForm.Range(lo1, lo1 + #size / 4);
            var r2 = new IntIndexForm.Range(lo2, lo2 + #size / 4);
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

        var template = Template.make(() -> {
            String[] invarRest = new String[] {$("invar1"), $("invar2"), $("invar3")};
            return body(
                let("size", size),
                let("type", type),
                let("T", type.letter()),
                let("aliasing", aliasing),
                let("form_a", form_a.generate()),
                let("form_b", form_b.generate()),
                """
                // --- $test start ---
                // size=#size type=#type aliasing=#aliasing
                private static #type[] $ORIGINAL_1 = new #type[#size];
                private static #type[] $ORIGINAL_2 = new #type[#size];

                private static #type[] $TEST_1 = new #type[#size];
                private static #type[] $TEST_2 = new #type[#size];

                private static #type[] $REFERENCE_1 = new #type[#size];
                private static #type[] $REFERENCE_2 = new #type[#size];

                private static int $iterations = 0;

                private static IntIndexForm $form_a = #form_a;
                private static IntIndexForm $form_b = #form_b;

                """,
                Arrays.stream(invarRest).map(invar ->
                    List.of("private static int ", invar, " = 0;\n")
                ).toList(),
                """

                @Run(test = "$test")
                @Warmup(100)
                public static void $run() {
                    $iterations++;
                    System.arraycopy($ORIGINAL_1, 0, $TEST_1, 0, #size);
                    System.arraycopy($ORIGINAL_2, 0, $TEST_2, 0, #size);
                    System.arraycopy($ORIGINAL_1, 0, $REFERENCE_1, 0, #size);
                    System.arraycopy($ORIGINAL_2, 0, $REFERENCE_2, 0, #size);
                """,
                switch(aliasing) {
                    case Aliasing.CONTAINER_DIFFERENT ->
                        """
                        #type[] TEST_SECOND      = $TEST_2;
                        #type[] REFERENCE_SECOND = $REFERENCE_2;
                        """;
                    case Aliasing.CONTAINER_SAME_ALIASING_NEVER,
                         Aliasing.CONTAINER_SAME_ALIASING_UNKNOWN ->
                        """
                        #type[] TEST_SECOND      = $TEST_1;
                        #type[] REFERENCE_SECOND = $REFERENCE_1;
                        """;
                    case Aliasing.CONTAINER_UNKNOWN_ALIASING_NEVER,
                         Aliasing.CONTAINER_UNKNOWN_ALIASING_UNKNOWN ->
                        """
                        final boolean isSame = ($iterations % 2 == 0);
                        #type[] TEST_SECOND      = isSame ? $TEST_1      : $TEST_2;
                        #type[] REFERENCE_SECOND = isSame ? $REFERENCE_1 : $REFERENCE_2;
                        """;
                },
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
                },
                """
                    // Compute loop bounds and loop invariants.
                    int ivLo = RANDOM.nextInt(-1000, 1000);
                    int ivHi = ivLo + #size;
                    int invar0_A = $form_a.invar0ForIvLo(r1, ivLo);
                    ivHi = Math.min(ivHi, $form_a.ivHiForInvar0(r1, invar0_A));
                    int invar0_B = $form_b.invar0ForIvLo(r2, ivLo);
                    ivHi = Math.min(ivHi, $form_b.ivHiForInvar0(r2, invar0_B));

                    // Let's check that the range is large enough, so that the vectorized
                    // main loop can even be entered.
                    if (ivLo + 1000 > ivHi) { throw new RuntimeException("iv range too small: " + ivLo + " " + ivHi); }
                """,
                Arrays.stream(invarRest).map(invar ->
                    List.of(invar, " = RANDOM.nextInt(-1, 2);\n")
                ).toList(),
                """

                    // Run test and compare with interpreter results.
                    var result   = $test($TEST_1,           TEST_SECOND,      ivLo, ivHi, invar0_A, invar0_B);
                    var expected = $reference($REFERENCE_1, REFERENCE_SECOND, ivLo, ivHi, invar0_A, invar0_B);
                    Verify.checkEQ(result, expected);
                }

                // TODO: inverted loop, other load/store patterns, better IR rules, eg with flags!
                @Test
                @IR(counts = {IRNode.LOAD_VECTOR_#T, "> 0",
                              IRNode.STORE_VECTOR,   "> 0"},
                    applyIfPlatform = {"64-bit", "true"},
                    applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
                public static Object $test(#type[] a, #type[] b, int ivLo, int ivHi, int invar0_A, int invar0_B) {
                    for (int i = ivLo; i < ivHi; i++) {
                """,
                "a[", form_a.index("invar0_A", invarRest), "] = b[", form_b.index("invar0_B", invarRest), "];\n",
                """
                    }
                    return new Object[] {a, b};
                }

                @DontCompile
                public static Object $reference(#type[] a, #type[] b, int ivLo, int ivHi, int invar0_A, int invar0_B) {
                    for (int i = ivLo; i < ivHi; i++) {
                """,
                "a[", form_a.index("invar0_A", invarRest), "] = b[", form_b.index("invar0_B", invarRest), "];\n",
                """
                    }
                    return new Object[] {a, b};
                }
                // --- $test end   ---
                """
          );
        });
        return template.asToken();

    }
}
