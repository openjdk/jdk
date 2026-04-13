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

package compiler.c2.igvn;

/*
 * @test
 * @bug JDK-8375645
 * @summary TODO
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,${test.main.class}::test
 *      ${test.main.class}
 * @run main ${test.main.class}
 *
 */

public class TestDuplicatePhiNpe {
    public static void main(String[] args) {
        for (int i = 0; i < 200; i++) {
            test(i%2==0);
        }
    }

    private static void test(boolean flag) {
        A a = null;
        if (flag) {
            C c = new C();
            B b = new B();
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 8; j++) {
                    switch (i) {
                        case -1, -2, -3 :
                            break;
                        case 0 :
                            b.c = c;
                    }
                }
            }
            b.c = c;
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 8; j++) {
                    switch (i) {
                        case -1, -2, -3 :
                            break;
                        case 0 :
                            a = new A();
                    }
                }
            }
            for (int i = 0; i < 32; i++) {
                if (i < 10) {
                    a = new A();
                }
            }
            a = new A();
            for (int i = 1; i < 32; i++) {
                a = new A();
            }
            for (int i = 1; i < 32; i++) {
                a = new A();
            }
            for (int i = 1; i < 32; i++) {
                a = new A();
            }

            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 8; j++) {
                    switch (i) {
                        case -1, -2, -3 :
                            break;
                        case 0 :
                            a.b = b;
                    }
                }
            }
            a.b = b;
        }

        if (a != null) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 8; j++) {
                    switch (i) {
                        case -1, -2, -3 :
                            break;
                        case 0 :
                            a.b.c.f = java.lang.Integer.valueOf(0x42);
                    }
                }
            }

            a.b.c.f = java.lang.Integer.valueOf(0x42); // should not throw a NPE
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