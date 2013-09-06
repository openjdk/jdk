/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test IsMethodCompilableTest
 * @bug 8007270 8006683 8007288 8022832
 * @library /testlibrary /testlibrary/whitebox
 * @build IsMethodCompilableTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm/timeout=2400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,TestCase$Helper::* IsMethodCompilableTest
 * @summary testing of WB::isMethodCompilable()
 * @author igor.ignatyev@oracle.com
 */
public class IsMethodCompilableTest extends CompilerWhiteBoxTest {
    /**
     * Value of {@code -XX:PerMethodRecompilationCutoff}
     */
    protected static final long PER_METHOD_RECOMPILATION_CUTOFF;

    static {
        long tmp = Long.parseLong(
                getVMOption("PerMethodRecompilationCutoff", "400"));
        if (tmp == -1) {
            PER_METHOD_RECOMPILATION_CUTOFF = -1 /* Inf */;
        } else {
            PER_METHOD_RECOMPILATION_CUTOFF = 1 + (0xFFFFFFFFL & tmp);
        }
    }

    public static void main(String[] args) throws Exception {
        for (TestCase test : TestCase.values()) {
            new IsMethodCompilableTest(test).runTest();
        }
    }

    public IsMethodCompilableTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    /**
     * Tests {@code WB::isMethodCompilable()} by recompilation of tested method
     * 'PerMethodRecompilationCutoff' times and checks compilation status. Also
     * checks that WB::clearMethodState() clears no-compilable flags.
     *
     * @throws Exception if one of the checks fails.
     */
    @Override
    protected void test() throws Exception {
        if (!isCompilable()) {
            throw new RuntimeException(method + " must be compilable");
        }
        System.out.println("PerMethodRecompilationCutoff = "
                + PER_METHOD_RECOMPILATION_CUTOFF);
        if (PER_METHOD_RECOMPILATION_CUTOFF == -1) {
            System.err.println(
                    "Warning: test is not applicable if PerMethodRecompilationCutoff == Inf");
            return;
        }

        // deoptimize 'PerMethodRecompilationCutoff' times and clear state
        for (long i = 0L, n = PER_METHOD_RECOMPILATION_CUTOFF - 1; i < n; ++i) {
            compileAndDeoptimize();
        }
        if (!testCase.isOsr && !isCompilable()) {
            // in osr test case count of deopt maybe more than iterations
            throw new RuntimeException(method + " is not compilable after "
                    + (PER_METHOD_RECOMPILATION_CUTOFF - 1) + " iterations");
        }
        WHITE_BOX.clearMethodState(method);

        // deoptimize 'PerMethodRecompilationCutoff' + 1 times
        long i;
        for (i = 0L; i < PER_METHOD_RECOMPILATION_CUTOFF
                && isCompilable(); ++i) {
            compileAndDeoptimize();
        }
        if (!testCase.isOsr && i != PER_METHOD_RECOMPILATION_CUTOFF) {
            // in osr test case count of deopt maybe more than iterations
            throw new RuntimeException(method + " is not compilable after "
                    + i + " iterations, but must only after "
                    + PER_METHOD_RECOMPILATION_CUTOFF);
        }
        if (isCompilable()) {
            throw new RuntimeException(method + " is still compilable after "
                    + PER_METHOD_RECOMPILATION_CUTOFF + " iterations");
        }
        compile();
        checkNotCompiled();

        // WB.clearMethodState() must reset no-compilable flags
        WHITE_BOX.clearMethodState(method);
        if (!isCompilable()) {
            throw new RuntimeException(method
                    + " is not compilable after clearMethodState()");
        }
        compile();
        checkCompiled();
    }

    private void compileAndDeoptimize() throws Exception {
        compile();
        waitBackgroundCompilation();
        deoptimize();
    }
}
