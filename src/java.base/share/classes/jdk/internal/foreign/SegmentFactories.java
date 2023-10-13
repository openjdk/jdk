/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign;

import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.foreign.HeapMemorySegmentImpl.OfByte;
import jdk.internal.foreign.HeapMemorySegmentImpl.OfChar;
import jdk.internal.foreign.HeapMemorySegmentImpl.OfDouble;
import jdk.internal.foreign.HeapMemorySegmentImpl.OfFloat;
import jdk.internal.foreign.HeapMemorySegmentImpl.OfInt;
import jdk.internal.foreign.HeapMemorySegmentImpl.OfLong;
import jdk.internal.foreign.HeapMemorySegmentImpl.OfShort;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * This class is used to retrieve concrete memory segment implementations, while making sure that classes
 * are initialized in the right order (that is, that {@code MemorySegment} is always initialized first).
 * See {@link SegmentFactories#ensureInitialized()}.
 */
public class SegmentFactories {

    // The maximum alignment supported by malloc - typically 16 bytes on
    // 64-bit platforms and 8 bytes on 32-bit platforms.
    private static final long MAX_MALLOC_ALIGN = Unsafe.ADDRESS_SIZE == 4 ? 8 : 16;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Unsafe native segment factories. These are used by the implementation code, to skip the sanity checks
    // associated with MemorySegment::ofAddress.

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize, MemorySessionImpl sessionImpl, Runnable action) {
        ensureInitialized();
        if (action == null) {
            sessionImpl.checkValidState();
        } else {
            sessionImpl.addCloseAction(action);
        }
        return new NativeMemorySegmentImpl(min, byteSize, false, sessionImpl);
    }

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize, MemorySessionImpl sessionImpl) {
        ensureInitialized();
        sessionImpl.checkValidState();
        return new NativeMemorySegmentImpl(min, byteSize, false, sessionImpl);
    }

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize) {
        ensureInitialized();
        return new NativeMemorySegmentImpl(min, byteSize, false, new GlobalSession(null));
    }

    public static MemorySegment fromArray(byte[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Unsafe.ARRAY_BYTE_INDEX_SCALE;
        return new OfByte(Unsafe.ARRAY_BYTE_BASE_OFFSET, arr, byteSize, false,
                MemorySessionImpl.heapSession(arr));
    }

    public static MemorySegment fromArray(short[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Unsafe.ARRAY_SHORT_INDEX_SCALE;
        return new OfShort(Unsafe.ARRAY_SHORT_BASE_OFFSET, arr, byteSize, false,
                MemorySessionImpl.heapSession(arr));
    }

    public static MemorySegment fromArray(int[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Unsafe.ARRAY_INT_INDEX_SCALE;
        return new OfInt(Unsafe.ARRAY_INT_BASE_OFFSET, arr, byteSize, false,
                MemorySessionImpl.heapSession(arr));
    }

    public static MemorySegment fromArray(char[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Unsafe.ARRAY_CHAR_INDEX_SCALE;
        return new OfChar(Unsafe.ARRAY_CHAR_BASE_OFFSET, arr, byteSize, false,
                MemorySessionImpl.heapSession(arr));
    }

    public static MemorySegment fromArray(float[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Unsafe.ARRAY_FLOAT_INDEX_SCALE;
        return new OfFloat(Unsafe.ARRAY_FLOAT_BASE_OFFSET, arr, byteSize, false,
                MemorySessionImpl.heapSession(arr));
    }

    public static MemorySegment fromArray(double[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
        return new OfDouble(Unsafe.ARRAY_DOUBLE_BASE_OFFSET, arr, byteSize, false,
                MemorySessionImpl.heapSession(arr));
    }

    public static MemorySegment fromArray(long[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Unsafe.ARRAY_LONG_INDEX_SCALE;
        return new OfLong(Unsafe.ARRAY_LONG_BASE_OFFSET, arr, byteSize, false,
                MemorySessionImpl.heapSession(arr));
    }

    public static MemorySegment allocateSegment(long byteSize, long byteAlignment, MemorySessionImpl sessionImpl,
                                                  boolean shouldReserve) {
        ensureInitialized();
        sessionImpl.checkValidState();
        if (VM.isDirectMemoryPageAligned()) {
            byteAlignment = Math.max(byteAlignment, AbstractMemorySegmentImpl.NIO_ACCESS.pageSize());
        }
        long alignedSize = Math.max(1L, byteAlignment > MAX_MALLOC_ALIGN ?
                byteSize + (byteAlignment - 1) :
                byteSize);

        if (shouldReserve) {
            AbstractMemorySegmentImpl.NIO_ACCESS.reserveMemory(alignedSize, byteSize);
        }

        long buf = allocateMemoryWrapper(alignedSize);
        long alignedBuf = Utils.alignUp(buf, byteAlignment);
        AbstractMemorySegmentImpl segment = new NativeMemorySegmentImpl(buf, alignedSize,
                false, sessionImpl);
        sessionImpl.addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                UNSAFE.freeMemory(buf);
                if (shouldReserve) {
                    AbstractMemorySegmentImpl.NIO_ACCESS.unreserveMemory(alignedSize, byteSize);
                }
            }
        });
        if (alignedSize != byteSize) {
            long delta = alignedBuf - buf;
            segment = segment.asSlice(delta, byteSize);
        }
        return segment;
    }

    private static long allocateMemoryWrapper(long size) {
        try {
            return UNSAFE.allocateMemory(size);
        } catch (IllegalArgumentException ex) {
            throw new OutOfMemoryError();
        }
    }

    public static MemorySegment mapSegment(long size, UnmapperProxy unmapper, boolean readOnly, MemorySessionImpl sessionImpl) {
        ensureInitialized();
        if (unmapper != null) {
            AbstractMemorySegmentImpl segment =
                    new MappedMemorySegmentImpl(unmapper.address(), unmapper, size,
                            readOnly, sessionImpl);
            MemorySessionImpl.ResourceList.ResourceCleanup resource =
                    new MemorySessionImpl.ResourceList.ResourceCleanup() {
                        @Override
                        public void cleanup() {
                            unmapper.unmap();
                        }
                    };
            sessionImpl.addOrCleanupIfFail(resource);
            return segment;
        } else {
            return new MappedMemorySegmentImpl(0, null, 0, readOnly, sessionImpl);
        }
    }

    // The method below needs to be called before any concrete subclass of MemorySegment
    // is instantiated. This is to make sure that we cannot have an initialization deadlock
    // where one thread attempts to initialize e.g. MemorySegment (and then NativeMemorySegmentImpl, via
    // the MemorySegment.NULL field) while another thread is attempting to initialize
    // NativeMemorySegmentImpl (and then MemorySegment, the super-interface).
    @ForceInline
    private static void ensureInitialized() {
        MemorySegment segment = MemorySegment.NULL;
    }
}
