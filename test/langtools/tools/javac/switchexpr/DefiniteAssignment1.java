/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8214031
 * @summary Verify that definite assignment when true works (legal code)
 * @compile --enable-preview --source 12 DefiniteAssignment1.java
 * @run main/othervm --enable-preview DefiniteAssignment1
 */
public class DefiniteAssignment1 {
    public static void main(String[] args) {
        int a = 0;
        boolean b = true;

        {
        int x;

        boolean t1 = (b && switch(a) {
            case 0: break (x = 1) == 1 || true;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t1) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t1 = (b && switch(a) {
            case 0: break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t1) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t1a = (b && switch(a) {
            case 0: break (x = 1) == 1;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t1a) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t1b = (switch(a) {
            case 0: break (x = 1) == 1;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t1b) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t2 = !(b && switch(a) {
            case 0: break (x = 1) == 1 || true;
            default: break false;
        }) || x == 1;

        if (!t2) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t2 = !(b && switch(a) {
            case 0: break (x = 1) == 1 || isTrue();
            default: break false;
        }) || x == 1;

        if (!t2) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t3 = !(switch(a) {
            case 0: break (x = 1) == 1 || true;
            default: break false;
        }) || x == 2;

        if (t3) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t3 = !(switch(a) {
            case 0: break (x = 1) == 1 || isTrue();
            default: break false;
        }) || x == 2;

        if (t3) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        boolean t4 = (b && switch(a) {
            case 0: break (x = 1) == 1 || true;
            default: throw new IllegalStateException();
        }) && x == 1; //x is definitelly assigned here

        if (!t4) {
            throw new IllegalStateException("Unexpected result.");
        }
        }


        {
        int x;

        boolean t4 = (b && switch(a) {
            case 0: break (x = 1) == 1 || isTrue();
            default: throw new IllegalStateException();
        }) && x == 1; //x is definitelly assigned here

        if (!t4) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        String s = "a";

        boolean t5 = (switch(s) {
            case "a": break (x = 1) == 1 || true;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t5) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        String s = "a";

        boolean t5 = (switch(s) {
            case "a": break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t5) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.B;

        boolean t6 = (switch(e) {
            case B: break (x = 1) == 1 || true;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t6) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.B;

        boolean t6 = (switch(e) {
            case B: break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t6) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        int t7 = new DefiniteAssignment1().id(switch(0) {
            default -> true;
        } && (x = 1) == 1 && x == 1 ? 2 : -1);

        if (t7 != 2) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;

        int t7 = new DefiniteAssignment1().id(switch(0) {
            default -> isTrue();
        } && (x = 1) == 1 && x == 1 ? 2 : -1);

        if (t7 != 2) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.B;

        boolean t8 = (switch(e) {
            case A: x = 1; break true;
            case B: break (x = 1) == 1 || true;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t8) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.B;

        boolean t8 = (switch(e) {
            case A: x = 1; break isTrue();
            case B: break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t8) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.A;

        boolean t9 = (switch(e) {
            case A: x = 1; break true;
            case B: break (x = 1) == 1 || true;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t9) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.A;

        boolean t9 = (switch(e) {
            case A: x = 1; break isTrue();
            case B: break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!t9) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.C;

        boolean tA = (switch(e) {
            case A: x = 1; break true;
            case B: break (x = 1) == 1 || true;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (tA) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.C;

        boolean tA = (switch(e) {
            case A: x = 1; break isTrue();
            case B: break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (tA) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        final int x;
        E e = E.C;

        boolean tA = (switch(e) {
            case A: x = 1; break true;
            case B: break (x = 2) == 2 || true;
            default: break false;
        }) || (x = 3) == 3; //x is definitelly unassigned here

        if (x != 3) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.A;

        boolean tA = (switch(e) {
            case A: break isTrue() && (x = 1) == 1;
            case B: break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!tA) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.A;

        boolean tA = (switch(e) {
            case A: break isTrue() && e != E.C ? (x = 1) == 1 && e != E.B : false;
            case B: break (x = 1) == 1 || true;
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!tA) {
            throw new IllegalStateException("Unexpected result.");
        }
        }

        {
        int x;
        E e = E.A;

        boolean tA = (switch(e) {
            case A: break isTrue() && e != E.C ? (x = 1) == 1 && e != E.B : false;
            case B: break (x = 1) == 1 || isTrue();
            default: break false;
        }) && x == 1; //x is definitelly assigned here

        if (!tA) {
            throw new IllegalStateException("Unexpected result.");
        }
        }
    }

    private int id(int v) {
        return v;
    }

    private static boolean isTrue() {
        return true;
    }

    enum E {
        A, B, C;
    }
}
