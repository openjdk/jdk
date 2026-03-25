/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8367785
 * @summary Verify that compilers adhere to the new memory model rules for strict fields.
 * @library /test/lib
 * @enablePreview
 * @compile TestStrictFieldBarriers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestStrictFieldBarriers$A1
 *             compiler.valhalla.inlinetypes.TestStrictFieldBarriers$B1
 *             compiler.valhalla.inlinetypes.TestStrictFieldBarriers$C1
 *             compiler.valhalla.inlinetypes.TestStrictFieldBarriers$A2
 *             compiler.valhalla.inlinetypes.TestStrictFieldBarriers$B2
 *             compiler.valhalla.inlinetypes.TestStrictFieldBarriers$C2
 * @run main/othervm compiler.valhalla.inlinetypes.TestStrictFieldBarriers
 * @run main/othervm -Xbatch
 *                   compiler.valhalla.inlinetypes.TestStrictFieldBarriers
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,java.lang.Object::*init*
 *                   compiler.valhalla.inlinetypes.TestStrictFieldBarriers
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,StrictInitTest$A::*init*
 *                   compiler.valhalla.inlinetypes.TestStrictFieldBarriers
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,StrictInitTest$B::*init*
 *                   compiler.valhalla.inlinetypes.TestStrictFieldBarriers
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,StrictInitTest$C::*init*
 *                   compiler.valhalla.inlinetypes.TestStrictFieldBarriers
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,*::*init*
 *                   compiler.valhalla.inlinetypes.TestStrictFieldBarriers
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.helpers.StrictInit;

public class TestStrictFieldBarriers {

    static A1 sharedA1 = new A1();
    static B1 sharedB1 = new B1();
    static C1 sharedC1_1 = new C1();
    static B1 sharedC1_2 = new C1();
    static D1 sharedD1_1 = new D1();
    static E1 sharedD1_2 = new D1();

    static A2 sharedA2 = new A2();
    static B2 sharedB2 = new B2();
    static C2 sharedC2_1 = new C2();
    static B2 sharedC2_2 = new C2();
    static D2 sharedD2_1 = new D2();
    static E2 sharedD2_2 = new D2();

    static class A1 {
        @StrictInit
        final int x;

        A1() {
            x = 1;
            super();
            sharedA1 = this;
        }
    }

    static class B1 {
        @StrictInit
        final int x;

        B1() {
            x = 1;
            super();
            sharedB1 = this;
        }

        B1(boolean unused) {
            x = 1;
            super();
            sharedC1_2 = this;
        }
    }

    static class C1 extends B1 {
        @StrictInit
        final int y;

        C1() {
            y = 1;
            super(true);
            sharedC1_1 = this;
        }
    }

    static abstract value class E1 {
        final int x;

        E1() {
            x = 1;
            super();
            sharedD1_2 = this;
        }
    }

    static class D1 extends E1 {
        final int y;
        final int z;

        D1() {
            y = 2;
            super();
            z = 3;
            sharedD1_1 = this;
        }
    }

    // Non final versions

    static class A2 {
        @StrictInit
        int x;

        A2() {
            x = 1;
            super();
            sharedA2 = this;
        }
    }

    static class B2 {
        @StrictInit
        int x;

        B2() {
            x = 1;
            super();
            sharedB2 = this;
        }

        B2(boolean unused) {
            x = 1;
            super();
            sharedC2_2 = this;
        }
    }

    static class C2 extends B2 {
        @StrictInit
        int y;

        C2() {
            y = 1;
            super(true);
            sharedC2_1 = this;
        }
    }

    static abstract value class E2 {
        final int x;

        E2() {
            x = 1;
            super();
            sharedD2_2 = this;
        }
    }

    static class D2 extends E2 {
        final int y;
        final int z;

        D2() {
            y = 2;
            super();
            z = 3;
            sharedD2_1 = this;
        }
    }

    public static void main(String[] args) throws Exception {
        // Spawn two threads, a reader and a writer and check that the
        // reader thread never observes an uninitialized strict field.
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 100_000; ++i) {
                // We don't check individual fields here because the checks need to be
                // as fast a possible to increase the likelihood of a race condition.
                int res = sharedA1.x & sharedB1.x & sharedC1_1.x & sharedC1_1.y & sharedC1_2.x & ((C1)sharedC1_2).y &
                          sharedA2.x & sharedB2.x & sharedC2_1.x & sharedC2_1.y & sharedC2_2.x & ((C2)sharedC2_2).y &
                          sharedD1_1.x & ((D1)sharedD1_2).x & sharedD2_1.x & ((D2)sharedD2_2).x;
                if (res != 1) {
                    System.err.println("Incorrect field value observed!");
                    System.exit(1);
                }
            }
        });

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100_000; ++i) {
                new A1();
                new B1();
                new C1();
                new D1();
                new A2();
                new B2();
                new C2();
                new D2();
            }
        });

        reader.start();
        writer.start();
        reader.join();
        writer.join();
    }
}
