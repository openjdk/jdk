/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

/**
 * @test
 * @bug 6747671
 * @summary -Xlint:rawtypes
 * @compile/ref=T6747671.out -XDrawDiagnostics -Xlint:rawtypes T6747671.java
 */


class T6747671<E> {

    static class B<X> {}

    class A<X> {
        class X {}
        class Z<Y> {}
    }


    A.X x1;//raw warning
    A.Z z1;//raw warning

    T6747671.B<Integer> b1;//ok
    T6747671.B b2;//raw warning

    A<String>.X x2;//ok
    A<String>.Z<Integer> z2;//ok
    A<B>.Z<A<B>> z3;//raw warning (2)

    void test(Object arg1, B arg2) {//raw warning
        boolean b = arg1 instanceof A;//raw warning
        Object a = (A)arg1;//raw warning
        A a2 = new A() {};//raw warning (2)
        a2.new Z() {};//raw warning
    }
}