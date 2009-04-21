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
 */

/*
 * @test
 * @author mcimadamore
 * @bug     6467183
 * @summary
 * @compile/fail/ref=T6467183a.out -Xlint:unchecked -Werror -XDrawDiagnostics T6467183a.java
 */

class T6467183a<T> {

    class A<S> {}
    class B extends A<Integer> {}
    class C<X> extends A<X> {}

    void cast1(B b) {
        Object o = (A<T>)b;
    }

    void cast2(B b) {
        Object o = (A<? extends Number>)b;
    }

    void cast3(A<Integer> a) {
        Object o = (C<? extends Number>)a;
    }

    void cast4(A<Integer> a) {
        Object o = (C<? extends Integer>)a;
    }
}
