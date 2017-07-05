/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates. All rights reserved.
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

import sun.misc.Unsafe;

import org.graalvm.compiler.jtt.JTTTest;

public class Unsafe_compareAndSwapNullCheck extends JTTTest {

    static final Unsafe unsafe = UnsafeAccess01.getUnsafe();
    static final long valueOffset;
    static {
        try {
            valueOffset = unsafe.objectFieldOffset(Unsafe_compareAndSwap.class.getDeclaredField("value"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    long value;
    long lng;

    public static void test(Unsafe_compareAndSwapNullCheck u, long expected, long newValue) {
        @SuppressWarnings("unused")
        long l = u.lng;
        unsafe.compareAndSwapLong(u, valueOffset, expected, newValue);
    }

    @Test
    public void run0() throws Throwable {
        runTest(EMPTY, false, true, "test", null, 1L, 2L);
    }
}
