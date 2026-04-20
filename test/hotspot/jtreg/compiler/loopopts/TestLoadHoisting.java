/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.loopopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8378626
 * @summary C2 should be able to hoist loads above loops.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestLoadHoisting {
    private static class A {
        int v;
    }

    private static class B extends A {
        final byte c;

        B(int c) {
            this.c = (byte) c;
        }
    }

    private static class C extends A {}

    private A a = new A();
    private B b = new B(0);
    private C c = new C();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.ITER_GVN1)
    @IR(failOn = IRNode.LOAD_I)
    private int testFoldLoad1(int v) {
        // The load is hoisted above the loop because it is independent of the store in the loop
        b.v = v;
        int sum = 0;
        for (int i = 1; i < 100; i *= 2) {
            c.v = sum;
            sum += b.v;
        }
        return sum;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.ITER_GVN1)
    @IR(failOn = IRNode.LOAD_I)
    @IR(counts = {IRNode.ALLOC, "1"})
    private B testFoldLoad2(int v) {
        // The load is hoisted above the loop because it is independent of the store in the loop
        b.v = v;
        int sum = 0;
        for (int i = 1; i < 100; i *= 2) {
            B newB = new B(sum);
            if (newB.c > 0) {
                return newB;
            }

            sum += b.v;
        }

        return null;
    }

    @Run(test = {"testFoldLoad1", "testFoldLoad2"})
    public void runTestFoldLoad() {
        Asserts.assertEQ(0, testFoldLoad1(0));
        Asserts.assertEQ(7, testFoldLoad1(1));
        Asserts.assertNull(testFoldLoad2(0));
        Asserts.assertNotNull(testFoldLoad2(1));
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"})
    @IR(counts = {IRNode.ALLOC, "1"})
    private B testNotFoldLoad1(int v) {
        // The load cannot be hoisted because the pointer is not a loop-invariant
        B b = this.b;
        b.v = v;
        int sum = 0;
        for (int i = 1; i < 100; i *= 2) {
            sum += b.v;
            B newB = new B(0);
            newB.v = sum;
            b = newB;
        }
        return b;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"})
    private int testNotFoldLoad2(int v) {
        // The load cannot be hoisted because there is a store in the loop interferes with it
        b.v = v;
        int sum = 0;
        for (int i = 1; i < 100; i *= 2) {
            sum += b.v;
            a.v = sum;
        }
        return sum;
    }

    @Run(test = {"testNotFoldLoad1", "testNotFoldLoad2"})
    public void runTestNotFoldLoad() {
        Asserts.assertEQ(0, testNotFoldLoad1(0).v);
        Asserts.assertEQ(64, testNotFoldLoad1(1).v);
        this.a = this.c;
        Asserts.assertEQ(0, testNotFoldLoad2(0));
        Asserts.assertEQ(7, testNotFoldLoad2(1));
        this.a = this.b;
        Asserts.assertEQ(0, testNotFoldLoad2(0));
        Asserts.assertEQ(64, testNotFoldLoad2(1));
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.ITER_GVN1)
    @IR(failOn = IRNode.LOAD_I)
    @IR(counts = {IRNode.ALLOC, "1"})
    private int testLoopNest1(int v) {
        // Hoist the load above both loops
        b.v = v;
        B newB = null;
        int sum = 0;
        for (int i = 1; i < 100; i *= 2) {
            newB = new B(0);
            for (int j = 1; j < 100; j *= 2) {
                newB.v = sum;
                sum += b.v;
            }
            c.v = sum;
        }
        b = newB;
        return sum;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "2"}, phase = CompilePhase.ITER_GVN1)
    @IR(counts = {IRNode.LOAD_I, "1"})
    @IR(counts = {IRNode.ALLOC, "2"})
    private int testLoopNest2(int v1, int v2) {
        // The loads can be hoisted above the inner loop but not the outer one
        b.v = v1;
        B newB = null;
        C newC = null;
        int sum = 0;
        for (int i = 1; i < 100; i *= 2) {
            c.v = v2;
            for (int j = 1; j < 100; j *= 2) {
                newB = new B(0);
                newC = new C();
                newB.v = sum;
                sum += b.v + c.v;
                newC.v = sum;
            }

            a.v = sum;
        }
        b = newB;
        c = newC;
        return sum;
    }

    @Run(test = {"testLoopNest1", "testLoopNest2"})
    public void runTestLoopNest() {
        Asserts.assertEQ(0, testLoopNest1(0));
        Asserts.assertEQ(49, testLoopNest1(1));
        Asserts.assertEQ(0, testLoopNest2(0, 0));
        Asserts.assertEQ(49, testLoopNest2(0, 1));
        Asserts.assertEQ(98, testLoopNest2(1, 1));
    }

    @Test
    @IR(failOn = IRNode.DIV_BY_ZERO_TRAP)
    private int testSideEffect1(int v) {
        // The load being hoisted out of the loop allows loop predication to move the zero divisor
        // checkout outside the loop
        int sum = v;
        for (int i = 1; i < 100; i *= 2) {
            sum += sum / b.v;
            c.v = sum;
        }
        return sum;
    }

    @Test
    @IR(failOn = IRNode.LOOP)
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    private int testSideEffect2() {
        // The load being hoisted out of the loop allows the loop to be transformed into a counted
        // loop
        int sum = 0;
        for (int i = 0; i < b.v; i++) {
            a = new B(i);
            c.v = sum;
            sum += i;
        }
        return sum;
    }

    @Run(test = {"testSideEffect1", "testSideEffect2"})
    public void runTestSideEffect() {
        b.v = 1;
        Asserts.assertEQ(0, testSideEffect1(0));
        Asserts.assertEQ(128, testSideEffect1(1));
        b.v = 10;
        Asserts.assertEQ(45, testSideEffect2());
    }
}
