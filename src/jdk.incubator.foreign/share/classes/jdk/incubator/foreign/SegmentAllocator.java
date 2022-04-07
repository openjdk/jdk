/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.foreign;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.ArenaAllocator;
import jdk.internal.foreign.ResourceScopeImpl;
import jdk.internal.foreign.Utils;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

/**
 * This interface models a memory allocator. Clients implementing this interface
 * must implement the {@link #allocate(long, long)} method. This interface defines several default methods
 * which can be useful to create segments from several kinds of Java values such as primitives and arrays.
 * This interface can be seen as a thin wrapper around the basic capabilities for
 * {@linkplain MemorySegment#allocateNative(long, long, ResourceScope) creating} native segments;
 * since {@link SegmentAllocator} is a <em>functional interface</em>,
 * clients can easily obtain a native allocator by using either a lambda expression or a method reference.
 * <p>
 * This interface also defines factories for commonly used allocators:
 * <ul>
 *     <li>{@link #nativeAllocator(ResourceScope)} creates an allocator which
 *     {@linkplain MemorySegment#allocateNative(long, long, ResourceScope) allocates} native segments, backed by a given scope;</li>
 *     <li>{@link #newNativeArena(ResourceScope)} creates a more efficient arena-style native allocator, where memory
 *     is allocated in bigger blocks, which are then sliced accordingly to fit allocation requests;</li>
 *     <li>{@link #prefixAllocator(MemorySegment)} creates an allocator which wraps a segment (either on-heap or off-heap)
 *     and recycles its content upon each new allocation request.</li>
 * </ul>
 * <p>
 * Passing a segment allocator to an API can be especially useful in circumstances where a client wants to communicate <em>where</em>
 * the results of a certain operation (performed by the API) should be stored, as a memory segment. For instance,
 * {@linkplain CLinker#downcallHandle(FunctionDescriptor) downcall method handles} can accept an additional
 * {@link SegmentAllocator} parameter if the underlying native function is known to return a struct by-value. Effectively,
 * the allocator parameter tells the linker runtime where to store the return value of the native function.
 */
@FunctionalInterface
public interface SegmentAllocator {

