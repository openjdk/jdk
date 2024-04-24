/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;

public class CLayouts {
    private static final Linker LINKER = Linker.nativeLinker();

    // the constants below are useful aliases for C types. The type/carrier association is only valid for 64-bit platforms.

    /**
     * The layout for the {@code bool} C type
     */
    public static final ValueLayout.OfBoolean C_BOOL = (ValueLayout.OfBoolean) LINKER.canonicalLayouts().get("bool");
    /**
     * The layout for the {@code char} C type
     */
    public static final ValueLayout.OfByte C_CHAR = (ValueLayout.OfByte) LINKER.canonicalLayouts().get("char");
    /**
     * The layout for the {@code short} C type
     */
    public static final ValueLayout.OfShort C_SHORT = (ValueLayout.OfShort) LINKER.canonicalLayouts().get("short");
    /**
     * The layout for the {@code int} C type
     */
    public static final ValueLayout.OfInt C_INT = (ValueLayout.OfInt) LINKER.canonicalLayouts().get("int");
    /**
     * The layout for the {@code long long} C type.
     */
    public static final ValueLayout.OfLong C_LONG_LONG = (ValueLayout.OfLong) LINKER.canonicalLayouts().get("long long");
    /**
     * The layout for the {@code float} C type
     */
    public static final ValueLayout.OfFloat C_FLOAT = (ValueLayout.OfFloat) LINKER.canonicalLayouts().get("float");
    /**
     * The layout for the {@code double} C type
     */
    public static final ValueLayout.OfDouble C_DOUBLE = (ValueLayout.OfDouble) LINKER.canonicalLayouts().get("double");

    /**
     * The {@code T*} native type.
     */
    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, C_CHAR));

    private static final MethodHandle FREE = LINKER.downcallHandle(
            LINKER.defaultLookup().findOrThrow("free"), FunctionDescriptor.ofVoid(C_POINTER));

    private static final MethodHandle MALLOC = LINKER.downcallHandle(
            LINKER.defaultLookup().findOrThrow("malloc"), FunctionDescriptor.of(C_POINTER, ValueLayout.JAVA_LONG));

    public static void freeMemory(MemorySegment address) {
        try {
            FREE.invokeExact(address);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MemorySegment allocateMemory(long size) {
        try {
            return (MemorySegment)MALLOC.invokeExact(size);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }
}
