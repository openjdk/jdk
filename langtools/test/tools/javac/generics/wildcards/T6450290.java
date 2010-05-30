/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6450290
 * @summary Capture of nested wildcards causes type error
 * @author Maurizio Cimadamore
 * @compile/fail T6450290.java
 */

public class T6450290 {
    static class Box<X extends Box<?,?>, T extends X> {
        T value;
        Box<X, T> same;
    }

    static class A extends Box<A,A> {}
    static class B extends Box<B,B> {}
    public static void main(String[] args) {
        Box<?,?> b = new Box<Box<A,A>,Box<A,A>>();
        b.value.same = new Box<B,B>(); //javac misses this bad assignment
    }
}
