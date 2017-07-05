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

public class LongBits extends JTTTest {

    @SuppressWarnings("unused") private static long init = Long.reverseBytes(42);
    private static long original = 0x0102030405060708L;
    private static long v = 0b1000L;
    private static long v2 = 0x0100000000L;
    private static long zero = 0L;

    public static long test(long o) {
        return Long.reverseBytes(o);
    }

    public static int test2(long o) {
        return Long.numberOfLeadingZeros(o);
    }

    public static int test3(long o) {
        return Long.numberOfTrailingZeros(o);
    }

    public static int test4(long o) {
        return Long.bitCount(o);
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
        runTest("test", 0x0102030405060708L);
    }

    @Test
    public void run6() {
        runTest("test3", 0b1000L);
    }

    @Test
    public void run7() {
        runTest("test2", 0b1000L);
    }

    @Test
    public void run8() {
        runTest("test3", 0L);
    }

    @Test
    public void run9() {
        runTest("test2", 0L);
    }

    @Test
    public void run10() {
        runTest("test2", v2);
    }

    @Test
    public void run11() {
        runTest("test3", v2);
    }

    @Test
    public void run12() {
        runTest("test2", 0x0100000000L);
    }

    @Test
    public void run13() {
        runTest("test3", 0x0100000000L);
    }

    @Test
    public void run14() {
        runTest("test4", 0L);
        runTest("test4", 1L);
        runTest("test4", 0xffff00ffL);
        runTest("test4", 0xffffffffL);
        runTest("test4", 0x3ffffffffL);
        runTest("test4", 0xffffffff3L);
        runTest("test4", 0xffffffffffffffffL);
    }
}
