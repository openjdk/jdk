/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;
import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.ForceInline;
import sun.security.action.GetPropertyAction;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * This abstract class provides an immutable implementation for the {@code MemorySegment} interface. This class contains information
 * about the segment's spatial and temporal bounds; each memory segment implementation is associated with an owner thread which is set at creation time.
 * Access to certain sensitive operations on the memory segment will fail with {@code IllegalStateException} if the
 * segment is either in an invalid state (e.g. it has already been closed) or if access occurs from a thread other
 * than the owner thread. See {@link MemoryScope} for more details on management of temporal bounds. Subclasses
 * are defined for each memory segment kind, see {@link NativeMemorySegmentImpl}, {@link HeapMemorySegmentImpl} and
 * {@link MappedMemorySegmentImpl}.
 */
public abstract class AbstractMemorySegmentImpl implements MemorySegment, MemorySegmentProxy {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final boolean enableSmallSegments =
            Boolean.parseBoolean(GetPropertyAction.privilegedGetProperty("jdk.incubator.foreign.SmallSegments", "true"));

    final static int FIRST_RESERVED_FLAG = 1 << 16; // upper 16 bits are reserved
    final static int SMALL = FIRST_RESERVED_FLAG;
    final static long NONCE = new Random().nextLong();

    final static JavaNioAccess nioAccess = SharedSecrets.getJavaNioAccess();

    final long length;
    final int mask;
    final MemoryScope scope;

    @ForceInline
    AbstractMemorySegmentImpl(long length, int mask, MemoryScope scope) {
        this.length = length;
        this.mask = mask;
        this.scope = scope;
    }

    abstract long min();

    abstract Object base();

    abstract AbstractMemorySegmentImpl dup(long offset, long size, int mask, MemoryScope scope);

    abstract ByteBuffer makeByteBuffer();

    static int defaultAccessModes(long size) {
        return (enableSmallSegments && size < Integer.MAX_VALUE) ?
                ALL_ACCESS | SMALL :
                ALL_ACCESS;
    }

    @Override
    public AbstractMemorySegmentImpl asSlice(long offset, long newSize) {
        checkBounds(offset, newSize);
        return asSliceNoCheck(offset, newSize);
    }

    private AbstractMemorySegmentImpl asSliceNoCheck(long offset, long newSize) {
        return dup(offset, newSize, mask, scope);
    }

    @SuppressWarnings("unchecked")
    public static <S extends MemorySegment> Spliterator<S> spliterator(S segment, SequenceLayout sequenceLayout) {
        ((AbstractMemorySegmentImpl)segment).checkValidState();
        if (sequenceLayout.byteSize() != segment.byteSize()) {
            throw new IllegalArgumentException();
        }
        return (Spliterator<S>)new SegmentSplitter(sequenceLayout.elementLayout().byteSize(), sequenceLayout.elementCount().getAsLong(),
                (AbstractMemorySegmentImpl)segment.withAccessModes(segment.accessModes() & ~CLOSE));
    }

    @Override
    public final MemorySegment fill(byte value){
        checkRange(0, length, true);
        UNSAFE.setMemory(base(), min(), length, value);
        return this;
    }

    public void copyFrom(MemorySegment src) {
        AbstractMemorySegmentImpl that = (AbstractMemorySegmentImpl)src;
        long size = that.byteSize();
        checkRange(0, size, true);
        that.checkRange(0, size, false);
        UNSAFE.copyMemory(
                that.base(), that.min(),
                base(), min(), size);
    }

    private final static VarHandle BYTE_HANDLE = MemoryLayout.ofSequence(MemoryLayouts.JAVA_BYTE)
            .varHandle(byte.class, MemoryLayout.PathElement.sequenceElement());

    @Override
    public long mismatch(MemorySegment other) {
        AbstractMemorySegmentImpl that = (AbstractMemorySegmentImpl)other;
        final long thisSize = this.byteSize();
        final long thatSize = that.byteSize();
        final long length = Math.min(thisSize, thatSize);
        this.checkRange(0, length, false);
        that.checkRange(0, length, false);
        if (this == other) {
            return -1;
        }

        long i = 0;
        if (length > 7) {
            if ((byte) BYTE_HANDLE.get(this.baseAddress(), 0) != (byte) BYTE_HANDLE.get(that.baseAddress(), 0)) {
                return 0;
            }
            i = ArraysSupport.vectorizedMismatchLargeForBytes(
                    this.base(), this.min(),
                    that.base(), that.min(),
                    length);
            if (i >= 0) {
                return i;
            }
            long remaining = ~i;
            assert remaining < 8 : "remaining greater than 7: " + remaining;
            i = length - remaining;
        }
        MemoryAddress thisAddress = this.baseAddress();
        MemoryAddress thatAddress = that.baseAddress();
        for (; i < length; i++) {
            if ((byte) BYTE_HANDLE.get(thisAddress, i) != (byte) BYTE_HANDLE.get(thatAddress, i)) {
                return i;
            }
        }
        return thisSize != thatSize ? length : -1;
    }

