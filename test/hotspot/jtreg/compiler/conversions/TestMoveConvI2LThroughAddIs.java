/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package compiler.conversions;

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8254317
 * @requires vm.compiler2.enabled
 * @summary Exercises the optimization that moves integer-to-long conversions
 *          upwards through different shapes of integer addition
 *          subgraphs. Contains three basic (small) tests and two stress tests
 *          that resulted in a compilation time and memory explosion, triggering
 *          the short specified timeout.
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:-Inline
 *      -XX:CompileOnly=::testChain,::testTree,::testDAG
 *      compiler.conversions.TestMoveConvI2LThroughAddIs basic
 * @run main/othervm/timeout=1 -Xcomp -XX:-TieredCompilation -XX:-Inline
 *      -XX:CompileOnly=::testStress1
 *      compiler.conversions.TestMoveConvI2LThroughAddIs stress1
 * @run main/othervm/timeout=1 -Xcomp -XX:-TieredCompilation -XX:-Inline
 *      -XX:CompileOnly=::testStress2
 *      compiler.conversions.TestMoveConvI2LThroughAddIs stress2
 */

public class TestMoveConvI2LThroughAddIs {

    // This guard is used to make C2 infer that the 'a' variables in the
    // different test methods are in a small value range, enabling the
    // optimization in ConvI2LNode::Ideal() for LP64 platforms.
    static boolean val = true;

    static void testChain() {
        int a = val ? 2 : 10;
        int b = a + a;
        int c = b + b;
        int d = c + c;
        long out = d;
        Asserts.assertEQ(out, 16L);
    }

    static void testTree() {
        int a0 = val ? 2 : 10;
        int a1 = val ? 2 : 10;
        int a2 = val ? 2 : 10;
        int a3 = val ? 2 : 10;
        int a4 = val ? 2 : 10;
        int a5 = val ? 2 : 10;
        int a6 = val ? 2 : 10;
        int a7 = val ? 2 : 10;
        int b0 = a0 + a1;
        int b1 = a2 + a3;
        int b2 = a4 + a5;
        int b3 = a6 + a7;
        int c0 = b0 + b1;
        int c1 = b2 + b3;
        int d = c0 + c1;
        long out = d;
        Asserts.assertEQ(out, 16L);
    }

    static void testDAG() {
        int a0 = val ? 2 : 10;
        int a1 = val ? 2 : 10;
        int a2 = val ? 2 : 10;
        int a3 = val ? 2 : 10;
        int b0 = a0 + a1;
        int b1 = a1 + a2;
        int b2 = a2 + a3;
        int c0 = b0 + b1;
        int c1 = b1 + b2;
        int d = c0 + c1;
        long out = d;
        Asserts.assertEQ(out, 16L);
    }

    static void testStress1() {
        int a = val ? 2 : 10;
        // This loop should be fully unrolled.
        for (int i = 0; i < 24; i++) {
            a = a + a;
        }
        long out = a;
        Asserts.assertEQ(out, 33554432L);
    }

    static void testStress2() {
         int a = val ? 1 : 2;
         int b = a;
         int c = a + a;
         // This loop should be fully unrolled.
         for (int i = 0; i < 16; i++) {
             b = b + c;
             c = b + c;
         }
         long out = b + c;
         Asserts.assertEQ(out, 14930352L);
     }

    public static void main(String[] args) {
        switch(args[0]) {
        case "basic":
            testChain();
            testTree();
            testDAG();
            break;
        case "stress1":
            testStress1();
        case "stress2":
            testStress2();
        default:
            System.out.println("invalid mode");
        }
    }
}
