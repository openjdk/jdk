/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/**
 * @test
 * @bug 6769124
 * @summary int value might not be correctly decoded on deopt with c1 on 64 bit
 *
 * @run main/othervm -Xcomp -XX:CompileOnly=TestDeoptInt6769124.m TestDeoptInt6769124
 */

public class TestDeoptInt6769124 {

    static class A {
        volatile int vl;
        A(int v) {
            vl = v;
        }
    }

    static void m(int b) {
        A a = new A(10);
        int c;
        c = b + a.vl; //accessing volatile field of class not loaded at compile time forces a deopt
        if(c != 20) {
            System.out.println("a (= " + a.vl + ") + b (= " + b + ") = c (= " + c + ") != 20");
            throw new InternalError();
        }
    }

    public static void main(String[] args) {
        m(10);
    }

}