    @Override
    @ForceInline
    public final MemoryAddress baseAddress() {
        return new MemoryAddressImpl(this, 0);
    }

    @Override
    public final ByteBuffer asByteBuffer() {
        if (!isSet(READ)) {
            throw unsupportedAccessMode(READ);
        }
        checkIntSize("ByteBuffer");
        ByteBuffer _bb = makeByteBuffer();
        if (!isSet(WRITE)) {
            //scope is IMMUTABLE - obtain a RO byte buffer
            _bb = _bb.asReadOnlyBuffer();
        }
        return _bb;
    }

    @Override
    public final int accessModes() {
        return mask & ALL_ACCESS;
    }

    @Override
    public final long byteSize() {
        return length;
    }

    @Override
    public final boolean isAlive() {
        return scope.isAlive();
    }

    @Override
    public Thread ownerThread() {
        return scope.ownerThread();
    }

    @Override
    public AbstractMemorySegmentImpl withAccessModes(int accessModes) {
        checkAccessModes(accessModes);
        if ((~accessModes() & accessModes) != 0) {
            throw new IllegalArgumentException("Cannot acquire more access modes");
        }
        return dup(0, length, (mask & ~ALL_ACCESS) | accessModes, scope);
    }

    @Override
    public boolean hasAccessModes(int accessModes) {
        checkAccessModes(accessModes);
        return (accessModes() & accessModes) == accessModes;
    }

    private void checkAccessModes(int accessModes) {
        if ((accessModes & ~ALL_ACCESS) != 0) {
            throw new IllegalArgumentException("Invalid access modes");
        }
    }

    @Override
    public MemorySegment withOwnerThread(Thread newOwner) {
        Objects.requireNonNull(newOwner);
        if (!isSet(HANDOFF)) {
            throw unsupportedAccessMode(HANDOFF);
        }
        if (scope.ownerThread() == newOwner) {
            throw new IllegalArgumentException("Segment already owned by thread: " + newOwner);
        } else {
            try {
                return dup(0L, length, mask, scope.dup(newOwner));
            } finally {
                //flush read/writes to segment memory before returning the new segment
                VarHandle.fullFence();
            }
        }
    }

    @Override
    public final void close() {
        if (!isSet(CLOSE)) {
            throw unsupportedAccessMode(CLOSE);
        }
        closeNoCheck();
    }

    private final void closeNoCheck() {
        scope.close();
    }

    final AbstractMemorySegmentImpl acquire() {
        if (Thread.currentThread() != ownerThread() && !isSet(ACQUIRE)) {
            throw unsupportedAccessMode(ACQUIRE);
        }
        return dup(0, length, mask, scope.acquire());
    }

    @Override
    public final byte[] toByteArray() {
        checkIntSize("byte[]");
        byte[] arr = new byte[(int)length];
        MemorySegment arrSegment = MemorySegment.ofArray(arr);
        arrSegment.copyFrom(this);
        return arr;
    }

    boolean isSmall() {
        return isSet(SMALL);
    }

    void checkRange(long offset, long length, boolean writeAccess) {
        scope.checkValidState();
        if (writeAccess && !isSet(WRITE)) {
            throw unsupportedAccessMode(WRITE);
        } else if (!writeAccess && !isSet(READ)) {
            throw unsupportedAccessMode(READ);
        }
        checkBounds(offset, length);
    }

    @Override
    public final void checkValidState() {
        scope.checkValidState();
    }

    // Helper methods

    private boolean isSet(int mask) {
        return (this.mask & mask) != 0;
    }

    private void checkIntSize(String typeName) {
        if (length > (Integer.MAX_VALUE - 8)) { //conservative check
            throw new UnsupportedOperationException(String.format("Segment is too large to wrap as %s. Size: %d", typeName, length));
        }
    }

    private void checkBounds(long offset, long length) {
        if (isSmall()) {
            checkBoundsSmall((int)offset, (int)length);
        } else {
            if (length < 0 ||
                    offset < 0 ||
                    offset > this.length - length) { // careful of overflow
                throw outOfBoundException(offset, length);
            }
        }
    }

    private void checkBoundsSmall(int offset, int length) {
        if (length < 0 ||
                offset < 0 ||
                offset > (int)this.length - length) { // careful of overflow
            throw outOfBoundException(offset, length);
        }
    }

    UnsupportedOperationException unsupportedAccessMode(int expected) {
        return new UnsupportedOperationException((String.format("Required access mode %s ; current access modes: %s",
                modeStrings(expected).get(0), modeStrings(mask))));
    }

    private List<String> modeStrings(int mode) {
        List<String> modes = new ArrayList<>();
        if ((mode & READ) != 0) {
            modes.add("READ");
        }
        if ((mode & WRITE) != 0) {
            modes.add("WRITE");
        }
        if ((mode & CLOSE) != 0) {
            modes.add("CLOSE");
        }
        if ((mode & ACQUIRE) != 0) {
            modes.add("ACQUIRE");
        }
        if ((mode & HANDOFF) != 0) {
            modes.add("HANDOFF");
        }
        return modes;
    }

