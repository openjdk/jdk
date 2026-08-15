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
 * @test 8385020
 * @summary dying substitutability call transformed during igvn
 * @enablePreview
 * @run main/othervm -Xcomp -XX:CompileOnly=${test.main.class}::test* -XX:CompileCommand=dontinline,${test.main.class}::notInlined
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=2677332830 ${test.main.class}
 * @run main/othervm -Xcomp -XX:CompileOnly=${test.main.class}::test* -XX:CompileCommand=dontinline,${test.main.class}::notInlined
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN ${test.main.class}
 * @run main/othervm -Xcomp -XX:CompileOnly=${test.main.class}::test* -XX:CompileCommand=dontinline,${test.main.class}::notInlined
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestDeadSubstitutabilityCallInIdeal {
    private static int intField;
    private static final MyValue[] flatField = { new MyValue(42) };

    public static void main(String[] args) {
        MyValue v = new MyValue(42);
        A a = new A(42);
        test1(a, v, a);
        test2(a, v, a);
    }

    private static boolean test1(A a, MyValue v, Object o) {
        A aa = new A(10);
        notInlined();
        int i = aa.intField;
        Object o1, o2 = o;
        int j = 0, k = 0, l = 0;
        if (i == 10) {
            o1 = v;
        } else {
            o1 = a;
        }
        if (i != 10) {
            int intVal = flatField[0].field;
            if (o1 == o2) {
                return true;
            }
            intField = intVal;
            return false;
         } else {
            return false;
        }
    }

    private static boolean test2(A a, MyValue v, Object o) {
        A aa = new A(10);
        notInlined();
        int i = aa.intField;
        Object o1, o2 = o;
        int j = 0, k = 0, l = 0;
        if (i == 10) {
            o1 = v;
        } else {
            o1 = a;
        }
        if (i != 10) {
            int intVal = flatField[0].field;
            intField = intVal;
            if (o1 == o2) {
                return true;
            }
            return false;
         } else {
            return false;
        }
    }

    static void notInlined() {
    }

    static class A {
        int intField;
        A(int i) {
            this.intField = i;
        }
    }

    static class B {
        A objectField;
        B(A o) {
            this.objectField = o;
        }
    }

    static value class MyValue {
        int field;
        MyValue(int field) {
            this.field = field;
        }
     }
}
