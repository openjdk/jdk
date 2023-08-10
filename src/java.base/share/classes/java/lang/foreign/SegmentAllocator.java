/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.SlicingAllocator;
import jdk.internal.foreign.Utils;
import jdk.internal.javac.PreviewFeature;

/**
 * An object that may be used to allocate {@linkplain MemorySegment memory segments}. Clients implementing this interface
 * must implement the {@link #allocate(long, long)} method. A segment allocator defines several methods
 * which can be useful to create segments from several kinds of Java values such as primitives and arrays.
 * <p>
 * {@code SegmentAllocator} is a {@linkplain FunctionalInterface functional interface}. Clients can easily obtain a new
 * segment allocator by using either a lambda expression or a method reference:
 *
 * {@snippet lang=java :
 * SegmentAllocator autoAllocator = (byteSize, byteAlignment) -> Arena.ofAuto().allocate(byteSize, byteAlignment);
 * }
 * <p>
 * This interface defines factories for commonly used allocators:
 * <ul>
 *     <li>{@link #slicingAllocator(MemorySegment)} obtains an efficient slicing allocator, where memory
 *     is allocated by repeatedly slicing the provided memory segment;</li>
 *     <li>{@link #prefixAllocator(MemorySegment)} obtains an allocator which wraps a segment
 *     and recycles its content upon each new allocation request.</li>
 * </ul>
 * <p>
 * Passing a segment allocator to an API can be especially useful in circumstances where a client wants to communicate <em>where</em>
 * the results of a certain operation (performed by the API) should be stored, as a memory segment. For instance,
 * {@linkplain Linker#downcallHandle(FunctionDescriptor, Linker.Option...) downcall method handles} can accept an additional
 * {@link SegmentAllocator} parameter if the underlying foreign function is known to return a struct by-value. Effectively,
 * the allocator parameter tells the linker where to store the return value of the foreign function.
 *
 * @apiNote Unless otherwise specified, the {@link #allocate(long, long)} method is not thread-safe.
 * Furthermore, memory segments allocated by a segment allocator can be associated with different
 * lifetimes, and can even be backed by overlapping regions of memory. For these reasons, clients should generally
 * only interact with a segment allocator they own.
 * <p>
 * Clients should consider using an {@linkplain Arena arena} instead, which, provides strong thread-safety,
 * lifetime and non-overlapping guarantees.
 *
 * @since 19
 */
