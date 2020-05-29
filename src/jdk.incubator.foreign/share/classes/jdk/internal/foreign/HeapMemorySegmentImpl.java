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
import java.util.function.Supplier;

/**
 * Implementation for heap memory segments. An heap memory segment is composed by an offset and
 * a base object (typically an array). To enhance performances, the access to the base object needs to feature
 * sharp type information, as well as sharp null-check information. For this reason, the factories for heap segments
 * use a lambda to implement the base object accessor, so that the type information will remain sharp (e.g.
 * the static compiler will generate specialized base accessor for us).
 */
public class HeapMemorySegmentImpl<H> extends AbstractMemorySegmentImpl {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final int BYTE_ARR_BASE = UNSAFE.arrayBaseOffset(byte[].class);

    final long offset;
    final Supplier<H> baseProvider;

    @ForceInline
    HeapMemorySegmentImpl(long offset, Supplier<H> baseProvider, long length, int mask, MemoryScope scope) {
        super(length, mask, scope);
        this.offset = offset;
        this.baseProvider = baseProvider;
    }

    @Override
    H base() {
        return Objects.requireNonNull(baseProvider.get());
    }

    @Override
    long min() {
        return offset;
    }

    @Override
    HeapMemorySegmentImpl<H> dup(long offset, long size, int mask, MemoryScope scope) {
        return new HeapMemorySegmentImpl<>(this.offset + offset, baseProvider, size, mask, scope);
    }

    @Override
    ByteBuffer makeByteBuffer() {
        if (!(base() instanceof byte[])) {
            throw new UnsupportedOperationException("Not an address to an heap-allocated byte array");
        }
        JavaNioAccess nioAccess = SharedSecrets.getJavaNioAccess();
        return nioAccess.newHeapByteBuffer((byte[]) base(), (int)min() - BYTE_ARR_BASE, (int) byteSize(), this);
    }

    // factories

    public static MemorySegment makeArraySegment(byte[] arr) {
        return makeHeapSegment(() -> arr, arr.length,
                Unsafe.ARRAY_BYTE_BASE_OFFSET, Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static MemorySegment makeArraySegment(char[] arr) {
        return makeHeapSegment(() -> arr, arr.length,
                Unsafe.ARRAY_CHAR_BASE_OFFSET, Unsafe.ARRAY_CHAR_INDEX_SCALE);
    }

    public static MemorySegment makeArraySegment(short[] arr) {
        return makeHeapSegment(() -> arr, arr.length,
                Unsafe.ARRAY_SHORT_BASE_OFFSET, Unsafe.ARRAY_SHORT_INDEX_SCALE);
    }

    public static MemorySegment makeArraySegment(int[] arr) {
        return makeHeapSegment(() -> arr, arr.length,
                Unsafe.ARRAY_INT_BASE_OFFSET, Unsafe.ARRAY_INT_INDEX_SCALE);
    }

    public static MemorySegment makeArraySegment(long[] arr) {
        return makeHeapSegment(() -> arr, arr.length,
                Unsafe.ARRAY_LONG_BASE_OFFSET, Unsafe.ARRAY_LONG_INDEX_SCALE);
    }

    public static MemorySegment makeArraySegment(float[] arr) {
        return makeHeapSegment(() -> arr, arr.length,
                Unsafe.ARRAY_FLOAT_BASE_OFFSET, Unsafe.ARRAY_FLOAT_INDEX_SCALE);
    }

    public static MemorySegment makeArraySegment(double[] arr) {
        return makeHeapSegment(() -> arr, arr.length,
                Unsafe.ARRAY_DOUBLE_BASE_OFFSET, Unsafe.ARRAY_DOUBLE_INDEX_SCALE);
    }

    static <Z> HeapMemorySegmentImpl<Z> makeHeapSegment(Supplier<Z> obj, int length, int base, int scale) {
        int byteSize = length * scale;
        MemoryScope scope = MemoryScope.create(null, null);
        return new HeapMemorySegmentImpl<>(base, obj, byteSize, defaultAccessModes(byteSize), scope);
    }
}
