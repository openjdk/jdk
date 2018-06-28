/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.jdk;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class IntegerBits extends JTTTest {

    @SuppressWarnings("unused") private static int init = Integer.reverseBytes(42);
    private static int original = 0x01020304;
    private static int v = 0b1000;
    private static int zero = 0;

    public static int test(int o) {
        return Integer.reverseBytes(o);
    }

    public static int test2(int o) {
        return Integer.numberOfLeadingZeros(o);
    }

    public static int test3(int o) {
        return Integer.numberOfTrailingZeros(o);
    }

    public static int test4(int o) {
        return Integer.bitCount(o);
    }

    @Test
    public void run0() {
        runTest("test", original);
    }

    @Test
    public void run1() {
        runTest("test3", v);
    }

    @Test
    public void run2() {
        runTest("test2", v);
    }

    @Test
    public void run3() {
        runTest("test3", zero);
    }

    @Test
    public void run4() {
        runTest("test2", zero);
    }

    @Test
    public void run5() {
        runTest("test", 0x01020304);
    }

    @Test
    public void run6() {
        runTest("test3", 0b1000);
    }

    @Test
    public void run7() {
        runTest("test2", 0b1000);
    }

    @Test
    public void run8() {
        runTest("test3", 0);
    }

    @Test
    public void run9() {
        runTest("test2", 0);
    }

    @Test
    public void run10() {
        runTest("test4", 0xffffffff);
    }

    @Test
    public void run11() {
        runTest("test2", 0xFFFFFFFF);
    }

    @Test
    public void run12() {
        runTest("test2", 0x7FFFFFFF);
    }

    @Test
    public void run17() {
        runTest("test2", 0x80000000);
    }

    @Test
    public void run18() {
        runTest("test2", 0x40000000);
    }

    @Test
    public void run13() {
        runTest("test3", 0x7FFFFFFF);
    }

    @Test
    public void run14() {
        runTest("test3", 0xFFFFFFFF);
    }

    @Test
    public void run15() {
        runTest("test3", 0x80000000);
    }

    @Test
    public void run16() {
        runTest("test3", 0x40000000);
    }

    @Test
    public void run19() {
        runTest("test4", 0x80000000);
    }

    @Test
    public void run20() {
        runTest("test4", 0x40000000);
    }

    @Test
    public void run21() {
        runTest("test4", 0x00000001);
    }

    public static int test5(int value) {
        return Integer.reverseBytes((12345 | (value & 0xffff0000))) >>> 16;
    }

    @Test
    public void run22() {
        runTest("test5", 1);
    }
}
