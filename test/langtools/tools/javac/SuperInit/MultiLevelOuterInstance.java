/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8334121
 * @summary Anonymous class capturing two enclosing instances fails to compile
 */

public class MultiLevelOuterInstance {

    interface A {
        void run();
    }
    interface B {
        void run();
    }

    class Inner1 {
        Inner1() {
            this(new A() {
                class Inner2 {
                    Inner2() {
                        this(new B() {
                            public void run() {
                                m();
                                g();
                            }
                        });
                    }

                    Inner2(B o) {
                        o.run();
                    }
                }

                public void run() {
                    new Inner2();
                }

                void m() { }
            });
        }

        Inner1(A o) { }
    }
    void g() { }

    public static void main(String[] args) {
        new MultiLevelOuterInstance().new Inner1();
    }
}