    private IndexOutOfBoundsException outOfBoundException(long offset, long length) {
        return new IndexOutOfBoundsException(String.format("Out of bound access on segment %s; new offset = %d; new length = %d",
                        this, offset, length));
    }

    protected int id() {
        //compute a stable and random id for this memory segment
        return Math.abs(Objects.hash(base(), min(), NONCE));
    }

    static class SegmentSplitter implements Spliterator<MemorySegment> {
        AbstractMemorySegmentImpl segment;
        long elemCount;
        final long elementSize;
        long currentIndex;

        SegmentSplitter(long elementSize, long elemCount, AbstractMemorySegmentImpl segment) {
            this.segment = segment;
            this.elementSize = elementSize;
            this.elemCount = elemCount;
        }

        @Override
        public SegmentSplitter trySplit() {
            if (currentIndex == 0 && elemCount > 1) {
                AbstractMemorySegmentImpl parent = segment;
                long rem = elemCount % 2;
                long split = elemCount / 2;
                long lobound = split * elementSize;
                long hibound = lobound + (rem * elementSize);
                elemCount  = split + rem;
                segment = parent.asSliceNoCheck(lobound, hibound);
                return new SegmentSplitter(elementSize, split, parent.asSliceNoCheck(0, lobound));
            } else {
                return null;
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super MemorySegment> action) {
            Objects.requireNonNull(action);
            if (currentIndex < elemCount) {
                AbstractMemorySegmentImpl acquired = segment.acquire();
                try {
                    action.accept(acquired.asSliceNoCheck(currentIndex * elementSize, elementSize));
                } finally {
                    acquired.closeNoCheck();
                    currentIndex++;
                    if (currentIndex == elemCount) {
                        segment = null;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void forEachRemaining(Consumer<? super MemorySegment> action) {
            Objects.requireNonNull(action);
            if (currentIndex < elemCount) {
                AbstractMemorySegmentImpl acquired = segment.acquire();
                try {
                    if (acquired.isSmall()) {
                        int index = (int) currentIndex;
                        int limit = (int) elemCount;
                        int elemSize = (int) elementSize;
                        for (; index < limit; index++) {
                            action.accept(acquired.asSliceNoCheck(index * elemSize, elemSize));
                        }
                    } else {
                        for (long i = currentIndex ; i < elemCount ; i++) {
                            action.accept(acquired.asSliceNoCheck(i * elementSize, elementSize));
                        }
                    }
                } finally {
                    acquired.closeNoCheck();
                    currentIndex = elemCount;
                    segment = null;
                }
            }
        }

        @Override
        public long estimateSize() {
            return elemCount;
        }

        @Override
        public int characteristics() {
            return NONNULL | SUBSIZED | SIZED | IMMUTABLE | ORDERED;
        }
    }

    // Object methods

    @Override
    public String toString() {
        return "MemorySegment{ id=0x" + Long.toHexString(id()) + " limit: " + length + " }";
    }

    public static AbstractMemorySegmentImpl ofBuffer(ByteBuffer bb) {
        long bbAddress = nioAccess.getBufferAddress(bb);
        Object base = nioAccess.getBufferBase(bb);
        UnmapperProxy unmapper = nioAccess.unmapper(bb);

        int pos = bb.position();
        int limit = bb.limit();
        int size = limit - pos;

        AbstractMemorySegmentImpl bufferSegment = (AbstractMemorySegmentImpl)nioAccess.bufferSegment(bb);
        final MemoryScope bufferScope;
        int modes;
        if (bufferSegment != null) {
            bufferScope = bufferSegment.scope;
            modes = bufferSegment.mask;
        } else {
            bufferScope = MemoryScope.create(bb, null);
            modes = defaultAccessModes(size);
        }
        if (bb.isReadOnly()) {
            modes &= ~WRITE;
        }
        if (base != null) {
            return new HeapMemorySegmentImpl<>(bbAddress + pos, () -> (byte[])base, size, modes, bufferScope);
        } else if (unmapper == null) {
            return new NativeMemorySegmentImpl(bbAddress + pos, size, modes, bufferScope);
        } else {
            return new MappedMemorySegmentImpl(bbAddress + pos, unmapper, size, modes, bufferScope);
        }
    }

    public static final AbstractMemorySegmentImpl NOTHING = new AbstractMemorySegmentImpl(
        0, 0, MemoryScope.createUnchecked(null, null, null)
    ) {
        @Override
        ByteBuffer makeByteBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        long min() {
            return 0;
        }

        @Override
        Object base() {
            return null;
        }

        @Override
        AbstractMemorySegmentImpl dup(long offset, long size, int mask, MemoryScope scope) {
            throw new UnsupportedOperationException();
        }
    };
}
