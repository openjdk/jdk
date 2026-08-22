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
import jdk.test.lib.Platform;

/*
 * @test
 * @bug 8380089
 * @summary Test that stores can be elided by peeling the loop.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestRemoveStoresAfterPeeling {
    private static class A {
        int v;
    }

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.addScenarios(new Scenario(0));
        if (Platform.isDebugBuild()) {
            framework.addScenarios(new Scenario(1, "-XX:+StressLoopPeeling"));
        }
        framework.start();
    }

    @Test
    @IR(counts = {IRNode.STORE_I, "1"})
    private void testStoreHoisting(A a, int v) {
        for (int i = 1; i < 100; i *= 2) {
            // Simple case, the store can be hoisted above the loop
            a.v = v;
        }
    }

    @Run(test = "testStoreHoisting")
    public void runStoreHoisting() {
        A a = new A();
        testStoreHoisting(a, 1);
        Asserts.assertEQ(1, a.v);
    }

    @Test
    @IR(counts = {IRNode.STORE_I, "1"})
    private void testStorePeeling1(int limit, A a, int v) {
        for (int i = 1; i < 100; i *= 2) {
            // The store does not post-dominate the loop head, so it cannot be hoisted
            if (i >= limit) {
                break;
            }

            a.v = v;
        }
    }

    @Run(test = "testStorePeeling1")
    public void runStorePeeling1() {
        A a = new A();
        testStorePeeling1(0, a, 1);
        Asserts.assertEQ(0, a.v);
        testStorePeeling1(200, a, 1);
        Asserts.assertEQ(1, a.v);
    }

    @Test
    @IR(counts = {IRNode.STORE_I, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    private A testStorePeeling2(A a1, int v1, float v2) {
        A a2 = new A();
        for (int i = 1; i < 100; i *= 2) {
            // There are 2 independent stores in the loop
            a2.v = Float.floatToRawIntBits(i * v2);
            a1.v = v1;
        }
        return a2;
    }

    @Run(test = "testStorePeeling2")
    public void runStorePeeling2() {
        A a1 = new A();
        A a2 = testStorePeeling2(a1, 1, 1);
        Asserts.assertEQ(1, a1.v);
        Asserts.assertEQ(Float.floatToRawIntBits(64), a2.v);
    }

    @Test
    @IR(counts = {IRNode.STORE_I, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    private A testStorePeeling3(int limit, A a1, int v1, float v2) {
        A a2 = new A();
        for (int i = 1; i < 100; i *= 2) {
            // Combine the previous 2 cases
            a2.v = Float.floatToRawIntBits(i * v2);
            if (i >= limit) {
                break;
            }
            a1.v = v1;
        }
        return a2;
    }

    @Run(test = "testStorePeeling3")
    public void runStorePeeling3() {
        A a1 = new A();
        A a2 = testStorePeeling3(0, a1, 1, 1);
        Asserts.assertEQ(0, a1.v);
        Asserts.assertEQ(Float.floatToRawIntBits(1), a2.v);
        a2 = testStorePeeling3(200, a1, 1, 1);
        Asserts.assertEQ(1, a1.v);
        Asserts.assertEQ(Float.floatToRawIntBits(64), a2.v);
    }

    @Test
    private void testStoreNotPeeled1(int limit, A a, int v) {
        for (int i = 1; i < 100; i *= 2) {
            // Cannot elide the store after peeling because it does not dominate the back edge
            if (i >= limit) {
                a.v = v;
            }
        }
    }

    @Run(test = "testStoreNotPeeled1")
    public void runStoreNotPeeled1() {
        A a = new A();
        testStoreNotPeeled1(-1, a, 1);
        Asserts.assertEQ(1, a.v);
        a.v = 0;
        testStoreNotPeeled1(50, a, 1);
        Asserts.assertEQ(1, a.v);
        a.v = 0;
        testStoreNotPeeled1(200, a, 1);
        Asserts.assertEQ(0, a.v);
    }

    @Test
    private void testStoreNotPeeled2(A a1, A a2, int v1, int v2) {
        for (int i = 1; i < 100; i *= 2) {
            // Cannot elide the store after peeling because there is an interfering store
            a2.v = Float.floatToRawIntBits(i * v2);
            a1.v = v1;
        }
    }

    @Run(test = "testStoreNotPeeled2")
    public void runStoreNotPeel2() {
        A a1 = new A();
        A a2 = new A();
        testStoreNotPeeled2(a1, a2, 1, 1);
        Asserts.assertEQ(1, a1.v);
        Asserts.assertEQ(Float.floatToRawIntBits(64), a2.v);
        a1.v = 0;
        testStoreNotPeeled2(a1, a1, 1, 1);
        Asserts.assertEQ(1, a1.v);
    }
}
