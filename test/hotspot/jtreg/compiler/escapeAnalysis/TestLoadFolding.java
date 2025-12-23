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

package compiler.escapeAnalysis;

import compiler.lib.ir_framework.*;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8373495
 * @summary Test that loads from a newly allocated object are aggressively folded if the object has not escaped
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestLoadFolding {
    public static class Point {
        int x;
        int y;

        Point() {
            this(1, 2);
        }

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Point p && x == p.x && y == p.y;
        }

        @Override
        public String toString() {
            return "Point[" + x + ", " + y + "]";
        }
    }

    public static class PointHolder {
        Point p;
    }

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addScenarios(new Scenario(0, "-XX:+UnlockDiagnosticVMOptions", "-XX:-DoLocalEscapeAnalysis"),
                new Scenario(1, "-XX:+UnlockDiagnosticVMOptions", "-XX:+DoLocalEscapeAnalysis"));
        framework.start();
    }

    @DontInline
    static void escape(Object o) {}

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test101() {
        // p only escapes at return
        Point p = new Point();
        escape(null);
        p.x += p.y;
        return p;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test102(boolean b) {
        // p escapes in another branch
        Point p = new Point();
        if (b) {
            escape(p);
        } else {
            escape(null);
            p.x += p.y;
        }
        return p;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test103(Point p, boolean b) {
        // A Phi of p1 and p, but a store to Phi is after all the loads from p1
        Point p1 = new Point();
        if (b) {
            p = new Point();
        }
        escape(null);
        p.x = p1.x + p1.y;
        return p;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public int test104(PointHolder h) {
        // Even if p escapes before the loads, if it is legal to execute the loads before the
        // store, then we can fold the loads
        Point p = new Point();
        escape(null);
        h.p = p;
        return p.x + p.y;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, counts = {IRNode.ALLOC, "1"})
    public Point test105(int begin, int end) {
        // Fold the load that is a part of a cycle
        Point p = new Point();
        for (int i = begin; i < end; i *= 2) {
            p.x++;
            escape(null); // Force a memory Phi
        }
        p.x += p.y;
        return p;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, counts = {IRNode.ALLOC, "1"})
    public Point test106(Point p2, int begin, int end, boolean b) {
        // A cycle and a Phi, this time the store is at a different field
        Point p1 = new Point();
        // This store is not on a Phi involving p1, so it does not interfere
        p2.y = 2;
        Point p = p1;
        for (int i = begin; i < end; i *= 2) {
            if (b) {
                p = p1;
            } else {
                p = p2;
            }
            b = !b;

            p.x = p1.y + 3;
            escape(null); // Force a memory Phi
        }
        p1.x = p1.y;
        return p;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, counts = {IRNode.LOAD_I, "1", IRNode.ALLOC_ARRAY, "1"})
    public int test107(int idx) {
        // Array
        int[] a = new int[2];
        a[0] = 1;
        a[1] = 2;
        int res = a[idx & 1];
        escape(null);
        res += a[0] + a[1];
        escape(a);
        return res;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC_ARRAY, "1"})
    public int test108(int idx) {
        // Array, even if we will give up if we encounter a[idx & 1] = 3, we meet a[0] = 4 first,
        // so the load int res = a[0] can still be folded
        int[] a = new int[2];
        a[0] = 1;
        a[1] = 2;
        escape(null);
        a[idx & 1] = 3;
        a[0] = 4;
        escape(null);
        int res = a[0];
        escape(a);
        return res;
    }

    static class SupplierHolder {
        Supplier<String> f;

        static final Supplier<String> DEFAULT_VALUE = () -> "test";
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"},
        failOn = {IRNode.DYNAMIC_CALL_OF_METHOD, "get", IRNode.LOAD_OF_FIELD, "f", IRNode.CLASS_CHECK_TRAP},
        counts = {IRNode.ALLOC, "1"})
    public String test109() {
        // Folding of the load o.f allows o.f.get to get devirtualized
        SupplierHolder o = new SupplierHolder();
        o.f = SupplierHolder.DEFAULT_VALUE;
        escape(null);
        String res = o.f.get();
        escape(o);
        return res;
    }
    
    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "2"})
    public int test110(PointHolder h, boolean b) {
        // Inspect the escape status of a Phi
        Point p1 = new Point();
        Point p2 = new Point();
        Point p = b ? p1 : p2;
        p.x = 4;
        escape(null);
        h.p = p1;
        return p.x;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "2"})
    public int test111(int begin, int end, boolean b) {
        // Inspect the escape status of a loop Phi
        Point p = new Point();
        for (int i = begin; i < end; i *= 2) {
            if (b) {
                p = new Point();
            }
        }
        p.x = 4;
        escape(null);
        int res = p.x;
        escape(p);
        return res;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public int test112() {
        // The object has been stored into memory but the destination does not escape
        PointHolder h = new PointHolder();
        Point p = new Point();
        h.p = p;
        VarHandle.fullFence();
        int res = p.x;
        escape(p);
        return res;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "2"})
    public int test113() {
        // The object has been stored into memory but the destination has not escaped
        PointHolder h = new PointHolder();
        Point p = new Point();
        h.p = p;
        VarHandle.fullFence();
        int res = p.x;
        escape(h);
        return res;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "3"})
    public int test114(boolean b) {
        // A Phi has been stored into memory but the destination has not escaped
        PointHolder h = new PointHolder();
        Point p1 = new Point();
        Point p2 = new Point();
        h.p = b ? p1 : p2;
        VarHandle.fullFence();
        int res = p1.x;
        escape(h);
        return res;
    }

    @Test
    @IR(applyIf = {"DoLocalEscapeAnalysis", "true"}, failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "3"})
    public int test115(boolean b) {
        // The object has been stored into a Phi but the destination has not escaped
        PointHolder h1 = new PointHolder();
        PointHolder h2 = new PointHolder();
        Point p = new Point();
        PointHolder h = b ? h1 : h2;
        h.p = p;
        VarHandle.fullFence();
        int res = p.x;
        escape(h1);
        return res;
    }

    @Run(test = {"test101", "test102", "test103", "test104", "test105", "test106", "test107", "test108", "test109",
                 "test110", "test111", "test112", "test113", "test114", "test115"})
    public void runPositiveTests() {
        Asserts.assertEQ(new Point(3, 2), test101());
        Asserts.assertEQ(new Point(3, 2), test102(false));
        Asserts.assertEQ(new Point(1, 2), test102(true));
        Asserts.assertEQ(new Point(3, 2), test103(new Point(), false));
        Asserts.assertEQ(new Point(3, 2), test103(new Point(), true));
        Asserts.assertEQ(3, test104(new PointHolder()));
        Asserts.assertEQ(new Point(7, 2), test105(1, 16));
        Asserts.assertEQ(new Point(2, 2), test106(new Point(), 1, 16, false));
        Asserts.assertEQ(new Point(5, 2), test106(new Point(), 1, 16, true));
        Asserts.assertEQ(4, test107(0));
        Asserts.assertEQ(4, test108(0));
        Asserts.assertEQ("test", test109());
        Asserts.assertEQ(4, test110(new PointHolder(), false));
        Asserts.assertEQ(4, test110(new PointHolder(), true));
        Asserts.assertEQ(4, test111(1, 16, false));
        Asserts.assertEQ(4, test111(1, 16, true));
        Asserts.assertEQ(1, test112());
        Asserts.assertEQ(1, test113());
        Asserts.assertEQ(1, test114(false));
        Asserts.assertEQ(1, test114(true));
        Asserts.assertEQ(1, test115(false));
        Asserts.assertEQ(1, test115(true));
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "2", IRNode.ALLOC, "1"})
    public int test001(PointHolder h) {
        Point p = new Point();
        h.p = p;
        // Actually, the only fence that requires the following loads to be executed after the
        // store is a fullFence
        VarHandle.fullFence();
        return p.x + p.y;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "1"})
    public int test002(boolean b) {
        Point p = new Point();
        if (b) {
            escape(p);
            // p escaped, so the load must not be removed
            return p.x;
        } else {
            escape(null);
            return 0;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "1"})
    public int test003(boolean b) {
        Point p = new Point();
        if (b) {
            escape(p);
        }
        // p escaped, so the load must not be removed
        return p.x;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "> 0", IRNode.ALLOC, "1"})
    public Point test004(int begin, int end) {
        Point p = new Point();
        for (int i = begin; i < end; i *= 2) {
            // p escaped here because this is a loop
            p.x++;
            escape(p);
        }
        return p;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "2", IRNode.ALLOC_ARRAY, "1"})
    public int test005(int idx) {
        int[] a = new int[2];
        a[0] = 1;
        a[1] = 2;
        escape(null);
        a[idx & 1] = 3;
        // Cannot fold the loads because we do not know which element is written to by
        // a[idx & 1] = 3
        return a[0] + a[1];
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "1"})
    public int test006(Point p, boolean b) {
        // A Phi with an input ineligible for escape analysis
        if (b) {
            p = new Point();
        }
        escape(null);
        int res = p.x;
        escape(p);
        return res;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "2"})
    public int test007(boolean b) {
        // A Phi that escapes because an input escapes
        Point p1 = new Point();
        Point p2 = new Point();
        Point p = b ? p1 : p2;
        escape(p1);
        return p.x;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "2"})
    public int test008() {
        // An object is stored into another object that escapes
        PointHolder h = new PointHolder();
        Point p = new Point();
        h.p = p;
        escape(h);
        return p.x;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "2"})
    public int test009(PointHolder h, boolean b) {
        // An object is stored into a Phi that is ineligible for escape analysis
        if (b) {
            h = new PointHolder();
        }
        Point p = new Point();
        h.p = p;
        escape(null);
        return p.x;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "3"})
    public int test010(boolean b) {
        // An object is stored into a Phi that escapes because one of its inputs escapes
        PointHolder h1 = new PointHolder();
        PointHolder h2 = new PointHolder();
        PointHolder h = b ? h1 : h2;
        Point p = new Point();
        h.p = p;
        escape(h1);
        return p.x;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "4"})
    public int test011(boolean b1, boolean b2) {
        // A Phi escapes because one of its inputs is stored into a Phi, that in turn escapes
        // because one of its inputs escapes
        PointHolder h1 = new PointHolder();
        PointHolder h2 = new PointHolder();
        PointHolder h = b1 ? h1 : h2;
        Point p1 = new Point();
        Point p2 = new Point();
        Point p = b2 ? p1 : p2;
        h.p = p1;
        escape(h2);
        return p.x;
    }

    @Run(test = {"test001", "test002", "test003", "test004", "test005", "test006", "test007", "test008", "test009",
                 "test010", "test011"})
    public void runNegativeTests() {
        Asserts.assertEQ(3, test001(new PointHolder()));
        Asserts.assertEQ(0, test002(false));
        Asserts.assertEQ(1, test002(true));
        Asserts.assertEQ(1, test003(false));
        Asserts.assertEQ(1, test003(true));
        Asserts.assertEQ(new Point(5, 2), test004(1, 16));
        Asserts.assertEQ(5, test005(0));
        Asserts.assertEQ(1, test006(new Point(), false));
        Asserts.assertEQ(1, test006(new Point(), true));
        Asserts.assertEQ(1, test007(false));
        Asserts.assertEQ(1, test007(true));
        Asserts.assertEQ(1, test008());
        Asserts.assertEQ(1, test009(new PointHolder(), false));
        Asserts.assertEQ(1, test009(new PointHolder(), true));
        Asserts.assertEQ(1, test010(false));
        Asserts.assertEQ(1, test010(true));
        Asserts.assertEQ(1, test011(false, false));
        Asserts.assertEQ(1, test011(false, true));
        Asserts.assertEQ(1, test011(true, false));
        Asserts.assertEQ(1, test011(true, true));
    }
}
