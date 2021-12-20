/*
 *  Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.HeapMemorySegmentImpl;
import jdk.internal.foreign.MappedMemorySegmentImpl;
import jdk.internal.foreign.ResourceScopeImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Spliterator;
import java.util.stream.Stream;

/**
 * A memory segment models a contiguous region of memory. A memory segment is associated with both spatial
 * and temporal bounds (e.g. a {@link ResourceScope}). Spatial bounds ensure that memory access operations on a memory segment cannot affect a memory location
 * which falls <em>outside</em> the boundaries of the memory segment being accessed. Temporal bounds ensure that memory access
 * operations on a segment cannot occur <em>after</em> the resource scope associated with a memory segment has been closed (see {@link ResourceScope#close()}).
 * <p>
 * All implementations of this interface must be <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>;
 * programmers should treat instances that are {@linkplain Object#equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may occur. For example, in a future release,
 * synchronization may fail. The {@code equals} method should be used for comparisons.
 * <p>
 * Non-platform classes should not implement {@linkplain MemorySegment} directly.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * <h2>Constructing memory segments</h2>
 *
 * There are multiple ways to obtain a memory segment. First, memory segments backed by off-heap memory can
 * be allocated using one of the many factory methods provided (see {@link MemorySegment#allocateNative(MemoryLayout, ResourceScope)},
 * {@link MemorySegment#allocateNative(long, ResourceScope)} and {@link MemorySegment#allocateNative(long, long, ResourceScope)}). Memory segments obtained
 * in this way are called <em>native memory segments</em>.
 * <p>
 * It is also possible to obtain a memory segment backed by an existing heap-allocated Java array,
 * using one of the provided factory methods (e.g. {@link MemorySegment#ofArray(int[])}). Memory segments obtained
 * in this way are called <em>array memory segments</em>.
 * <p>
 * It is possible to obtain a memory segment backed by an existing Java byte buffer (see {@link ByteBuffer}),
 * using the factory method {@link MemorySegment#ofByteBuffer(ByteBuffer)}.
 * Memory segments obtained in this way are called <em>buffer memory segments</em>. Note that buffer memory segments might
 * be backed by native memory (as in the case of native memory segments) or heap memory (as in the case of array memory segments),
 * depending on the characteristics of the byte buffer instance the segment is associated with. For instance, a buffer memory
 * segment obtained from a byte buffer created with the {@link ByteBuffer#allocateDirect(int)} method will be backed
 * by native memory.
 *
 * <h2>Mapping memory segments from files</h2>
 *
 * It is also possible to obtain a native memory segment backed by a memory-mapped file using the factory method
 * {@link MemorySegment#mapFile(Path, long, long, FileChannel.MapMode, ResourceScope)}. Such native memory segments are
 * called <em>mapped memory segments</em>; mapped memory segments are associated with an underlying file descriptor.
 * <p>
 * Contents of mapped memory segments can be {@linkplain #force() persisted} and {@linkplain #load() loaded} to and from the underlying file;
 * these capabilities are suitable replacements for some capabilities in the {@link java.nio.MappedByteBuffer} class.
 * Note that, while it is possible to map a segment into a byte buffer (see {@link MemorySegment#asByteBuffer()}),
 * and then call e.g. {@link java.nio.MappedByteBuffer#force()} that way, this can only be done when the source segment
 * is small enough, due to the size limitation inherent to the ByteBuffer API.
 * <p>
 * Clients requiring sophisticated, low-level control over mapped memory segments, should consider writing
 * custom mapped memory segment factories; using {@link CLinker}, e.g. on Linux, it is possible to call {@code mmap}
 * with the desired parameters; the returned address can be easily wrapped into a memory segment, using
 * {@link MemoryAddress#ofLong(long)} and {@link MemorySegment#ofAddress(MemoryAddress, long, ResourceScope)}.
 *
 * <h2>Restricted native segments</h2>
 *
 * Sometimes it is necessary to turn a memory address obtained from native code into a memory segment with
 * full spatial, temporal and confinement bounds. To do this, clients can {@link #ofAddress(MemoryAddress, long, ResourceScope) obtain}
 * a native segment <em>unsafely</em> from a give memory address, by providing the segment size, as well as the segment {@linkplain ResourceScope scope}.
 * This is a <a href="package-summary.html#restricted"><em>restricted</em></a> operation and should be used with
 * caution: for instance, an incorrect segment size could result in a VM crash when attempting to dereference
 * the memory segment.
 *
 * <h2>Dereference</h2>
 *
 * A memory segment can be read or written using various methods provided in this class (e.g. {@link #get(ValueLayout.OfInt, long)}).
 * Each dereference method takes a {@linkplain jdk.incubator.foreign.ValueLayout value layout}, which specifies the size,
 * alignment constraints, byte order as well as the Java type associated with the dereference operation, and an offset.
 * For instance, to read an int from a segment, using {@link ByteOrder#nativeOrder() default endianness}, the following code can be used:
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * int value = segment.get(ValueLayout.JAVA_INT, 0);
 * }
 *
 * If the value to be read is stored in memory using {@link ByteOrder#BIG_ENDIAN big-endian} encoding, the dereference operation
 * can be expressed as follows:
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * int value = segment.get(ValueLayout.JAVA_INT.withOrder(BIG_ENDIAN), 0);
 * }
 *
 * For more complex dereference operations (e.g. structured memory access), clients can obtain a <em>memory access var handle</em>,
 * that is, a var handle that accepts a segment and, optionally, one or more additional {@code long} coordinates. Memory
 * access var handles can be obtained from {@linkplain MemoryLayout#varHandle(MemoryLayout.PathElement...) memory layouts}
 * by providing a so called <a href="MemoryLayout.html#layout-paths"><em>layout path</em></a>.
 * Alternatively, clients can obtain raw memory access var handles from a given
 * {@linkplain MemoryHandles#varHandle(ValueLayout) value layout}, and then adapt it using the var handle combinator
 * functions defined in the {@link MemoryHandles} class.
 *
 * <h2>Lifecycle and confinement</h2>
 *
 * Memory segments are associated with a resource scope (see {@link ResourceScope}), which can be accessed using
 * the {@link #scope()} method. As for all resources associated with a resource scope, a segment cannot be
 * accessed after its corresponding scope has been closed. For instance, the following code will result in an
 * exception:
 * {@snippet lang=java :
 * MemorySegment segment = null;
 * try (ResourceScope scope = ResourceScope.newConfinedScope()) {
 *     segment = MemorySegment.allocateNative(8, scope);
 * }
 * segment.get(ValueLayout.JAVA_LONG, 0); // already closed!
 * }
 * Additionally, access to a memory segment is subject to the thread-confinement checks enforced by the owning scope; that is,
 * if the segment is associated with a shared scope, it can be accessed by multiple threads; if it is associated with a confined
 * scope, it can only be accessed by the thread which owns the scope.
 * <p>
 * Heap and buffer segments are always associated with a <em>global</em>, shared scope. This scope cannot be closed,
 * and segments associated with it can be considered as <em>always alive</em>.
 *
 * <h2>Memory segment views</h2>
 *
 * Memory segments support <em>views</em>. For instance, it is possible to create an <em>immutable</em> view of a memory segment, as follows:
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * MemorySegment roSegment = segment.asReadOnly();
 * }
 * It is also possible to create views whose spatial bounds are stricter than the ones of the original segment
 * (see {@link MemorySegment#asSlice(long, long)}).
 * <p>
 * Temporal bounds of the original segment are inherited by the views; that is, when the scope associated with a segment
 * is closed, all the views associated with that segment will also be rendered inaccessible.
 * <p>
 * To allow for interoperability with existing code, a byte buffer view can be obtained from a memory segment
 * (see {@link #asByteBuffer()}). This can be useful, for instance, for those clients that want to keep using the
 * {@link ByteBuffer} API, but need to operate on large memory segments. Byte buffers obtained in such a way support
 * the same spatial and temporal access restrictions associated with the memory segment from which they originated.
 *
 * <h2>Stream support</h2>
 *
 * A client might obtain a {@link Stream} from a segment, which can then be used to slice the segment (according to a given
 * element layout) and even allow multiple threads to work in parallel on disjoint segment slices
 * (to do this, the segment has to be associated with a shared scope). The following code can be used to sum all int
 * values in a memory segment in parallel:
 *
 * {@snippet lang=java :
 * try (ResourceScope scope = ResourceScope.newSharedScope()) {
 *     SequenceLayout SEQUENCE_LAYOUT = MemoryLayout.sequenceLayout(1024, ValueLayout.JAVA_INT);
 *     MemorySegment segment = MemorySegment.allocateNative(SEQUENCE_LAYOUT, scope);
 *     int sum = segment.elements(ValueLayout.JAVA_INT).parallel()
 *                      .mapToInt(s -> s.get(ValueLayout.JAVA_INT, 0))
 *                      .sum();
 * }
 * }
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public sealed interface MemorySegment extends Addressable permits AbstractMemorySegmentImpl {

    /**
     * The base memory address associated with this native memory segment.
     * @throws UnsupportedOperationException if this segment is not a {@linkplain #isNative() native} segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @return The base memory address.
     */
    @Override
    MemoryAddress address();

    /**
     * Returns a spliterator for this memory segment. The returned spliterator reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, {@link Spliterator#IMMUTABLE}, {@link Spliterator#NONNULL} and {@link Spliterator#ORDERED}
     * characteristics.
     * <p>
     * The returned spliterator splits this segment according to the specified element layout; that is,
     * if the supplied layout has size N, then calling {@link Spliterator#trySplit()} will result in a spliterator serving
     * approximately {@code S/N/2} elements (depending on whether N is even or not), where {@code S} is the size of
     * this segment. As such, splitting is possible as long as {@code S/N >= 2}. The spliterator returns segments that
     * are associated with the same scope as this segment.
     * <p>
     * The returned spliterator effectively allows to slice this segment into disjoint {@linkplain #asSlice(long, long) slices},
     * which can then be processed in parallel by multiple threads.
     *
     * @param elementLayout the layout to be used for splitting.
     * @return the element spliterator for this segment
     * @throws IllegalArgumentException if the {@code elementLayout} size is zero, or the segment size modulo the
     * {@code elementLayout} size is greater than zero.
     */
    Spliterator<MemorySegment> spliterator(MemoryLayout elementLayout);

    /**
     * Returns a sequential {@code Stream} over disjoint slices (whose size matches that of the specified layout)
     * in this segment. Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * StreamSupport.stream(segment.spliterator(elementLayout), false);
     * }
     *
     * @param elementLayout the layout to be used for splitting.
     * @return a sequential {@code Stream} over disjoint slices in this segment.
     * @throws IllegalArgumentException if the {@code elementLayout} size is zero, or the segment size modulo the
     * {@code elementLayout} size is greater than zero.
     */
    Stream<MemorySegment> elements(MemoryLayout elementLayout);

    /**
     * Returns the resource scope associated with this memory segment.
     * @return the resource scope associated with this memory segment.
     */
    ResourceScope scope();

    /**
     * The size (in bytes) of this memory segment.
     * @return The size (in bytes) of this memory segment.
     */
    long byteSize();

    /**
     * Obtains a new memory segment view whose base address is the same as the base address of this segment plus a given offset,
     * and whose new size is specified by the given argument.
     *
     * @see #asSlice(long)
     *
     * @param offset The new segment base offset (relative to the current segment base address), specified in bytes.
     * @param newSize The new segment size, specified in bytes.
     * @return a new memory segment view with updated base/limit addresses.
     * @throws IndexOutOfBoundsException if {@code offset < 0}, {@code offset > byteSize()}, {@code newSize < 0}, or {@code newSize > byteSize() - offset}
     */
    MemorySegment asSlice(long offset, long newSize);

    /**
     * Obtains a new memory segment view whose base address is the same as the base address of this segment plus a given offset,
     * and whose new size is computed by subtracting the specified offset from this segment size.
     * <p>
     * Equivalent to the following code:
     * {@snippet lang=java :
     * asSlice(offset, byteSize() - offset);
     * }
     *
     * @see #asSlice(long, long)
     *
     * @param offset The new segment base offset (relative to the current segment base address), specified in bytes.
     * @return a new memory segment view with updated base/limit addresses.
     * @throws IndexOutOfBoundsException if {@code offset < 0}, or {@code offset > byteSize()}.
     */
    default MemorySegment asSlice(long offset) {
        return asSlice(offset, byteSize() - offset);
    }

    /**
     * Is this segment read-only?
     * @return {@code true}, if this segment is read-only.
     * @see #asReadOnly()
     */
    boolean isReadOnly();

    /**
     * Obtains a read-only view of this segment. The resulting segment will be identical to this one, but
     * attempts to overwrite the contents of the returned segment will cause runtime exceptions.
     * @return a read-only view of this segment
     * @see #isReadOnly()
     */
    MemorySegment asReadOnly();

    /**
     * Is this a native segment? Returns true if this segment is a native memory segment,
     * created using the {@link #allocateNative(long, ResourceScope)} (and related) factory, or a buffer segment
     * derived from a direct {@link java.nio.ByteBuffer} using the {@link #ofByteBuffer(ByteBuffer)} factory,
     * or if this is a {@linkplain #isMapped() mapped} segment.
     * @return {@code true} if this segment is native segment.
     */
    boolean isNative();

    /**
     * Is this a mapped segment? Returns true if this segment is a mapped memory segment,
     * created using the {@link #mapFile(Path, long, long, FileChannel.MapMode, ResourceScope)} factory, or a buffer segment
     * derived from a {@link java.nio.MappedByteBuffer} using the {@link #ofByteBuffer(ByteBuffer)} factory.
     * @return {@code true} if this segment is a mapped segment.
     */
    boolean isMapped();

    /**
     * Returns a slice of this segment that is the overlap between this and
     * the provided segment.
     *
     * <p>Two segments {@code S1} and {@code S2} are said to overlap if it is possible to find
     * at least two slices {@code L1} (from {@code S1}) and {@code L2} (from {@code S2}) that are backed by the
     * same memory region. As such, it is not possible for a
     * {@link #isNative() native} segment to overlap with a heap segment; in
     * this case, or when no overlap occurs, {@code null} is returned.
     *
     * @param other the segment to test for an overlap with this segment.
     * @return a slice of this segment, or {@code null} if no overlap occurs.
     */
    MemorySegment asOverlappingSlice(MemorySegment other);

    /**
     * Returns the offset, in bytes, of the provided segment, relative to this
     * segment.
     *
     * <p>The offset is relative to the base address of this segment and can be
     * a negative or positive value. For instance, if both segments are native
     * segments, the resulting offset can be computed as follows:
     *
     * {@snippet lang=java :
     * other.baseAddress().toRawLongValue() - segment.baseAddress().toRawLongValue()
     * }
     *
     * If the segments share the same base address, {@code 0} is returned. If
     * {@code other} is a slice of this segment, the offset is always
     * {@code 0 <= x < this.byteSize()}.
     *
     * @param other the segment to retrieve an offset to.
     * @return the relative offset, in bytes, of the provided segment.
     */
    long segmentOffset(MemorySegment other);

    /**
     * Fills a value into this memory segment.
     * <p>
     * More specifically, the given value is filled into each address of this
     * segment. Equivalent to (but likely more efficient than) the following code:
     *
     * {@snippet lang=java :
     * byteHandle = MemoryLayout.ofSequence(ValueLayout.JAVA_BYTE)
     *         .varHandle(byte.class, MemoryLayout.PathElement.sequenceElement());
     * for (long l = 0; l < segment.byteSize(); l++) {
     *     byteHandle.set(segment.address(), l, value);
     * }
     * }
     *
     * without any regard or guarantees on the ordering of particular memory
     * elements being set.
     * <p>
     * Fill can be useful to initialize or reset the memory of a segment.
     *
     * @param value the value to fill into this segment
     * @return this memory segment
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws UnsupportedOperationException if this segment is read-only (see {@link #isReadOnly()}).
     */
    MemorySegment fill(byte value);

    /**
     * Performs a bulk copy from given source segment to this segment. More specifically, the bytes at
     * offset {@code 0} through {@code src.byteSize() - 1} in the source segment are copied into this segment
     * at offset {@code 0} through {@code src.byteSize() - 1}.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * MemorySegment.copy(src, 0, this, 0, src.byteSize);
     * }
     * @param src the source segment.
     * @throws IndexOutOfBoundsException if {@code src.byteSize() > this.byteSize()}.
     * @throws IllegalStateException if either the scope associated with the source segment or the scope associated
     * with this segment have been already closed, or if access occurs from a thread other than the thread owning either
     * scopes.
     * @throws UnsupportedOperationException if this segment is read-only (see {@link #isReadOnly()}).
     * @return this segment.
     */
    default MemorySegment copyFrom(MemorySegment src) {
        MemorySegment.copy(src, 0, this, 0, src.byteSize());
        return this;
    }

    /**
     * Finds and returns the offset, in bytes, of the first mismatch between
     * this segment and a given other segment. The offset is relative to the
     * {@linkplain #address() base address} of each segment and will be in the
     * range of 0 (inclusive) up to the {@linkplain #byteSize() size} (in bytes) of
     * the smaller memory segment (exclusive).
     * <p>
     * If the two segments share a common prefix then the returned offset is
     * the length of the common prefix, and it follows that there is a mismatch
     * between the two segments at that offset within the respective segments.
     * If one segment is a proper prefix of the other, then the returned offset is
     * the smallest of the segment sizes, and it follows that the offset is only
     * valid for the larger segment. Otherwise, there is no mismatch and {@code
     * -1} is returned.
     *
     * @param other the segment to be tested for a mismatch with this segment
     * @return the relative offset, in bytes, of the first mismatch between this
     * and the given other segment, otherwise -1 if no mismatch
     * @throws IllegalStateException if either the scope associated with this segment or the scope associated
     * with the {@code other} segment have been already closed, or if access occurs from a thread other than the thread
     * owning either scopes.
     */
    long mismatch(MemorySegment other);

    /**
     * Tells whether the contents of this mapped segment is resident in physical
     * memory.
     *
     * <p> A return value of {@code true} implies that it is highly likely
     * that all the data in this segment is resident in physical memory and
     * may therefore be accessed without incurring any virtual-memory page
     * faults or I/O operations.  A return value of {@code false} does not
     * necessarily imply that this segment's content is not resident in physical
     * memory.
     *
     * <p> The returned value is a hint, rather than a guarantee, because the
     * underlying operating system may have paged out some of this segment's data
     * by the time that an invocation of this method returns.  </p>
     *
     * @return  {@code true} if it is likely that the contents of this segment
     *          is resident in physical memory
     *
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws UnsupportedOperationException if this segment is not a mapped memory segment, e.g. if
     * {@code isMapped() == false}.
     */
    boolean isLoaded();

    /**
     * Loads the contents of this mapped segment into physical memory.
     *
     * <p> This method makes a best effort to ensure that, when it returns,
     * this contents of this segment is resident in physical memory.  Invoking this
     * method may cause some number of page faults and I/O operations to
     * occur. </p>
     *
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws UnsupportedOperationException if this segment is not a mapped memory segment, e.g. if
     * {@code isMapped() == false}.
     */
    void load();

    /**
     * Unloads the contents of this mapped segment from physical memory.
     *
     * <p> This method makes a best effort to ensure that the contents of this segment are
     * are no longer resident in physical memory. Accessing this segment's contents
     * after invoking this method may cause some number of page faults and I/O operations to
     * occur (as this segment's contents might need to be paged back in). </p>
     *
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws UnsupportedOperationException if this segment is not a mapped memory segment, e.g. if
     * {@code isMapped() == false}.
     */
    void unload();

    /**
     * Forces any changes made to the contents of this mapped segment to be written to the
     * storage device described by the mapped segment's file descriptor.
     *
     * <p> If the file descriptor associated with this mapped segment resides on a local storage
     * device then when this method returns it is guaranteed that all changes
     * made to this segment since it was created, or since this method was last
     * invoked, will have been written to that device.
     *
     * <p> If the file descriptor associated with this mapped segment does not reside on a local device then
     * no such guarantee is made.
     *
     * <p> If this segment was not mapped in read/write mode ({@link
     * java.nio.channels.FileChannel.MapMode#READ_WRITE}) then
     * invoking this method may have no effect. In particular, the
     * method has no effect for segments mapped in read-only or private
     * mapping modes. This method may or may not have an effect for
     * implementation-specific mapping modes.
     * </p>
     *
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws UnsupportedOperationException if this segment is not a mapped memory segment, e.g. if
     * {@code isMapped() == false}.
     * @throws UncheckedIOException if there is an I/O error writing the contents of this segment to the associated storage device
     */
    void force();

    /**
     * Wraps this segment in a {@link ByteBuffer}. Some properties of the returned buffer are linked to
     * the properties of this segment. For instance, if this segment is <em>immutable</em>
     * (e.g. the segment is a read-only segment, see {@link #isReadOnly()}), then the resulting buffer is <em>read-only</em>
     * (see {@link ByteBuffer#isReadOnly()}). Additionally, if this is a native memory segment, the resulting buffer is
     * <em>direct</em> (see {@link ByteBuffer#isDirect()}).
     * <p>
     * The returned buffer's position (see {@link ByteBuffer#position()}) is initially set to zero, while
     * the returned buffer's capacity and limit (see {@link ByteBuffer#capacity()} and {@link ByteBuffer#limit()}, respectively)
     * are set to this segment' size (see {@link MemorySegment#byteSize()}). For this reason, a byte buffer cannot be
     * returned if this segment' size is greater than {@link Integer#MAX_VALUE}.
     * <p>
     * The life-cycle of the returned buffer will be tied to that of this segment. That is, accessing the returned buffer
     * after the scope associated with this segment has been closed (see {@link ResourceScope#close()}), will throw an {@link IllegalStateException}.
     * <p>
     * If this segment is associated with a confined scope, calling read/write I/O operations on the resulting buffer
     * might result in an unspecified exception being thrown. Examples of such problematic operations are
     * {@link java.nio.channels.AsynchronousSocketChannel#read(ByteBuffer)} and
     * {@link java.nio.channels.AsynchronousSocketChannel#write(ByteBuffer)}.
     * <p>
     * Finally, the resulting buffer's byte order is {@link java.nio.ByteOrder#BIG_ENDIAN}; this can be changed using
     * {@link ByteBuffer#order(java.nio.ByteOrder)}.
     *
     * @return a {@link ByteBuffer} view of this memory segment.
     * @throws UnsupportedOperationException if this segment cannot be mapped onto a {@link ByteBuffer} instance,
     * e.g. because it models a heap-based segment that is not based on a {@code byte[]}), or if its size is greater
     * than {@link Integer#MAX_VALUE}.
     */
    ByteBuffer asByteBuffer();

    /**
     * Copy the contents of this memory segment into a fresh byte array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @return a fresh byte array copy of this memory segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope, or if this segment's contents cannot be copied into a {@link byte[]} instance,
     * e.g. its size is greater than {@link Integer#MAX_VALUE}.
     */
    byte[] toArray(ValueLayout.OfByte elementLayout);

    /**
     * Copy the contents of this memory segment into a fresh short array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @return a fresh short array copy of this memory segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope, or if this segment's contents cannot be copied into a {@link short[]} instance,
     * e.g. because {@code byteSize() % 2 != 0}, or {@code byteSize() / 2 > Integer#MAX_VALUE}
     */
    short[] toArray(ValueLayout.OfShort elementLayout);

    /**
     * Copy the contents of this memory segment into a fresh char array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @return a fresh char array copy of this memory segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope, or if this segment's contents cannot be copied into a {@link char[]} instance,
     * e.g. because {@code byteSize() % 2 != 0}, or {@code byteSize() / 2 > Integer#MAX_VALUE}.
     */
    char[] toArray(ValueLayout.OfChar elementLayout);

    /**
     * Copy the contents of this memory segment into a fresh int array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @return a fresh int array copy of this memory segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope, or if this segment's contents cannot be copied into a {@link int[]} instance,
     * e.g. because {@code byteSize() % 4 != 0}, or {@code byteSize() / 4 > Integer#MAX_VALUE}.
     */
    int[] toArray(ValueLayout.OfInt elementLayout);

    /**
     * Copy the contents of this memory segment into a fresh float array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @return a fresh float array copy of this memory segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope, or if this segment's contents cannot be copied into a {@link float[]} instance,
     * e.g. because {@code byteSize() % 4 != 0}, or {@code byteSize() / 4 > Integer#MAX_VALUE}.
     */
    float[] toArray(ValueLayout.OfFloat elementLayout);

    /**
     * Copy the contents of this memory segment into a fresh long array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @return a fresh long array copy of this memory segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope, or if this segment's contents cannot be copied into a {@link long[]} instance,
     * e.g. because {@code byteSize() % 8 != 0}, or {@code byteSize() / 8 > Integer#MAX_VALUE}.
     */
    long[] toArray(ValueLayout.OfLong elementLayout);

    /**
     * Copy the contents of this memory segment into a fresh double array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @return a fresh double array copy of this memory segment.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope, or if this segment's contents cannot be copied into a {@link double[]} instance,
     * e.g. because {@code byteSize() % 8 != 0}, or {@code byteSize() / 8 > Integer#MAX_VALUE}.
     */
    double[] toArray(ValueLayout.OfDouble elementLayout);

    /**
     * Reads a UTF-8 encoded, null-terminated string from this segment at given offset.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a Java string constructed from the bytes read from the given starting address up to (but not including)
     * the first {@code '\0'} terminator character (assuming one is found).
     * @throws IllegalArgumentException if the size of the native string is greater than the largest string supported by the platform.
     * @throws IllegalStateException if the size of the native string is greater than the size of this segment,
     * or if the scope associated with this segment has been closed, or if access occurs from a thread other than the thread owning that scope.
     */
    default String getUtf8String(long offset) {
        return SharedUtils.toJavaStringInternal(this, offset);
    }

    /**
     * Writes the given string into this segment at given offset, converting it to a null-terminated byte sequence using UTF-8 encoding.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param str the Java string to be written into this segment.
     * @throws IllegalArgumentException if the size of the native string is greater than the largest string supported by the platform.
     * @throws IllegalStateException if the size of the native string is greater than the size of this segment,
     * or if the scope associated with this segment has been closed, or if access occurs from a thread other than the thread owning that scope.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    default void setUtf8String(long offset, String str) {
        Utils.toCString(str.getBytes(StandardCharsets.UTF_8), SegmentAllocator.prefixAllocator(asSlice(offset)));
    }


    /**
     * Creates a new buffer memory segment that models the memory associated with the given byte
     * buffer. The segment starts relative to the buffer's position (inclusive)
     * and ends relative to the buffer's limit (exclusive).
     * <p>
     * If the buffer is {@link ByteBuffer#isReadOnly() read-only}, the resulting segment will also be
     * {@link ByteBuffer#isReadOnly() read-only}. The scope associated with this segment can either be the
     * {@linkplain ResourceScope#globalScope() global} resource scope, in case the buffer has been created independently,
     * or some other resource scope, in case the buffer has been obtained using {@link #asByteBuffer()}.
     * <p>
     * The resulting memory segment keeps a reference to the backing buffer, keeping it <em>reachable</em>.
     *
     * @param bb the byte buffer backing the buffer memory segment.
     * @return a new buffer memory segment.
     */
    static MemorySegment ofByteBuffer(ByteBuffer bb) {
        return AbstractMemorySegmentImpl.ofBuffer(bb);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated byte array.
     * The returned segment is associated with the {@linkplain ResourceScope#globalScope() global} resource scope.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(byte[] arr) {
        return HeapMemorySegmentImpl.OfByte.fromArray(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated char array.
     * The returned segment is associated with the {@linkplain ResourceScope#globalScope() global} resource scope.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(char[] arr) {
        return HeapMemorySegmentImpl.OfChar.fromArray(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated short array.
     * The returned segment is associated with the {@linkplain ResourceScope#globalScope() global} resource scope.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(short[] arr) {
        return HeapMemorySegmentImpl.OfShort.fromArray(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated int array.
     * The returned segment is associated with the {@linkplain ResourceScope#globalScope() global} resource scope.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(int[] arr) {
        return HeapMemorySegmentImpl.OfInt.fromArray(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated float array.
     * The returned segment is associated with the {@linkplain ResourceScope#globalScope() global} resource scope.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(float[] arr) {
        return HeapMemorySegmentImpl.OfFloat.fromArray(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated long array.
     * The returned segment is associated with the {@linkplain ResourceScope#globalScope() global} resource scope.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(long[] arr) {
        return HeapMemorySegmentImpl.OfLong.fromArray(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated double array.
     * The returned segment is associated with the {@linkplain ResourceScope#globalScope() global} resource scope.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(double[] arr) {
        return HeapMemorySegmentImpl.OfDouble.fromArray(arr);
    }


    /**
     * Creates a new native memory segment with given size and resource scope, and whose base address is the given address.
     * This method can be useful when interacting with custom
     * native memory sources (e.g. custom allocators), where an address to some
     * underlying memory region is typically obtained from native code (often as a plain {@code long} value).
     * The returned segment is not read-only (see {@link MemorySegment#isReadOnly()}), and is associated with the
     * provided resource scope.
     * <p>
     * Clients should ensure that the address and bounds refer to a valid region of memory that is accessible for reading and,
     * if appropriate, writing; an attempt to access an invalid memory location from Java code will either return an arbitrary value,
     * have no visible effect, or cause an unspecified exception to be thrown.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     *
     * @param address the returned segment's base address.
     * @param bytesSize the desired size.
     * @param scope the native segment scope.
     * @return a new native memory segment with given base address, size and scope.
     * @throws IllegalArgumentException if {@code bytesSize <= 0}.
     * @throws IllegalStateException if the provided scope has been already closed,
     * or if access occurs from a thread other than the thread owning the scope.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static MemorySegment ofAddress(MemoryAddress address, long bytesSize, ResourceScope scope) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Objects.requireNonNull(address);
        Objects.requireNonNull(scope);
        if (bytesSize <= 0) {
            throw new IllegalArgumentException("Invalid size : " + bytesSize);
        }
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(address, bytesSize, (ResourceScopeImpl)scope);
    }

    /**
     * Creates a new native memory segment that models a newly allocated block of off-heap memory with given layout
     * and resource scope. A client is responsible make sure that the resource scope associated with the returned segment is closed
     * when the segment is no longer in use. Failure to do so will result in off-heap memory leaks.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang=java :
     * allocateNative(layout.bytesSize(), layout.bytesAlignment(), scope);
     * }
     * <p>
     * The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     *
     * @param layout the layout of the off-heap memory block backing the native memory segment.
     * @param scope the segment scope.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if the specified layout has illegal size or alignment constraint.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     */
    static MemorySegment allocateNative(MemoryLayout layout, ResourceScope scope) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(layout);
        return allocateNative(layout.byteSize(), layout.byteAlignment(), scope);
    }

    /**
     * Creates a new native memory segment that models a newly allocated block of off-heap memory with given size (in bytes)
     * and resource scope. A client is responsible make sure that the resource scope associated with the returned segment is closed
     * when the segment is no longer in use. Failure to do so will result in off-heap memory leaks.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang=java :
     * allocateNative(bytesSize, 1, scope);
     * }
     * <p>
     * The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     *
     * @param bytesSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param scope the segment scope.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize <= 0}.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     */
    static MemorySegment allocateNative(long bytesSize, ResourceScope scope) {
        return allocateNative(bytesSize, 1, scope);
    }

    /**
     * Creates a new native memory segment that models a newly allocated block of off-heap memory with given size
     * (in bytes), alignment constraint (in bytes) and resource scope. A client is responsible make sure that the resource
     * scope associated with the returned segment is closed when the segment is no longer in use.
     * Failure to do so will result in off-heap memory leaks.
     * <p>
     * The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     *
     * @param bytesSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param alignmentBytes the alignment constraint (in bytes) of the off-heap memory block backing the native memory segment.
     * @param scope the segment scope.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize <= 0}, {@code alignmentBytes <= 0}, or if {@code alignmentBytes}
     * is not a power of 2.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     */
    static MemorySegment allocateNative(long bytesSize, long alignmentBytes, ResourceScope scope) {
        Objects.requireNonNull(scope);
        if (bytesSize <= 0) {
            throw new IllegalArgumentException("Invalid allocation size : " + bytesSize);
        }

        if (alignmentBytes <= 0 ||
                ((alignmentBytes & (alignmentBytes - 1)) != 0L)) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + alignmentBytes);
        }

        return NativeMemorySegmentImpl.makeNativeSegment(bytesSize, alignmentBytes, (ResourceScopeImpl) scope);
    }

    /**
     * Creates a new mapped memory segment that models a memory-mapped region of a file from a given path.
     * <p>
     * If the specified mapping mode is {@linkplain FileChannel.MapMode#READ_ONLY READ_ONLY}, the resulting segment
     * will be read-only (see {@link #isReadOnly()}).
     * <p>
     * The content of a mapped memory segment can change at any time, for example
     * if the content of the corresponding region of the mapped file is changed by
     * this (or another) program.  Whether such changes occur, and when they
     * occur, is operating-system dependent and therefore unspecified.
     * <p>
     * All or part of a mapped memory segment may become
     * inaccessible at any time, for example if the backing mapped file is truncated.  An
     * attempt to access an inaccessible region of a mapped memory segment will not
     * change the segment's content and will cause an unspecified exception to be
     * thrown either at the time of the access or at some later time.  It is
     * therefore strongly recommended that appropriate precautions be taken to
     * avoid the manipulation of a mapped file by this (or another) program, except to read or write
     * the file's content.
     *
     * @implNote When obtaining a mapped segment from a newly created file, the initialization state of the contents of the block
     * of mapped memory associated with the returned mapped memory segment is unspecified and should not be relied upon.
     *
     * @param path the path to the file to memory map.
     * @param bytesOffset the offset (expressed in bytes) within the file at which the mapped segment is to start.
     * @param bytesSize the size (in bytes) of the mapped memory backing the memory segment.
     * @param mapMode a file mapping mode, see {@link FileChannel#map(FileChannel.MapMode, long, long)}; the mapping mode
     *                might affect the behavior of the returned memory mapped segment (see {@link #force()}).
     * @param scope the segment scope.
     * @return a new mapped memory segment.
     * @throws IllegalArgumentException if {@code bytesOffset < 0}, {@code bytesSize < 0}, or if {@code path} is not associated
     * with the default file system.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     * @throws UnsupportedOperationException if an unsupported map mode is specified.
     * @throws IOException if the specified path does not point to an existing file, or if some other I/O error occurs.
     * @throws  SecurityException If a security manager is installed, and it denies an unspecified permission required by the implementation.
     * In the case of the default provider, the {@link SecurityManager#checkRead(String)} method is invoked to check
     * read access if the file is opened for reading. The {@link SecurityManager#checkWrite(String)} method is invoked to check
     * write access if the file is opened for writing.
     */
    static MemorySegment mapFile(Path path, long bytesOffset, long bytesSize, FileChannel.MapMode mapMode, ResourceScope scope) throws IOException {
        Objects.requireNonNull(scope);
        return MappedMemorySegmentImpl.makeMappedSegment(path, bytesOffset, bytesSize, mapMode, (ResourceScopeImpl) scope);
    }

    /**
     * Performs a bulk copy from source segment to destination segment. More specifically, the bytes at offset
     * {@code srcOffset} through {@code srcOffset + bytes - 1} in the source segment are copied into the destination
     * segment at offset {@code dstOffset} through {@code dstOffset + bytes - 1}.
     * <p>
     * If the source segment overlaps with this segment, then the copying is performed as if the bytes at
     * offset {@code srcOffset} through {@code srcOffset + bytes - 1} in the source segment were first copied into a
     * temporary segment with size {@code bytes}, and then the contents of the temporary segment were copied into
     * the destination segment at offset {@code dstOffset} through {@code dstOffset + bytes - 1}.
     * <p>
     * The result of a bulk copy is unspecified if, in the uncommon case, the source segment and the destination segment
     * do not overlap, but refer to overlapping regions of the same backing storage using different addresses.
     * For example, this may occur if the same file is {@linkplain MemorySegment#mapFile mapped} to two segments.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * MemorySegment.copy(srcSegment, ValueLayout.JAVA_BYTE, srcOffset, dstSegment, ValueLayout.JAVA_BYTE, dstOffset, bytes);
     * }
     * @param srcSegment the source segment.
     * @param srcOffset the starting offset, in bytes, of the source segment.
     * @param dstSegment the destination segment.
     * @param dstOffset the starting offset, in bytes, of the destination segment.
     * @param bytes the number of bytes to be copied.
     * @throws IllegalStateException if either the scope associated with the source segment or the scope associated
     * with the destination segment have been already closed, or if access occurs from a thread other than the thread
     * owning either scopes.
     * @throws IndexOutOfBoundsException if {@code srcOffset + bytes > srcSegment.byteSize()} or if
     * {@code dstOffset + bytes > dstSegment.byteSize()}, or if either {@code srcOffset}, {@code dstOffset}
     * or {@code bytes} are {@code < 0}.
     * @throws UnsupportedOperationException if the destination segment is read-only (see {@link #isReadOnly()}).
     */
    @ForceInline
    static void copy(MemorySegment srcSegment, long srcOffset, MemorySegment dstSegment, long dstOffset, long bytes) {
        copy(srcSegment, ValueLayout.JAVA_BYTE, srcOffset, dstSegment, ValueLayout.JAVA_BYTE, dstOffset, bytes);
    }

    /**
     * Performs a bulk copy from source segment to destination segment. More specifically, if {@code S} is the byte size
     * of the element layouts, the bytes at offset {@code srcOffset} through {@code srcOffset + (elementCount * S) - 1}
     * in the source segment are copied into the destination segment at offset {@code dstOffset} through {@code dstOffset + (elementCount * S) - 1}.
     * <p>
     * The copy occurs in an element-wise fashion: the bytes in the source segment are interpreted as a sequence of elements
     * whose layout is {@code srcElementLayout}, whereas the bytes in the destination segment are interpreted as a sequence of
     * elements whose layout is {@code dstElementLayout}. Both element layouts must have same size {@code S}.
     * If the byte order of the two element layouts differ, the bytes corresponding to each element to be copied
     * are swapped accordingly during the copy operation.
     * <p>
     * If the source segment overlaps with this segment, then the copying is performed as if the bytes at
     * offset {@code srcOffset} through {@code srcOffset + (elementCount * S) - 1} in the source segment were first copied into a
     * temporary segment with size {@code bytes}, and then the contents of the temporary segment were copied into
     * the destination segment at offset {@code dstOffset} through {@code dstOffset + (elementCount * S) - 1}.
     * <p>
     * The result of a bulk copy is unspecified if, in the uncommon case, the source segment and the destination segment
     * do not overlap, but refer to overlapping regions of the same backing storage using different addresses.
     * For example, this may occur if the same file is {@linkplain MemorySegment#mapFile mapped} to two segments.
     * @param srcSegment the source segment.
     * @param srcElementLayout the element layout associated with the source segment.
     * @param srcOffset the starting offset, in bytes, of the source segment.
     * @param dstSegment the destination segment.
     * @param dstElementLayout the element layout associated with the destination segment.
     * @param dstOffset the starting offset, in bytes, of the destination segment.
     * @param elementCount the number of elements to be copied.
     * @throws IllegalArgumentException if the element layouts have different sizes, if the source offset is incompatible
     * with the alignment constraints in the source element layout, or if the destination offset is incompatible with the
     * alignment constraints in the destination element layout.
     * @throws IllegalStateException if either the scope associated with the source segment or the scope associated
     * with the destination segment have been already closed, or if access occurs from a thread other than the thread
     * owning either scopes.
     * @throws IndexOutOfBoundsException if {@code srcOffset + (elementCount * S) > srcSegment.byteSize()} or if
     * {@code dstOffset + (elementCount * S) > dstSegment.byteSize()}, where {@code S} is the byte size
     * of the element layouts, or if either {@code srcOffset}, {@code dstOffset} or {@code elementCount} are {@code < 0}.
     * @throws UnsupportedOperationException if the destination segment is read-only (see {@link #isReadOnly()}).
     */
    @ForceInline
    static void copy(MemorySegment srcSegment, ValueLayout srcElementLayout, long srcOffset, MemorySegment dstSegment,
                     ValueLayout dstElementLayout, long dstOffset, long elementCount) {
        Objects.requireNonNull(srcSegment);
        Objects.requireNonNull(srcElementLayout);
        Objects.requireNonNull(dstSegment);
        Objects.requireNonNull(dstElementLayout);
        AbstractMemorySegmentImpl srcImpl = (AbstractMemorySegmentImpl)srcSegment;
        AbstractMemorySegmentImpl dstImpl = (AbstractMemorySegmentImpl)dstSegment;
        if (srcElementLayout.byteSize() != dstElementLayout.byteSize()) {
            throw new IllegalArgumentException("Source and destination layouts must have same sizes");
        }
        if (srcOffset % srcElementLayout.byteAlignment() != 0) {
            throw new IllegalArgumentException("Source segment incompatible with alignment constraints");
        }
        if (dstOffset % dstElementLayout.byteAlignment() != 0) {
            throw new IllegalArgumentException("Target segment incompatible with alignment constraints");
        }
        long size = elementCount * srcElementLayout.byteSize();
        srcImpl.checkAccess(srcOffset, size, true);
        dstImpl.checkAccess(dstOffset, size, false);
        if (srcElementLayout.byteSize() == 1 || srcElementLayout.order() == dstElementLayout.order()) {
            ScopedMemoryAccess.getScopedMemoryAccess().copyMemory(srcImpl.scope(), dstImpl.scope(),
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstImpl.unsafeGetBase(), dstImpl.unsafeGetOffset() + dstOffset, size);
        } else {
            ScopedMemoryAccess.getScopedMemoryAccess().copySwapMemory(srcImpl.scope(), dstImpl.scope(),
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstImpl.unsafeGetBase(), dstImpl.unsafeGetOffset() + dstOffset, size, srcElementLayout.byteSize());
        }
    }

    /**
     * Reads a byte from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a byte value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default byte get(ValueLayout.OfByte layout, long offset) {
        return (byte)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a byte to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the byte value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfByte layout, long offset, byte value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a boolean from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a boolean value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default boolean get(ValueLayout.OfBoolean layout, long offset) {
        return (boolean)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a boolean to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the boolean value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfBoolean layout, long offset, boolean value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a char from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a char value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default char get(ValueLayout.OfChar layout, long offset) {
        return (char)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a char to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the char value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfChar layout, long offset, char value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a short from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a short value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default short get(ValueLayout.OfShort layout, long offset) {
        return (short)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a short to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the short value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfShort layout, long offset, short value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads an int from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return an int value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default int get(ValueLayout.OfInt layout, long offset) {
        return (int)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes an int to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the int value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfInt layout, long offset, int value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a float from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a float value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default float get(ValueLayout.OfFloat layout, long offset) {
        return (float)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a float to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the float value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfFloat layout, long offset, float value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a long from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a long value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default long get(ValueLayout.OfLong layout, long offset) {
        return (long)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a long to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the long value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfLong layout, long offset, long value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a double from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a double value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default double get(ValueLayout.OfDouble layout, long offset) {
        return (double)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a double to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the double value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfDouble layout, long offset, double value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads an address from this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return an address value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default MemoryAddress get(ValueLayout.OfAddress layout, long offset) {
        return (MemoryAddress)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes an address to this segment and offset with given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the address value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfAddress layout, long offset, Addressable value) {
        layout.accessHandle().set(this, offset, value.address());
    }

    /**
     * Reads a char from this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a char value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default char getAtIndex(ValueLayout.OfChar layout, long index) {
        return (char)layout.accessHandle().get(this, Utils.scaleOffset(this, index, layout.byteSize()));
    }

    /**
     * Writes a char to this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the char value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfChar layout, long index, char value) {
        layout.accessHandle().set(this, Utils.scaleOffset(this, index, layout.byteSize()), value);
    }

    /**
     * Reads a short from this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a short value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default short getAtIndex(ValueLayout.OfShort layout, long index) {
        return (short)layout.accessHandle().get(this, Utils.scaleOffset(this, index, layout.byteSize()));
    }

    /**
     * Writes a short to this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the short value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfShort layout, long index, short value) {
        layout.accessHandle().set(this, Utils.scaleOffset(this, index, layout.byteSize()), value);
    }

    /**
     * Reads an int from this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return an int value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default int getAtIndex(ValueLayout.OfInt layout, long index) {
        return (int)layout.accessHandle().get(this, Utils.scaleOffset(this, index, layout.byteSize()));
    }

    /**
     * Writes an int to this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the int value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfInt layout, long index, int value) {
        layout.accessHandle().set(this, Utils.scaleOffset(this, index, layout.byteSize()), value);
    }

    /**
     * Reads a float from this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a float value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default float getAtIndex(ValueLayout.OfFloat layout, long index) {
        return (float)layout.accessHandle().get(this, Utils.scaleOffset(this, index, layout.byteSize()));
    }

    /**
     * Writes a float to this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the float value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfFloat layout, long index, float value) {
        layout.accessHandle().set(this, Utils.scaleOffset(this, index, layout.byteSize()), value);
    }

    /**
     * Reads a long from this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a long value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default long getAtIndex(ValueLayout.OfLong layout, long index) {
        return (long)layout.accessHandle().get(this, Utils.scaleOffset(this, index, layout.byteSize()));
    }

    /**
     * Writes a long to this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the long value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfLong layout, long index, long value) {
        layout.accessHandle().set(this, Utils.scaleOffset(this, index, layout.byteSize()), value);
    }

    /**
     * Reads a double from this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a double value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default double getAtIndex(ValueLayout.OfDouble layout, long index) {
        return (double)layout.accessHandle().get(this, Utils.scaleOffset(this, index, layout.byteSize()));
    }

    /**
     * Writes a double to this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the double value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfDouble layout, long index, double value) {
        layout.accessHandle().set(this, Utils.scaleOffset(this, index, layout.byteSize()), value);
    }

    /**
     * Reads an address from this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return an address value read from this address.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default MemoryAddress getAtIndex(ValueLayout.OfAddress layout, long index) {
        return (MemoryAddress)layout.accessHandle().get(this, Utils.scaleOffset(this, index, layout.byteSize()));
    }

    /**
     * Writes an address to this segment and index, scaled by given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the address value to be written.
     * @throws IllegalStateException if the scope associated with this segment has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfAddress layout, long index, Addressable value) {
        layout.accessHandle().set(this, Utils.scaleOffset(this, index, layout.byteSize()), value.address());
    }


    /**
     * Copies a number of elements from a source segment to a destination array,
     * starting at a given segment offset (expressed in bytes), and a given array index, using the given source element layout.
     * Supported array types are {@code byte[]}, {@code char[]}, {@code short[]}, {@code int[]}, {@code float[]}, {@code long[]} and {@code double[]}.
     * @param srcSegment the source segment.
     * @param srcLayout the source element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @param srcOffset the starting offset, in bytes, of the source segment.
     * @param dstArray the destination array.
     * @param dstIndex the starting index of the destination array.
     * @param elementCount the number of array elements to be copied.
     * @throws  IllegalArgumentException if {@code dstArray} is not an array, or if it is an array but whose type is not supported,
     * or if the destination array component type does not match the carrier of the source element layout.
     */
    @ForceInline
    static void copy(
            MemorySegment srcSegment, ValueLayout srcLayout, long srcOffset,
            Object dstArray, int dstIndex, int elementCount) {
        Objects.requireNonNull(srcSegment);
        Objects.requireNonNull(dstArray);
        Objects.requireNonNull(srcLayout);
        long baseAndScale = getBaseAndScale(dstArray.getClass());
        if (dstArray.getClass().componentType() != srcLayout.carrier()) {
            throw new IllegalArgumentException("Incompatible value layout: " + srcLayout);
        }
        int dstBase = (int)baseAndScale;
        int dstWidth = (int)(baseAndScale >> 32);
        AbstractMemorySegmentImpl srcImpl = (AbstractMemorySegmentImpl)srcSegment;
        srcImpl.checkAccess(srcOffset, elementCount * dstWidth, true);
        Objects.checkFromIndexSize(dstIndex, elementCount, Array.getLength(dstArray));
        if (dstWidth == 1 || srcLayout.order() == ByteOrder.nativeOrder()) {
            ScopedMemoryAccess.getScopedMemoryAccess().copyMemory(srcImpl.scope(), null,
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstArray, dstBase + (dstIndex * dstWidth), elementCount * dstWidth);
        } else {
            ScopedMemoryAccess.getScopedMemoryAccess().copySwapMemory(srcImpl.scope(), null,
                    srcImpl.unsafeGetBase(), srcImpl.unsafeGetOffset() + srcOffset,
                    dstArray, dstBase + (dstIndex * dstWidth), elementCount * dstWidth, dstWidth);
        }
    }

    /**
     * Copies a number of elements from a source array to a destination segment,
     * starting at a given array index, and a given segment offset (expressed in bytes), using the given destination element layout.
     * Supported array types are {@code byte[]}, {@code char[]}, {@code short[]}, {@code int[]}, {@code float[]}, {@code long[]} and {@code double[]}.
     * @param srcArray the source array.
     * @param srcIndex the starting index of the source array.
     * @param dstSegment the destination segment.
     * @param dstLayout the destination element layout. If the byte order associated with the layout is
     * different from the native order, a byte swap operation will be performed on each array element.
     * @param dstOffset the starting offset, in bytes, of the destination segment.
     * @param elementCount the number of array elements to be copied.
     * @throws  IllegalArgumentException if {@code srcArray} is not an array, or if it is an array but whose type is not supported,
     * or if the source array component type does not match the carrier of the destination element layout.
     */
    @ForceInline
    static void copy(
            Object srcArray, int srcIndex,
            MemorySegment dstSegment, ValueLayout dstLayout, long dstOffset, int elementCount) {
        Objects.requireNonNull(srcArray);
        Objects.requireNonNull(dstSegment);
        Objects.requireNonNull(dstLayout);
        long baseAndScale = getBaseAndScale(srcArray.getClass());
        if (srcArray.getClass().componentType() != dstLayout.carrier()) {
            throw new IllegalArgumentException("Incompatible value layout: " + dstLayout);
        }
        int srcBase = (int)baseAndScale;
        int srcWidth = (int)(baseAndScale >> 32);
        Objects.checkFromIndexSize(srcIndex, elementCount, Array.getLength(srcArray));
        AbstractMemorySegmentImpl destImpl = (AbstractMemorySegmentImpl)dstSegment;
        destImpl.checkAccess(dstOffset, elementCount * srcWidth, false);
        if (srcWidth == 1 || dstLayout.order() == ByteOrder.nativeOrder()) {
            ScopedMemoryAccess.getScopedMemoryAccess().copyMemory(null, destImpl.scope(),
                    srcArray, srcBase + (srcIndex * srcWidth),
                    destImpl.unsafeGetBase(), destImpl.unsafeGetOffset() + dstOffset, elementCount * srcWidth);
        } else {
            ScopedMemoryAccess.getScopedMemoryAccess().copySwapMemory(null, destImpl.scope(),
                    srcArray, srcBase + (srcIndex * srcWidth),
                    destImpl.unsafeGetBase(), destImpl.unsafeGetOffset() + dstOffset, elementCount * srcWidth, srcWidth);
        }
    }

    private static long getBaseAndScale(Class<?> arrayType) {
        if (arrayType.equals(byte[].class)) {
            return (long)Unsafe.ARRAY_BYTE_BASE_OFFSET | ((long)Unsafe.ARRAY_BYTE_INDEX_SCALE << 32);
        } else if (arrayType.equals(char[].class)) {
            return (long)Unsafe.ARRAY_CHAR_BASE_OFFSET | ((long)Unsafe.ARRAY_CHAR_INDEX_SCALE << 32);
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
