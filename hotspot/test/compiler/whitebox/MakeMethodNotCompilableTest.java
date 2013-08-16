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
 * @test MakeMethodNotCompilableTest
 * @bug 8012322
 * @library /testlibrary /testlibrary/whitebox
 * @build MakeMethodNotCompilableTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,TestCase$Helper::* MakeMethodNotCompilableTest
 * @summary testing of WB::makeMethodNotCompilable()
 * @author igor.ignatyev@oracle.com
 */
public class MakeMethodNotCompilableTest extends CompilerWhiteBoxTest {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (TestCase test : TestCase.values()) {
                new MakeMethodNotCompilableTest(test).runTest();
            }
        } else {
            for (String name : args) {
                new MakeMethodNotCompilableTest(
                        TestCase.valueOf(name)).runTest();
            }
        }
    }

    public MakeMethodNotCompilableTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    /**
     * Tests {@code WB::makeMethodNotCompilable()} by calling it before
     * compilation and checking that method isn't compiled. Also
     * checks that WB::clearMethodState() clears no-compilable flags. For
     * tiered, additional checks for all available levels are conducted.
     *
     * @throws Exception if one of the checks fails.
     */
    @Override
    protected void test() throws Exception {
        checkNotCompiled();
        if (!WHITE_BOX.isMethodCompilable(method)) {
            throw new RuntimeException(method + " must be compilable");
        }

        if (TIERED_COMPILATION) {
            final int tierLimit = TIERED_STOP_AT_LEVEL + 1;
            for (int testedTier = 1; testedTier < tierLimit; ++testedTier) {
                testTier(testedTier);
            }
            for (int testedTier = 1; testedTier < tierLimit; ++testedTier) {
                WHITE_BOX.makeMethodNotCompilable(method, testedTier);
                if (WHITE_BOX.isMethodCompilable(method, testedTier)) {
                    throw new RuntimeException(method
                            + " must be not compilable at level" + testedTier);
                }
                WHITE_BOX.enqueueMethodForCompilation(method, testedTier);
                checkNotCompiled();

                if (!WHITE_BOX.isMethodCompilable(method)) {
                    System.out.println(method
                            + " is not compilable after level " + testedTier);
                }
            }
        } else {
            compile();
            checkCompiled();
            int compLevel = WHITE_BOX.getMethodCompilationLevel(method);
            WHITE_BOX.deoptimizeMethod(method);
            WHITE_BOX.makeMethodNotCompilable(method, compLevel);
            if (WHITE_BOX.isMethodCompilable(method, COMP_LEVEL_ANY)) {
                throw new RuntimeException(method
                        + " must be not compilable at CompLevel::CompLevel_any,"
                        + " after it is not compilable at " + compLevel);
            }
            WHITE_BOX.clearMethodState(method);

            // nocompilable at opposite level must make no sense
            int oppositeLevel;
            if (isC1Compile(compLevel)) {
              oppositeLevel = COMP_LEVEL_FULL_OPTIMIZATION;
            } else {
              oppositeLevel = COMP_LEVEL_SIMPLE;
            }
            WHITE_BOX.makeMethodNotCompilable(method, oppositeLevel);

            if (!WHITE_BOX.isMethodCompilable(method, COMP_LEVEL_ANY)) {
                  throw new RuntimeException(method
                        + " must be compilable at CompLevel::CompLevel_any,"
                        + " even it is not compilable at opposite level ["
                        + compLevel + "]");
            }

            if (!WHITE_BOX.isMethodCompilable(method, compLevel)) {
                  throw new RuntimeException(method
                        + " must be compilable at level " + compLevel
                        + ", even it is not compilable at opposite level ["
                        + compLevel + "]");
            }
        }

        // clearing after tiered/non-tiered tests
        // WB.clearMethodState() must reset no-compilable flags
        WHITE_BOX.clearMethodState(method);
        if (!WHITE_BOX.isMethodCompilable(method)) {
            throw new RuntimeException(method
                    + " is not compilable after clearMethodState()");
        }

        WHITE_BOX.makeMethodNotCompilable(method);
        if (WHITE_BOX.isMethodCompilable(method)) {
            throw new RuntimeException(method + " must be not compilable");
        }

        compile();
        checkNotCompiled();
        if (WHITE_BOX.isMethodCompilable(method)) {
            throw new RuntimeException(method + " must be not compilable");
        }
        // WB.clearMethodState() must reset no-compilable flags
        WHITE_BOX.clearMethodState(method);
        if (!WHITE_BOX.isMethodCompilable(method)) {
            throw new RuntimeException(method
                    + " is not compilable after clearMethodState()");
        }
        compile();
        checkCompiled();
    }

    // separately tests each tier
    private void testTier(int testedTier) {
        if (!WHITE_BOX.isMethodCompilable(method, testedTier)) {
            throw new RuntimeException(method
                    + " is not compilable on start");
        }
        WHITE_BOX.makeMethodNotCompilable(method, testedTier);

        // tests for all other tiers
        for (int anotherTier = 1, tierLimit = TIERED_STOP_AT_LEVEL + 1;
                    anotherTier < tierLimit; ++anotherTier) {
            boolean isCompilable = WHITE_BOX.isMethodCompilable(method,
                    anotherTier);
            if (sameCompile(testedTier, anotherTier)) {
                if (isCompilable) {
                    throw new RuntimeException(method
                            + " must be not compilable at level " + anotherTier
                            + ", if it is not compilable at " + testedTier);
                }
                WHITE_BOX.enqueueMethodForCompilation(method, anotherTier);
                checkNotCompiled();
            } else {
                if (!isCompilable) {
                    throw new RuntimeException(method
                            + " must be compilable at level " + anotherTier
                            + ", even if it is not compilable at "
                            + testedTier);
                }
                WHITE_BOX.enqueueMethodForCompilation(method, anotherTier);
                checkCompiled();
                WHITE_BOX.deoptimizeMethod(method);
            }

            if (!WHITE_BOX.isMethodCompilable(method, COMP_LEVEL_ANY)) {
                throw new RuntimeException(method
                        + " must be compilable at 'CompLevel::CompLevel_any'"
                        + ", if it is not compilable only at " + testedTier);
            }
        }

        // clear state after test
        WHITE_BOX.clearMethodState(method);
        if (!WHITE_BOX.isMethodCompilable(method, testedTier)) {
            throw new RuntimeException(method
                    + " is not compilable after clearMethodState()");
        }
    }

    private boolean sameCompile(int level1, int level2) {
        if (level1 == level2) {
            return true;
        }
        if (isC1Compile(level1) && isC1Compile(level2)) {
            return true;
        }
        if (isC2Compile(level1) && isC2Compile(level2)) {
            return true;
        }
        return false;
    }
}
