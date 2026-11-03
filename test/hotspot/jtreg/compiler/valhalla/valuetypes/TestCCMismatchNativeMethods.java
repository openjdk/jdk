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

package compiler.valhalla.valuetypes;

/*
 * @test
 * @bug 8395610
 * @summary native method calls are not scalarized and cause mismatches
 * @enablePreview
 * @run main/othervm -XX:-TieredCompilation ${test.main.class}
 * @run main ${test.main.class}
 */


public class TestCCMismatchNativeMethods {
    static {
        System.loadLibrary("TestCCMismatchNativeMethods");
    }

    value record V1(int x) { }

    static class A1 {
        int m1(V1 v1, V1 v2) {
            return v1.x;
        }
    }

    static class B1 extends A1 {
        native int m1(V1 v1, V1 v2);
    }

    static class C1 extends A1 {
    }

    static class A2 {
        int m1(V1 v1, V1 v2) {
            return v1.x;
        }
    }

    static class B2 extends A2 {
    }

    static class C2 extends A2 {
    }

    static class D2 extends A2 {
        native int m1(V1 v1, V1 v2);
    }

    static int test1(A1 a, V1 v) {
        return a.m1(v, v);
    }

    static int test2(A2 a, V1 v) {
        return a.m1(v, v);
    }

    public static void main(String[] args) {
        A1 a1 = new A1();
        B1 b1 = new B1();
        C1 c1 = new C1();
        A2 a2 = new A2();
        B2 b2 = new B2();
        C2 c2 = new C2();
        for (int i = 0; i < 20_000; i++) {
            test1(a1, new V1(42));
            test1(b1, new V1(42));
            test1(c1, new V1(42));
            test2(a2, new V1(42));
            test2(b2, new V1(42));
            test2(c2, new V1(42));
        }
        D2 d2 = new D2();
        for (int i = 0; i < 20_000; i++) {
            test2(a2, new V1(42));
            test2(b2, new V1(42));
            test2(c2, new V1(42));
            test2(d2, new V1(42));
        }
    }
}
