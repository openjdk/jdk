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
 * @test EnqueueMethodForCompilationTest
 * @bug 8006683 8007288 8022832
 * @library /testlibrary /testlibrary/whitebox
 * @build EnqueueMethodForCompilationTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -Xmixed -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,TestCase$Helper::* EnqueueMethodForCompilationTest
 * @summary testing of WB::enqueueMethodForCompilation()
 * @author igor.ignatyev@oracle.com
 */
public class EnqueueMethodForCompilationTest extends CompilerWhiteBoxTest {

    public static void main(String[] args) throws Exception {
        for (TestCase test : TestCase.values()) {
            new EnqueueMethodForCompilationTest(test).runTest();
        }
    }

    public EnqueueMethodForCompilationTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    @Override
    protected void test() throws Exception {
        checkNotCompiled();

        // method can not be compiled on level 'none'
        WHITE_BOX.enqueueMethodForCompilation(method, COMP_LEVEL_NONE);
        if (isCompilable(COMP_LEVEL_NONE)) {
            throw new RuntimeException(method
                    + " is compilable at level COMP_LEVEL_NONE");
        }
        checkNotCompiled();

        // COMP_LEVEL_ANY is inapplicable as level for compilation
        WHITE_BOX.enqueueMethodForCompilation(method, COMP_LEVEL_ANY);
        checkNotCompiled();

        // not existing comp level
        WHITE_BOX.enqueueMethodForCompilation(method, 42);
        checkNotCompiled();

        compile();
        checkCompiled();

        int compLevel = getCompLevel();
        int bci = WHITE_BOX.getMethodEntryBci(method);
        System.out.println("bci = " + bci);
        printInfo();
        deoptimize();
        printInfo();
        checkNotCompiled();
        printInfo();
        WHITE_BOX.enqueueMethodForCompilation(method, compLevel, bci);
        checkCompiled();
        deoptimize();
        checkNotCompiled();

        compile();
        checkCompiled();
        deoptimize();
        checkNotCompiled();
    }
}
