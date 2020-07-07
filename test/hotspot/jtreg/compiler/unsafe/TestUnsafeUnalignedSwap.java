/*
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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

/* @test
 * @library / /test/lib
 * @bug 8246051
 * @summary A test for SIGBUS in aarch64 by unalgined unsafe access
 * @requires os.arch=="aarch64"
 * @run main/othervm/timeout=200 -XX:-Inline TestUnsafeUnalignedSwap
 */

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.*;
import jdk.test.lib.Asserts;

public class TestUnsafeUnalignedSwap {
    private final static Unsafe U;
    private static long sum = 4;
    static volatile long[] arrayLong = new long[1001];
    static volatile int[] arrayInt = new int[1001];
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }
    // Bug 8246051 : Unsafe.compareAndSwapLong should not crash
    public static void testCompareAndSwapLong() {
        try {
            if (U.compareAndSwapLong(arrayLong, Unsafe.ARRAY_LONG_BASE_OFFSET + 1, 3243, 2334)) {
                sum++;
            } else {
                sum--;
            }
        } catch (InternalError e) {
            System.out.println(e.getMessage());
        }
    }
    public static void testCompareAndSwapInt() {
        try {
            if (U.compareAndSwapInt(arrayInt, Unsafe.ARRAY_INT_BASE_OFFSET + 1, 3243, 2334)) {
                sum++;
            } else {
                sum--;
            }
        } catch (InternalError e) {
            System.out.println(e.getMessage());
        }
    }
    public static void test() {
        testCompareAndSwapLong();
        testCompareAndSwapInt();
    }
    public static void main(String[] args) {
        test();
    }
}
