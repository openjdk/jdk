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
package compiler.types;

/*
 * @test
 * @bug 8379667
 * @summary C2 crashes due to deep recursion in cmovP_regNode::bottom_type
 * @run main/othervm -Xbatch -XX:CompileCommand=memlimit,${test.main.class}::test,50M~crash ${test.main.class}
 */
public class TestCMovePMachType {
    interface I0 {}
    interface I1 {}
    interface I2 {}
    interface I3 {}
    interface I4 {}
    interface I5 {}
    interface I6 {}
    interface I7 {}
    interface I8 {}
    interface I9 {}
    interface I10 {}
    interface I11 {}
    interface I12 {}
    interface I13 {}
    interface I14 {}
    interface I15 {}
    interface I16 {}
    interface I17 {}
    interface I18 {}
    interface I19 {}
    interface IA {}
    interface IB {}

    static class P implements I0, I1, I2, I3, I4, I5, I6, I7, I8, I9, I10, I11, I12, I13, I14, I15, I16, I17, I18, I19 {
        int v;
    }

    static class A extends P implements IA {}
    static class B extends P implements IB {}

    public static void main(String[] args) {
        A a = new A();
        B b = new B();
        for (int i = 0; i < 20000; i++) {
            test(a, b, false, false);
            test(a, b, false, true);
            test(a, b, true, false);
            test(a, b, true, true);
        }
    }

    // This method is compiled into a giant chain of CMovePs, which leads to a deep recursion when
    // invoking Node::bottom_type on the corresponding MachNodes
    private static int test(A p1, B p2, boolean b1, boolean b2) {
        P p = b1 ? p1 : p2;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        p = b2 ? p : p2;
        p = b1 ? p : p1;
        int r = p.v;
        p.v = 0;
        return r;
    }
}
