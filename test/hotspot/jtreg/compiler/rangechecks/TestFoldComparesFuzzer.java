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
 * TODO: description
 */
public class TestFoldComparesFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

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

        // TODO: adjust number
        for (int i = 0; i < 100; i++) {
            testTemplateTokens.add(generateTest());
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
        LT(" < "),
        LE(" <= "),
        GT(" > "),
        GE(" >= "),
        EQ(" == "),
        NE(" != ");

        private final String token;

        Comparator(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public Comparator negate() {
            return switch(this) {
                case LT -> GE;
                case LE -> GT;
                case GT -> LE;
                case GE -> GT;
                case EQ -> NE;
                case NE -> EQ;
            };
        }
    }

    public static TemplateToken generateTest() {
        RestrictableGenerator<Integer> gen = Generators.G.ints();
        final int N_HI = gen.next();
        final int N_LO = gen.next();
        final int A_HI = gen.next();
        final int A_LO = gen.next();
        final int B_HI = gen.next();
        final int B_LO = gen.next();

        // TODO: brainstorming
        //
        // - All permutations of tests. All comparisons.
        // - Cases where we are always in/out / mixed.
        // - Cases with array length.
        // - Cases with switch
        // - limits: constant, range, array.length
        // - type: int and long
        var testMethodTemplate = Template.make("methodName", (String methodName) -> scope(
            let("N_HI", N_HI),
            let("N_LO", N_LO),
            let("A_HI", A_HI),
            let("A_LO", A_LO),
            let("B_HI", B_HI),
            let("B_LO", B_LO),
            """
            static boolean #methodName(int n, int a, int b) {
                //n = Math.min(#N_HI, Math.max(#N_LO, n));
                //a = Math.min(#A_HI, Math.max(#A_LO, a));
                //b = Math.min(#B_HI, Math.max(#B_LO, b));

                if (a > b) {
                    a = #A_LO; //0;
                    b = #B_LO; //1;
                } else {
                    a = #A_HI; //Integer.MIN_VALUE;
                    b = #B_HI; //Integer.MAX_VALUE;
                }

                if (n < a || n > b) {
                    return true;
                }
                return false;
            }
            """
        ));

        // TODO: Xcomp or not?
        var testTemplate = Template.make(() -> scope(
            """
            // --- $test start ---
            @Run(test = "$test")
            @Warmup(0) // like XComp
            public static void $run() {
                for (int i = 0; i < 100; i++) {
                    // Generate random values.
                    RestrictableGenerator<Integer> gen = Generators.G.ints();
                    int n = gen.next();
                    int a = gen.next();
                    int b = gen.next();

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
