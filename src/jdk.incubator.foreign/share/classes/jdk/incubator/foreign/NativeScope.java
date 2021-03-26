/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.foreign;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.AbstractNativeScope;
import jdk.internal.foreign.Utils;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A native scope is an abstraction which provides shared temporal bounds for one or more allocations, backed
 * by off-heap memory. Native scopes can be either <em>bounded</em> or <em>unbounded</em>, depending on whether the size
 * of the native scope is known statically. If an application knows before-hand how much memory it needs to allocate,
 * then using a <em>bounded</em> native scope will typically provide better performance than independently allocating the memory
 * for each value (e.g. using {@link MemorySegment#allocateNative(long)}), or using an <em>unbounded</em> native scope.
 * For this reason, using a bounded native scope is recommended in cases where programs might need to emulate native stack allocation.
 * <p>
 * Allocation scopes are thread-confined (see {@link #ownerThread()}; as such, the resulting {@link MemorySegment} instances
 * returned by the native scope will be backed by memory segments confined by the same owner thread as the native scope's
 * owner thread.
 * <p>
 * To allow for more usability, it is possible for a native scope to reclaim ownership of an existing memory segment
 * (see {@link MemorySegment#handoff(NativeScope)}). This might be useful to allow one or more segments which were independently
 * created to share the same life-cycle as a given native scope - which in turns enables a client to group all memory
 * allocation and usage under a single <em>try-with-resources block</em>.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * @apiNote In the future, if the Java language permits, {@link NativeScope}
 * may become a {@code sealed} interface, which would prohibit subclassing except by
 * explicitly permitted types.
 */
public interface NativeScope extends AutoCloseable {

    /**
     * If this native scope is bounded, returns the size, in bytes, of this native scope.
     * @return the size, in bytes, of this native scope (if available).
     */
    OptionalLong byteSize();

    /**
     * The thread owning this native scope.
     * @return the thread owning this native scope.
     */
    Thread ownerThread();

    /**
     * Returns the number of allocated bytes in this native scope.
     * @return the number of allocated bytes in this native scope.
     */
    long allocatedBytes();

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given byte value.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize()} does not conform to the size of a byte value.
     */
    default MemorySegment allocate(ValueLayout layout, byte value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle(byte.class);
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given char value.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize()} does not conform to the size of a char value.
     */
    default MemorySegment allocate(ValueLayout layout, char value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle(char.class);
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given short value.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize()} does not conform to the size of a short value.
     */
    default MemorySegment allocate(ValueLayout layout, short value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle(short.class);
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given int value.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize()} does not conform to the size of a int value.
     */
    default MemorySegment allocate(ValueLayout layout, int value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle(int.class);
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given float value.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize()} does not conform to the size of a float value.
     */
    default MemorySegment allocate(ValueLayout layout, float value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle(float.class);
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given long value.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize()} does not conform to the size of a long value.
     */
    default MemorySegment allocate(ValueLayout layout, long value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle(long.class);
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given double value.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize()} does not conform to the size of a double value.
     */
    default MemorySegment allocate(ValueLayout layout, double value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle(double.class);
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given address value
     * (expressed as an {@link Addressable} instance).
     * The address value might be narrowed according to the platform address size (see {@link MemoryLayouts#ADDRESS}).
     * The segment returned by this method cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     * @throws IllegalArgumentException if {@code layout.byteSize() != MemoryLayouts.ADDRESS.byteSize()}.
     */
    default MemorySegment allocate(ValueLayout layout, Addressable value) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(layout);
        if (MemoryLayouts.ADDRESS.byteSize() != layout.byteSize()) {
            throw new IllegalArgumentException("Layout size mismatch - " + layout.byteSize() + " != " + MemoryLayouts.ADDRESS.byteSize());
        }
        switch ((int)layout.byteSize()) {
            case 4: return allocate(layout, (int)value.address().toRawLongValue());
            case 8: return allocate(layout, value.address().toRawLongValue());
            default: throw new UnsupportedOperationException("Unsupported pointer size"); // should not get here
        }
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given byte array.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize()} does not conform to the size of a byte value.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, byte[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given short array.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize()} does not conform to the size of a short value.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, short[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given char array.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize()} does not conform to the size of a char value.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, char[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given int array.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize()} does not conform to the size of a int value.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, int[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given float array.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize()} does not conform to the size of a float value.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, float[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given long array.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize()} does not conform to the size of a long value.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, long[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given double array.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize()} does not conform to the size of a double value.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, double[] array) {
        return copyArrayWithSwapIfNeeded(array, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocate a block of memory in this native scope with given layout and initialize it with given address array.
     * The address value of each array element might be narrowed according to the platform address size (see {@link MemoryLayouts#ADDRESS}).
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover, the returned
     * segment must conform to the layout alignment constraints.
     * @param elementLayout the element layout of the array to be allocated.
     * @param array the array to be copied on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * array.length)}.
     * @throws IllegalArgumentException if {@code layout.byteSize() != MemoryLayouts.ADDRESS.byteSize()}.
     */
    default MemorySegment allocateArray(ValueLayout elementLayout, Addressable[] array) {
        Objects.requireNonNull(elementLayout);
        Objects.requireNonNull(array);
        Stream.of(array).forEach(Objects::requireNonNull);
        if (MemoryLayouts.ADDRESS.byteSize() != elementLayout.byteSize()) {
            throw new IllegalArgumentException("Layout size mismatch - " + elementLayout.byteSize() + " != " + MemoryLayouts.ADDRESS.byteSize());
        }
        switch ((int)elementLayout.byteSize()) {
            case 4: return copyArrayWithSwapIfNeeded(Stream.of(array)
                            .mapToInt(a -> (int)a.address().toRawLongValue()).toArray(),
                            elementLayout, MemorySegment::ofArray);
            case 8: return copyArrayWithSwapIfNeeded(Stream.of(array)
                            .mapToLong(a -> a.address().toRawLongValue()).toArray(),
                            elementLayout, MemorySegment::ofArray);
            default: throw new UnsupportedOperationException("Unsupported pointer size"); // should not get here
        }
    }

    private <Z> MemorySegment copyArrayWithSwapIfNeeded(Z array, ValueLayout elementLayout,
                                                        Function<Z, MemorySegment> heapSegmentFactory) {
        Objects.requireNonNull(array);
        Objects.requireNonNull(elementLayout);
        Utils.checkPrimitiveCarrierCompat(array.getClass().componentType(), elementLayout);
        MemorySegment addr = allocate(MemoryLayout.ofSequence(Array.getLength(array), elementLayout));
        if (elementLayout.byteSize() == 1 || (elementLayout.order() == ByteOrder.nativeOrder())) {
            addr.copyFrom(heapSegmentFactory.apply(array));
        } else {
            ((AbstractMemorySegmentImpl)addr).copyFromSwap(heapSegmentFactory.apply(array), elementLayout.byteSize());
        }
        return addr;
    }

    /**
     * Allocate a block of memory in this native scope with given layout. The segment returned by this method is
     * associated with a segment which cannot be closed. Moreover, the returned segment must conform to the layout alignment constraints.
     * @param layout the layout of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < layout.byteSize()}.
     */
    default MemorySegment allocate(MemoryLayout layout) {
        Objects.requireNonNull(layout);
        return allocate(layout.byteSize(), layout.byteAlignment());
    }

    /**
     * Allocate a block of memory corresponding to an array with given element layout and size.
     * The segment returned by this method is associated with a segment which cannot be closed.
     * Moreover, the returned segment must conform to the layout alignment constraints. This is equivalent to the
     * following code:
     * <pre>{@code
    allocate(MemoryLayout.ofSequence(size, elementLayout));
     * }</pre>
     * @param elementLayout the array element layout.
     * @param count the array element count.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if this is a
     * bounded allocation scope, and {@code byteSize().getAsLong() - allocatedBytes() < (elementLayout.byteSize() * count)}.
     */
    default MemorySegment allocateArray(MemoryLayout elementLayout, long count) {
        Objects.requireNonNull(elementLayout);
        return allocate(MemoryLayout.ofSequence(count, elementLayout));
    }

    /**
     * Allocate a block of memory in this native scope with given size. The segment returned by this method is
     * associated with a segment which cannot be closed. Moreover, the returned segment must be aligned to {@code size}.
     * @param bytesSize the size (in bytes) of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if
     * {@code limit() - size() < bytesSize}.
     */
    default MemorySegment allocate(long bytesSize) {
        return allocate(bytesSize, bytesSize);
    }

    /**
     * Allocate a block of memory in this native scope with given size and alignment constraint.
     * The segment returned by this method is associated with a segment which cannot be closed. Moreover,
     * the returned segment must be aligned to {@code alignment}.
     * @param bytesSize the size (in bytes) of the block of memory to be allocated.
     * @param bytesAlignment the alignment (in bytes) of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     * @throws OutOfMemoryError if there is not enough space left in this native scope, that is, if
     * {@code limit() - size() < bytesSize}.
     */
    MemorySegment allocate(long bytesSize, long bytesAlignment);

    /**
     * Close this native scope; calling this method will render any segment obtained through this native scope
     * unusable and might release any backing memory resources associated with this native scope.
     */
    @Override
    void close();

    /**
     * Creates a new bounded native scope, backed by off-heap memory.
     * @param size the size of the native scope.
     * @return a new bounded native scope, with given size (in bytes).
     */
    static NativeScope boundedScope(long size) {
        return new AbstractNativeScope.BoundedNativeScope(size);
    }

    /**
     * Creates a new unbounded native scope, backed by off-heap memory.
     * @return a new unbounded native scope.
     */
    static NativeScope unboundedScope() {
        return new AbstractNativeScope.UnboundedNativeScope();
    }
}
