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

/*
 * @test
 * @bug 6711619
 *
 * @summary javac doesn't allow access to protected members in intersection types
 * @author Maurizio Cimadamore
 *
 * @compile/fail/ref=T6711619b.out -XDrawDiagnostics T6711619b.java
 */

class T6711619b {
    static class X1<E extends X1<E>> {
         private int i;
         E e;
         int f() {
             return e.i;
         }
    }

    static class X2<E extends X2<E>> {
         static private int i;
         int f() {
             return E.i;
         }
    }

    static class X3<E extends X3<E> & java.io.Serializable> {
         private int i;
         E e;
         int f() {
             return e.i;
         }
    }

    static class X4<E extends X4<E> & java.io.Serializable> {
         static private int i;
         int f() {
             return E.i;
         }
    }
}