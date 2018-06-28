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
 * Tests optimization of integer operations.
 */
public class Reduce_LongShift01 extends JTTTest {

    public static long test(long arg) {
        if (arg == 0) {
            return shift0(arg + 10);
        }
        if (arg == 1) {
            return shift1(arg + 10);
        }
        if (arg == 2) {
            return shift2(arg + 10);
        }
        if (arg == 3) {
            return shift3(arg + 10);
        }
        if (arg == 4) {
            return shift4(arg + 10);
        }
        if (arg == 5) {
            return shift5(arg + 10);
        }
        return 0;
    }

    public static long shift0(long x) {
        return x >> 0;
    }

    public static long shift1(long x) {
        return x >>> 0;
    }

    public static long shift2(long x) {
        return x << 0;
    }

    public static long shift3(long x) {
        return x >> 64;
    }

    public static long shift4(long x) {
        return x >>> 64;
    }

    public static long shift5(long x) {
        return x << 64;
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

    @Test
    public void run4() throws Throwable {
        runTest("test", 4L);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5L);
    }

}
