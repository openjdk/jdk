/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests optimization integer conversions.
 */
public class VN_Convert01 extends JTTTest {

    public static int test(int arg) {
        if (arg == 0) {
            return i2b(arg + 10);
        }
        if (arg == 1) {
            return i2s(arg + 10);
        }
        if (arg == 2) {
            return i2c(arg + 10);
        }
        return 0;
    }

    public static int i2b(int arg) {
        int x = (byte) arg;
        int y = (byte) arg;
        return x + y;
    }

    public static int i2s(int arg) {
        int x = (short) arg;
        int y = (short) arg;
        return x + y;
    }

    public static int i2c(int arg) {
        int x = (char) arg;
        int y = (char) arg;
        return x + y;
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

}
