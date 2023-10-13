/*
 *  Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Optional;

import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;

/**
 * Implementation for native memory segments. A native memory segment is essentially a wrapper around
 * a native long address.
 */
public sealed class NativeMemorySegmentImpl extends AbstractMemorySegmentImpl permits MappedMemorySegmentImpl {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // The maximum alignment supported by malloc - typically 16 bytes on
    // 64-bit platforms and 8 bytes on 32-bit platforms.
    private static final long MAX_MALLOC_ALIGN = Unsafe.ADDRESS_SIZE == 4 ? 8 : 16;

    final long min;

    @ForceInline
    NativeMemorySegmentImpl(long min, long length, boolean readOnly, MemorySessionImpl scope) {
        super(length, readOnly, scope);
        this.min = (Unsafe.getUnsafe().addressSize() == 4)
                // On 32-bit systems, normalize the upper unused 32-bits to zero
                ? min & 0x0000_0000_FFFF_FFFFL
                // On 64-bit systems, all the bits are used
                : min;
    }

    /**
     * This constructor should only be used when initializing {@link MemorySegment#NULL}. Note: because of the memory
     * segment class hierarchy, it is possible to end up in a situation where this constructor is called
     * when the static fields in this class are not yet initialized.
     */
    @ForceInline
    public NativeMemorySegmentImpl() {
        super(0L, false, new GlobalSession(null));
        this.min = 0L;
    }

    @Override
    public long address() {
        return min;
    }

    @Override
    public Optional<Object> heapBase() {
        return Optional.empty();
    }

    @ForceInline
    @Override
    NativeMemorySegmentImpl dup(long offset, long size, boolean readOnly, MemorySessionImpl scope) {
        return new NativeMemorySegmentImpl(min + offset, size, readOnly, scope);
    }

    @Override
    ByteBuffer makeByteBuffer() {
        return NIO_ACCESS.newDirectByteBuffer(min, (int) this.length, null,
                scope == MemorySessionImpl.GLOBAL ? null : this);
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public long unsafeGetOffset() {
        return min;
    }

    @Override
    public Object unsafeGetBase() {
        return null;
    }

    @Override
    public long maxAlignMask() {
        return 0;
    }

    // factories

    public static MemorySegment makeNativeSegmentNoZeroing(long byteSize, long byteAlignment, MemorySessionImpl sessionImpl,
                                                  boolean shouldReserve) {
        sessionImpl.checkValidState();
        if (VM.isDirectMemoryPageAligned()) {
            byteAlignment = Math.max(byteAlignment, NIO_ACCESS.pageSize());
        }
        long alignedSize = Math.max(1L, byteAlignment > MAX_MALLOC_ALIGN ?
                byteSize + (byteAlignment - 1) :
                byteSize);

        if (shouldReserve) {
            NIO_ACCESS.reserveMemory(alignedSize, byteSize);
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
                    NIO_ACCESS.unreserveMemory(alignedSize, byteSize);
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

    // Unsafe native segment factories. These are used by the implementation code, to skip the sanity checks
    // associated with MemorySegment::ofAddress.

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize, MemorySessionImpl sessionImpl, Runnable action) {
        if (action == null) {
            sessionImpl.checkValidState();
        } else {
            sessionImpl.addCloseAction(action);
        }
        return new NativeMemorySegmentImpl(min, byteSize, false, sessionImpl);
    }

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize, MemorySessionImpl sessionImpl) {
        sessionImpl.checkValidState();
        return new NativeMemorySegmentImpl(min, byteSize, false, sessionImpl);
    }

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize) {
        return new NativeMemorySegmentImpl(min, byteSize, false, new GlobalSession(null));
    }
}
