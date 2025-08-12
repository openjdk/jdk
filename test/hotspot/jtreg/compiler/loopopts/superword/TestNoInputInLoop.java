/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts.superword;

/*
 * @test id=Vanilla
 * @bug 8327172
 * @summary Test bad loop ctrl: a node is in the loop, but has no input in the loop
 * @run main/othervm compiler.loopopts.superword.TestNoInputInLoop
 */

/*
 * @test id=WithFlags
 * @bug 8327172
 * @summary Test bad loop ctrl: a node is in the loop, but has no input in the loop
 * @run main/othervm -Xbatch -XX:PerMethodTrapLimit=0
 *                   compiler.loopopts.superword.TestNoInputInLoop
 */

/*
 * @test id=WithMoreFlags
 * @bug 8327172
 * @summary Test bad loop ctrl: a node is in the loop, but has no input in the loop
 * @run main/othervm -Xbatch -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestNoInputInLoop::test*
 *                   compiler.loopopts.superword.TestNoInputInLoop
 */

public class TestNoInputInLoop {
    static long lFld;
    static float fFld;
    static int iArr[] = new int[400];

    public static void main(String[] strArr) {
        for (int i = 0; i < 100; i++) {
            test1();
        }
        for (int i = 0; i < 1_000; i++) {
            test2(0);
        }
    }

    // It specifically reproduced with UseAVX=2
    // 1. PhaseIdealLoop::build_loop_early
    //    We have a Store in a loop, with a Load after it.
    //    Both have ctrl as the CountedLoopNode.
    // 2. split_if_with_blocks -> PhaseIdealLoop::try_move_store_before_loop
    //    The Store is moved out of the loop, and its ctrl updated accordingly.
    //    But the Load has its ctrl not updated, even though it has now no input in the loop.
    // 3. SuperWord (VLoopBody::construct)
    //    We detect a data node in the loop that has no input in the loop.
    //    This is not expected.

    // OSR failure
    static void test1() {
        for (int i = 0; i < 200; i++) {
            fFld *= iArr[0] - lFld;
            iArr[1] = (int) lFld;
            for (int j = 0; j < 100_000; j++) {} // empty loop, trigger OSR
        }
    }

    // Normal compilation
    static void test2(int start) {
        for (int i = start; i < 200; i++) {
            fFld *= iArr[0] - lFld;
            iArr[1] = (int) lFld;
        }
    }
}
