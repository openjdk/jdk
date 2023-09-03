/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;
import java.lang.reflect.Array;
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
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
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
public abstract sealed class AbstractMemorySegmentImpl
        implements MemorySegment, SegmentAllocator, BiFunction<String, List<Number>, RuntimeException>
        permits HeapMemorySegmentImpl, NativeMemorySegmentImpl {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    static final JavaNioAccess NIO_ACCESS = SharedSecrets.getJavaNioAccess();

    final long length;
    final boolean readOnly;
    final MemorySessionImpl scope;

    @ForceInline
    AbstractMemorySegmentImpl(long length, boolean readOnly, MemorySessionImpl scope) {
        this.length = length;
        this.readOnly = readOnly;
        this.scope = scope;
    }

    abstract AbstractMemorySegmentImpl dup(long offset, long size, boolean readOnly, MemorySessionImpl scope);

    abstract ByteBuffer makeByteBuffer();

    @Override
    public AbstractMemorySegmentImpl asReadOnly() {
        return dup(0, length, true, scope);
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

    @Override
    public MemorySegment asSlice(long offset, long newSize, long byteAlignment) {
        checkBounds(offset, newSize);
        Utils.checkAlign(byteAlignment);

        if (!isAlignedForElement(offset, byteAlignment)) {
            throw new IllegalArgumentException("Target offset incompatible with alignment constraints");
        }
        return asSliceNoCheck(offset, newSize);
    }

    @Override
    @CallerSensitive
    public final MemorySegment reinterpret(long newSize, Arena arena, Consumer<MemorySegment> cleanup) {
        Objects.requireNonNull(arena);
        return reinterpretInternal(Reflection.getCallerClass(), newSize,
                MemorySessionImpl.toMemorySession(arena), cleanup);
    }

    @Override
    @CallerSensitive
    public final MemorySegment reinterpret(long newSize) {
        return reinterpretInternal(Reflection.getCallerClass(), newSize, scope, null);
    }

    @Override
    @CallerSensitive
    public final MemorySegment reinterpret(Arena arena, Consumer<MemorySegment> cleanup) {
        Objects.requireNonNull(arena);
        return reinterpretInternal(Reflection.getCallerClass(), byteSize(),
                MemorySessionImpl.toMemorySession(arena), cleanup);
    }

    public MemorySegment reinterpretInternal(Class<?> callerClass, long newSize, Scope scope, Consumer<MemorySegment> cleanup) {
        Reflection.ensureNativeAccess(callerClass, MemorySegment.class, "reinterpret");
        if (newSize < 0) {
            throw new IllegalArgumentException("newSize < 0");
        }
        if (!isNative()) throw new UnsupportedOperationException("Not a native segment");
        Runnable action = cleanup != null ?
                () -> cleanup.accept(NativeMemorySegmentImpl.makeNativeSegmentUnchecked(address(), newSize)) :
                null;
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(address(), newSize,
                (MemorySessionImpl)scope, action);
    }

    private AbstractMemorySegmentImpl asSliceNoCheck(long offset, long newSize) {
        return dup(offset, newSize, readOnly, scope);
    }

    @Override
    public Spliterator<MemorySegment> spliterator(MemoryLayout elementLayout) {
        Objects.requireNonNull(elementLayout);
        if (elementLayout.byteSize() == 0) {
            throw new IllegalArgumentException("Element layout size cannot be zero");
        }
        Utils.checkElementAlignment(elementLayout, "Element layout size is not multiple of alignment");
        if (!isAlignedForElement(0, elementLayout)) {
            throw new IllegalArgumentException("Incompatible alignment constraints");
        }
        if ((byteSize() % elementLayout.byteSize()) != 0) {
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
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        return asSlice(0, byteSize, byteAlignment);
    }

    /**
     * Mismatch over long lengths.
     */
    public static long vectorizedMismatchLargeForBytes(MemorySessionImpl aSession, MemorySessionImpl bSession,
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
        throw notAMappedSegment();
    }

    @Override
    public void unload() {
        throw notAMappedSegment();
    }

    @Override
    public boolean isLoaded() {
        throw notAMappedSegment();
    }

    @Override
    public void force() {
        throw notAMappedSegment();
    }

    private static UnsupportedOperationException notAMappedSegment() {
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
        return isAlignedForElement(offset, layout.byteAlignment());
    }

    @ForceInline
    public final boolean isAlignedForElement(long offset, long byteAlignment) {
        return (((unsafeGetOffset() + offset) | maxAlignMask()) & (byteAlignment - 1)) == 0;
    }

    private int checkArraySize(String typeName, int elemSize) {
        // elemSize is guaranteed to be a power of two, so we can use an alignment check
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
    public Scope scope() {
        return scope;
    }

    @Override
    public boolean isAccessibleBy(Thread thread) {
        return sessionImpl().isAccessibleBy(thread);
    }

    @ForceInline
    public final MemorySessionImpl sessionImpl() {
        return scope;
    }

    private IndexOutOfBoundsException outOfBoundException(long offset, long length) {
        return new IndexOutOfBoundsException(String.format("Out of bound access on segment %s; new offset = %d; new length = %d",
                        this, offset, length));
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
        return "MemorySegment{ " +
                heapBase().map(hb -> "heapBase: " + hb + ", ").orElse("") +
                "address: " + Utils.toHexString(address()) +
                ", byteSize: " + length +
                " }";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractMemorySegmentImpl that &&
                unsafeGetBase() == that.unsafeGetBase() &&
                unsafeGetOffset() == that.unsafeGetOffset();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                unsafeGetOffset(),
                unsafeGetBase());
    }

    public static AbstractMemorySegmentImpl ofBuffer(Buffer bb) {
        Objects.requireNonNull(bb);
        Object base = NIO_ACCESS.getBufferBase(bb);
        if (!bb.isDirect() && base == null) {
            throw new IllegalArgumentException("The provided heap buffer is not backed by an array.");
        }
        long bbAddress = NIO_ACCESS.getBufferAddress(bb);
        UnmapperProxy unmapper = NIO_ACCESS.unmapper(bb);

        int pos = bb.position();
        int limit = bb.limit();
        int size = limit - pos;

        AbstractMemorySegmentImpl bufferSegment = (AbstractMemorySegmentImpl) NIO_ACCESS.bufferSegment(bb);
        boolean readOnly = bb.isReadOnly();
        int scaleFactor = getScaleFactor(bb);
        final MemorySessionImpl bufferScope;
        if (bufferSegment != null) {
            bufferScope = bufferSegment.scope;
        } else {
            bufferScope = MemorySessionImpl.heapSession(bb);
        }
        if (base != null) {
            if (base instanceof byte[]) {
                return new HeapMemorySegmentImpl.OfByte(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly, bufferScope);
            } else if (base instanceof short[]) {
                return new HeapMemorySegmentImpl.OfShort(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly, bufferScope);
            } else if (base instanceof char[]) {
                return new HeapMemorySegmentImpl.OfChar(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly, bufferScope);
            } else if (base instanceof int[]) {
                return new HeapMemorySegmentImpl.OfInt(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly, bufferScope);
            } else if (base instanceof float[]) {
                return new HeapMemorySegmentImpl.OfFloat(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly, bufferScope);
            } else if (base instanceof long[]) {
                return new HeapMemorySegmentImpl.OfLong(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly, bufferScope);
            } else if (base instanceof double[]) {
                return new HeapMemorySegmentImpl.OfDouble(bbAddress + (pos << scaleFactor), base, size << scaleFactor, readOnly, bufferScope);
            } else {
                throw new AssertionError("Cannot get here");
            }
        } else if (unmapper == null) {
            return new NativeMemorySegmentImpl(bbAddress + (pos << scaleFactor), size << scaleFactor, readOnly, bufferScope);
        } else {
            // we can ignore scale factor here, a mapped buffer is always a byte buffer, so scaleFactor == 0.
            return new MappedMemorySegmentImpl(bbAddress + pos, unmapper, size, readOnly, bufferScope);
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

    @ForceInline
    public static void copy(MemorySegment srcSegment, ValueLayout srcElementLayout, long srcOffset,
                            MemorySegment dstSegment, ValueLayout dstElementLayout, long dstOffset,
                            long elementCount) {

        AbstractMemorySegmentImpl srcImpl = (AbstractMemorySegmentImpl)srcSegment;
        AbstractMemorySegmentImpl dstImpl = (AbstractMemorySegmentImpl)dstSegment;
        if (srcElementLayout.byteSize() != dstElementLayout.byteSize()) {
            throw new IllegalArgumentException("Source and destination layouts must have same size");
        }
        Utils.checkElementAlignment(srcElementLayout, "Source layout alignment greater than its size");
        Utils.checkElementAlignment(dstElementLayout, "Destination layout alignment greater than its size");
        if (!srcImpl.isAlignedForElement(srcOffset, srcElementLayout)) {
            throw new IllegalArgumentException("Source segment incompatible with alignment constraints");
        }
        if (!dstImpl.isAlignedForElement(dstOffset, dstElementLayout)) {
            throw new IllegalArgumentException("Destination segment incompatible with alignment constraints");
        }
        long size = elementCount * srcElementLayout.byteSize();
        srcImpl.checkAccess(srcOffset, size, true);
        dstImpl.checkAccess(dstOffset, size, false);
        if (srcElementLayout.byteSize() == 1 || srcElementLayout.order() == dstElementLayout.order()) {
            ScopedMemoryAccess.getScopedMemoryAccess().copyMemory(srcImpl.sessionImpl(), dstImpl.sessionImpl(),
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstImpl.unsafeGetBase(), dstImpl.unsafeGetOffset() + dstOffset, size);
        } else {
            ScopedMemoryAccess.getScopedMemoryAccess().copySwapMemory(srcImpl.sessionImpl(), dstImpl.sessionImpl(),
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstImpl.unsafeGetBase(), dstImpl.unsafeGetOffset() + dstOffset, size, srcElementLayout.byteSize());
        }
    }

    @ForceInline
    public static void copy(MemorySegment srcSegment, ValueLayout srcLayout, long srcOffset,
                            Object dstArray, int dstIndex,
                            int elementCount) {

        long baseAndScale = getBaseAndScale(dstArray.getClass());
        if (dstArray.getClass().componentType() != srcLayout.carrier()) {
            throw new IllegalArgumentException("Incompatible value layout: " + srcLayout);
        }
        int dstBase = (int)baseAndScale;
        long dstWidth = (int)(baseAndScale >> 32); // Use long arithmetics below
        AbstractMemorySegmentImpl srcImpl = (AbstractMemorySegmentImpl)srcSegment;
        Utils.checkElementAlignment(srcLayout, "Source layout alignment greater than its size");
        if (!srcImpl.isAlignedForElement(srcOffset, srcLayout)) {
            throw new IllegalArgumentException("Source segment incompatible with alignment constraints");
        }
        srcImpl.checkAccess(srcOffset, elementCount * dstWidth, true);
        Objects.checkFromIndexSize(dstIndex, elementCount, Array.getLength(dstArray));
        if (dstWidth == 1 || srcLayout.order() == ByteOrder.nativeOrder()) {
            ScopedMemoryAccess.getScopedMemoryAccess().copyMemory(srcImpl.sessionImpl(), null,
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstArray, dstBase + (dstIndex * dstWidth), elementCount * dstWidth);
        } else {
            ScopedMemoryAccess.getScopedMemoryAccess().copySwapMemory(srcImpl.sessionImpl(), null,
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstArray, dstBase + (dstIndex * dstWidth), elementCount * dstWidth, dstWidth);
        }
    }

    @ForceInline
    public static void copy(Object srcArray, int srcIndex,
                            MemorySegment dstSegment, ValueLayout dstLayout, long dstOffset,
                            int elementCount) {

        long baseAndScale = getBaseAndScale(srcArray.getClass());
        if (srcArray.getClass().componentType() != dstLayout.carrier()) {
            throw new IllegalArgumentException("Incompatible value layout: " + dstLayout);
        }
        int srcBase = (int)baseAndScale;
        long srcWidth = (int)(baseAndScale >> 32); // Use long arithmetics below
        Objects.checkFromIndexSize(srcIndex, elementCount, Array.getLength(srcArray));
        AbstractMemorySegmentImpl destImpl = (AbstractMemorySegmentImpl)dstSegment;
        Utils.checkElementAlignment(dstLayout, "Destination layout alignment greater than its size");
        if (!destImpl.isAlignedForElement(dstOffset, dstLayout)) {
            throw new IllegalArgumentException("Destination segment incompatible with alignment constraints");
        }
        destImpl.checkAccess(dstOffset, elementCount * srcWidth, false);
        if (srcWidth == 1 || dstLayout.order() == ByteOrder.nativeOrder()) {
            ScopedMemoryAccess.getScopedMemoryAccess().copyMemory(null, destImpl.sessionImpl(),
                    srcArray, srcBase + (srcIndex * srcWidth),
                    destImpl.unsafeGetBase(), destImpl.unsafeGetOffset() + dstOffset, elementCount * srcWidth);
        } else {
            ScopedMemoryAccess.getScopedMemoryAccess().copySwapMemory(null, destImpl.sessionImpl(),
                    srcArray, srcBase + (srcIndex * srcWidth),
                    destImpl.unsafeGetBase(), destImpl.unsafeGetOffset() + dstOffset, elementCount * srcWidth, srcWidth);
        }
    }

    public static long mismatch(MemorySegment srcSegment, long srcFromOffset, long srcToOffset,
                                MemorySegment dstSegment, long dstFromOffset, long dstToOffset) {
        AbstractMemorySegmentImpl srcImpl = (AbstractMemorySegmentImpl)Objects.requireNonNull(srcSegment);
        AbstractMemorySegmentImpl dstImpl = (AbstractMemorySegmentImpl)Objects.requireNonNull(dstSegment);
        long srcBytes = srcToOffset - srcFromOffset;
        long dstBytes = dstToOffset - dstFromOffset;
        srcImpl.checkAccess(srcFromOffset, srcBytes, true);
        dstImpl.checkAccess(dstFromOffset, dstBytes, true);
        if (dstImpl == srcImpl) {
            srcImpl.checkValidState();
            return -1;
        }

        long bytes = Math.min(srcBytes, dstBytes);
        long i = 0;
        if (bytes > 7) {
            if (srcImpl.get(JAVA_BYTE, srcFromOffset) != dstImpl.get(JAVA_BYTE, dstFromOffset)) {
                return 0;
            }
            i = AbstractMemorySegmentImpl.vectorizedMismatchLargeForBytes(srcImpl.sessionImpl(), dstImpl.sessionImpl(),
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcFromOffset,
                    dstImpl.unsafeGetBase(), dstImpl.unsafeGetOffset() + dstFromOffset,
                    bytes);
            if (i >= 0) {
                return i;
            }
            long remaining = ~i;
            assert remaining < 8 : "remaining greater than 7: " + remaining;
            i = bytes - remaining;
        }
        for (; i < bytes; i++) {
            if (srcImpl.get(JAVA_BYTE, srcFromOffset + i) != dstImpl.get(JAVA_BYTE, dstFromOffset + i)) {
                return i;
            }
        }
        return srcBytes != dstBytes ? bytes : -1;
    }

    private static long getBaseAndScale(Class<?> arrayType) {
        if (arrayType.equals(byte[].class)) {
            return (long) Unsafe.ARRAY_BYTE_BASE_OFFSET | ((long)Unsafe.ARRAY_BYTE_INDEX_SCALE << 32);
        } else if (arrayType.equals(char[].class)) {
            return (long) Unsafe.ARRAY_CHAR_BASE_OFFSET | ((long)Unsafe.ARRAY_CHAR_INDEX_SCALE << 32);
        } else if (arrayType.equals(short[].class)) {
            return (long)Unsafe.ARRAY_SHORT_BASE_OFFSET | ((long)Unsafe.ARRAY_SHORT_INDEX_SCALE << 32);
        } else if (arrayType.equals(int[].class)) {
            return (long)Unsafe.ARRAY_INT_BASE_OFFSET | ((long) Unsafe.ARRAY_INT_INDEX_SCALE << 32);
        } else if (arrayType.equals(float[].class)) {
            return (long)Unsafe.ARRAY_FLOAT_BASE_OFFSET | ((long)Unsafe.ARRAY_FLOAT_INDEX_SCALE << 32);
        } else if (arrayType.equals(long[].class)) {
            return (long)Unsafe.ARRAY_LONG_BASE_OFFSET | ((long)Unsafe.ARRAY_LONG_INDEX_SCALE << 32);
        } else if (arrayType.equals(double[].class)) {
            return (long)Unsafe.ARRAY_DOUBLE_BASE_OFFSET | ((long)Unsafe.ARRAY_DOUBLE_INDEX_SCALE << 32);
        } else {
            throw new IllegalArgumentException("Not a supported array class: " + arrayType.getSimpleName());
        }
    }
}
