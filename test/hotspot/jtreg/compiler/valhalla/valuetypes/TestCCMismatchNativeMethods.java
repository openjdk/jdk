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

package compiler.valhalla.valuetypes;

/*
 * @test
 * @enablePreview
 * @run main/othervm  ${test.main.class}
 */


public class TestCCMismatchNativeMethods {
    static {
        System.loadLibrary("TestCCMismatchNativeMethods");
    }


    value record V(int x) { }

    static class A {
        V m(V v) {
            return v;
        }
    }

    static class B {
        native V m(V v);
    }

    static int test1(A a, V v) {
        return a.m(v).x;
    }

    public static void main(String[] args) {
        A a = new A();
        B b = new B();
        for (int i = 0; i < 20_000; i++) {
            test(a, new V(42));
            test(b, new V(42));
        }
    }
}
