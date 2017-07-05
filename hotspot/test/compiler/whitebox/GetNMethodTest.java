/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import sun.hotspot.code.NMethod;

/*
 * @test GetNMethodTest
 * @bug 8038240
 * @library /testlibrary /testlibrary/whitebox
 * @build GetNMethodTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmixed -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,SimpleTestCase$Helper::* GetNMethodTest
 * @summary testing of WB::getNMethod()
 * @author igor.ignatyev@oracle.com
 */
public class GetNMethodTest extends CompilerWhiteBoxTest {
    public static void main(String[] args) throws Exception {
        CompilerWhiteBoxTest.main(GetNMethodTest::new, args);
    }

    private GetNMethodTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    @Override
    protected void test() throws Exception {
        checkNotCompiled();

        compile();
        checkCompiled();
        NMethod nmethod = NMethod.get(method, testCase.isOsr());
        if (IS_VERBOSE) {
            System.out.println("nmethod = " + nmethod);
        }
        if (nmethod == null) {
            throw new RuntimeException("nmethod of compiled method is null");
        }
        if (nmethod.insts.length == 0) {
            throw new RuntimeException("compiled method's instructions is empty");
        }
        deoptimize();
        checkNotCompiled();
        nmethod = NMethod.get(method, testCase.isOsr());
        if (nmethod != null) {
            throw new RuntimeException("nmethod of non-compiled method isn't null");
        }
    }
}
