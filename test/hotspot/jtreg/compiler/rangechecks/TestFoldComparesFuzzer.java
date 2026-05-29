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
 * @bug 8346420
 * @summary Fuzz patterns for IfNode::fold_compares_helper
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../lib/ir_framework/TestFramework.java
 * @compile ../lib/generators/Generators.java
 * @compile ../lib/verify/Verify.java
 * @run driver ${test.main.class}
 */

package compiler.rangechecks;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

import jdk.test.lib.Utils;

import compiler.lib.compile_framework.*;
import compiler.lib.generators.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;

import compiler.lib.template_framework.library.TestFrameworkClass;

/**
 * For more basic examples, see TestFoldCompares.java
 *
 * I'm only covering some basic cases to test the fundamental
 * logic inside IfNode::fold_compares_helper.
 * - TestMethodGeneratorConstIR does extensive result and IR verification
 *   for the cases a-d) in IfNode::fold_compares_helper, but only with
 *   constant lo and hi.
 * - Other test generators currently don't have IR rules, but check
 *   correctness in various relevant scenarios I came across during
 *   the bugfix of JDK-8346420.
 * - I'm also mixing signed and unsigned comparisons, just to ensure
 *   the less often used (and tested) unsigned comparisons don't slip
 *   through the cracks.
 *
 * In the future, we could add more cases:
 * - Extend to long - though the optimization does not yet cover longs anyway.
 * - More IR rules: difficult to make stable. Not all permutations are covered
 *   by the optimizations, edge-cases could make IR rules brittle.
 */
