/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Invoke_except01 extends JTTTest {

    public static class TestClass {
        public static int method(int[] arg) {
            return arg.length;
        }
    }

    public static int test(int arg) throws IllegalAccessException, InvocationTargetException {
        Object[] args;
        if (arg == 0) {
            args = new Object[]{new int[0]};
        } else if (arg == 1) {
            args = new Object[]{new int[3]};
        } else if (arg == 2) {
            args = new Object[]{null};
        } else if (arg == 3) {
            args = new Object[]{new char[3]};
        } else {
            args = null;
        }
        for (Method m : TestClass.class.getDeclaredMethods()) {
            if ("method".equals(m.getName())) {
                return (Integer) m.invoke(null, args);
            }
        }
        return 42;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4);
    }

}
