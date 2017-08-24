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
package org.graalvm.compiler.jtt.bytecode;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class BC_lshr02 extends JTTTest {

    public static long test0(long arg) {
        long a = arg >> 32;
        return a >> 32;
    }

    @Test
    public void run0a() throws Throwable {
        runTest("test0", 1L);
    }

    @Test
    public void run0b() throws Throwable {
        runTest("test0", 0L);
    }

    @Test
    public void run0c() throws Throwable {
        runTest("test0", -1L);
    }

    @Test
    public void run0d() throws Throwable {
        runTest("test0", Long.MAX_VALUE);
    }

    @Test
    public void run0e() throws Throwable {
        runTest("test0", Long.MIN_VALUE);
    }

    /* testcase for a postive stamp */
    public static int test1(long[] arg) {
        int a = arg.length >> 16;
        return a >> 16;
    }

    @Test
    public void run1a() throws Throwable {
        long[] arg = new long[0x100];
        runTest("test1", arg);
    }

    /* testcase for a strictly negative stamp */
    public static int test2(long[] arg) {
        int a = (-arg.length - 1) >> 16;
        return a >> 16;
    }

    @Test
    public void run2a() throws Throwable {
        long[] arg = new long[0x100];
        runTest("test2", arg);
    }
}
