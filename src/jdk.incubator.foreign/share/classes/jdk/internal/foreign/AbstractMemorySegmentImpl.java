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

import jdk.incubator.foreign.*;
import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.ForceInline;
import sun.security.action.GetPropertyAction;

import java.io.FileDescriptor;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * This abstract class provides an immutable implementation for the {@code MemorySegment} interface. This class contains information
 * about the segment's spatial and temporal bounds; each memory segment implementation is associated with an owner thread which is set at creation time.
 * Access to certain sensitive operations on the memory segment will fail with {@code IllegalStateException} if the
 * segment is either in an invalid state (e.g. it has already been closed) or if access occurs from a thread other
 * than the owner thread. See {@link MemoryScope} for more details on management of temporal bounds. Subclasses
 * are defined for each memory segment kind, see {@link NativeMemorySegmentImpl}, {@link HeapMemorySegmentImpl} and
 * {@link MappedMemorySegmentImpl}.
 */
public abstract class AbstractMemorySegmentImpl extends MemorySegmentProxy implements MemorySegment {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

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

    @Override
    public AbstractMemorySegmentImpl asSlice(long offset) {
        checkBounds(offset, 0);
        return asSliceNoCheck(offset, length - offset);
    }

    private AbstractMemorySegmentImpl asSliceNoCheck(long offset, long newSize) {
        return dup(offset, newSize, mask, scope);
    }

    @Override
    public Spliterator<MemorySegment> spliterator(SequenceLayout sequenceLayout) {
        Objects.requireNonNull(sequenceLayout);
        checkValidState();
        if (sequenceLayout.byteSize() != byteSize()) {
            throw new IllegalArgumentException();
        }
        return new SegmentSplitter(sequenceLayout.elementLayout().byteSize(), sequenceLayout.elementCount().getAsLong(),
                withAccessModes(accessModes() & ~CLOSE));
    }

    @Override
    public final MemorySegment fill(byte value){
        checkAccess(0, length, false);
        SCOPED_MEMORY_ACCESS.setMemory(scope, base(), min(), length, value);
        return this;
    }

    public void copyFrom(MemorySegment src) {
        AbstractMemorySegmentImpl that = (AbstractMemorySegmentImpl)Objects.requireNonNull(src);
        long size = that.byteSize();
        checkAccess(0, size, false);
        that.checkAccess(0, size, true);
        SCOPED_MEMORY_ACCESS.copyMemory(scope, that.scope,
                that.base(), that.min(),
                base(), min(), size);
    }

    public void copyFromSwap(MemorySegment src, long elemSize) {
        AbstractMemorySegmentImpl that = (AbstractMemorySegmentImpl)src;
        long size = that.byteSize();
        checkAccess(0, size, false);
        that.checkAccess(0, size, true);
        SCOPED_MEMORY_ACCESS.copySwapMemory(scope, that.scope,
                        that.base(), that.min(),
                        base(), min(), size, elemSize);
    }

