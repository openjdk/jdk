/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
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
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import jdk.internal.vm.annotation.ForceInline;

/**
 * This class provides an immutable implementation for the {@code MemoryAddress} interface. This class contains information
 * about the segment this address is associated with, as well as an offset into such segment.
 */
public final class MemoryAddressImpl implements MemoryAddress, Scoped {

    private final long offset;

    public MemoryAddressImpl(long offset) {
        this.offset = offset;
    }

    // MemoryAddress methods

    @Override
    public MemoryAddress addOffset(long offset) {
        return new MemoryAddressImpl(this.offset + offset);
    }

    @Override
    public long toRawLongValue() {
        return offset;
    }

    @Override
    public final MemoryAddress address() {
        return this;
    }

    // Object methods

    @Override
    public int hashCode() {
        return (int) toRawLongValue();
    }

    @Override
    public boolean equals(Object that) {
        return (that instanceof MemoryAddressImpl addressImpl &&
            offset == addressImpl.offset);
    }

    @Override
    public String toString() {
        return "MemoryAddress{ offset=0x" + Long.toHexString(offset) + " }";
    }

    public static MemorySegment ofLongUnchecked(long value) {
        return ofLongUnchecked(value, Long.MAX_VALUE);
    }

    public static MemorySegment ofLongUnchecked(long value, long byteSize, ResourceScopeImpl resourceScope) {
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(MemoryAddress.ofLong(value), byteSize, resourceScope);
    }

    public static MemorySegment ofLongUnchecked(long value, long byteSize) {
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(MemoryAddress.ofLong(value), byteSize, ResourceScopeImpl.GLOBAL);
    }

    @Override
    public ResourceScope scope() {
        return ResourceScopeImpl.GLOBAL;
    }

    @Override
    @CallerSensitive
    @ForceInline
    public String getUtf8String(long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        SharedUtils.checkAddress(this);
        return NativeMemorySegmentImpl.EVERYTHING.getUtf8String(toRawLongValue() + offset);
    }

    @Override
    @CallerSensitive
    @ForceInline
    public void setUtf8String(long offset, String str) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        SharedUtils.checkAddress(this);
        NativeMemorySegmentImpl.EVERYTHING.setUtf8String(toRawLongValue() + offset, str);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public byte get(ValueLayout.OfByte layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfByte layout, long offset, byte value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public boolean get(ValueLayout.OfBoolean layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfBoolean layout, long offset, boolean value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public char get(ValueLayout.OfChar layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfChar layout, long offset, char value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public short get(ValueLayout.OfShort layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfShort layout, long offset, short value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public int get(ValueLayout.OfInt layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfInt layout, long offset, int value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public float get(ValueLayout.OfFloat layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfFloat layout, long offset, float value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public long get(ValueLayout.OfLong layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfLong layout, long offset, long value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public double get(ValueLayout.OfDouble layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfDouble layout, long offset, double value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public MemoryAddress get(ValueLayout.OfAddress layout, long offset) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + offset);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void set(ValueLayout.OfAddress layout, long offset, Addressable value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + offset, value.address());
    }

    @Override
    @ForceInline
    @CallerSensitive
    public char getAtIndex(ValueLayout.OfChar layout, long index) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + (index * layout.byteSize()));
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void setAtIndex(ValueLayout.OfChar layout, long index, char value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + (index * layout.byteSize()), value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public short getAtIndex(ValueLayout.OfShort layout, long index) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + (index * layout.byteSize()));
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void setAtIndex(ValueLayout.OfShort layout, long index, short value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + (index * layout.byteSize()), value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public int getAtIndex(ValueLayout.OfInt layout, long index) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + (index * layout.byteSize()));
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void setAtIndex(ValueLayout.OfInt layout, long index, int value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + (index * layout.byteSize()), value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public float getAtIndex(ValueLayout.OfFloat layout, long index) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + (index * layout.byteSize()));
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void setAtIndex(ValueLayout.OfFloat layout, long index, float value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + (index * layout.byteSize()), value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public long getAtIndex(ValueLayout.OfLong layout, long index) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + (index * layout.byteSize()));
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void setAtIndex(ValueLayout.OfLong layout, long index, long value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + (index * layout.byteSize()), value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public double getAtIndex(ValueLayout.OfDouble layout, long index) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + (index * layout.byteSize()));
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void setAtIndex(ValueLayout.OfDouble layout, long index, double value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + (index * layout.byteSize()), value);
    }

    @Override
    @ForceInline
    @CallerSensitive
    public MemoryAddress getAtIndex(ValueLayout.OfAddress layout, long index) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        return NativeMemorySegmentImpl.EVERYTHING.get(layout, toRawLongValue() + (index * layout.byteSize()));
    }

    @Override
    @ForceInline
    @CallerSensitive
    public void setAtIndex(ValueLayout.OfAddress layout, long index, Addressable value) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        NativeMemorySegmentImpl.EVERYTHING.set(layout, toRawLongValue() + (index * layout.byteSize()), value.address());
    }
}
