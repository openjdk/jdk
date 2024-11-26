/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8340214
 * @summary C2 compilation asserts with "no node with a side effect" in PhaseIdealLoop::try_sink_out_of_loop
 *
 * @run main/othervm -XX:-BackgroundCompilation TestBadMemSliceWithInterfaces
 *
 */

public class TestBadMemSliceWithInterfaces {
    public static void main(String[] args) {
        B b = new B();
        C c = new C();
        for (int i = 0; i < 20_000; i++) {
            test1(b, c, true);
            test1(b, c, false);
            b.field = 0;
            c.field = 0;
            int res = test2(b, c, true);
            if (res != 42) {
                throw new RuntimeException("incorrect result " + res);
            }
            res = test2(b, c, false);
            if (res != 42) {
                throw new RuntimeException("incorrect result " + res);
            }
        }
    }

    private static void test1(B b, C c, boolean flag) {
        A a;
        if (flag) {
            a = b;
        } else {
            a = c;
        }
        for (int i = 0; i < 1000; i++) {
            a.field = 42;
        }
    }

    private static int test2(B b, C c, boolean flag) {
        A a;
        if (flag) {
            a = b;
        } else {
            a = c;
        }
        int v = 0;
        for (int i = 0; i < 2; i++) {
            v += a.field;
            a.field = 42;
        }
        return v;
    }

    interface I {
        void m();
    }

    static class A {
        int field;
    }

    static class B extends A implements I {
        @Override
        public void m() {

        }
    }

    static class C extends A implements I {
        @Override
        public void m() {

        }
    }
}
