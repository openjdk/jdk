/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8287700
 * @summary C2 Crash running eclipse benchmark from Dacapo
 *
 * @run main/othervm -XX:-BackgroundCompilation TestEACheckCastPP
 *
 */

public class TestEACheckCastPP {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(false);
            test_helper2(new A(), true);
        }
    }

    private static void test(boolean flag) {
        I i = test_helper();
        test_helper2(i, flag);
    }

    private static void test_helper2(I i, boolean flag) {
        if (flag) {
            // branch never taken when called from test()
            A a = (A)i;
            C c = new C();
            c.a = a;
        }
    }

    private static I test_helper() {
        B b = new B();
        return b;
    }

    interface I {

    }

    private static class A implements I {

    }
    private static class B extends A {
    }

    private static class C {
        public A a;
    }
}
