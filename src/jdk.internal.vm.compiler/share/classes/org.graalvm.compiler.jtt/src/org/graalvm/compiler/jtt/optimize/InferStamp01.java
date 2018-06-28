/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * test some stamps in combination with full loop unrolling and shifts.
 */
public class InferStamp01 extends JTTTest {

    public static int testi0(int arg) {
        int a = arg;
        for (int i = 0; i < 2; i++) {
            a = a >> 16;
        }
        return a;
    }

    @Test
    public void runi0() throws Throwable {
        runTest("testi0", 0x7788_99aa);
    }

    @Test
    public void runi0neg() throws Throwable {
        runTest("testi0", 0xf788_99aa);
    }

    public static int testi1(int arg) {
        int a = arg;
        for (int i = 0; i < 2; i++) {
            a = a >>> 16;
        }
        return a;
    }

    @Test
    public void runi1() throws Throwable {
        runTest("testi1", 0x7788_99aa);
    }

    @Test
    public void runi1neg() throws Throwable {
        runTest("testi1", 0xf788_99aa);
    }

    public static int testi2(int arg) {
        int a = arg;
        for (int i = 0; i < 2; i++) {
            a = a << 16;
        }
        return a;
    }

    @Test
    public void runi2() throws Throwable {
        runTest("testi2", 0x7788_99aa);
    }

    @Test
    public void runi2neg() throws Throwable {
        runTest("testi2", 0xf788_99aa);
    }

    public static long testl0(long arg) {
        long a = arg;
        for (long i = 0; i < 2; i++) {
            a = a >> 32;
        }
        return a;
    }

    @Test
    public void runl0() throws Throwable {
        runTest("testl0", 0x3344_5566_7788_99aaL);
    }

    @Test
    public void runl0neg() throws Throwable {
        runTest("testl0", 0xf344_5566_7788_99aaL);
    }

    public static long testl1(long arg) {
        long a = arg;
        for (long i = 0; i < 2; i++) {
            a = a >>> 32;
        }
        return a;
    }

    @Test
    public void runl1() throws Throwable {
        runTest("testl1", 0x3344_5566_7788_99aaL);
    }

    @Test
    public void runl1neg() throws Throwable {
        runTest("testl1", 0xf344_5566_7788_99aaL);
    }

    public static long testl2(long arg) {
        long a = arg;
        for (long i = 0; i < 2; i++) {
            a = a << 32;
        }
        return a;
    }

    @Test
    public void runl2() throws Throwable {
        runTest("testl2", 0x3344_5566_7788_99aaL);
    }

    @Test
    public void runl2neg() throws Throwable {
        runTest("testl2", 0xf344_5566_7788_99aaL);
    }
}