    @Override
    public long mismatch(MemorySegment other) {
        AbstractMemorySegmentImpl that = (AbstractMemorySegmentImpl)Objects.requireNonNull(other);
        final long thisSize = this.byteSize();
        final long thatSize = that.byteSize();
        final long length = Math.min(thisSize, thatSize);
        this.checkAccess(0, length, true);
        that.checkAccess(0, length, true);
        if (this == other) {
            checkValidState();
            return -1;
        }

        long i = 0;
        if (length > 7) {
            if (MemoryAccess.getByte(this) != MemoryAccess.getByte(that)) {
                return 0;
            }
            i = vectorizedMismatchLargeForBytes(scope, that.scope,
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
        for (; i < length; i++) {
            if (MemoryAccess.getByteAtOffset(this, i) != MemoryAccess.getByteAtOffset(that, i)) {
                return i;
            }
        }
        return thisSize != thatSize ? length : -1;
    }

    /**
     * Mismatch over long lengths.
     */
    private static long vectorizedMismatchLargeForBytes(MemoryScope aScope, MemoryScope bScope,
                                                       Object a, long aOffset,
                                                       Object b, long bOffset,
                                                       long length) {
        long off = 0;
        long remaining = length;
        int i, size;
        boolean lastSubRange = false;
        while (remaining > 7 && !lastSubRange) {
            if (remaining > Integer.MAX_VALUE) {
                size = Integer.MAX_VALUE;
            } else {
                size = (int) remaining;
                lastSubRange = true;
            }
            i = SCOPED_MEMORY_ACCESS.vectorizedMismatch(aScope, bScope,
                    a, aOffset + off,
                    b, bOffset + off,
                    size, ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE);
            if (i >= 0)
                return off + i;

            i = size - ~i;
            off += i;
            remaining -= i;
        }
        return ~remaining;
    }

    @Override
    @ForceInline
    public final MemoryAddress address() {
        checkValidState();
        return new MemoryAddressImpl(base(), min());
    }

    @Override
    public final ByteBuffer asByteBuffer() {
        if (!isSet(READ)) {
            throw unsupportedAccessMode(READ);
        }
        checkArraySize("ByteBuffer", 1);
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

    public MemorySegment handoff(Thread thread) {
        Objects.requireNonNull(thread);
        checkValidState();
        if (!isSet(HANDOFF)) {
            throw unsupportedAccessMode(HANDOFF);
        }
        try {
            return dup(0L, length, mask, scope.confineTo(thread));
        } finally {
            //flush read/writes to segment memory before returning the new segment
            VarHandle.fullFence();
        }
    }

    @Override
    public MemorySegment share() {
        checkValidState();
        if (!isSet(SHARE)) {
            throw unsupportedAccessMode(SHARE);
        }
        try {
            return dup(0L, length, mask, scope.share());
        } finally {
            //flush read/writes to segment memory before returning the new segment
            VarHandle.fullFence();
        }
    }

    @Override
    public MemorySegment handoff(NativeScope scope) {
        Objects.requireNonNull(scope);
        checkValidState();
        if (!isSet(HANDOFF)) {
            throw unsupportedAccessMode(HANDOFF);
        }
        if (!isSet(CLOSE)) {
            throw unsupportedAccessMode(CLOSE);
        }
        MemorySegment dup = handoff(scope.ownerThread());
        ((AbstractNativeScope)scope).register(dup);
        return dup.withAccessModes(accessModes() & (READ | WRITE));
    }

    @Override
    public MemorySegment registerCleaner(Cleaner cleaner) {
        Objects.requireNonNull(cleaner);
        checkValidState();
        if (!isSet(CLOSE)) {
            throw unsupportedAccessMode(CLOSE);
        }
        return dup(0L, length, mask, scope.cleanable(cleaner));
    }

    @Override
    public final void close() {
        checkValidState();
        if (!isSet(CLOSE)) {
            throw unsupportedAccessMode(CLOSE);
        }
        scope.close();
    }

    @Override
    public boolean isMapped() {
        return false;
    }

    @Override
    public final byte[] toByteArray() {
        return toArray(byte[].class, 1, byte[]::new, MemorySegment::ofArray);
    }

    @Override
    public final short[] toShortArray() {
        return toArray(short[].class, 2, short[]::new, MemorySegment::ofArray);
    }

    @Override
    public final char[] toCharArray() {
        return toArray(char[].class, 2, char[]::new, MemorySegment::ofArray);
    }

    @Override
    public final int[] toIntArray() {
        return toArray(int[].class, 4, int[]::new, MemorySegment::ofArray);
    }

    @Override
    public final float[] toFloatArray() {
        return toArray(float[].class, 4, float[]::new, MemorySegment::ofArray);
    }

    @Override
    public final long[] toLongArray() {
        return toArray(long[].class, 8, long[]::new, MemorySegment::ofArray);
    }

    @Override
    public final double[] toDoubleArray() {
        return toArray(double[].class, 8, double[]::new, MemorySegment::ofArray);
    }

    private <Z> Z toArray(Class<Z> arrayClass, int elemSize, IntFunction<Z> arrayFactory, Function<Z, MemorySegment> segmentFactory) {
        int size = checkArraySize(arrayClass.getSimpleName(), elemSize);
        Z arr = arrayFactory.apply(size);
        MemorySegment arrSegment = segmentFactory.apply(arr);
        arrSegment.copyFrom(this);
        return arr;
    }

    @Override
    public boolean isSmall() {
        return isSet(SMALL);
    }

    @Override
    public void checkAccess(long offset, long length, boolean readOnly) {
        if (!readOnly && !isSet(WRITE)) {
            throw unsupportedAccessMode(WRITE);
        } else if (readOnly && !isSet(READ)) {
            throw unsupportedAccessMode(READ);
        }
        checkBounds(offset, length);
    }

    private void checkAccessAndScope(long offset, long length, boolean readOnly) {
        checkValidState();
        checkAccess(offset, length, readOnly);
    }

    private void checkValidState() {
        try {
            scope.checkValidState();
        } catch (ScopedMemoryAccess.Scope.ScopedAccessError ex) {
            throw new IllegalStateException("This segment is already closed");
        }
    }

    @Override
    public long unsafeGetOffset() {
        return min();
    }

    @Override
    public Object unsafeGetBase() {
        return base();
    }

    // Helper methods

    private boolean isSet(int mask) {
        return (this.mask & mask) != 0;
    }

    private int checkArraySize(String typeName, int elemSize) {
        if (length % elemSize != 0) {
            throw new UnsupportedOperationException(String.format("Segment size is not a multiple of %d. Size: %d", elemSize, length));
        }
        long arraySize = length / elemSize;
        if (arraySize > (Integer.MAX_VALUE - 8)) { //conservative check
            throw new UnsupportedOperationException(String.format("Segment is too large to wrap as %s. Size: %d", typeName, length));
        }
        return (int)arraySize;
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

    @Override
    public MemoryScope scope() {
        return scope;
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
        if ((mode & SHARE) != 0) {
            modes.add("SHARE");
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
                AbstractMemorySegmentImpl acquired = segment;
                try {
                    action.accept(acquired.asSliceNoCheck(currentIndex * elementSize, elementSize));
                } finally {
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
                AbstractMemorySegmentImpl acquired = segment;
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
        Objects.requireNonNull(bb);
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
            bufferScope = MemoryScope.createConfined(bb, MemoryScope.DUMMY_CLEANUP_ACTION, null);
            modes = defaultAccessModes(size);
        }
        if (bb.isReadOnly()) {
            modes &= ~WRITE;
        }
        if (base != null) {
            return new HeapMemorySegmentImpl.OfByte(bbAddress + pos, (byte[])base, size, modes, bufferScope);
        } else if (unmapper == null) {
            return new NativeMemorySegmentImpl(bbAddress + pos, size, modes, bufferScope);
        } else {
            return new MappedMemorySegmentImpl(bbAddress + pos, unmapper, size, modes, bufferScope);
        }
    }
}
