/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import sun.misc.Unsafe;

/**
 * Launched by UnsafeMemoryAccessWarnings with a '+' delimited list of methods to invoke.
 */
@SuppressWarnings("removal")
public class TryUnsafeMemoryAccess {
    private static final Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private static long address;
    private static long offset;

    static class TestClass {
        long value;
        TestClass(long value) {
            this.value = value;
        }
    }

    /**
     * The argument is a list of names of no-arg static methods in this class to invoke.
     * The names are separated with a '+'.
     */
    public static void main(String[] args) throws Exception {
        String[] methodNames = args[0].split("\\+");
        for (String methodName : methodNames) {
            Method m = TryUnsafeMemoryAccess.class.getDeclaredMethod(methodName);
            try {
                m.invoke(null);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    // a selection of Unsafe memory access methods to test

    static void allocateMemory() {
        address = UNSAFE.allocateMemory(100);
    }

    static void freeMemory() {
        if (address == 0)
            throw new RuntimeException("allocateMemory not called");
        UNSAFE.freeMemory(address);
    }

    static void objectFieldOffset() throws Exception {
        Field f = TestClass.class.getDeclaredField("value");
        offset = UNSAFE.objectFieldOffset(f);
    }

    static void getLong() {
        if (offset == 0)
            throw new RuntimeException("objectFieldOffset not called");
        var obj = new TestClass(99);
        long value = UNSAFE.getLong(obj, offset);
        if (value != 99) {
            throw new RuntimeException();
        }
    }

    static void putLong() {
        if (offset == 0)
            throw new RuntimeException("objectFieldOffset not called");
        var obj = new TestClass(0);
        UNSAFE.putLong(obj, offset, 99);
        if (obj.value != 99) {
            throw new RuntimeException();
        }
    }

    static void invokeCleaner() {
        var dbb = ByteBuffer.allocateDirect(1000);
        UNSAFE.invokeCleaner(dbb);
    }

    /**
     * Invoke Unsafe.allocateMemory reflectively.
     */
    static void reflectivelyAllocateMemory() throws Exception {
        Method allocateMemory = Unsafe.class.getMethod("allocateMemory", long.class);
        address = (long) allocateMemory.invoke(UNSAFE, 100);
    }

    /**
     * Invoke Unsafe.freeMemory reflectively.
     */
    static void reflectivelyFreeMemory() throws Exception {
        if (address == 0)
            throw new RuntimeException("allocateMemory not called");
        Method freeMemory = Unsafe.class.getMethod("freeMemory", long.class);
        freeMemory.invoke(UNSAFE, address);
    }

    /**
     * Used to test that the property value from startup is used.
     */
    static void setSystemPropertyToAllow() {
        System.setProperty("sun.misc.unsafe.memory.access", "allow");
    }
}
