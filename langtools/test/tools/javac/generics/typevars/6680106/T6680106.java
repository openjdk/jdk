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
 * @bug     6680106
 * @summary StackOverFlowError for Cyclic inheritance in TypeParameters with ArrayType Bounds
 * @author  Maurizio Cimadamore
 * @compile/fail/ref=T6680106.out -XDrawDiagnostics T6680106.java
 */

class T6680106 {
    class A0 {}
    class A1<T extends T[]> {}
    class A2<T extends S[], S extends T[]> {}
    class A3<T extends S[], S extends U[], U extends T[]> {}
    class A5<T extends A0 & T[]> {}
    class A6<T extends A0 & S[], S extends A0 & T[]> {}
    class A7<T extends A0 & S[], S extends A0 & U[], U extends A0 & T[]> {}
}
