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
 * @test id=with-flags
 * @bug 8370332
 * @summary This test shows a case where split_if split a node through a phi, but left the
 *          dead node and a dead phi in the loop _body. Subsequently, SuperWord was run, and
 *          found the dead nodes in the _body, which is not expected.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,*TestSplitThruPhiRemoveDeadNodesFromLoopBody::test
 *      -Xbatch
 *      compiler.loopopts.superword.TestSplitThruPhiRemoveDeadNodesFromLoopBody
 */

/*
 * @test id=vanilla
 * @bug 8370332
 * @run main compiler.loopopts.superword.TestSplitThruPhiRemoveDeadNodesFromLoopBody
 */

package compiler.loopopts.superword;

public class TestSplitThruPhiRemoveDeadNodesFromLoopBody {
    static int N = 400;
    static float floatZero = 0;
    static boolean falseFlag = false;;

    static int fieldStore = 0;
    static int fieldIncr = 0;
    static int arrayI[] = new int[N];

    static void inlined() {
        int x = 0;
        for (int i = 0; i < 100; i++) {
            fieldStore = 42;
            if (falseFlag) {
                for (int k = 0; k < 20; k++) {
                    x += i;
                }
            }
        }
    }

    static void test() {
        inlined();
        for (int k = 0; k < 10; k++) {
            for (int j = 0; j < 100; j++) {
                fieldIncr += floatZero;
                arrayI[j] = 42; // SuperWord happens here -> SIGSEGV
            }
        }
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 1_000; i++) {
            test();
        }
    }
}