    /**
     * Converts a Java string into a UTF-8 encoded, null-terminated C string,
     * storing the result into a memory segment.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     *
     * @implSpec the default implementation for this method copies the contents of the provided Java string
     * into a new memory segment obtained by calling {@code this.allocate(str.length() + 1)}.
     * @param str the Java string to be converted into a C string.
     * @return a new native memory segment containing the converted C string.
     */
    default MemorySegment allocateUtf8String(String str) {
        Objects.requireNonNull(str);
        return Utils.toCString(str.getBytes(StandardCharsets.UTF_8), this);
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given byte value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfByte layout, byte value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given char value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfChar layout, char value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given short value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfShort layout, short value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given int value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfInt layout, int value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given float value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfFloat layout, float value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given long value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfLong layout, long value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given double value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfDouble layout, double value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given address value
     * (expressed as an {@link Addressable} instance).
     * The address value might be narrowed according to the platform address size (see {@link ValueLayout#ADDRESS}).
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfAddress layout, Addressable value) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(layout);
        MemorySegment segment = allocate(layout);
        layout.varHandle().set(segment, value.address());
        return segment;
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given byte array.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfByte elementLayout, byte[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given short array.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfShort elementLayout, short[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given char array.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfChar elementLayout, char[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given int array.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfInt elementLayout, int[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given float array.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfFloat elementLayout, float[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given long array.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfLong elementLayout, long[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a memory segment with given layout and initialize it with given double array.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfDouble elementLayout, double[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    private <Z> MemorySegment copyArrayWithSwapIfNeeded(Z array, ValueLayout elementLayout,
                                                        Function<Z, MemorySegment> heapSegmentFactory) {
        Objects.requireNonNull(array);
        Objects.requireNonNull(elementLayout);
        int size = Array.getLength(array);
        MemorySegment addr = allocate(MemoryLayout.sequenceLayout(size, elementLayout));
        MemorySegment.copy(heapSegmentFactory.apply(array), elementLayout, 0,
                addr, elementLayout.withOrder(ByteOrder.nativeOrder()), 0, size);
        return addr;
    }

    /**
     * Allocate a memory segment with given layout.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout.byteSize(), layout.byteAlignment())}.
     * @param layout the layout of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(MemoryLayout layout) {
        Objects.requireNonNull(layout);
        return allocate(layout.byteSize(), layout.byteAlignment());
    }

    /**
     * Allocate a memory segment with given element layout and size.
     * @implSpec the default implementation for this method calls {@code this.allocate(MemoryLayout.sequenceLayout(count, elementLayout))}.
     * @param elementLayout the array element layout.
     * @param count the array element count.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(MemoryLayout elementLayout, long count) {
        Objects.requireNonNull(elementLayout);
        return allocate(MemoryLayout.sequenceLayout(count, elementLayout));
    }

    /**
     * Allocate a memory segment with given size
     * and default alignment constraints (1-byte aligned).
     * @implSpec the default implementation for this method calls {@code this.allocate(bytesSize, 1)}.
     * @param bytesSize the size (in bytes) of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(long bytesSize) {
        return allocate(bytesSize, 1);
    }

    /**
     * Allocate a memory segment with given size and alignment constraints.
     * @param bytesSize the size (in bytes) of the block of memory to be allocated.
     * @param bytesAlignment the alignment (in bytes) of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     */
    MemorySegment allocate(long bytesSize, long bytesAlignment);

    /**
     * Returns a native unbounded arena-based allocator, with predefined block size and maximum arena size,
     * associated with the provided scope. Equivalent to the following code:
     * {@snippet lang=java :
     * SegmentAllocator.newNativeArena(Long.MAX_VALUE, predefinedBlockSize, scope);
     * }
     *
     * @param scope the scope associated with the segments returned by the arena-based allocator.
     * @return a new unbounded arena-based allocator
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     */
    static SegmentAllocator newNativeArena(ResourceScope scope) {
        return newNativeArena(Long.MAX_VALUE, ArenaAllocator.DEFAULT_BLOCK_SIZE, scope);
    }

    /**
     * Returns a native unbounded arena-based allocator, with block size set to the specified arena size, associated with
     * the provided scope, with given arena size. Equivalent to the following code:
     * {@snippet lang=java :
     * SegmentAllocator.newNativeArena(arenaSize, arenaSize, scope);
     * }
     *
     * @param arenaSize the size (in bytes) of the allocation arena.
     * @param scope the scope associated with the segments returned by the arena-based allocator.
     * @return a new unbounded arena-based allocator
     * @throws IllegalArgumentException if {@code arenaSize <= 0}.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     */
    static SegmentAllocator newNativeArena(long arenaSize, ResourceScope scope) {
        return newNativeArena(arenaSize, arenaSize, scope);
    }

    /**
     * Returns a native arena-based allocator, associated with the provided scope, with given arena size and block size.
     * <p>
     * The returned allocator {@linkplain MemorySegment#allocateNative(long, ResourceScope) allocates} a memory segment
     * {@code S} of the specified block size and then responds to allocation requests in one of the following ways:
     * <ul>
     *     <li>if the size of the allocation requests is smaller than the size of {@code S}, and {@code S} has a <em>free</em>
     *     slice {@code S'} which fits that allocation request, return that {@code S'}.
     *     <li>if the size of the allocation requests is smaller than the size of {@code S}, and {@code S} has no <em>free</em>
     *     slices which fits that allocation request, allocate a new segment {@code S'}, which has same size as {@code S}
     *     and set {@code S = S'}; the allocator then tries to respond to the same allocation request again.
     *     <li>if the size of the allocation requests is bigger than the size of {@code S}, allocate a new segment {@code S'},
     *     which has a sufficient size to satisfy the allocation request, and return {@code S'}.
     * </ul>
     * <p>
     * This segment allocator can be useful when clients want to perform multiple allocation requests while avoiding the
     * cost associated with allocating a new off-heap memory region upon each allocation request.
     * <p>
     * The returned allocator might throw an {@link OutOfMemoryError} if the total memory allocated with this allocator
     * exceeds the arena size, or the system capacity. Furthermore, the returned allocator is not thread safe.
     * Concurrent allocation needs to be guarded with synchronization primitives.
     *
     * @param arenaSize the size (in bytes) of the allocation arena.
     * @param blockSize the block size associated with the arena-based allocator.
     * @param scope the scope associated with the segments returned by the arena-based allocator.
     * @return a new unbounded arena-based allocator
     * @throws IllegalArgumentException if {@code blockSize <= 0}, if {@code arenaSize <= 0} or if {@code arenaSize < blockSize}.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     */
    static SegmentAllocator newNativeArena(long arenaSize, long blockSize, ResourceScope scope) {
        Objects.requireNonNull(scope);
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Invalid block size: " + blockSize);
        }
        if (arenaSize <= 0 || arenaSize < blockSize) {
            throw new IllegalArgumentException("Invalid arena size: " + arenaSize);
        }
        return new ArenaAllocator(blockSize, arenaSize, scope);
    }

    /**
     * Returns a segment allocator which responds to allocation requests by recycling a single segment; that is,
     * each new allocation request will return a new slice starting at the segment offset {@code 0} (alignment
     * constraints are ignored by this allocator), hence the name <em>prefix allocator</em>.
     * Equivalent to (but likely more efficient than) the following code:
     * {@snippet lang=java :
     * MemorySegment segment = ...
     * SegmentAllocator prefixAllocator = (size, align) -> segment.asSlice(0, size);
     * }
     * <p>
     * This allocator can be useful to limit allocation requests in case a client
     * knows that they have fully processed the contents of the allocated segment before the subsequent allocation request
     * takes place.
     * <p>
     * While the allocator returned by this method is <em>thread-safe</em>, concurrent access on the same recycling
     * allocator might cause a thread to overwrite contents written to the underlying segment by a different thread.
     *
     * @param segment the memory segment to be recycled by the returned allocator.
     * @return an allocator which recycles an existing segment upon each new allocation request.
     */
    static SegmentAllocator prefixAllocator(MemorySegment segment) {
        Objects.requireNonNull(segment);
        return (AbstractMemorySegmentImpl)segment;
    }

    /**
     * Returns a native allocator, associated with the provided scope. Equivalent to (but likely more efficient than)
     * the following code:
     * {@snippet lang=java :
     * ResourceScope scope = ...
     * SegmentAllocator nativeAllocator = (size, align) -> MemorySegment.allocateNative(size, align, scope);
     * }
     *
     * @param scope the scope associated with the returned allocator.
     * @return a native allocator, associated with the provided scope.
     */
    static SegmentAllocator nativeAllocator(ResourceScope scope) {
        Objects.requireNonNull(scope);
        return (ResourceScopeImpl)scope;
    }

    /**
     * Returns a native allocator which allocates segments in independent {@linkplain ResourceScope#newImplicitScope() implicit scopes}.
     * Equivalent to (but likely more efficient than) the following code:
     * {@snippet lang=java :
     * SegmentAllocator implicitAllocator = (size, align) -> MemorySegment.allocateNative(size, align, ResourceScope.newImplicitScope());
     * }
     *
     * @return a native allocator which allocates segments in independent {@linkplain ResourceScope#newImplicitScope() implicit scopes}.
     */
    static SegmentAllocator implicitAllocator() {
        class Holder {
            static final SegmentAllocator IMPLICIT_ALLOCATOR = (size, align) ->
                    MemorySegment.allocateNative(size, align, ResourceScope.newImplicitScope());
        }
        return Holder.IMPLICIT_ALLOCATOR;
    }
}
