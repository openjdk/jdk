/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8296318
 * @summary Loops inside infinite loops may not be detected, thus a region may still
 *          be the loop head, even if it is not a LoopNode.
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,TestUndetectedLoopInInfiniteLoop::test*
 *      -XX:PerMethodTrapLimit=0
 *      TestUndetectedLoopInInfiniteLoop
 */


public class TestUndetectedLoopInInfiniteLoop {
    public static void main (String[] args) {
        test(true, false);
    }
    public static void test(boolean flag, boolean flag2) {
        int x = 0;
        if (flag2) { // runtime check, avoid infinite loop
            while (true) { // infinite loop (no exit)
                if (flag) {
                    x++;
                }
                do { // inner loop
                    // assert for this block
                    // Region
                    // Phi -> SubI -> XorI ---> Phi
                    x = (x - 1) ^ 1;
                    // Problem: this looks like a loop, but we have no LoopNode
                    // We have no LoopNode, because this is all in an infinite
                    // loop, and during PhaseIdealLoop::build_loop_tree we do not
                    // attach the loops of an infinite loop to the loop tree,
                    // and hence we do not get to call beautify_loop on these loops
                    // which would have turned the Region into a LoopNode.
                } while (x < 0);
            }
        }
    }
}
