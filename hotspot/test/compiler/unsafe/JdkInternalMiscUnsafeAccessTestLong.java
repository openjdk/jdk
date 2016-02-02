/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8143628
 * @summary Test unsafe access for long
 * @modules java.base/jdk.internal.misc
 * @run testng/othervm -Diters=100   -Xint                   JdkInternalMiscUnsafeAccessTestLong
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 JdkInternalMiscUnsafeAccessTestLong
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  JdkInternalMiscUnsafeAccessTestLong
 * @run testng/othervm -Diters=20000                         JdkInternalMiscUnsafeAccessTestLong
 */

import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.testng.Assert.*;

public class JdkInternalMiscUnsafeAccessTestLong {
    static final int ITERS = Integer.getInteger("iters", 1);

    static final jdk.internal.misc.Unsafe UNSAFE;

    static final long V_OFFSET;

    static final Object STATIC_V_BASE;

    static final long STATIC_V_OFFSET;

    static int ARRAY_OFFSET;

    static int ARRAY_SHIFT;

    static {
        try {
            Field f = jdk.internal.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (jdk.internal.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get Unsafe instance.", e);
        }

        try {
            Field staticVField = JdkInternalMiscUnsafeAccessTestLong.class.getDeclaredField("static_v");
            STATIC_V_BASE = UNSAFE.staticFieldBase(staticVField);
            STATIC_V_OFFSET = UNSAFE.staticFieldOffset(staticVField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Field vField = JdkInternalMiscUnsafeAccessTestLong.class.getDeclaredField("v");
            V_OFFSET = UNSAFE.objectFieldOffset(vField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ARRAY_OFFSET = UNSAFE.arrayBaseOffset(long[].class);
        int ascale = UNSAFE.arrayIndexScale(long[].class);
        ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(ascale);
    }

    static long static_v;

    long v;

    @Test
    public void testFieldInstance() {
        JdkInternalMiscUnsafeAccessTestLong t = new JdkInternalMiscUnsafeAccessTestLong();
        for (int c = 0; c < ITERS; c++) {
            testAccess(t, V_OFFSET);
        }
    }

    @Test
    public void testFieldStatic() {
        for (int c = 0; c < ITERS; c++) {
            testAccess(STATIC_V_BASE, STATIC_V_OFFSET);
        }
    }

    @Test
    public void testArray() {
        long[] array = new long[10];
        for (int c = 0; c < ITERS; c++) {
            for (int i = 0; i < array.length; i++) {
                testAccess(array, (((long) i) << ARRAY_SHIFT) + ARRAY_OFFSET);
            }
        }
    }

    @Test
    public void testArrayOffHeap() {
        int size = 10;
        long address = UNSAFE.allocateMemory(size << ARRAY_SHIFT);
        try {
            for (int c = 0; c < ITERS; c++) {
                for (int i = 0; i < size; i++) {
                    testAccess(null, (((long) i) << ARRAY_SHIFT) + address);
                }
            }
        } finally {
            UNSAFE.freeMemory(address);
        }
    }

    @Test
    public void testArrayOffHeapDirect() {
        int size = 10;
        long address = UNSAFE.allocateMemory(size << ARRAY_SHIFT);
        try {
            for (int c = 0; c < ITERS; c++) {
                for (int i = 0; i < size; i++) {
                    testAccess((((long) i) << ARRAY_SHIFT) + address);
                }
            }
        } finally {
            UNSAFE.freeMemory(address);
        }
    }

    static void testAccess(Object base, long offset) {
        // Plain
        {
            UNSAFE.putLong(base, offset, 1L);
            long x = UNSAFE.getLong(base, offset);
            assertEquals(x, 1L, "set long value");
        }

        // Volatile
        {
            UNSAFE.putLongVolatile(base, offset, 2L);
            long x = UNSAFE.getLongVolatile(base, offset);
            assertEquals(x, 2L, "putVolatile long value");
        }

        // Lazy
        {
            UNSAFE.putOrderedLong(base, offset, 1L);
            long x = UNSAFE.getLongVolatile(base, offset);
            assertEquals(x, 1L, "putRelease long value");
        }

        // Unaligned
        {
            UNSAFE.putLongUnaligned(base, offset, 2L);
            long x = UNSAFE.getLongUnaligned(base, offset);
            assertEquals(x, 2L, "putUnaligned long value");
        }

        {
            UNSAFE.putLongUnaligned(base, offset, 1L, true);
            long x = UNSAFE.getLongUnaligned(base, offset, true);
            assertEquals(x, 1L, "putUnaligned big endian long value");
        }

        {
            UNSAFE.putLongUnaligned(base, offset, 2L, false);
            long x = UNSAFE.getLongUnaligned(base, offset, false);
            assertEquals(x, 2L, "putUnaligned little endian long value");
        }

        UNSAFE.putLong(base, offset, 1L);

        // Compare
        {
            boolean r = UNSAFE.compareAndSwapLong(base, offset, 1L, 2L);
            assertEquals(r, true, "success compareAndSwap long");
            long x = UNSAFE.getLong(base, offset);
            assertEquals(x, 2L, "success compareAndSwap long value");
        }

        {
            boolean r = UNSAFE.compareAndSwapLong(base, offset, 1L, 3L);
            assertEquals(r, false, "failing compareAndSwap long");
            long x = UNSAFE.getLong(base, offset);
            assertEquals(x, 2L, "failing compareAndSwap long value");
        }

        // Compare set and get
        {
            long o = UNSAFE.getAndSetLong(base, offset, 1L);
            assertEquals(o, 2L, "getAndSet long");
            long x = UNSAFE.getLong(base, offset);
            assertEquals(x, 1L, "getAndSet long value");
        }

        UNSAFE.putLong(base, offset, 1L);

        // get and add, add and get
        {
            long o = UNSAFE.getAndAddLong(base, offset, 2L);
            assertEquals(o, 1L, "getAndAdd long");
            long x = UNSAFE.getLong(base, offset);
            assertEquals(x, 1L + 2L, "weakCompareAndSwapRelease long");
        }
    }

    static void testAccess(long address) {
        // Plain
        {
            UNSAFE.putLong(address, 1L);
            long x = UNSAFE.getLong(address);
            assertEquals(x, 1L, "set long value");
        }
    }
}
