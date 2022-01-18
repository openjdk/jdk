/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.MemorySegment;
import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Implementation for heap memory segments. A heap memory segment is composed by an offset and
 * a base object (typically an array). To enhance performances, the access to the base object needs to feature
 * sharp type information, as well as sharp null-check information. For this reason, many concrete subclasses
 * of {@link HeapMemorySegmentImpl} are defined (e.g. {@link OfFloat}, so that each subclass can override the
 * {@link HeapMemorySegmentImpl#base()} method so that it returns an array of the correct (sharp) type. Note that
 * the field type storing the 'base' coordinate is just Object; similarly, all the constructor in the subclasses
 * accept an Object 'base' parameter instead of a sharper type (e.g. {@code byte[]}). This is deliberate, as
 * using sharper types would require use of type-conversions, which in turn would inhibit some C2 optimizations,
 * such as the elimination of store barriers in methods like {@link HeapMemorySegmentImpl#dup(long, long, int, ResourceScopeImpl)}.
 */
public abstract class HeapMemorySegmentImpl extends AbstractMemorySegmentImpl {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final int BYTE_ARR_BASE = UNSAFE.arrayBaseOffset(byte[].class);

    private static final long MAX_ALIGN_1 = 1;
    private static final long MAX_ALIGN_2 = 2;
    private static final long MAX_ALIGN_4 = 4;
    private static final long MAX_ALIGN_8 = 8;

    final long offset;
    final Object base;

    @ForceInline
    HeapMemorySegmentImpl(long offset, Object base, long length, int mask) {
        super(length, mask, ResourceScopeImpl.GLOBAL);
        this.offset = offset;
        this.base = base;
    }

    @Override
    abstract Object base();

    @Override
    long min() {
        return offset;
    }

    @Override
    abstract HeapMemorySegmentImpl dup(long offset, long size, int mask, ResourceScopeImpl scope);

    @Override
    ByteBuffer makeByteBuffer() {
        if (!(base() instanceof byte[])) {
            throw new UnsupportedOperationException("Not an address to an heap-allocated byte array");
        }
        JavaNioAccess nioAccess = SharedSecrets.getJavaNioAccess();
        return nioAccess.newHeapByteBuffer((byte[]) base(), (int)min() - BYTE_ARR_BASE, (int) byteSize(), null);
    }

    // factories

    public static class OfByte extends HeapMemorySegmentImpl {

        OfByte(long offset, Object base, long length, int mask) {
            super(offset, base, length, mask);
        }

        @Override
        OfByte dup(long offset, long size, int mask, ResourceScopeImpl scope) {
            return new OfByte(this.offset + offset, base, size, mask);
        }

        @Override
        byte[] base() {
            return (byte[])Objects.requireNonNull(base);
        }

        public static MemorySegment fromArray(byte[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_BYTE_INDEX_SCALE;
            return new OfByte(Unsafe.ARRAY_BYTE_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }

        @Override
        public long maxAlignMask() {
            return MAX_ALIGN_1;
        }
    }

    public static class OfChar extends HeapMemorySegmentImpl {

        OfChar(long offset, Object base, long length, int mask) {
            super(offset, base, length, mask);
        }

        @Override
        OfChar dup(long offset, long size, int mask, ResourceScopeImpl scope) {
            return new OfChar(this.offset + offset, base, size, mask);
        }

        @Override
        char[] base() {
            return (char[])Objects.requireNonNull(base);
        }

        public static MemorySegment fromArray(char[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_CHAR_INDEX_SCALE;
            return new OfChar(Unsafe.ARRAY_CHAR_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }

        @Override
        public long maxAlignMask() {
            return MAX_ALIGN_2;
        }
    }

    public static class OfShort extends HeapMemorySegmentImpl {

        OfShort(long offset, Object base, long length, int mask) {
            super(offset, base, length, mask);
        }

        @Override
        OfShort dup(long offset, long size, int mask, ResourceScopeImpl scope) {
            return new OfShort(this.offset + offset, base, size, mask);
        }

        @Override
        short[] base() {
            return (short[])Objects.requireNonNull(base);
        }

        public static MemorySegment fromArray(short[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_SHORT_INDEX_SCALE;
            return new OfShort(Unsafe.ARRAY_SHORT_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }

        @Override
        public long maxAlignMask() {
            return MAX_ALIGN_2;
        }
    }

    public static class OfInt extends HeapMemorySegmentImpl {

        OfInt(long offset, Object base, long length, int mask) {
            super(offset, base, length, mask);
        }

        @Override
        OfInt dup(long offset, long size, int mask, ResourceScopeImpl scope) {
            return new OfInt(this.offset + offset, base, size, mask);
        }

        @Override
        int[] base() {
            return (int[])Objects.requireNonNull(base);
        }

        public static MemorySegment fromArray(int[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_INT_INDEX_SCALE;
            return new OfInt(Unsafe.ARRAY_INT_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }

        @Override
        public long maxAlignMask() {
            return MAX_ALIGN_4;
        }
    }

    public static class OfLong extends HeapMemorySegmentImpl {

        OfLong(long offset, Object base, long length, int mask) {
            super(offset, base, length, mask);
        }

        @Override
        OfLong dup(long offset, long size, int mask, ResourceScopeImpl scope) {
            return new OfLong(this.offset + offset, base, size, mask);
        }

        @Override
        long[] base() {
            return (long[])Objects.requireNonNull(base);
        }

        public static MemorySegment fromArray(long[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_LONG_INDEX_SCALE;
            return new OfLong(Unsafe.ARRAY_LONG_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }

        @Override
        public long maxAlignMask() {
            return MAX_ALIGN_8;
        }
    }

    public static class OfFloat extends HeapMemorySegmentImpl {

        OfFloat(long offset, Object base, long length, int mask) {
            super(offset, base, length, mask);
        }

        @Override
        OfFloat dup(long offset, long size, int mask, ResourceScopeImpl scope) {
            return new OfFloat(this.offset + offset, base, size, mask);
        }

        @Override
        float[] base() {
            return (float[])Objects.requireNonNull(base);
        }

        public static MemorySegment fromArray(float[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            return new OfFloat(Unsafe.ARRAY_FLOAT_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }

        @Override
        public long maxAlignMask() {
            return MAX_ALIGN_4;
        }
    }

    public static class OfDouble extends HeapMemorySegmentImpl {

        OfDouble(long offset, Object base, long length, int mask) {
            super(offset, base, length, mask);
        }

        @Override
        OfDouble dup(long offset, long size, int mask, ResourceScopeImpl scope) {
            return new OfDouble(this.offset + offset, base, size, mask);
        }

        @Override
        double[] base() {
            return (double[])Objects.requireNonNull(base);
        }

        public static MemorySegment fromArray(double[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            return new OfDouble(Unsafe.ARRAY_DOUBLE_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }

        @Override
        public long maxAlignMask() {
            return MAX_ALIGN_8;
        }
    }

}
