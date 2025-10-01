/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.vm.annotation.DontInline;
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
    public static NativeMemorySegmentImpl makeNativeSegmentUnchecked(long min, long byteSize,
                                                                     MemorySessionImpl sessionImpl,
                                                                     boolean readOnly, Runnable action) {
        ensureInitialized();
        if (action == null) {
            sessionImpl.checkValidState();
        } else {
            sessionImpl.addCloseAction(action);
        }
        return new NativeMemorySegmentImpl(min, byteSize, readOnly, sessionImpl);
    }

    @ForceInline
    public static NativeMemorySegmentImpl makeNativeSegmentUnchecked(long min, long byteSize, MemorySessionImpl sessionImpl) {
        ensureInitialized();
        sessionImpl.checkValidState();
        return new NativeMemorySegmentImpl(min, byteSize, false, sessionImpl);
    }

    @ForceInline
    public static NativeMemorySegmentImpl makeNativeSegmentUnchecked(long min, long byteSize) {
        ensureInitialized();
        return new NativeMemorySegmentImpl(min, byteSize, false, MemorySessionImpl.GLOBAL_SESSION);
    }

    public static OfByte fromArray(byte[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.BYTE.scale();
        return new OfByte(Utils.BaseAndScale.BYTE.base(), arr, byteSize, false,
                MemorySessionImpl.createHeap(arr));
    }

    public static OfShort fromArray(short[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.SHORT.scale();
        return new OfShort(Utils.BaseAndScale.SHORT.base(), arr, byteSize, false,
                MemorySessionImpl.createHeap(arr));
    }

    public static OfInt fromArray(int[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.INT.scale();
        return new OfInt(Utils.BaseAndScale.INT.base(), arr, byteSize, false,
                MemorySessionImpl.createHeap(arr));
    }

    public static OfChar fromArray(char[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.CHAR.scale();
        return new OfChar(Utils.BaseAndScale.CHAR.base(), arr, byteSize, false,
                MemorySessionImpl.createHeap(arr));
    }

    public static OfFloat fromArray(float[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.FLOAT.scale();
        return new OfFloat(Utils.BaseAndScale.FLOAT.base(), arr, byteSize, false,
                MemorySessionImpl.createHeap(arr));
    }

    public static OfDouble fromArray(double[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.DOUBLE.scale();
        return new OfDouble(Utils.BaseAndScale.DOUBLE.base(), arr, byteSize, false,
                MemorySessionImpl.createHeap(arr));
    }

    public static OfLong fromArray(long[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.LONG.scale();
        return new OfLong(Utils.BaseAndScale.LONG.base(), arr, byteSize, false,
                MemorySessionImpl.createHeap(arr));
    }

    // Buffer conversion factories

    public static OfByte arrayOfByteSegment(Object base, long offset, long length,
                                            boolean readOnly, MemorySessionImpl bufferScope) {
        return new OfByte(offset, base, length, readOnly, bufferScope);
    }

    public static OfShort arrayOfShortSegment(Object base, long offset, long length,
                                              boolean readOnly, MemorySessionImpl bufferScope) {
        return new OfShort(offset, base, length, readOnly, bufferScope);
    }

    public static OfChar arrayOfCharSegment(Object base, long offset, long length,
                                            boolean readOnly, MemorySessionImpl bufferScope) {
        return new OfChar(offset, base, length, readOnly, bufferScope);
    }

    public static OfInt arrayOfIntSegment(Object base, long offset, long length,
                                          boolean readOnly, MemorySessionImpl bufferScope) {
        return new OfInt(offset, base, length, readOnly, bufferScope);
    }

    public static OfFloat arrayOfFloatSegment(Object base, long offset, long length,
                                              boolean readOnly, MemorySessionImpl bufferScope) {
        return new OfFloat(offset, base, length, readOnly, bufferScope);
    }

    public static OfLong arrayOfLongSegment(Object base, long offset, long length,
                                            boolean readOnly, MemorySessionImpl bufferScope) {
        return new OfLong(offset, base, length, readOnly, bufferScope);
    }

    public static OfDouble arrayOfDoubleSegment(Object base, long offset, long length,
                                                boolean readOnly, MemorySessionImpl bufferScope) {
        return new OfDouble(offset, base, length, readOnly, bufferScope);
    }

    public static NativeMemorySegmentImpl allocateNativeSegment(long byteSize, long byteAlignment, MemorySessionImpl sessionImpl,
                                                                boolean shouldReserve, boolean init) {
        long address = SegmentFactories.allocateNativeInternal(byteSize, byteAlignment, sessionImpl, shouldReserve, init);
        return new NativeMemorySegmentImpl(address, byteSize, false, sessionImpl);
    }

    private static long allocateNativeInternal(long byteSize, long byteAlignment, MemorySessionImpl sessionImpl,
                                               boolean shouldReserve, boolean init) {
        ensureInitialized();
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        sessionImpl.checkValidState();
        if (VM.isDirectMemoryPageAligned()) {
            byteAlignment = Math.max(byteAlignment, AbstractMemorySegmentImpl.NIO_ACCESS.pageSize());
        }
        // Align the allocation size up to a multiple of 8 so we can init the memory with longs
        long alignedSize = init ? Utils.alignUp(byteSize, Long.BYTES) : byteSize;
        // Check for wrap around
        if (alignedSize < 0) {
            throw new OutOfMemoryError();
        }
        // Always allocate at least some memory so that zero-length segments have distinct
        // non-zero addresses.
        alignedSize = Math.max(1, alignedSize);

        long allocationSize;
        long allocationBase;
        long result;
        if (byteAlignment > MAX_MALLOC_ALIGN) {
            allocationSize = alignedSize + byteAlignment - MAX_MALLOC_ALIGN;
            if (shouldReserve) {
                AbstractMemorySegmentImpl.NIO_ACCESS.reserveMemory(allocationSize, byteSize);
            }

            allocationBase = allocateMemoryWrapper(allocationSize);
            result = Utils.alignUp(allocationBase, byteAlignment);
        } else {
            allocationSize = alignedSize;
            if (shouldReserve) {
                AbstractMemorySegmentImpl.NIO_ACCESS.reserveMemory(allocationSize, byteSize);
            }

            allocationBase = allocateMemoryWrapper(allocationSize);
            result = allocationBase;
        }

        if (init) {
            initNativeMemory(result, alignedSize);
        }
        sessionImpl.addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                UNSAFE.freeMemory(allocationBase);
                if (shouldReserve) {
                    AbstractMemorySegmentImpl.NIO_ACCESS.unreserveMemory(allocationSize, byteSize);
                }
            }
        });
        return result;
    }

    private static void initNativeMemory(long address, long byteSize) {
        for (long i = 0; i < byteSize; i += Long.BYTES) {
            UNSAFE.putLongUnaligned(null, address + i, 0);
        }
    }

    private static long allocateMemoryWrapper(long size) {
        try {
            return UNSAFE.allocateMemory(size);
        } catch (IllegalArgumentException ex) {
            throw new OutOfMemoryError();
        }
    }

    public static MappedMemorySegmentImpl mapSegment(long size, UnmapperProxy unmapper, boolean readOnly, MemorySessionImpl sessionImpl) {
        ensureInitialized();
        if (unmapper != null) {
            MappedMemorySegmentImpl segment =
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
