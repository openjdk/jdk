/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;

public class CLayouts {

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
    public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;
    /**
     * The layout for the {@code int} C type
     */
    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

    /**
     * The layout for the {@code long long} C type.
     */
    public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;
    /**
     * The layout for the {@code float} C type
     */
    public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;
    /**
     * The layout for the {@code double} C type
     */
    public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;
    /**
     * The {@code T*} native type.
     */
    public static final ValueLayout.OfAddress C_POINTER = ValueLayout.ADDRESS;

    private static CLinker LINKER = CLinker.systemCLinker();

    private static final MethodHandle FREE = LINKER.downcallHandle(
            LINKER.lookup("free").get(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private static final MethodHandle MALLOC = LINKER.downcallHandle(
            LINKER.lookup("malloc").get(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

    public static void freeMemory(Addressable address) {
        try {
            FREE.invokeExact(address);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MemoryAddress allocateMemory(long size) {
        try {
            return (MemoryAddress)MALLOC.invokeExact(size);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }
}
