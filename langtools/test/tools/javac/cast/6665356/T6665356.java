/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @author Maurizio Cimadamore
 * @bug     6665356
 * @summary Cast not allowed when both qualifying type and inner class are parameterized
 * @compile/fail/ref=T6665356.out -XDrawDiagnostics T6665356.java
 */

class T6665356 {
    class Outer<S> {
        class Inner<T> {}
    }

    void cast1(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<Integer>.Inner<Long>)p;
    }

    void cast2(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<? extends Number>.Inner<Long>)p;
    }

    void cast3(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<Integer>.Inner<? extends Number>)p;
    }

    void cast4(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<? extends Number>.Inner<? extends Number>)p;
    }

    void cast5(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<? super Number>.Inner<Long>)p;
    }

    void cast6(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<Integer>.Inner<? super Number>)p;
    }

    void cast7(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<? super Number>.Inner<? super Number>)p;
    }

    void cast8(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<? extends String>.Inner<Long>)p;
    }

    void cast9(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<Integer>.Inner<? extends String>)p;
    }

    void cast10(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<? super String>.Inner<Long>)p;
    }

    void cast11(Outer<Integer>.Inner<Long> p) {
        Object o = (Outer<Integer>.Inner<? super String>)p;
    }
}