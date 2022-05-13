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

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.nio.ByteBuffer;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;
import sun.security.action.GetBooleanAction;

/**
 * Implementation for native memory segments. A native memory segment is essentially a wrapper around
 * a native long address.
 */
public class NativeMemorySegmentImpl extends AbstractMemorySegmentImpl {

    public static final MemorySegment EVERYTHING = new NativeMemorySegmentImpl(0, Long.MAX_VALUE, 0, MemorySessionImpl.GLOBAL) {
        @Override
        void checkBounds(long offset, long length) {
            // do nothing
        }

        @Override
        NativeMemorySegmentImpl dup(long offset, long size, int mask, MemorySession scope) {
            throw new IllegalStateException();
        }
    };

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    // The maximum alignment supported by malloc - typically 16 on
    // 64-bit platforms and 8 on 32-bit platforms.
    private static final long MAX_MALLOC_ALIGN = Unsafe.ADDRESS_SIZE == 4 ? 8 : 16;

    private static final boolean skipZeroMemory = GetBooleanAction.privilegedGetProperty("jdk.internal.foreign.skipZeroMemory");

    final long min;

    @ForceInline
    NativeMemorySegmentImpl(long min, long length, int mask, MemorySession session) {
        super(length, mask, session);
        this.min = min;
    }

    @ForceInline
    @Override
    public MemoryAddress address() {
        checkValidState();
        return MemoryAddress.ofLong(unsafeGetOffset());
    }

    @Override
    NativeMemorySegmentImpl dup(long offset, long size, int mask, MemorySession session) {
        return new NativeMemorySegmentImpl(min + offset, size, mask, session);
    }

    @Override
    ByteBuffer makeByteBuffer() {
        return nioAccess.newDirectByteBuffer(min(), (int) this.length, null,
                session == MemorySessionImpl.GLOBAL ? null : this);
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    long min() {
        return min;
    }

    @Override
    Object base() {
        return null;
    }

    @Override
    public long maxAlignMask() {
        return 0;
    }

    // factories

    public static MemorySegment makeNativeSegment(long bytesSize, long alignmentBytes, MemorySession session) {
        MemorySessionImpl sessionImpl = MemorySessionImpl.toSessionImpl(session);
        sessionImpl.checkValidStateSlow();
        if (VM.isDirectMemoryPageAligned()) {
            alignmentBytes = Math.max(alignmentBytes, nioAccess.pageSize());
        }
        long alignedSize = Math.max(1L, alignmentBytes > MAX_MALLOC_ALIGN ?
                bytesSize + (alignmentBytes - 1) :
                bytesSize);

        nioAccess.reserveMemory(alignedSize, bytesSize);

        long buf = unsafe.allocateMemory(alignedSize);
        if (!skipZeroMemory) {
            unsafe.setMemory(buf, alignedSize, (byte)0);
        }
        long alignedBuf = Utils.alignUp(buf, alignmentBytes);
        AbstractMemorySegmentImpl segment = new NativeMemorySegmentImpl(buf, alignedSize,
                DEFAULT_MODES, session);
        sessionImpl.addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                unsafe.freeMemory(buf);
                nioAccess.unreserveMemory(alignedSize, bytesSize);
            }
        });
        if (alignedSize != bytesSize) {
            long delta = alignedBuf - buf;
            segment = segment.asSlice(delta, bytesSize);
        }
        return segment;
    }

    public static MemorySegment makeNativeSegmentUnchecked(MemoryAddress min, long bytesSize, MemorySession session) {
        MemorySessionImpl sessionImpl = MemorySessionImpl.toSessionImpl(session);
        sessionImpl.checkValidStateSlow();
        AbstractMemorySegmentImpl segment = new NativeMemorySegmentImpl(min.toRawLongValue(), bytesSize, DEFAULT_MODES, session);
        return segment;
    }
}
