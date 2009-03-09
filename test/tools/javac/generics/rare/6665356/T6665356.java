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

    void test1() {
        boolean b;
        b = null instanceof Outer.Inner;
        b = null instanceof Outer<?>.Inner;
        b = null instanceof Outer.Inner<?>;
        b = null instanceof Outer<?>.Inner<?>;
    }

    void test2() {
        boolean b;
        Object o;
        o = (Outer.Inner)null;
        o = (Outer<?>.Inner)null;
        o = (Outer.Inner<?>)null;
        o = (Outer<?>.Inner<?>)null;
    }
}