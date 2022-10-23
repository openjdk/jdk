/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test unsafe access for int
 *
 * @modules java.base/jdk.internal.misc:+open
 * @run testng/othervm -Diters=100   -Xint                   compiler.unsafe.JdkInternalMiscUnsafeAccessTestInt
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 compiler.unsafe.JdkInternalMiscUnsafeAccessTestInt
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  compiler.unsafe.JdkInternalMiscUnsafeAccessTestInt
 * @run testng/othervm -Diters=20000                         compiler.unsafe.JdkInternalMiscUnsafeAccessTestInt
 */

package compiler.unsafe;

import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.testng.Assert.*;

public class JdkInternalMiscUnsafeAccessTestInt {
    static final int ITERS = Integer.getInteger("iters", 1);

    // More resilience for Weak* tests. These operations may spuriously
    // fail, and so we do several attempts with delay on failure.
    // Be mindful of worst-case total time on test, which would be at
    // roughly (delay*attempts) milliseconds.
    //
    static final int WEAK_ATTEMPTS = Integer.getInteger("weakAttempts", 100);
    static final int WEAK_DELAY_MS = Math.max(1, Integer.getInteger("weakDelay", 1));

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
            Field staticVField = JdkInternalMiscUnsafeAccessTestInt.class.getDeclaredField("static_v");
            STATIC_V_BASE = UNSAFE.staticFieldBase(staticVField);
            STATIC_V_OFFSET = UNSAFE.staticFieldOffset(staticVField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Field vField = JdkInternalMiscUnsafeAccessTestInt.class.getDeclaredField("v");
            V_OFFSET = UNSAFE.objectFieldOffset(vField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ARRAY_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
        int ascale = UNSAFE.arrayIndexScale(int[].class);
        ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(ascale);
    }

    static void weakDelay() {
        try {
            if (WEAK_DELAY_MS > 0) {
                Thread.sleep(WEAK_DELAY_MS);
            }
        } catch (InterruptedException ie) {
            // Do nothing.
        }
    }

    static int static_v;

    int v;

    @Test
    public void testFieldInstance() {
        JdkInternalMiscUnsafeAccessTestInt t = new JdkInternalMiscUnsafeAccessTestInt();
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
        int[] array = new int[10];
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
            UNSAFE.putInt(base, offset, 0x01234567);
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "set int value");
        }

        // Volatile
        {
            UNSAFE.putIntVolatile(base, offset, 0x89ABCDEF);
            int x = UNSAFE.getIntVolatile(base, offset);
            assertEquals(x, 0x89ABCDEF, "putVolatile int value");
        }


        // Lazy
        {
            UNSAFE.putIntRelease(base, offset, 0x01234567);
            int x = UNSAFE.getIntAcquire(base, offset);
            assertEquals(x, 0x01234567, "putRelease int value");
        }

        // Opaque
        {
            UNSAFE.putIntOpaque(base, offset, 0x89ABCDEF);
            int x = UNSAFE.getIntOpaque(base, offset);
            assertEquals(x, 0x89ABCDEF, "putOpaque int value");
        }

        // Unaligned
        {
            UNSAFE.putIntUnaligned(base, offset, 0x89ABCDEF);
            int x = UNSAFE.getIntUnaligned(base, offset);
            assertEquals(x, 0x89ABCDEF, "putUnaligned int value");
        }

        {
            UNSAFE.putIntUnaligned(base, offset, 0x01234567, true);
            int x = UNSAFE.getIntUnaligned(base, offset, true);
            assertEquals(x, 0x01234567, "putUnaligned big endian int value");
        }

        {
            UNSAFE.putIntUnaligned(base, offset, 0x89ABCDEF, false);
            int x = UNSAFE.getIntUnaligned(base, offset, false);
            assertEquals(x, 0x89ABCDEF, "putUnaligned little endian int value");
        }

        UNSAFE.putInt(base, offset, 0x01234567);

