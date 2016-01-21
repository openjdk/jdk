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
 * @summary Test unsafe access for byte
 * @modules java.base/jdk.internal.misc
 * @run testng/othervm -Diters=100   -Xint                   JdkInternalMiscUnsafeAccessTestByte
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 JdkInternalMiscUnsafeAccessTestByte
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  JdkInternalMiscUnsafeAccessTestByte
 * @run testng/othervm -Diters=20000                         JdkInternalMiscUnsafeAccessTestByte
 */

import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.testng.Assert.*;

public class JdkInternalMiscUnsafeAccessTestByte {
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
            Field staticVField = JdkInternalMiscUnsafeAccessTestByte.class.getDeclaredField("static_v");
            STATIC_V_BASE = UNSAFE.staticFieldBase(staticVField);
            STATIC_V_OFFSET = UNSAFE.staticFieldOffset(staticVField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Field vField = JdkInternalMiscUnsafeAccessTestByte.class.getDeclaredField("v");
            V_OFFSET = UNSAFE.objectFieldOffset(vField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        int ascale = UNSAFE.arrayIndexScale(byte[].class);
        ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(ascale);
    }

    static byte static_v;

    byte v;

    @Test
    public void testFieldInstance() {
        JdkInternalMiscUnsafeAccessTestByte t = new JdkInternalMiscUnsafeAccessTestByte();
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
        byte[] array = new byte[10];
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
            UNSAFE.putByte(base, offset, (byte)1);
            byte x = UNSAFE.getByte(base, offset);
            assertEquals(x, (byte)1, "set byte value");
        }

        // Volatile
        {
            UNSAFE.putByteVolatile(base, offset, (byte)2);
            byte x = UNSAFE.getByteVolatile(base, offset);
            assertEquals(x, (byte)2, "putVolatile byte value");
        }




    }

    static void testAccess(long address) {
        // Plain
        {
            UNSAFE.putByte(address, (byte)1);
            byte x = UNSAFE.getByte(address);
            assertEquals(x, (byte)1, "set byte value");
        }
    }
}
