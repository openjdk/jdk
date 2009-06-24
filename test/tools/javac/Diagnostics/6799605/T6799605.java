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

/**
 * @test
 * @bug     6799605
 * @summary Basic/Raw formatters should use type/symbol printer instead of toString()
 * @author  mcimadamore
 * @compile/fail/ref=T6799605.out -XDrawDiagnostics  T6799605.java
 * @compile/fail/ref=T6799605.out -XDoldDiags -XDrawDiagnostics  T6799605.java
 */

class T6799605<X> {

    <T extends T6799605<T>> void m(T6799605<T> x1) {}
    <T> void m(T6799605<T> x1, T6799605<T> x2) {}
    <T> void m(T6799605<T> x1, T6799605<T> x2, T6799605<T> x3) {}

    void test(T6799605<?> t) {
        m(t);
        m(t, t);
        m(t, t, t);
    }
}
