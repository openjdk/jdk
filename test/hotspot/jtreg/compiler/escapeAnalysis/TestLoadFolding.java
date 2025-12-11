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
            x = 1;
            y = 2;
        }

        static final Point DEFAULT = new Point();
    }

    static Point staticField;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test11", "test12", "test13", "test14", "test15", "test16"})
    public void runPositiveTests() {
        test11();
        test12(false);
        test12(true);
        test13(false);
        test13(true);
        test14();
        test15(1, 16);
        test16(1, 16, false);
        test16(1, 16, true);
    }

    @Run(test = {"test01", "test02", "test03", "test04"})
    public void runNegativeTests() {
        test01();
        test02(false);
        test02(true);
        test03(false);
        test03(true);
        test04(1, 16);
    }

    @DontInline
    static void escape(Object o) {}

    @Test
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test11() {
        // p only escapes at return
        Point p = new Point();
        escape(null);
        p.x += p.y;
        return p;
    }

    @Test
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test12(boolean b) {
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
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test13(boolean b) {
        // A Phi of p1 and Point.DEFAULT, but a store to Phi is after all the loads from p1
        Point p1 = new Point();
        Point p = b ? p1 : Point.DEFAULT;
        escape(null);
        p.x = p1.x + p1.y;
        return p;
    }

    @Test
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public int test14() {
        // Even if p escapes before the loads, if it is legal to execute the loads before the
        // store, then we can fold the loads
        Point p = new Point();
        escape(null);
        staticField = p;
        return p.x + p.y;
    }

    @Test
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test15(int begin, int end) {
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
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.ALLOC, "1"})
    public Point test16(int begin, int end, boolean b) {
        // A cycle and a Phi, this time the store is at a different
        Point p1 = new Point();
        // This store is not on a Phi involving p1, so it does not interfere
        Point.DEFAULT.y = 3;
        Point p = p1;
        for (int i = begin; i < end; i += 2) {
            if (b) {
                p = p1;
            } else {
                p = Point.DEFAULT;
            }
            b = !b;

            p.x = p1.y + 3;
            escape(null); // Force a memory Phi
        }
        p1.x = p1.y;
        return p;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "2", IRNode.ALLOC, "1"})
    public int test01() {
        Point p = new Point();
        staticField = p;
        // Actually, the only fence that requires the following loads to be executed after the
        // store is a fullFence
        VarHandle.fullFence();
        return p.x + p.y;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.ALLOC, "1"})
    public int test02(boolean b) {
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
    public int test03(boolean b) {
        Point p = new Point();
        if (b) {
            escape(p);
        }
        // p escaped, so the load must not be removed
        return p.x;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "> 0", IRNode.ALLOC, "1"})
    public Point test04(int begin, int end) {
        Point p = new Point();
        for (int i = begin; i < end; i *= 2) {
            // p escaped here because this is a loop
            p.x++;
            escape(p);
        }
        return p;
    }
}
