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
 * @test ClearMethodStateTest
 * @bug 8006683 8007288 8022832
 * @library /testlibrary /testlibrary/whitebox
 * @build ClearMethodStateTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmixed -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,TestCase$Helper::* ClearMethodStateTest
 * @summary testing of WB::clearMethodState()
 * @author igor.ignatyev@oracle.com
 */
public class ClearMethodStateTest extends CompilerWhiteBoxTest {

    public static void main(String[] args) throws Exception {
        for (TestCase test : TestCase.values()) {
            new ClearMethodStateTest(test).runTest();
        }
    }

    public ClearMethodStateTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }


    /**
     * Tests {@code WB::clearMethodState()} by calling it before/after
     * compilation. For non-tiered, checks that counters will be rested after
     * clearing of method state.
     *
     * @throws Exception if one of the checks fails.
     */
    @Override
    protected void test() throws Exception {
        checkNotCompiled();
        compile();
        WHITE_BOX.clearMethodState(method);
        checkCompiled();
        WHITE_BOX.clearMethodState(method);
        deoptimize();
        checkNotCompiled();

        if (testCase.isOsr) {
            // part test isn't applicable for OSR test case
            return;
        }
        if (!TIERED_COMPILATION) {
            WHITE_BOX.clearMethodState(method);
            compile(COMPILE_THRESHOLD);
            checkCompiled();

            deoptimize();
            checkNotCompiled();
            WHITE_BOX.clearMethodState(method);

            // invoke method one less time than needed to compile
            if (COMPILE_THRESHOLD > 1) {
                compile(COMPILE_THRESHOLD - 1);
                checkNotCompiled();
            } else {
                System.err.println("Warning: 'CompileThreshold' <= 1");
            }

            compile(1);
            checkCompiled();
        } else {
            System.err.println(
                    "Warning: part of test is not applicable in Tiered");
        }
    }
}
