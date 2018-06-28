/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.jtt.micro;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class InvokeVirtual_02 extends JTTTest {

    static class A {

        long plus(long a) {
            return a;
        }
    }

    static class B extends A {

        @Override
        long plus(long a) {
            return a + 10;
        }
    }

    static class C extends A {

        @Override
        long plus(long a) {
            return a + 20;
        }
    }

    static A objectA = new A();
    static A objectB = new B();
    static A objectC = new C();

    public static long test(long a) {
        if (a == 0) {
            return objectA.plus(a);
        }
        if (a == 1) {
            return objectB.plus(a);
        }
        if (a == 2) {
            return objectC.plus(a);
        }
        return 42;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0L);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1L);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2L);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3L);
    }

}
