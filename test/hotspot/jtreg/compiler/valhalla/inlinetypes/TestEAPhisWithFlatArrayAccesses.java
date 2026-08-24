/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @bug 8385328
 * @summary [lworld] C2: assert(phi->_idx >= nodes_size()) failed: only new Phi per instance memory slice still happens after JDK-8374742
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:-BackgroundCompilation ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestEAPhisWithFlatArrayAccesses {
    static int field;

    static value class MyValue {
        byte b1 = 42;
        byte b2 = 43;
    }

    static class A {
        public A(int field) {
            this.field = field;
            this.field2 = field;
        }

        private int field;
        private int field2;
    }

    static class UnloadedKlass {
        public static int field;
    }

    public static void main(String[] args) {
        MyValue v = new MyValue();
        A a = new A(42);
        MyValue[] flatArray1 = new MyValue[2];
        for (int i = 0; i < 20_000; i++) {
            test1(v, true, true, false, false);
            test1(v, false, true, false, false);
            test1(v, false, false, false, false);
            test1Helper1(a, v, flatArray1, true, false, true);
            test1Helper1(a, v, flatArray1, true, false, false);
        }
    }

    static MyValue[] flatArray3 = new MyValue[2];

    public static int test1(MyValue v, boolean flag, boolean flag2, boolean flag3, boolean flag5) {
        Object obj = new Object();
        A a = new A(42);
        MyValue[] flatArray1 = new MyValue[2];
        MyValue res;
        field = 42;
        if (flag) {
            res = flatArray1[0];
        } else {
            if (flag2) {
                res = flatArray3[0];
            } else {
                res = flatArray3[0];
            }
            test1Helper1(a, res, flatArray1, flag3, true, flag5);
            res = flatArray3[0];
        }
        flatArray3[0] = res;
        return a.field;
    }

    static void test1Helper1(A a, MyValue res, MyValue[] flatArray1, boolean flag3, boolean flag4, boolean flag5) {
        if (flag3) {
            flatArray1[0] = res;
            if (flag5) {
                a.field = 42;
                a.field2 = 42;
            }
            if (flag4) {
                UnloadedKlass.field = 42;
            }
        }
    }
}
