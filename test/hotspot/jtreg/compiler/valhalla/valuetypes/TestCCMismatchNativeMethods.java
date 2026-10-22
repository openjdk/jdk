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
 * @enablePreview
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:CompileOnly=${test.main.class}::test1  ${test.main.class}
 */


public class TestCCMismatchNativeMethods {
    static {
        System.loadLibrary("TestCCMismatchNativeMethods");
    }

    value record V1(int x) { }

    static abstract value class V2 {
        private int x;

        V2(int x) {
            this.x = x;
        }
        
        abstract int m1(V1 v);
    }

    static value class V3 extends V2 {
        V3(int x) {
            super(x);
        }

        native int m1(V1 v);
    }

    static value class V4 extends V2 {
        V4(int x) {
            super(x);
        }

        int m1(V1 v) {
            return v.x;
        }
    }

    static value class V5 extends V2 {
        V5(int x) {
            super(x);
        }

        int m1(V1 v) {
            return v.x;
        }
    }

    static class A {
        int m1(V1 v1, V1 v2) {
            return v1.x;
        }
    }

    static class B extends A {
        native int m1(V1 v1, V1 v2);
    }

    static class C extends A {
    }
    
    static int test1(A a, V1 v) {
        return a.m1(v, v);
    }

    static int test2(V2 v1, V1 v2) {
        return v1.m1(v2);
    }

    public static void main(String[] args) {
        A a = new A();
        B b = new B();
        C c = new C();
        for (int i = 0; i < 20_000; i++) {
            // test1(a, new V1(42));
            // test1(b, new V1(42));
            // test1(c, new V1(42));
            test2(new V3(42), new V1(42));
            test2(new V4(42), new V1(42));
            test2(new V5(42), new V1(42));
        }
    }
}
