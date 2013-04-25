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
 * @library /testlibrary /testlibrary/whitebox
 * @build EnqueueMethodForCompilationTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmixed -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI EnqueueMethodForCompilationTest
 * @author igor.ignatyev@oracle.com
 */
public class EnqueueMethodForCompilationTest extends CompilerWhiteBoxTest {
    public static void main(String[] args) throws Exception {
        // to prevent inlining #method into #compile()
        WHITE_BOX.testSetDontInlineMethod(METHOD, true);
        new EnqueueMethodForCompilationTest().runTest();
    }

    protected void test() throws Exception {
        checkNotCompiled(METHOD);

        WHITE_BOX.enqueueMethodForCompilation(METHOD, 0);
        if (WHITE_BOX.isMethodCompilable(METHOD, 0)) {
          throw new RuntimeException(METHOD + " is compilable at level 0");
        }
        checkNotCompiled(METHOD);

        WHITE_BOX.enqueueMethodForCompilation(METHOD, -1);
        checkNotCompiled(METHOD);

        WHITE_BOX.enqueueMethodForCompilation(METHOD, 5);
        if (!WHITE_BOX.isMethodCompilable(METHOD, 5)) {
          checkNotCompiled(METHOD);
          compile();
          checkCompiled(METHOD);
        } else {
          checkCompiled(METHOD);
        }

        int compLevel = WHITE_BOX.getMethodCompilationLevel(METHOD);
        WHITE_BOX.deoptimizeMethod(METHOD);
        checkNotCompiled(METHOD);

        WHITE_BOX.enqueueMethodForCompilation(METHOD, compLevel);
        checkCompiled(METHOD);
        WHITE_BOX.deoptimizeMethod(METHOD);
        checkNotCompiled(METHOD);

        compile();
        checkCompiled(METHOD);
        WHITE_BOX.deoptimizeMethod(METHOD);
        checkNotCompiled(METHOD);
    }
}
