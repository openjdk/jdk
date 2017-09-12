/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7024568
 * @summary Very long method resolution causing OOM error
 * @compile/fail/ref=T7024568.out -XDrawDiagnostics T7024568.java
 */

class Main {
    void test(Obj o) {
        o.test(0, 0, 0, 0, 0, 0, 0, 0, undefined);
    }
}

interface Test {
    public void test(int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, String str);
    public void test(int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, long l);
}

interface Obj extends Test, A, B, C, D, E {}
interface A extends Test {}
interface B extends A, Test {}
interface C extends A, B, Test {}
interface D extends A, B, C, Test {}
interface E extends A, B, C, D, Test {}
