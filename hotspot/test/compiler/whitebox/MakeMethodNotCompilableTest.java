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
 * @library /testlibrary /testlibrary/whitebox
 * @build MakeMethodNotCompilableTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI MakeMethodNotCompilableTest
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
            for (int i = 1, n = TIERED_STOP_AT_LEVEL + 1; i < n; ++i) {
                WHITE_BOX.makeMethodNotCompilable(method, i);
                if (WHITE_BOX.isMethodCompilable(method, i)) {
                    throw new RuntimeException(method
                            + " must be not compilable at level" + i);
                }
                WHITE_BOX.enqueueMethodForCompilation(method, i);
                checkNotCompiled();

                if (!WHITE_BOX.isMethodCompilable(method)) {
                    System.out.println(method
                            + " is not compilable after level " + i);
                }
            }

            // WB.clearMethodState() must reset no-compilable flags
            WHITE_BOX.clearMethodState(method);
            if (!WHITE_BOX.isMethodCompilable(method)) {
                throw new RuntimeException(method
                        + " is not compilable after clearMethodState()");
            }
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
}
