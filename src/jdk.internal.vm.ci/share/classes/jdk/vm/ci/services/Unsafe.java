/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.services;

import java.lang.reflect.Field;
import jdk.internal.vm.annotation.ForceInline;

public final class Unsafe {

    private static final jdk.internal.misc.Unsafe theInternalUnsafe = jdk.internal.misc.Unsafe.getUnsafe();

    public static final long ARRAY_BYTE_BASE_OFFSET = jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;
    public static final int ARRAY_BYTE_INDEX_SCALE = jdk.internal.misc.Unsafe.ARRAY_BYTE_INDEX_SCALE;

    public static Unsafe getUnsafe() {
        return new Unsafe();
    }

    @ForceInline
    public void putShort(Object o, long offset, short x) {
        theInternalUnsafe.putShort(o, offset, x);
    }

    @ForceInline
    public void putByte(Object o, long offset, byte x) {
        theInternalUnsafe.putByte(o, offset, x);
    }

    @ForceInline
    public void putInt(Object o, long offset, int x) {
        theInternalUnsafe.putInt(o, offset, x);
    }

    @ForceInline
    public void putLong(Object o, long offset, long x) {
        theInternalUnsafe.putLong(o, offset, x);
    }

    @ForceInline
    public byte getByte(long address) {
        return getByte(null, address);
    }

    @ForceInline
    public byte getByte(Object o, long offset) {
        return theInternalUnsafe.getByte(o, offset);
    }

    @ForceInline
    public int getInt(long address) {
        return getInt(null, address);
    }

    @ForceInline
    public char getChar(long address) {
        return getChar(null, address);
    }

    @ForceInline
    public short getShort(long address) {
        return getShort(null, address);
    }

    @ForceInline
    public long getLong(long address) {
        return getLong(null, address);
    }

    @ForceInline
    public float getFloat(long address) {
        return getFloat(null, address);
    }

    @ForceInline
    public double getDouble(long address) {
        return getDouble(null, address);
    }

    @ForceInline
    public long getAddress(long address) {
        return theInternalUnsafe.getAddress(null, address);
    }

    @ForceInline
    public void putByte(long address, byte x) {
        putByte(null, address, x);
    }

    @ForceInline
    public void putChar(long address, char x) {
        putChar(null, address, x);
    }

    @ForceInline
    public void putShort(long address, short x) {
        putShort(null, address, x);
    }

    @ForceInline
    public void putInt(long address, int x) {
        putInt(null, address, x);
    }

    @ForceInline
    public void putLong(long address, long x) {
        putLong(null, address, x);
    }

    @ForceInline
    public void putFloat(long address, float x) {
        putFloat(null, address, x);
    }

    @ForceInline
    public void putDouble(long address, double x) {
        putDouble(null, address, x);
    }

    @ForceInline
    public void putAddress(long address, long x) {
        theInternalUnsafe.putAddress(address, x);
    }

    @ForceInline
    public boolean compareAndSetInt(Object object, long offset, int expectedValue, int newValue) {
        return theInternalUnsafe.compareAndSetInt(object, offset, expectedValue, newValue);
    }

    @ForceInline
    public boolean compareAndSetLong(Object object, long offset, long expectedValue, long newValue) {
        return theInternalUnsafe.compareAndSetLong(object, offset, expectedValue, newValue);
    }

    @ForceInline
    public boolean compareAndSetReference(Object object, long offset, Object expectedValue, Object newValue) {
        return theInternalUnsafe.compareAndSetReference(object, offset, expectedValue, newValue);
    }

    public long objectFieldOffset(Field f) {
        return theInternalUnsafe.objectFieldOffset(f);
    }

    @ForceInline
    public Object allocateInstance(Class<?> cls) throws InstantiationException {
        return theInternalUnsafe.allocateInstance(cls);
    }

    @ForceInline
    public long allocateMemory(int bytes) {
        return theInternalUnsafe.allocateMemory(bytes);
    }

    @ForceInline
    public void freeMemory(long address) {
        theInternalUnsafe.freeMemory(address);
    }

    @ForceInline
    public void putLongVolatile(Object o, long offset, long x) {
        theInternalUnsafe.putLongVolatile(o, offset, x);
    }

    @ForceInline
    public long getLongVolatile(Object o, long offset) {
        return theInternalUnsafe.getLongVolatile(o, offset);
    }

    @ForceInline
    public long getAndSetLong(Object o, long offset, long newValue) {
        return theInternalUnsafe.getAndSetLong(o, offset, newValue);
    }

    @ForceInline
    public long getAndAddLong(Object o, long offset, long delta) {
        return theInternalUnsafe.getAndAddLong(o, offset, delta);
    }

    @ForceInline
    public int getInt(Object o, long offset) {
        return theInternalUnsafe.getInt(o, offset);
    }

    @ForceInline
    public float getFloat(Object o, long offset) {
        return theInternalUnsafe.getFloat(o, offset);
    }

    @ForceInline
    public void putFloat(Object o, long offset, float x) {
        theInternalUnsafe.putFloat(o, offset, x);
    }

    @ForceInline
    public boolean getBoolean(Object o, long offset) {
        return theInternalUnsafe.getBoolean(o, offset);
    }

    @ForceInline
    public void putBoolean(Object o, long offset, boolean x) {
        theInternalUnsafe.putBoolean(o, offset, x);
    }

    @ForceInline
    public long getLong(Object o, long offset) {
        return theInternalUnsafe.getLong(o, offset);
    }

    @ForceInline
    public double getDouble(Object o, long offset) {
        return theInternalUnsafe.getDouble(o, offset);
    }

    @ForceInline
    public void putDouble(Object o, long offset, double x) {
        theInternalUnsafe.putDouble(o, offset, x);
    }

    @ForceInline
    public short getShort(Object o, long offset) {
        return theInternalUnsafe.getShort(o, offset);
    }

    @ForceInline
    public char getChar(Object o, long offset) {
        return theInternalUnsafe.getChar(o, offset);
    }

    @ForceInline
    public void putChar(Object o, long offset, char x) {
        theInternalUnsafe.putChar(o, offset, x);
    }

    @ForceInline
    public Object getReference(Object o, long offset) {
        return theInternalUnsafe.getReference(o, offset);
    }

    @ForceInline
    public void putReference(Object o, long offset, Object x) {
        theInternalUnsafe.putReference(o, offset, x);
    }

    @ForceInline
    public long arrayBaseOffset(Class<?> aClass) {
        return theInternalUnsafe.arrayBaseOffset(aClass);
    }

    @ForceInline
    public void copyMemory(Object srcBase, long srcOffset,
                    Object destBase, long destOffset,
                    long bytes) {
        theInternalUnsafe.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
    }
}
