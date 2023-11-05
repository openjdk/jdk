/* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8285835
 * @summary EA does not propagate NSR (not scalar replaceable) state.
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestBrokenEA
 */

public class TestBrokenEA {

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(true);
            test1(false);
            test2(true);
            test2(false);
        }
    }

    private static void test1(boolean flag) {
        A[] array = new A[1];
        if (flag) {
            C c = new C();
            B b = new B();
            b.c = c;
            A a = new A();
            a.b = b;
            array[0] = a;
        }
        A a = array[0];
        if (a != null) {
            a.b.c.f = 0x42;
        }
    }

    private static void test2(boolean flag) {
        A a = null;
        if (flag) {
            C c = new C();
            B b = new B();
            b.c = c;
            a = new A();
            a.b = b;
        }
        if (a != null) {
            a.b.c.f = 0x42;
        }
    }

    private static class A {
        public B b;
    }

    private static class B {
        public C c;
    }

    private static class C {
        public int f;
    }
}