public class TestFoldComparesFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final RestrictableGenerator<Integer> INT_GEN = Generators.G.ints();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        long t0 = System.nanoTime();
        // Add a java source file.
        comp.addJavaSourceCode("compiler.rangecheck.templated.Generated", generate(comp));

        long t1 = System.nanoTime();
        // Compile the source file.
        comp.compile();

        long t2 = System.nanoTime();

        // Run the tests without any additional VM flags.
        comp.invoke("compiler.rangecheck.templated.Generated", "main", new Object[] {new String[] {}});
        long t3 = System.nanoTime();

        System.out.println("Code Generation:  " + (t1-t0) * 1e-9f);
        System.out.println("Code Compilation: " + (t2-t1) * 1e-9f);
        System.out.println("Running Tests:    " + (t3-t2) * 1e-9f);
    }

    public static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> testTemplateTokens = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            testTemplateTokens.add(generateTest(/* no warmup, like -Xcomp */ 0));
        }
        for (int i = 0; i < 5; i++) {
            testTemplateTokens.add(generateTest(/* with warmup, slower */ 10_000));
        }

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.rangecheck.templated", "Generated",
            // List of imports.
            Set.of("compiler.lib.generators.*",
                   "compiler.lib.verify.*",
                   "java.util.Random",
                   "jdk.test.lib.Utils"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    enum Comparator {
        // TODO: enable again after JDK-8385157
        // ULT(" <  0", false),
        // ULE(" <= 0", false),
        // UGT(" >  0", false),
        // UGE(" >= 0", false),
        // UEQ(" == 0", false),
        // UNE(" != 0", false),
        LT(" <  ", true),
        LE(" <= ", true),
        GT(" >  ", true),
        GE(" >= ", true),
        EQ(" == ", true),
        NE(" != ", true);

        private final String token;
        private final boolean signed;

        Comparator(String token, boolean signed) {
            this.token = token;
            this.signed = signed;
        }

        public String getToken() {
            return token;
        }

        public boolean isSigned() {
            return signed;
        }

        public Comparator negate() {
            return switch(this) {
                // TODO: enable again after JDK-8385157
                // case ULT -> UGE;
                // case ULE -> UGT;
                // case UGT -> ULE;
                // case UGE -> ULT;
                // case UEQ -> UNE;
                // case UNE -> UEQ;
                case LT -> GE;
                case LE -> GT;
                case GT -> LE;
                case GE -> LT;
                case EQ -> NE;
                case NE -> EQ;
            };
        }

        public Comparator flip() {
            return switch(this) {
                // TODO: enable again after JDK-8385157
                // case ULT -> UGT;
                // case ULE -> UGE;
                // case UGT -> ULT;
                // case UGE -> ULE;
                // case UEQ -> UEQ;
                // case UNE -> UNE;
                case LT -> GT;
                case LE -> GE;
                case GT -> LT;
                case GE -> LE;
                case EQ -> EQ;
                case NE -> NE;
            };
        }

        static Comparator random() {
            return values()[RANDOM.nextInt(values().length)];
        }

        static Comparator randomGreater() {
            return RANDOM.nextBoolean() ? GE : GT;
        }

        static Comparator randomLess() {
            return RANDOM.nextBoolean() ? LE : LT;
        }
    }

    record Comparison(String lhs, Comparator cmp, String rhs, boolean negated) {
        public Comparison(String lhs, Comparator cmp, String rhs) {
            this(lhs, cmp, rhs, false);
        }

        public String toString() {
            return cmp.isSigned()
                ? ((negated ? "!" : "") + "(" + lhs + " "+ cmp.getToken() + " " + rhs + ")")
                : ((negated ? "!" : "") + "(Integer.compareUnsigned(" + lhs + ", " + rhs + ")" + cmp.getToken() + ")");
        }

        // Keep the same semantics of the test, but change its form.
        Comparison permuteRandom() {
            return flipRandom().complementRandom();
        }

        Comparison flipRandom() {
            return RANDOM.nextBoolean() ? this : new Comparison(rhs, cmp.flip(), lhs);
        }

        Comparison complementRandom() {
            return RANDOM.nextBoolean() ? this : new Comparison(lhs, cmp.negate(), rhs, true);
        }

        Comparison negateCmp() {
            return new Comparison(lhs, cmp.negate(), rhs, negated);
        }
    }

    interface TestMethodGenerator {
        Template.OneArg<String> getTestTemplate();

        default Template.ZeroArgs getIRTemplate(boolean withWarmup) {
            return Template.make(() -> scope("// No IR rule.\n"));
        }

        default Template.ZeroArgs getInputTemplate() {
            return Template.make(() -> scope(
                """
                RestrictableGenerator<Integer> gen = Generators.G.ints();
                int n = gen.next();
                int a = gen.next();
                int b = gen.next();
                """
            ));
        };
    }

    // Some basic ranges with constant bounds.
    // This should test some basic correctness, and also covers the case
    // of bug JDK-8135069.
    static class TestMethodGeneratorConst implements TestMethodGenerator {
        private final int con1 = INT_GEN.next();
        private final int con2 = INT_GEN.next();

        private final Comparison c1 = new Comparison("n", Comparator.random(), "con1").permuteRandom();
        private final Comparison c2 = new Comparison("n", Comparator.random(), "con2").permuteRandom();

        private final Template.OneArg<String> testTemplate = Template.make("methodName", (String methodName) -> scope(
            let("con1", con1),
            let("con2", con2),
            let("c1", c1),
            let("c2", c2),
            """
            static boolean #methodName(int n, int a, int b) {
                int con1 = #con1;
                int con2 = #con2;
                if (#c1 || #c2) {
                    return true;
                }
                return false;
            }
            """
        ));

        public Template.OneArg<String> getTestTemplate() { return testTemplate; }
    }

    // Cases where a and b are ranges that touch min_int/max_int.
    // Note: if con1=0 and con2=1 then this is like the cases:
    // - test_Case3a_LTLE_overflow
    // - test_Case3b_LTLE_overflow
    // - test_Case4a_LELE_assert
    //
    // Hence, I think this test gives us quite good coverage for the kinds of bugs
    // such as JDK-8346420.
    static class TestMethodGeneratorWithIf implements TestMethodGenerator {
        private final int con1 = INT_GEN.next();
        private final int con2 = INT_GEN.next();
        private final String m1 = RANDOM.nextBoolean() ? "Integer.MIN_VALUE" : "Integer.MAX_VALUE";
        private final String m2 = RANDOM.nextBoolean() ? "Integer.MIN_VALUE" : "Integer.MAX_VALUE";

        private final Comparison c1 = new Comparison("n", Comparator.random(), "a").permuteRandom();
        private final Comparison c2 = new Comparison("n", Comparator.random(), "b").permuteRandom();

        private final Template.OneArg<String> testTemplate = Template.make("methodName", (String methodName) -> scope(
            let("con1", con1),
            let("con2", con2),
            let("m1", m1),
            let("m2", m2),
            let("c1", c1),
            let("c2", c2),
            """
            static boolean #methodName(int n, int a, int b) {
                if (a < b) {
                    a = #con1;
                    b = #con2;
                } else {
                    a = #m1;
                    b = #m2;
                }
                if (#c1 || #c2) {
                    return true;
                }
                return false;
            }
            """
        ));

        public Template.OneArg<String> getTestTemplate() { return testTemplate; }
    }

    // Just for good practice: add some case where the ranges are more free.
    static class TestMethodGeneratorRanges implements TestMethodGenerator {
        private final int n_hi = INT_GEN.next();
        private final int n_lo = INT_GEN.next();
        private final int a_hi = INT_GEN.next();
        private final int a_lo = INT_GEN.next();
        private final int b_hi = INT_GEN.next();
        private final int b_lo = INT_GEN.next();

        private final Comparison c1 = new Comparison("n", Comparator.random(), "a").permuteRandom();
        private final Comparison c2 = new Comparison("n", Comparator.random(), "b").permuteRandom();

        private final Template.OneArg<String> template = Template.make("methodName", (String methodName) -> scope(
            let("n_hi", n_hi),
            let("n_lo", n_lo),
            let("a_hi", a_hi),
            let("a_lo", a_lo),
            let("b_hi", b_hi),
            let("b_lo", b_lo),
            let("c1", c1),
            let("c2", c2),
            """
            static boolean #methodName(int n, int a, int b) {
                n = Math.min(#n_hi, Math.max(#n_lo, n));
                a = Math.min(#a_hi, Math.max(#a_lo, a));
                b = Math.min(#b_hi, Math.max(#b_lo, b));
                if (#c1 || #c2) {
                    return true;
                }
                return false;
            }
            """
        ));

        public Template.OneArg<String> getTestTemplate() {
            return template;
        }
    }

    // Generate some more constrained cases, but with IR rules
    static class TestMethodGeneratorConstIR implements TestMethodGenerator {
        private final int lo;
        private final int hi;
        { // instance initializer
            // We want to cover all cases for lo and hi combinations. But the
            // critical cases happen around int_min and int_max, and when
            // lo and hi are close to each other.
            switch (RANDOM.nextInt(3)) {
                case 0 -> {
                    // Full freedom, will eventually cover all cases
                    lo = INT_GEN.next();
                    hi = INT_GEN.next();
                }
                case 1 -> {
                    // Pick cases around overflow and underflow
                    lo = Integer.MAX_VALUE - 5 + RANDOM.nextInt(10);
                    hi = Integer.MAX_VALUE - 5 + RANDOM.nextInt(10);
                }
                default -> {
                    // Pick cases where lo and hi are close to each other
                    lo = INT_GEN.next();
                    hi = lo - 5 + RANDOM.nextInt(10);
                }
            }
        }

        // Since we are using constants for lo and hi, the checks should get canonicalized,
        // so that n is always in the lhs. We only create cases that are covered by the
        // 4 cases of "2 CmpI -> 1 CmpU" optimization in IfNode::fold_compares_helper.
        private final Comparison c_lo = new Comparison("n", Comparator.randomGreater(), "lo");
        private final Comparison c_hi = new Comparison("n", Comparator.randomLess(), "hi");
        private final boolean swap = RANDOM.nextBoolean();
        private final Comparison c1Permuted = (swap ? c_lo : c_hi).permuteRandom();
        private final Comparison c2Permuted = (swap ? c_hi : c_lo).permuteRandom();
        // n >  lo && n <  hi -> check for inside range
        // n <= lo || n >= hi -> chedk for outside range
        private final boolean withAnd = RANDOM.nextBoolean();
        private final String operator = withAnd ? "&&" : "||";
        private final Comparison c1 = withAnd ? c1Permuted : c1Permuted.negateCmp();
        private final Comparison c2 = withAnd ? c2Permuted : c2Permuted.negateCmp();

        private final Template.OneArg<String> testTemplate = Template.make("methodName", (String methodName) -> scope(
            let("lo", lo),
            let("hi", hi),
            let("c1", c1),
            let("c2", c2),
            let("op", operator),
            """
            static boolean #methodName(int n, int a, int b) {
                int lo = #lo;
                int hi = #hi;
                if (#c1 #op #c2) {
                    return true;
                }
                return false;
            }
            """
        ));

        public Template.OneArg<String> getTestTemplate() { return testTemplate; }

        public Template.ZeroArgs getIRTemplate(boolean withWarmup) {
            return Template.make(() -> {
                String cmpIParse, cmpUParse, cmpIFinal, cmpUFinal;
                String comment;

                // If both branches are compiled (in -Xcomp mode, i.e. no warmup), then
                // we can know very precisely what happens in each case.
                if (c_lo.cmp() == Comparator.GT && c_hi.cmp() == Comparator.LT) {
                    // a)   (n >  lo && n <  hi)
                    if (lo == Integer.MAX_VALUE || hi == Integer.MIN_VALUE) {
                        cmpIParse = "< 2"; cmpUParse = "= 0"; cmpIFinal = "< 2"; cmpUFinal = "= 0";
                        comment = "a) one or both checks fold at parse time";
                    } else if (lo < hi && lo+2 == hi) {
                        // Not yet folded at parsing, because lo != hi
                        // BoolNode::Ideal: x <u 1 or x <=u 0 -> x==0 (signed)
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 1"; cmpUFinal = "= 0";
                        comment = "a) replace with CmpU (single element) -> CmpI eq";
                    } else if (lo < hi && lo+1 == hi) {
                        // Not yet folded at parsing, because lo != hi
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "a) impossible condition (exact) -> fold away";
                    } else if (lo < hi) {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 1";
                        comment = "a) replace with CmpU (non-empty)";
                    } else if (lo == hi) {
                        // same CmpI at parse time
                        cmpIParse = "= 1"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "a) impossible condition -> fold away";
                    } else {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "a) impossible condition -> fold away";
                    }
                } else if (c_lo.cmp() == Comparator.GT && c_hi.cmp() == Comparator.LE) {
                    // b)   (n >  lo && n <= hi)
                    if (lo == Integer.MAX_VALUE || hi == Integer.MAX_VALUE) {
                        cmpIParse = "< 2"; cmpUParse = "= 0"; cmpIFinal = "< 2"; cmpUFinal = "= 0";
                        comment = "b) one or both checks fold at parse time";
                    } else if (lo < hi && lo+1 == hi) {
                        // BoolNode::Ideal: x <u 1 or x <=u 0 -> x==0 (signed)
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 1"; cmpUFinal = "= 0";
                        comment = "b) replace with CmpU (single element) -> CmpI eq";
                    } else if (lo < hi && lo+1 < hi) {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 1";
                        comment = "b) replace with CmpU (non-empty)";
                    } else if (lo == hi) {
                        cmpIParse = "= 1"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "b) impossible condition (exact) -> fold away";
                    } else {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "b) impossible condition -> fold away";
                    }
                } else if (c_lo.cmp() == Comparator.GE && c_hi.cmp() == Comparator.LT) {
                    // c)   (n >= lo && n <  hi)
                    if (lo == Integer.MIN_VALUE || hi == Integer.MIN_VALUE) {
                        cmpIParse = "< 2"; cmpUParse = "= 0"; cmpIFinal = "< 2"; cmpUFinal = "= 0";
                        comment = "c) one or both checks fold at parse time";
                    } else if (lo < hi && lo+1 == hi) {
                        // BoolNode::Ideal: x <u 1 or x <=u 0 -> x==0 (signed)
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 1"; cmpUFinal = "= 0";
                        comment = "c) replace with CmpU (single element) -> CmpI eq";
                    } else if (lo < hi && lo+1 < hi) {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 1";
                        comment = "c) replace with CmpU (non-empty)";
                    } else if (lo == hi) {
                        // RegionNode::optimize_trichotomy: can fold (n >= x && n < x) -> never
                        cmpIParse = "< 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "c) impossible condition (exact) -> fold away";
                    } else {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "c) impossible condition -> fold away";
                    }
                } else if (c_lo.cmp() == Comparator.GE && c_hi.cmp() == Comparator.LE) {
                    // d)   (n >= lo && n <= hi)
                    if (lo == Integer.MIN_VALUE || hi == Integer.MAX_VALUE) {
                        cmpIParse = "< 2"; cmpUParse = "= 0"; cmpIFinal = "< 2"; cmpUFinal = "= 0";
                        comment = "d) one or both checks fold at parse time";
                    } else if (lo == hi) {
                        // same CmpI at parse time
                        // BoolNode::Ideal: x <u 1 or x <=u 0 -> x==0 (signed)
                        cmpIParse = "= 1"; cmpUParse = "= 0"; cmpIFinal = "= 1"; cmpUFinal = "= 0";
                        comment = "d) replace with CmpU (single element) -> CmpI eq";
                    } else if (lo < hi) {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 1";
                        comment = "d) replace with CmpU (non-empty)";
                    } else {
                        cmpIParse = "= 2"; cmpUParse = "= 0"; cmpIFinal = "= 0"; cmpUFinal = "= 0";
                        comment = "d) impossible condition -> fold away";
                    }
                } else {
                    throw new RuntimeException("should not be generated: " + c_lo + " and " + c_hi);
                }

                // All the precise counting above assumes that both ifs get compiled, and hence
                // both CmpI are generated. Further, it assumes that both of the "or" branches
                // (fail1 and fail2) end up "in the same place": either at the same region, or
                // both in an uncommon trap. With profiling, the following cases are possible:
                // - The first if is constant folded to fail1, and we have no CmpI nor CmpU
                //   in the graph.
                // - The first if always leads to fail1, and away from the second if, and so we
                //   only have a single CmpI in the graph after parsing.
                // - The first if always leads towards the second if, and away from fail1. And
                //   the second if always points towards fail2 and away from succ. We get an
                //   uncommon trap for fail1 and succ, and only the fail2 path is compiled.
                //   Hence, we have two CmpI, but fail1 and fail2 do not end up "in the same place".
                // This makes our IR rule quite weak, sadly. We could make the IR rules stronger,
                // but we would need to control warmup, and generate corresponding inputs that
                // ensure the right paths are compiled or not compiled.
                if (withWarmup) {
                    cmpIParse = "<= 2"; cmpUParse = "= 0"; cmpIFinal = "<= 2"; cmpUFinal = "< 2";
                    comment = "with warmup: unstable-if makes precise counting hard.";
                }

                return scope(
                    let("IP", cmpIParse),
                    let("UP", cmpUParse),
                    let("IF", cmpIFinal),
                    let("UF", cmpUFinal),
                    let("comment", comment),
                    """
                    // #comment
                    @IR(counts = {IRNode.CMP_I, "#IP", IRNode.CMP_U, "#UP"}, phase = CompilePhase.AFTER_PARSING)
                    @IR(counts = {IRNode.CMP_I, "#IF", IRNode.CMP_U, "#UF"})
                    """
                );
            });
        }

        @Override
        public Template.ZeroArgs getInputTemplate() {
            return Template.make(() -> scope(
                let("lo", lo),
                let("hi", hi),
                """
                Random r = Utils.getRandomInstance();
                RestrictableGenerator<Integer> gen = Generators.G.ints();
                int a = gen.next();
                int b = gen.next();
                """,
                switch (RANDOM.nextInt(9)) {
                    // Random values
                    case 0 -> "int n = gen.next();\n";
                    // Fuzz around specific values
                    case 1 -> "int n = r.nextInt(10) - 5 + #lo;\n";
                    case 2 -> "int n = r.nextInt(10) - 5 + #hi;\n";
                    case 3 -> "int n = r.nextInt(10) - 5 + (r.nextBoolean() ? #lo : #hi);\n";
                    case 4 -> "int n = r.nextInt(10) - 5 + Integer.MAX_VALUE;\n";
                    // Only very low or very high values, or in the middle
                    case 5 -> "int n = r.nextInt(10) - 10 + Integer.MAX_VALUE;\n";
                    case 6 -> "int n = r.nextInt(10) + Integer.MIN_VALUE;\n";
                    case 7 -> "int n = r.nextInt(10) - 5 + #lo/2 + #hi/2;\n";
                    // Always the same constant
                    default -> "int n = " + INT_GEN.next() + ";\n";
                }
            ));
        };
    }

    // switch cases can also be implemented with range checks using
    // constants, and then we can optimize 2 CmpI with a single CmpU,
    // at least in some cases.
    static class TestMethodGeneratorSwitch implements TestMethodGenerator {
        Set<Short> cases = new HashSet<>();
        { // instance initializer
            int n = RANDOM.nextInt(1, 20);
            for (int i = 0; i < n; i++) {
                cases.add((short)(int)INT_GEN.next());
            }
        }

        private final Template.OneArg<String> testTemplate = Template.make("methodName", (String methodName) -> scope(
            """
            static boolean #methodName(int n, int a, int b) {
                switch((short)n) {
            """,
            cases.stream().map(i -> scope(
                let("i", i),
                """
                case (short)#i:
                """
            )).toList(),
            """
                    return true;
                default:
                    return false;
                }
            }
            """
        ));

        public Template.OneArg<String> getTestTemplate() { return testTemplate; }
    }

    // If arr.length is in the second check, the null-check for arr
    // is located between the two checks.
    // I'm not adding any IR rules here, just checking for correctness.
    static class TestMethodGeneratorArrLength implements TestMethodGenerator {
        private final int n_hi = INT_GEN.next();
        private final int n_lo = INT_GEN.next();
        private final int a_hi = INT_GEN.next();
        private final int a_lo = INT_GEN.next();
        private final int size = INT_GEN.restricted(0, 100_000).next();

        // Get checks like: n < a || n >= arr.length
        private final Comparison c_lo = new Comparison("n", Comparator.random(), "a").permuteRandom();
        private final Comparison c_hi = new Comparison("n", Comparator.random(), "arr.length").permuteRandom();
        private final boolean swap = RANDOM.nextBoolean();
        private final Comparison c1Permuted = (swap ? c_lo : c_hi).permuteRandom();
        private final Comparison c2Permuted = (swap ? c_hi : c_lo).permuteRandom();
        // n >  lo && n <  hi -> check for inside range
        // n <= lo || n >= hi -> chedk for outside range
        private final boolean withAnd = RANDOM.nextBoolean();
        private final String operator = withAnd ? "&&" : "||";
        private final Comparison c1 = withAnd ? c1Permuted : c1Permuted.negateCmp();
        private final Comparison c2 = withAnd ? c2Permuted : c2Permuted.negateCmp();

        private final Template.OneArg<String> testTemplate = Template.make("methodName", (String methodName) -> scope(
            let("n_hi", n_hi),
            let("n_lo", n_lo),
            let("a_hi", a_hi),
            let("a_lo", a_lo),
            let("size", size),
            let("c1", c1),
            let("c2", c2),
            let("op", operator),
            """
            static boolean #methodName(int n, int a, int b) {
                int[] arr = $arr;
                n = Math.min(#n_hi, Math.max(#n_lo, n));
                a = Math.min(#a_hi, Math.max(#a_lo, a));
                if (#c1 #op #c2) {
                    return true;
                }
                return false;
            }
            static int[] $arr = new int[#size];
            """
        ));

        public Template.OneArg<String> getTestTemplate() { return testTemplate; }
    }

    public static TemplateToken generateTest(int warmup) {
        TestMethodGenerator tg = switch(RANDOM.nextInt(6)) {
            case 0 -> new TestMethodGeneratorConst();
            case 1 -> new TestMethodGeneratorWithIf();
            case 2 -> new TestMethodGeneratorRanges();
            case 3 -> new TestMethodGeneratorConstIR();
            case 4 -> new TestMethodGeneratorSwitch();
            case 5 -> new TestMethodGeneratorArrLength();
            default -> throw new RuntimeException("not expected");
        };
        Template.ZeroArgs testInputTemplate = tg.getInputTemplate();
        Template.OneArg<String> testMethodTemplate = tg.getTestTemplate();
        Template.ZeroArgs testIRTemplate = tg.getIRTemplate(warmup >= 10_000);

        var testTemplate = Template.make(() -> scope(
            let("warmup", warmup / 100),
            """
            // --- $test start ---
            @Run(test = "$test")
            @Warmup(#warmup)
            public static void $run() {
                for (int i = 0; i < 100; i++) {
                    // Generate random values for n, a, b.
                    """,
                    testInputTemplate.asToken(),
                    """

                    // Run test and compare with interpreter results.
                    var result   =      $test(n, a, b);
                    var expected = $reference(n, a, b);
                    if (result != expected) {
                        throw new RuntimeException("wrong result: " + result + " vs " + expected
                                                   + "\\nn: " + n
                                                   + "\\na: " + a
                                                   + "\\nb: " + b);
                    }
                }
            }

            @Test
            """,
            testIRTemplate.asToken(),
            testMethodTemplate.asToken($("test")),
            """

            @DontCompile
            """,
            testMethodTemplate.asToken($("reference")),
            """
            // --- $test end   ---
            """
        ));
        return testTemplate.asToken();
    }
}
