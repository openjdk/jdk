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


package org.graalvm.compiler.jtt.optimize;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests optimization of integer operations.
 */
public class Reduce_IntShift02 extends JTTTest {

    public static int test(int arg) {
        if (arg == 0) {
            return shift0(arg + 80);
        }
        if (arg == 1) {
            return shift1(arg + 0x8000000a);
        }
        if (arg == 2) {
            return shift2(arg + 192);
        }
        if (arg == 3) {
            return shift3(arg + 208);
        }
        if (arg == 4) {
            return shift4(arg);
        }
        if (arg == 5) {
            return shift5(arg);
        }
        return 0;
    }

    public static int shift0(int x) {
        return x >>> 3 << 3;
    }

    public static int shift1(int x) {
        return x << 3 >>> 3;
    }

    public static int shift2(int x) {
        return x >> 3 >> 1;
    }

    public static int shift3(int x) {
        return x >>> 3 >>> 1;
    }

    public static int shift4(int x) {
        return x << 3 << 1;
    }

    public static int shift5(int x) {
        return x << 16 << 17;
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

    @Test
    public void run5() throws Throwable {
        runTest("test", 5);
    }

}