        // Compare
        {
            boolean r = UNSAFE.compareAndSetInt(base, offset, 0x01234567, 0x89ABCDEF);
            assertEquals(r, true, "success compareAndSet int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "success compareAndSet int value");
        }

        {
            boolean r = UNSAFE.compareAndSetInt(base, offset, 0x01234567, 0xCAFEBABE);
            assertEquals(r, false, "failing compareAndSet int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "failing compareAndSet int value");
        }

        // Advanced compare
        {
            int r = UNSAFE.compareAndExchangeInt(base, offset, 0x89ABCDEF, 0x01234567);
            assertEquals(r, 0x89ABCDEF, "success compareAndExchange int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "success compareAndExchange int value");
        }

        {
            int r = UNSAFE.compareAndExchangeInt(base, offset, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(r, 0x01234567, "failing compareAndExchange int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "failing compareAndExchange int value");
        }

        {
            int r = UNSAFE.compareAndExchangeIntAcquire(base, offset, 0x01234567, 0x89ABCDEF);
            assertEquals(r, 0x01234567, "success compareAndExchangeAcquire int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "success compareAndExchangeAcquire int value");
        }

        {
            int r = UNSAFE.compareAndExchangeIntAcquire(base, offset, 0x01234567, 0xCAFEBABE);
            assertEquals(r, 0x89ABCDEF, "failing compareAndExchangeAcquire int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "failing compareAndExchangeAcquire int value");
        }

        {
            int r = UNSAFE.compareAndExchangeIntRelease(base, offset, 0x89ABCDEF, 0x01234567);
            assertEquals(r, 0x89ABCDEF, "success compareAndExchangeRelease int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "success compareAndExchangeRelease int value");
        }

        {
            int r = UNSAFE.compareAndExchangeIntRelease(base, offset, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(r, 0x01234567, "failing compareAndExchangeRelease int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "failing compareAndExchangeRelease int value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = UNSAFE.weakCompareAndSetIntPlain(base, offset, 0x01234567, 0x89ABCDEF);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "success weakCompareAndSetPlain int value");
        }

        {
            boolean success = UNSAFE.weakCompareAndSetIntPlain(base, offset, 0x01234567, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetPlain int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "failing weakCompareAndSetPlain int value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = UNSAFE.weakCompareAndSetIntAcquire(base, offset, 0x89ABCDEF, 0x01234567);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "success weakCompareAndSetAcquire int");
        }

        {
            boolean success = UNSAFE.weakCompareAndSetIntAcquire(base, offset, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetAcquire int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "failing weakCompareAndSetAcquire int value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = UNSAFE.weakCompareAndSetIntRelease(base, offset, 0x01234567, 0x89ABCDEF);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "success weakCompareAndSetRelease int");
        }

        {
            boolean success = UNSAFE.weakCompareAndSetIntRelease(base, offset, 0x01234567, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetRelease int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x89ABCDEF, "failing weakCompareAndSetRelease int value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = UNSAFE.weakCompareAndSetInt(base, offset, 0x89ABCDEF, 0x01234567);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "success weakCompareAndSet int");
        }

        {
            boolean success = UNSAFE.weakCompareAndSetInt(base, offset, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSet int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "failing weakCompareAndSet int value");
        }

        UNSAFE.putInt(base, offset, 0x89ABCDEF);

        // Compare set and get
        {
            int o = UNSAFE.getAndSetInt(base, offset, 0x01234567);
            assertEquals(o, 0x89ABCDEF, "getAndSet int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, 0x01234567, "getAndSet int value");
        }

        UNSAFE.putInt(base, offset, 0x01234567);

        // get and add, add and get
        {
            int o = UNSAFE.getAndAddInt(base, offset, 0x89ABCDEF);
            assertEquals(o, 0x01234567, "getAndAdd int");
            int x = UNSAFE.getInt(base, offset);
            assertEquals(x, (int)(0x01234567 + 0x89ABCDEF), "getAndAdd int");
        }
    }

    static void testAccess(long address) {
        // Plain
        {
            UNSAFE.putInt(address, 0x01234567);
            int x = UNSAFE.getInt(address);
            assertEquals(x, 0x01234567, "set int value");
        }
    }
}
