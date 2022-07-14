/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.util.ArraysSupport;
import jdk.internal.util.Preconditions;
import jdk.internal.vm.annotation.ForceInline;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * This abstract class provides an immutable implementation for the {@code MemorySegment} interface. This class contains information
 * about the segment's spatial and temporal bounds; each memory segment implementation is associated with an owner thread which is set at creation time.
 * Access to certain sensitive operations on the memory segment will fail with {@code IllegalStateException} if the
 * segment is either in an invalid state (e.g. it has already been closed) or if access occurs from a thread other
 * than the owner thread. See {@link MemorySessionImpl} for more details on management of temporal bounds. Subclasses
 * are defined for each memory segment kind, see {@link NativeMemorySegmentImpl}, {@link HeapMemorySegmentImpl} and
 * {@link MappedMemorySegmentImpl}.
 */
public abstract non-sealed class AbstractMemorySegmentImpl implements MemorySegment, SegmentAllocator, Scoped, BiFunction<String, List<Number>, RuntimeException> {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    static final long NONCE = new Random().nextLong();

    static final JavaNioAccess nioAccess = SharedSecrets.getJavaNioAccess();

    final long length;
    final boolean readOnly;
    final MemorySession session;

    @ForceInline
    AbstractMemorySegmentImpl(long length, boolean readOnly, MemorySession session) {
        this.length = length;
        this.readOnly = readOnly;
        this.session = session;
    }

    abstract AbstractMemorySegmentImpl dup(long offset, long size, boolean readOnly, MemorySession session);

    abstract ByteBuffer makeByteBuffer();

    @Override
    public AbstractMemorySegmentImpl asReadOnly() {
        return dup(0, length, true, session);
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
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
        return dup(offset, newSize, readOnly, session);
    }

    @Override
    public Spliterator<MemorySegment> spliterator(MemoryLayout elementLayout) {
        Objects.requireNonNull(elementLayout);
        if (elementLayout.byteSize() == 0) {
            throw new IllegalArgumentException("Element layout size cannot be zero");
        }
        Utils.checkElementAlignment(elementLayout, "Element layout alignment greater than its size");
        if (!isAlignedForElement(0, elementLayout)) {
            throw new IllegalArgumentException("Incompatible alignment constraints");
        }
        if (!Utils.isAligned(byteSize(), elementLayout.byteSize())) {
            throw new IllegalArgumentException("Segment size is not a multiple of layout size");
        }
        return new SegmentSplitter(elementLayout.byteSize(), byteSize() / elementLayout.byteSize(),
                this);
    }

    @Override
    public Stream<MemorySegment> elements(MemoryLayout elementLayout) {
        return StreamSupport.stream(spliterator(elementLayout), false);
    }

    @Override
    public final MemorySegment fill(byte value){
        checkAccess(0, length, false);
        SCOPED_MEMORY_ACCESS.setMemory(sessionImpl(), unsafeGetBase(), unsafeGetOffset(), length, value);
        return this;
    }

    @Override
    public MemorySegment allocate(long bytesSize, long bytesAlignment) {
        Utils.checkAllocationSizeAndAlign(bytesSize, bytesAlignment);
        return asSlice(0, bytesSize);
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
            if (get(JAVA_BYTE, 0) != that.get(JAVA_BYTE, 0)) {
                return 0;
            }
            i = vectorizedMismatchLargeForBytes(sessionImpl(), that.sessionImpl(),
                    this.unsafeGetBase(), this.unsafeGetOffset(),
                    that.unsafeGetBase(), that.unsafeGetOffset(),
                    length);
            if (i >= 0) {
                return i;
            }
            long remaining = ~i;
            assert remaining < 8 : "remaining greater than 7: " + remaining;
            i = length - remaining;
        }
        for (; i < length; i++) {
            if (get(JAVA_BYTE, i) != that.get(JAVA_BYTE, i)) {
                return i;
            }
        }
        return thisSize != thatSize ? length : -1;
    }

    /**
     * Mismatch over long lengths.
     */
    private static long vectorizedMismatchLargeForBytes(MemorySessionImpl aSession, MemorySessionImpl bSession,
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
            i = SCOPED_MEMORY_ACCESS.vectorizedMismatch(aSession, bSession,
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
    public MemoryAddress address() {
        throw new UnsupportedOperationException("Cannot obtain address of on-heap segment");
    }

    @Override
    public final ByteBuffer asByteBuffer() {
        checkArraySize("ByteBuffer", 1);
        ByteBuffer _bb = makeByteBuffer();
        if (readOnly) {
            //session is IMMUTABLE - obtain a RO byte buffer
            _bb = _bb.asReadOnlyBuffer();
        }
        return _bb;
    }

    @Override
    public final long byteSize() {
        return length;
    }

    @Override
    public boolean isMapped() {
        return false;
    }

    @Override
    public boolean isNative() {
        return false;
    }

    @Override
    public final Optional<MemorySegment> asOverlappingSlice(MemorySegment other) {
        AbstractMemorySegmentImpl that = (AbstractMemorySegmentImpl)Objects.requireNonNull(other);
        if (unsafeGetBase() == that.unsafeGetBase()) {  // both either native or heap
            final long thisStart = this.unsafeGetOffset();
            final long thatStart = that.unsafeGetOffset();
            final long thisEnd = thisStart + this.byteSize();
            final long thatEnd = thatStart + that.byteSize();

            if (thisStart < thatEnd && thisEnd > thatStart) {  //overlap occurs
                long offsetToThat = this.segmentOffset(that);
                long newOffset = offsetToThat >= 0 ? offsetToThat : 0;
                return Optional.of(asSlice(newOffset, Math.min(this.byteSize() - newOffset, that.byteSize() + offsetToThat)));
            }
        }
        return Optional.empty();
    }

    @Override
    public final long segmentOffset(MemorySegment other) {
        AbstractMemorySegmentImpl that = (AbstractMemorySegmentImpl) Objects.requireNonNull(other);
        if (unsafeGetBase() == that.unsafeGetBase()) {
            return that.unsafeGetOffset() - this.unsafeGetOffset();
        }
        throw new UnsupportedOperationException("Cannot compute offset from native to heap (or vice versa).");
    }

    @Override
    public void load() {
        throw new UnsupportedOperationException("Not a mapped segment");
    }

    @Override
    public void unload() {
        throw new UnsupportedOperationException("Not a mapped segment");
    }

    @Override
    public boolean isLoaded() {
        throw new UnsupportedOperationException("Not a mapped segment");
    }

    @Override
    public void force() {
        throw new UnsupportedOperationException("Not a mapped segment");
    }

    @Override
    public final byte[] toArray(ValueLayout.OfByte elementLayout) {
        return toArray(byte[].class, elementLayout, byte[]::new, MemorySegment::ofArray);
    }

    @Override
    public final short[] toArray(ValueLayout.OfShort elementLayout) {
        return toArray(short[].class, elementLayout, short[]::new, MemorySegment::ofArray);
    }

    @Override
    public final char[] toArray(ValueLayout.OfChar elementLayout) {
        return toArray(char[].class, elementLayout, char[]::new, MemorySegment::ofArray);
    }

    @Override
    public final int[] toArray(ValueLayout.OfInt elementLayout) {
        return toArray(int[].class, elementLayout, int[]::new, MemorySegment::ofArray);
    }

    @Override
    public final float[] toArray(ValueLayout.OfFloat elementLayout) {
        return toArray(float[].class, elementLayout, float[]::new, MemorySegment::ofArray);
    }

    @Override
    public final long[] toArray(ValueLayout.OfLong elementLayout) {
        return toArray(long[].class, elementLayout, long[]::new, MemorySegment::ofArray);
    }

    @Override
    public final double[] toArray(ValueLayout.OfDouble elementLayout) {
        return toArray(double[].class, elementLayout, double[]::new, MemorySegment::ofArray);
    }

    private <Z> Z toArray(Class<Z> arrayClass, ValueLayout elemLayout, IntFunction<Z> arrayFactory, Function<Z, MemorySegment> segmentFactory) {
        int size = checkArraySize(arrayClass.getSimpleName(), (int)elemLayout.byteSize());
        Z arr = arrayFactory.apply(size);
        MemorySegment arrSegment = segmentFactory.apply(arr);
        MemorySegment.copy(this, elemLayout, 0, arrSegment, elemLayout.withOrder(ByteOrder.nativeOrder()), 0, size);
        return arr;
    }

    @ForceInline
    public void checkAccess(long offset, long length, boolean readOnly) {
        if (!readOnly && this.readOnly) {
            throw new UnsupportedOperationException("Attempt to write a read-only segment");
        }
        checkBounds(offset, length);
    }

    public void checkValidState() {
        sessionImpl().checkValidState();
    }

    public abstract long unsafeGetOffset();

    public abstract Object unsafeGetBase();

    // Helper methods

    public abstract long maxAlignMask();

    @ForceInline
    public final boolean isAlignedForElement(long offset, MemoryLayout layout) {
        return (((unsafeGetOffset() + offset) | maxAlignMask()) & (layout.byteAlignment() - 1)) == 0;
    }

    private int checkArraySize(String typeName, int elemSize) {
        if (!Utils.isAligned(length, elemSize)) {
            throw new IllegalStateException(String.format("Segment size is not a multiple of %d. Size: %d", elemSize, length));
        }
        long arraySize = length / elemSize;
        if (arraySize > (Integer.MAX_VALUE - 8)) { //conservative check
            throw new IllegalStateException(String.format("Segment is too large to wrap as %s. Size: %d", typeName, length));
        }
        return (int)arraySize;
    }

    @ForceInline
    void checkBounds(long offset, long length) {
        if (length > 0) {
            Preconditions.checkIndex(offset, this.length - length + 1, this);
        } else if (length < 0 || offset < 0 ||
                offset > this.length - length) {
            throw outOfBoundException(offset, length);
        }
    }

    @Override
    public RuntimeException apply(String s, List<Number> numbers) {
        long offset = numbers.get(0).longValue();
        long length = byteSize() - numbers.get(1).longValue() + 1;
        return outOfBoundException(offset, length);
    }

    @Override
    public MemorySession session() {
        return session;
    }

    private IndexOutOfBoundsException outOfBoundException(long offset, long length) {
        return new IndexOutOfBoundsException(String.format("Out of bound access on segment %s; new offset = %d; new length = %d",
                        this, offset, length));
    }

    protected int id() {
        //compute a stable and random id for this memory segment
        return Math.abs(Objects.hash(unsafeGetBase(), unsafeGetOffset(), NONCE));
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
                    for (long i = currentIndex ; i < elemCount ; i++) {
                        action.accept(acquired.asSliceNoCheck(i * elementSize, elementSize));
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

    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractMemorySegmentImpl that &&
                isNative() == that.isNative() &&
                unsafeGetOffset() == that.unsafeGetOffset() &&
                unsafeGetBase() == that.unsafeGetBase() &&
                length == that.length &&
                session.equals(that.session);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                isNative(),
                unsafeGetOffset(),
                unsafeGetBase(),
                length,
                session
        );
    }

    public static AbstractMemorySegmentImpl ofBuffer(Buffer bb) {
        Objects.requireNonNull(bb);
        long bbAddress = nioAccess.getBufferAddress(bb);
        Object base = nioAccess.getBufferBase(bb);
        UnmapperProxy unmapper = nioAccess.unmapper(bb);

        int pos = bb.position();
        int limit = bb.limit();
        int size = limit - pos;

        AbstractMemorySegmentImpl bufferSegment = (AbstractMemorySegmentImpl)nioAccess.bufferSegment(bb);
        final MemorySession bufferSession;
        if (bufferSegment != null) {
            bufferSession = bufferSegment.session;
        } else {
            bufferSession = MemorySessionImpl.heapSession(bb);
        }
        boolean readOnly = bb.isReadOnly();
        int scaleFactor = getScaleFactor(bb);
        if (base != null) {
            if (base instanceof byte[]) {
                return new HeapMemorySegmentImpl.OfByte(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly);
            } else if (base instanceof short[]) {
                return new HeapMemorySegmentImpl.OfShort(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly);
            } else if (base instanceof char[]) {
                return new HeapMemorySegmentImpl.OfChar(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly);
            } else if (base instanceof int[]) {
                return new HeapMemorySegmentImpl.OfInt(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly);
            } else if (base instanceof float[]) {
                return new HeapMemorySegmentImpl.OfFloat(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly);
            } else if (base instanceof long[]) {
                return new HeapMemorySegmentImpl.OfLong(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly);
            } else if (base instanceof double[]) {
                return new HeapMemorySegmentImpl.OfDouble(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly);
            } else {
                throw new AssertionError("Cannot get here");
            }
        } else if (unmapper == null) {
            return new NativeMemorySegmentImpl(bbAddress + (pos << scaleFactor), size << scaleFactor, readOnly, bufferSession);
        } else {
            // we can ignore scale factor here, a mapped buffer is always a byte buffer, so scaleFactor == 0.
            return new MappedMemorySegmentImpl(bbAddress + pos, unmapper, size, readOnly, bufferSession);
        }
    }

    private static int getScaleFactor(Buffer buffer) {
        if (buffer instanceof ByteBuffer) {
            return 0;
        } else if (buffer instanceof CharBuffer) {
            return 1;
        } else if (buffer instanceof ShortBuffer) {
            return 1;
        } else if (buffer instanceof IntBuffer) {
            return 2;
        } else if (buffer instanceof FloatBuffer) {
            return 2;
        } else if (buffer instanceof LongBuffer) {
            return 3;
        } else if (buffer instanceof DoubleBuffer) {
            return 3;
        } else {
            throw new AssertionError("Cannot get here");
        }
    }
}
