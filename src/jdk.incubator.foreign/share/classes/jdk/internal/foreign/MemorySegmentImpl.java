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

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.Unsafe;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Random;

/**
 * This class provides an immutable implementation for the {@code MemorySegment} interface. This class contains information
 * about the segment's spatial and temporal bounds, as well as the addressing coordinates (base + offset) which allows
 * unsafe access; each memory segment implementation is associated with an owner thread which is set at creation time.
 * Access to certain sensitive operations on the memory segment will fail with {@code IllegalStateException} if the
 * segment is either in an invalid state (e.g. it has already been closed) or if access occurs from a thread other
 * than the owner thread. See {@link MemoryScope} for more details on management of temporal bounds.
 */
public final class MemorySegmentImpl implements MemorySegment, MemorySegmentProxy {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final int BYTE_ARR_BASE = UNSAFE.arrayBaseOffset(byte[].class);

    final long length;
    final int mask;
    final long min;
    final Object base;
    final Thread owner;
    final MemoryScope scope;

    final static int READ_ONLY = 1;
    final static long NONCE = new Random().nextLong();

    public MemorySegmentImpl(long min, Object base, long length, int mask, Thread owner, MemoryScope scope) {
        this.length = length;
        this.mask = mask;
        this.min = min;
        this.base = base;
        this.owner = owner;
        this.scope = scope;
    }

    // MemorySegment methods

    @Override
    public final MemorySegmentImpl asSlice(long offset, long newSize) {
        checkValidState();
        checkBounds(offset, newSize);
        return new MemorySegmentImpl(min + offset, base, newSize, mask, owner, scope);
    }

    @Override
    public MemorySegment acquire() {
        return new MemorySegmentImpl(min, base, length, mask, Thread.currentThread(), scope.acquire());
    }

    @Override
    public final MemoryAddress baseAddress() {
        return new MemoryAddressImpl(this, 0);
    }

    @Override
    public final long byteSize() {
        return length;
    }

    @Override
    public final MemorySegmentImpl asReadOnly() {
        checkValidState();
        return new MemorySegmentImpl(min, base, length, mask | READ_ONLY, owner, scope);
    }

    @Override
    public final boolean isAlive() {
        return scope.isAliveThreadSafe();
    }

    @Override
    public final boolean isReadOnly() {
        return isSet(READ_ONLY);
    }

    @Override
    public boolean isAccessible() {
        return owner == Thread.currentThread();
    }

    @Override
    public final void close() {
        checkValidState();
        scope.close();
    }

    @Override
    public ByteBuffer asByteBuffer() {
        checkValidState();
        checkIntSize("ByteBuffer");
        JavaNioAccess nioAccess = SharedSecrets.getJavaNioAccess();
        ByteBuffer _bb;
        if (base() != null) {
            if (!(base() instanceof byte[])) {
                throw new UnsupportedOperationException("Not an address to an heap-allocated byte array");
            }
            _bb = nioAccess.newHeapByteBuffer((byte[]) base(), (int)min - BYTE_ARR_BASE, (int) length, this);
        } else {
            _bb = nioAccess.newDirectByteBuffer(min, (int) length, null, this);
        }
        if (isReadOnly()) {
            //scope is IMMUTABLE - obtain a RO byte buffer
            _bb = _bb.asReadOnlyBuffer();
        }
        return _bb;
    }

    @Override
    public byte[] toByteArray() {
        checkValidState();
        checkIntSize("byte[]");
        byte[] arr = new byte[(int)length];
        MemorySegment arrSegment = MemorySegment.ofArray(arr);
        MemoryAddress.copy(this.baseAddress(), arrSegment.baseAddress(), length);
        return arr;
    }

    // MemorySegmentProxy methods

    @Override
    public final void checkValidState() {
        if (owner != Thread.currentThread()) {
            throw new IllegalStateException("Attempt to access segment outside owning thread");
        }
        scope.checkAliveConfined();
    }

    // Object methods

    @Override
    public String toString() {
        return "MemorySegment{ id=0x" + Long.toHexString(id()) + " limit: " + byteSize() + " }";
    }

    // Helper methods

    void checkRange(long offset, long length, boolean writeAccess) {
        checkValidState();
        if (isReadOnly() && writeAccess) {
            throw new UnsupportedOperationException("Cannot write to read-only memory segment");
        }
        checkBounds(offset, length);
    }

    Object base() {
        return base;
    }

    private boolean isSet(int mask) {
        return (this.mask & mask) != 0;
    }

    private void checkIntSize(String typeName) {
        if (length > (Integer.MAX_VALUE - 8)) { //conservative check
            throw new UnsupportedOperationException(String.format("Segment is too large to wrap as %s. Size: %d", typeName, length));
        }
    }

    private void checkBounds(long offset, long length) {
        if (length < 0 ||
                offset < 0 ||
                offset > this.length - length) { // careful of overflow
            throw new IndexOutOfBoundsException(String.format("Out of bound access on segment %s; new offset = %d; new length = %d",
                this, offset, length));
        }
    }

    private int id() {
        //compute a stable and random id for this memory segment
        return Math.abs(Objects.hash(base, min, NONCE));
    }

}
