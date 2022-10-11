/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemorySession;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Optional;

import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;
import sun.security.action.GetBooleanAction;

/**
 * Implementation for native memory segments. A native memory segment is essentially a wrapper around
 * a native long address.
 */
public sealed class NativeMemorySegmentImpl extends AbstractMemorySegmentImpl permits MappedMemorySegmentImpl {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // The maximum alignment supported by malloc - typically 16 on
    // 64-bit platforms and 8 on 32-bit platforms.
    private static final long MAX_MALLOC_ALIGN = Unsafe.ADDRESS_SIZE == 4 ? 8 : 16;
    private static final boolean SKIP_ZERO_MEMORY = GetBooleanAction.privilegedGetProperty("jdk.internal.foreign.skipZeroMemory");

    final long min;

    @ForceInline
    NativeMemorySegmentImpl(long min, long length, boolean readOnly, MemorySession session) {
        super(length, readOnly, session);
        this.min = min;
    }

    @Override
    public long address() {
        return min;
    }

    @Override
    public Optional<Object> array() {
        return Optional.empty();
    }

    @ForceInline
    @Override
    NativeMemorySegmentImpl dup(long offset, long size, boolean readOnly, MemorySession session) {
        return new NativeMemorySegmentImpl(min + offset, size, readOnly, session);
    }

    @Override
    ByteBuffer makeByteBuffer() {
        return NIO_ACCESS.newDirectByteBuffer(min, (int) this.length, null,
                session == MemorySessionImpl.GLOBAL ? null : this);
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

    public static MemorySegment makeNativeSegment(long byteSize, long byteAlignment, MemorySession session) {
        MemorySessionImpl sessionImpl = MemorySessionImpl.toSessionImpl(session);
        sessionImpl.checkValidState();
        if (VM.isDirectMemoryPageAligned()) {
            byteAlignment = Math.max(byteAlignment, NIO_ACCESS.pageSize());
        }
        long alignedSize = Math.max(1L, byteAlignment > MAX_MALLOC_ALIGN ?
                byteSize + (byteAlignment - 1) :
                byteSize);

        NIO_ACCESS.reserveMemory(alignedSize, byteSize);

        long buf = UNSAFE.allocateMemory(alignedSize);
        if (!SKIP_ZERO_MEMORY) {
            UNSAFE.setMemory(buf, alignedSize, (byte)0);
        }
        long alignedBuf = Utils.alignUp(buf, byteAlignment);
        AbstractMemorySegmentImpl segment = new NativeMemorySegmentImpl(buf, alignedSize,
                false, session);
        sessionImpl.addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                UNSAFE.freeMemory(buf);
                NIO_ACCESS.unreserveMemory(alignedSize, byteSize);
            }
        });
        if (alignedSize != byteSize) {
            long delta = alignedBuf - buf;
            segment = segment.asSlice(delta, byteSize);
        }
        return segment;
    }

    // Unsafe native segment factories. These are used by the implementation code, to skip the sanity checks
    // associated with MemorySegment::ofAddress.

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize, MemorySession session) {
        MemorySessionImpl sessionImpl = MemorySessionImpl.toSessionImpl(session);
        sessionImpl.checkValidState();
        return new NativeMemorySegmentImpl(min, byteSize, false, session);
    }

    @ForceInline
    public static MemorySegment makeNativeSegmentUnchecked(long min, long byteSize) {
        return new NativeMemorySegmentImpl(min, byteSize, false, MemorySession.global());
    }
}