@FunctionalInterface
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public interface SegmentAllocator {

    /**
     * {@return a new memory segment with a Java string converted into a UTF-8 encoded, null-terminated C string}
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     * <p>
     * If the given string contains any {@code '\0'} characters, they will be
     * copied as well. This means that, depending on the method used to read
     * the string, such as {@link MemorySegment#getUtf8String(long)}, the string
     * will appear truncated when read again.
     *
     * @implSpec The default implementation for this method copies the contents of the provided Java string
     * into a new memory segment obtained by calling {@code this.allocate(str.length() + 1)}.
     * @param str the Java string to be converted into a C string.
     */
    default MemorySegment allocateUtf8String(String str) {
        Objects.requireNonNull(str);
        return Utils.toCString(str.getBytes(StandardCharsets.UTF_8), this);
    }

    /**
     * {@return a new memory segment initialized with the provided {@code byte} {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(ValueLayout.OfByte layout, byte value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment seg = allocate(layout);
        handle.set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment initialized with the provided {@code char} {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(ValueLayout.OfChar layout, char value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment seg = allocate(layout);
        handle.set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment initialized with the provided {@code short} {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(ValueLayout.OfShort layout, short value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment seg = allocate(layout);
        handle.set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment initialized with the provided {@code int} {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(ValueLayout.OfInt layout, int value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment seg = allocate(layout);
        handle.set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment initialized with the provided {@code float} {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(ValueLayout.OfFloat layout, float value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment seg = allocate(layout);
        handle.set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment initialized with the provided {@code long} {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(ValueLayout.OfLong layout, long value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment seg = allocate(layout);
        handle.set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment initialized with the provided {@code double} {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(ValueLayout.OfDouble layout, double value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment seg = allocate(layout);
        handle.set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment initialized with the address of the provided {@code value} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     * <p>
     * The address value might be narrowed according to the platform address size (see {@link ValueLayout#ADDRESS}).
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  Objects.requireNonNull(value);
     *  MemorySegment seg = allocate(Objects.requireNonNull(layout));
     *  seg.set(layout, 0, value);
     *  return seg;
     * }
     *
     * @param layout the layout of the block of memory to be allocated.
     * @param value  the value to be set in the newly allocated memory segment.
     */
    default MemorySegment allocate(AddressLayout layout, MemorySegment value) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(layout);
        MemorySegment seg = allocate(layout);
        layout.varHandle().set(seg, value);
        return seg;
    }

    /**
     * {@return a new memory segment with a {@linkplain MemorySegment#byteSize() byteSize()} of
     * {@code E*layout.byteSize()} initialized with the provided {@code E} {@code byte} {@code elements} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  int size = Objects.requireNonNull(elements).length;
     *  MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
     *  MemorySegment.copy(elements, 0, seg, elementLayout, 0, size);
     *  return seg;
     * }
     *
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements      the short elements to be copied to the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfByte elementLayout, byte... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * {@return a new memory segment with a {@linkplain MemorySegment#byteSize() byteSize()} of
     * {@code E*layout.byteSize()} initialized with the provided {@code E} {@code short} {@code elements} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  int size = Objects.requireNonNull(elements).length;
     *  MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
     *  MemorySegment.copy(elements, 0, seg, elementLayout, 0, size);
     *  return seg;
     * }
     *
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements      the short elements to be copied to the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfShort elementLayout, short... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * {@return a new memory segment with a {@linkplain MemorySegment#byteSize() byteSize()} of
     * {@code E*layout.byteSize()} initialized with the provided {@code E} {@code char} {@code elements} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  int size = Objects.requireNonNull(elements).length;
     *  MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
     *  MemorySegment.copy(elements, 0, seg, elementLayout, 0, size);
     *  return seg;
     * }
     *
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements      the short elements to be copied to the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfChar elementLayout, char... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * {@return a new memory segment with a {@linkplain MemorySegment#byteSize() byteSize()} of
     * {@code E*layout.byteSize()} initialized with the provided {@code E} {@code int} {@code elements} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  int size = Objects.requireNonNull(elements).length;
     *  MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
     *  MemorySegment.copy(elements, 0, seg, elementLayout, 0, size);
     *  return seg;
     * }
     *
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements      the short elements to be copied to the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfInt elementLayout, int... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * {@return a new memory segment with a {@linkplain MemorySegment#byteSize() byteSize()} of
     * {@code E*layout.byteSize()} initialized with the provided {@code E} {@code float} {@code elements} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  int size = Objects.requireNonNull(elements).length;
     *  MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
     *  MemorySegment.copy(elements, 0, seg, elementLayout, 0, size);
     *  return seg;
     * }
     *
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements      the short elements to be copied to the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfFloat elementLayout, float... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * {@return a new memory segment with a {@linkplain MemorySegment#byteSize() byteSize()} of
     * {@code E*layout.byteSize()} initialized with the provided {@code E} {@code long} {@code elements} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  int size = Objects.requireNonNull(elements).length;
     *  MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
     *  MemorySegment.copy(elements, 0, seg, elementLayout, 0, size);
     *  return seg;
     * }
     *
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements      the short elements to be copied to the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfLong elementLayout, long... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * {@return a new memory segment with a {@linkplain MemorySegment#byteSize() byteSize()} of
     * {@code E*layout.byteSize()} initialized with the provided {@code E} {@code double} {@code elements} as
     * specified by the provided {@code layout} (i.e. byte ordering, alignment and size)}
     *
     * @implSpec The default implementation is equivalent to:
     * {@snippet lang=java :
     *  int size = Objects.requireNonNull(elements).length;
     *  MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
     *  MemorySegment.copy(elements, 0, seg, elementLayout, 0, size);
     *  return seg;
     * }
     *
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements      the short elements to be copied to the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfDouble elementLayout, double... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    private <Z> MemorySegment copyArrayWithSwapIfNeeded(Z array, ValueLayout elementLayout,
                                                        Function<Z, MemorySegment> heapSegmentFactory) {
        int size = Array.getLength(Objects.requireNonNull(array));
        MemorySegment seg = allocateArray(Objects.requireNonNull(elementLayout), size);
        if (size > 0) {
            MemorySegment.copy(heapSegmentFactory.apply(array), elementLayout, 0,
                    seg, elementLayout.withOrder(ByteOrder.nativeOrder()), 0, size);
        }
        return seg;
    }

    /**
     * {@return a new memory segment with the given layout}
     *
     * @implSpec The default implementation for this method calls
     * {@code this.allocate(layout.byteSize(), layout.byteAlignment())}.
     *
     * @param layout the layout of the block of memory to be allocated.
     */
    default MemorySegment allocate(MemoryLayout layout) {
        Objects.requireNonNull(layout);
        return allocate(layout.byteSize(), layout.byteAlignment());
    }

    /**
     * {@return a new memory segment with the given {@code elementLayout} and {@code count}}
     *
     * @implSpec The default implementation for this method calls
     * {@code this.allocate(MemoryLayout.sequenceLayout(count, elementLayout))}.
     *
     * @param elementLayout the array element layout.
     * @param count the array element count.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize() * count} overflows.
     * @throws IllegalArgumentException if {@code count < 0}.
     */
    default MemorySegment allocateArray(MemoryLayout elementLayout, long count) {
        Objects.requireNonNull(elementLayout);
        if (count < 0) {
            throw new IllegalArgumentException("Negative array size");
        }
        return allocate(MemoryLayout.sequenceLayout(count, elementLayout));
    }

    /**
     * {@return a new memory segment with the given {@code byteSize}}
     *
     * @implSpec The default implementation for this method calls
     * {@code this.allocate(byteSize, 1)}.
     *
     * @param byteSize the size (in bytes) of the block of memory to be allocated.
     * @throws IllegalArgumentException if {@code byteSize < 0}
     */
    default MemorySegment allocate(long byteSize) {
        return allocate(byteSize, 1);
    }

    /**
     * {@return a new memory segment with the given {@code byteSize} and {@code byteAlignment}}
     *
     * @param byteSize the size (in bytes) of the block of memory to be allocated.
     * @param byteAlignment the alignment (in bytes) of the block of memory to be allocated.
     * @throws IllegalArgumentException if {@code byteSize < 0}, {@code byteAlignment <= 0},
     * or if {@code byteAlignment} is not a power of 2.
     */
    MemorySegment allocate(long byteSize, long byteAlignment);

    /**
     * Returns a segment allocator which responds to allocation requests by returning consecutive slices
     * obtained from the provided segment. Each new allocation request will return a new slice starting at the
     * current offset (modulo additional padding to satisfy alignment constraint), with given size.
     * <p>
     * The returned allocator throws {@link IndexOutOfBoundsException} when a slice of the provided
     * segment with the requested size and alignment cannot be found.
     *
     * @implNote A slicing allocator is not <em>thread-safe</em>.
     *
     * @param segment the segment which the returned allocator should slice from.
     * @return a new slicing allocator
     */
    static SegmentAllocator slicingAllocator(MemorySegment segment) {
        Objects.requireNonNull(segment);
        return new SlicingAllocator(segment);
    }

    /**
     * Returns a segment allocator which responds to allocation requests by recycling a single segment. Each
     * new allocation request will return a new slice starting at the segment offset {@code 0}, hence the name
     * <em>prefix allocator</em>.
     * Equivalent to (but likely more efficient than) the following code:
     * {@snippet lang=java :
     * MemorySegment segment = ...
     * SegmentAllocator prefixAllocator = (size, align) -> segment.asSlice(0, size, align);
     * }
     * The returned allocator throws {@link IndexOutOfBoundsException} when a slice of the provided
     * segment with the requested size and alignment cannot be found.
     *
     * @apiNote A prefix allocator can be useful to limit allocation requests in case a client
     * knows that they have fully processed the contents of the allocated segment before the subsequent allocation request
     * takes place.
     * @implNote While a prefix allocator is <em>thread-safe</em>, concurrent access on the same recycling
     * allocator might cause a thread to overwrite contents written to the underlying segment by a different thread.
     *
     * @param segment the memory segment to be recycled by the returned allocator.
     * @return an allocator which recycles an existing segment upon each new allocation request.
     */
    static SegmentAllocator prefixAllocator(MemorySegment segment) {
        return (AbstractMemorySegmentImpl)Objects.requireNonNull(segment);
    }
}
