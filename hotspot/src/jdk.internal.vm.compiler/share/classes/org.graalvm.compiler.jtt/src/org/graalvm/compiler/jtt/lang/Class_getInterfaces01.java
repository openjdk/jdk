/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.lang;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public final class Class_getInterfaces01 extends JTTTest {

    public static Class<?>[] test(Class<?> clazz) {
        return clazz.getInterfaces();
    }

    interface I1 {

    }

    interface I2 extends I1 {

    }

    static class C1 implements I1 {

    }

    static class C2 implements I2 {

    }

    static class C12 implements I1, I2 {

    }

    @Test
    public void run0() throws Throwable {
        runTest("test", I1.class);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", I2.class);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", C1.class);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", C2.class);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", C12.class);
    }
}
