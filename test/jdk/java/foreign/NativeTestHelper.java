/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class NativeTestHelper {

    public static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    public static boolean isIntegral(MemoryLayout layout) {
        return layout instanceof ValueLayout valueLayout && isIntegral(valueLayout.carrier());
    }

    static boolean isIntegral(Class<?> clazz) {
        return clazz == byte.class || clazz == char.class || clazz == short.class
                || clazz == int.class || clazz == long.class;
    }

    public static boolean isPointer(MemoryLayout layout) {
        return layout instanceof ValueLayout valueLayout && valueLayout.carrier() == MemorySegment.class;
    }

    // the constants below are useful aliases for C types. The type/carrier association is only valid for 64-bit platforms.

    /**
     * The layout for the {@code bool} C type
     */
    public static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;
    /**
     * The layout for the {@code char} C type
     */
    public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;
    /**
     * The layout for the {@code short} C type
     */
    public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT.withBitAlignment(16);
    /**
     * The layout for the {@code int} C type
     */
    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT.withBitAlignment(32);

    /**
     * The layout for the {@code long long} C type.
     */
    public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG.withBitAlignment(64);
    /**
     * The layout for the {@code float} C type
     */
    public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT.withBitAlignment(32);
    /**
     * The layout for the {@code double} C type
     */
    public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE.withBitAlignment(64);
    /**
     * The {@code T*} native type.
     */
    public static final ValueLayout.OfAddress C_POINTER = ValueLayout.ADDRESS.withBitAlignment(64).asUnbounded();

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle FREE = LINKER.downcallHandle(
            LINKER.defaultLookup().find("free").get(), FunctionDescriptor.ofVoid(C_POINTER));

    private static final MethodHandle MALLOC = LINKER.downcallHandle(
            LINKER.defaultLookup().find("malloc").get(), FunctionDescriptor.of(C_POINTER, C_LONG_LONG));

    public static void freeMemory(MemorySegment address) {
        try {
            FREE.invokeExact(address);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MemorySegment allocateMemory(long size) {
        try {
            return (MemorySegment) MALLOC.invokeExact(size);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MemorySegment findNativeOrThrow(String name) {
        return SymbolLookup.loaderLookup().find(name).orElseThrow();
    }

    public static MethodHandle downcallHandle(String symbol, FunctionDescriptor desc, Linker.Option... options) {
        return LINKER.downcallHandle(findNativeOrThrow(symbol), desc, options);
    }

    public static MemorySegment upcallStub(Class<?> holder, String name, FunctionDescriptor descriptor) {
        try {
            MethodHandle target = MethodHandles.lookup().findStatic(holder, name, descriptor.toMethodType());
            return LINKER.upcallStub(target, descriptor, MemorySession.implicit());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
