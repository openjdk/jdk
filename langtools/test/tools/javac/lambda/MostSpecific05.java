/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003280
 * @summary Add lambda tests
 *  Structural most specific doesn't handle cases with wildcards in functional interfaces
 * @compile/fail/ref=MostSpecific05.out -XDrawDiagnostics MostSpecific05.java
 */
public class MostSpecific05 {

    interface ObjectConverter<T extends Object> {
        T map(Object o);
    }

    interface NumberConverter<T extends Number> {
        T map(Object o);
    }

    static class MyMapper<A extends Object, B extends Number> {
        void map(ObjectConverter<? extends A> m) { }
        void map(NumberConverter<? extends B> m) { }
    }

    public static void main(String[] args) {
        MyMapper<Number, Double> mm = new MyMapper<Number, Double>();
        mm.map(e->1.0); //ambiguous - implicit
        mm.map((Object e)->1.0); //ok
    }
}
