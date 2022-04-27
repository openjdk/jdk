/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.HeapMemorySegmentImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;

/**
 * A memory segment models a contiguous region of memory. A memory segment is associated with both spatial
 * and temporal bounds (e.g. a {@link MemorySession}). Spatial bounds ensure that memory access operations on a memory segment cannot affect a memory location
 * which falls <em>outside</em> the boundaries of the memory segment being accessed. Temporal bounds ensure that memory access
 * operations on a segment cannot occur after the memory session associated with a memory segment has been closed (see {@link MemorySession#close()}).
 *
 * There are many kinds of memory segments:
 * <ul>
 *     <li>{@linkplain MemorySegment#allocateNative(long, long, MemorySession) native memory segments}, backed by off-heap memory;</li>
 *     <li>{@linkplain FileChannel#map(FileChannel.MapMode, long, long, MemorySession) mapped memory segments}, obtained by mapping
 * a file into main memory ({@code mmap}); the contents of a mapped memory segments can be {@linkplain #force() persisted} and
 * {@linkplain #load() loaded} to and from the underlying memory-mapped file;</li>
 *     <li>{@linkplain MemorySegment#ofArray(int[]) array segments}, wrapping an existing, heap-allocated Java array; and</li>
 *     <li>{@linkplain MemorySegment#ofByteBuffer(ByteBuffer) buffer segments}, wrapping an existing {@link ByteBuffer} instance;
 * buffer memory segments might be backed by either off-heap memory or on-heap memory, depending on the characteristics of the
 * wrapped byte buffer instance. For instance, a buffer memory segment obtained from a byte buffer created with the
 * {@link ByteBuffer#allocateDirect(int)} method will be backed by off-heap memory.</li>
 * </ul>
 *
 * <h2>Lifecycle and confinement</h2>
 *
 * Memory segments are associated with a {@linkplain MemorySegment#session() memory session}. As for all resources associated
 * with a memory session, a segment cannot be accessed after its underlying session has been closed. For instance,
 * the following code will result in an exception:
 * {@snippet lang=java :
 * MemorySegment segment = null;
 * try (MemorySession session = MemorySession.openConfined()) {
 *     segment = MemorySegment.allocateNative(8, session);
 * }
 * segment.get(ValueLayout.JAVA_LONG, 0); // already closed!
 * }
 * Additionally, access to a memory segment is subject to the thread-confinement checks enforced by the owning memory
 * session; that is, if the segment is associated with a shared session, it can be accessed by multiple threads;
 * if it is associated with a confined session, it can only be accessed by the thread which owns the memory session.
 * <p>
 * Heap and buffer segments are always associated with a <em>global</em>, shared memory session. This session cannot be closed,
 * and segments associated with it can be considered as <em>always alive</em>.
 *
 * <h2><a id = "segment-deref">Dereferencing memory segments</a></h2>
 *
 * A memory segment can be read or written using various methods provided in this class (e.g. {@link #get(ValueLayout.OfInt, long)}).
 * Each dereference method takes a {@linkplain ValueLayout value layout}, which specifies the size,
 * alignment constraints, byte order as well as the Java type associated with the dereference operation, and an offset.
 * For instance, to read an int from a segment, using {@linkplain ByteOrder#nativeOrder() default endianness}, the following code can be used:
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * int value = segment.get(ValueLayout.JAVA_INT, 0);
 * }
 *
 * If the value to be read is stored in memory using {@linkplain ByteOrder#BIG_ENDIAN big-endian} encoding, the dereference operation
 * can be expressed as follows:
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * int value = segment.get(ValueLayout.JAVA_INT.withOrder(BIG_ENDIAN), 0);
 * }
 *
 * For more complex dereference operations (e.g. structured memory access), clients can obtain a
 * {@linkplain MethodHandles#memorySegmentViewVarHandle(ValueLayout) memory segment view var handle},
 * that is, a var handle that accepts a segment and a {@code long} offset. More complex access var handles
 * can be obtained by adapting a segment var handle view using the var handle combinator functions defined in the
 * {@link java.lang.invoke.MethodHandles} class:
 *
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * VarHandle intHandle = MethodHandles.memorySegmentViewVarHandle(ValueLayout.JAVA_INT);
 * MethodHandle multiplyExact = MethodHandles.lookup()
 *                                           .findStatic(Math.class, "multiplyExact",
 *                                                                   MethodType.methodType(long.class, long.class, long.class));
 * intHandle = MethodHandles.filterCoordinates(intHandle, 1,
 *                                             MethodHandles.insertArguments(multiplyExact, 0, 4L));
 * intHandle.get(segment, 3L); // get int element at offset 3 * 4 = 12
 * }
 *
 * Alternatively, complex access var handles can can be obtained
 * from {@linkplain MemoryLayout#varHandle(MemoryLayout.PathElement...) memory layouts}
 * by providing a so called <a href="MemoryLayout.html#layout-paths"><em>layout path</em></a>:
 *
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * VarHandle intHandle = ValueLayout.JAVA_INT.arrayElementVarHandle();
 * intHandle.get(segment, 3L); // get int element at offset 3 * 4 = 12
 * }
 *
 * <h2>Slicing memory segments</h2>
 *
 * Memory segments support <em>slicing</em>. A memory segment can be used to {@linkplain MemorySegment#asSlice(long, long) obtain}
 * other segments backed by the same underlying memory region, but with <em>stricter</em> spatial bounds than the ones
 * of the original segment:
 * {@snippet lang=java :
 * MemorySession session = ...
 * MemorySegment segment = MemorySegment.allocateNative(100, session);
 * MemorySegment slice = segment.asSlice(50, 10);
 * slice.get(ValueLayout.JAVA_INT, 20); // Out of bounds!
 * session.close();
 * slice.get(ValueLayout.JAVA_INT, 0); // Already closed!
 * }
 * The above code creates a native segment that is 100 bytes long; then, it creates a slice that starts at offset 50
 * of {@code segment}, and is 10 bytes long. As a result, attempting to read an int value at offset 20 of the
 * {@code slice} segment will result in an exception. The {@linkplain MemorySession temporal bounds} of the original segment
 * are inherited by its slices; that is, when the memory session associated with {@code segment} is closed, {@code slice}
 * will also be become inaccessible.
 * <p>
 * A client might obtain a {@link Stream} from a segment, which can then be used to slice the segment (according to a given
 * element layout) and even allow multiple threads to work in parallel on disjoint segment slices
 * (to do this, the segment has to be associated with a shared memory session). The following code can be used to sum all int
 * values in a memory segment in parallel:
 *
 * {@snippet lang=java :
 * try (MemorySession session = MemorySession.openShared()) {
 *     SequenceLayout SEQUENCE_LAYOUT = MemoryLayout.sequenceLayout(1024, ValueLayout.JAVA_INT);
 *     MemorySegment segment = MemorySegment.allocateNative(SEQUENCE_LAYOUT, session);
 *     int sum = segment.elements(ValueLayout.JAVA_INT).parallel()
 *                      .mapToInt(s -> s.get(ValueLayout.JAVA_INT, 0))
 *                      .sum();
 * }
 * }
 *
 * <h2 id="segment-alignment">Alignment</h2>
 *
 * When dereferencing a memory segment using a layout, the runtime must check that the segment address being dereferenced
 * matches the layout's {@linkplain MemoryLayout#byteAlignment() alignment constraints}. If the segment being
 * dereferenced is a native segment, then it has a concrete {@linkplain #address() base address}, which can
 * be used to perform the alignment check. The pseudo-function below demonstrates this:
 *
 * {@snippet lang=java :
 * boolean isAligned(MemorySegment segment, long offset, MemoryLayout layout) {
 *   return ((segment.address().toRawLongValue() + offset) % layout.byteAlignment()) == 0
 * }
 * }
 *
 * If, however, the segment being dereferenced is a heap segment, the above function will not work: a heap
 * segment's base address is <em>virtualized</em> and, as such, cannot be used to construct an alignment check. Instead,
 * heap segments are assumed to produce addresses which are never more aligned than the element size of the Java array from which
 * they have originated from, as shown in the following table:
 *
 * <blockquote><table class="plain">
 * <caption style="display:none">Array type of an array backing a segment and its address alignment</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Array type</th>
 *     <th scope="col">Alignment</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="font-weight:normal">{@code boolean[]}</th>
 *     <td style="text-align:center;">{@code 1}</td></tr>
 * <tr><th scope="row" style="font-weight:normal">{@code byte[]}</th>
 *     <td style="text-align:center;">{@code 1}</td></tr>
 * <tr><th scope="row" style="font-weight:normal">{@code char[]}</th>
 *     <td style="text-align:center;">{@code 2}</td></tr>
 * <tr><th scope="row" style="font-weight:normal">{@code short[]}</th>
 *     <td style="text-align:center;">{@code 2}</td></tr>
 * <tr><th scope="row" style="font-weight:normal">{@code int[]}</th>
 *     <td style="text-align:center;">{@code 4}</td></tr>
 * <tr><th scope="row" style="font-weight:normal">{@code float[]}</th>
 *     <td style="text-align:center;">{@code 4}</td></tr>
 * <tr><th scope="row" style="font-weight:normal">{@code long[]}</th>
 *     <td style="text-align:center;">{@code 8}</td></tr>
 * <tr><th scope="row" style="font-weight:normal">{@code double[]}</th>
 *     <td style="text-align:center;">{@code 8}</td></tr>
 * </tbody>
 * </table></blockquote>
 *
 * Note that the above definition is conservative: it might be possible, for instance, that a heap segment
 * constructed from a {@code byte[]} might have a subset of addresses {@code S} which happen to be 8-byte aligned. But determining
 * which segment addresses belong to {@code S} requires reasoning about details which are ultimately implementation-dependent.
 *
 * <h2>Restricted memory segments</h2>
 * Sometimes it is necessary to turn a memory address obtained from native code into a memory segment with
 * full spatial, temporal and confinement bounds. To do this, clients can {@linkplain #ofAddress(MemoryAddress, long, MemorySession) obtain}
 * a native segment <em>unsafely</em> from a give memory address, by providing the segment size, as well as the segment {@linkplain MemorySession session}.
 * This is a <a href="package-summary.html#restricted"><em>restricted</em></a> operation and should be used with
 * caution: for instance, an incorrect segment size could result in a VM crash when attempting to dereference
 * the memory segment.
 * <p>
 * Clients requiring sophisticated, low-level control over mapped memory segments, might consider writing
 * custom mapped memory segment factories; using {@link Linker}, e.g. on Linux, it is possible to call {@code mmap}
 * with the desired parameters; the returned address can be easily wrapped into a memory segment, using
 * {@link MemoryAddress#ofLong(long)} and {@link MemorySegment#ofAddress(MemoryAddress, long, MemorySession)}.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface MemorySegment extends Addressable permits AbstractMemorySegmentImpl {

    /**
     * {@return the base memory address associated with this native memory segment}
     * @throws UnsupportedOperationException if this segment is not a {@linkplain #isNative() native} segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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
     * approximately {@code S/N} elements (depending on whether N is even or not), where {@code S} is the size of
     * this segment. As such, splitting is possible as long as {@code S/N >= 2}. The spliterator returns segments that
     * are associated with the same memory session as this segment.
     * <p>
     * The returned spliterator effectively allows to slice this segment into disjoint {@linkplain #asSlice(long, long) slices},
     * which can then be processed in parallel by multiple threads.
     *
     * @param elementLayout the layout to be used for splitting.
     * @return the element spliterator for this segment
     * @throws IllegalArgumentException if the {@code elementLayout} size is zero, or the segment size modulo the
     * {@code elementLayout} size is greater than zero, if this segment is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the {@code elementLayout} alignment is greater than its size.
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
     * {@code elementLayout} size is greater than zero, if this segment is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the {@code elementLayout} alignment is greater than its size.
     */
    Stream<MemorySegment> elements(MemoryLayout elementLayout);

    /**
     * {@return the memory session associated with this memory segment}
     */
    MemorySession session();

    /**
     * {@return the size (in bytes) of this memory segment}
     */
    long byteSize();

    /**
     * Returns a slice of this memory segment, at the given offset. The returned segment's base address is the base address
     * of this segment plus the given offset; its size is specified by the given argument.
     *
     * @see #asSlice(long)
     *
     * @param offset The new segment base offset (relative to the current segment base address), specified in bytes.
     * @param newSize The new segment size, specified in bytes.
     * @return a slice of this memory segment.
     * @throws IndexOutOfBoundsException if {@code offset < 0}, {@code offset > byteSize()}, {@code newSize < 0}, or {@code newSize > byteSize() - offset}
     */
    MemorySegment asSlice(long offset, long newSize);

    /**
     * Returns a slice of this memory segment, at the given offset. The returned segment's base address is the base address
     * of this segment plus the given offset; its size is computed by subtracting the specified offset from this segment size.
     * <p>
     * Equivalent to the following code:
     * {@snippet lang=java :
     * asSlice(offset, byteSize() - offset);
     * }
     *
     * @see #asSlice(long, long)
     *
     * @param offset The new segment base offset (relative to the current segment base address), specified in bytes.
     * @return a slice of this memory segment.
     * @throws IndexOutOfBoundsException if {@code offset < 0}, or {@code offset > byteSize()}.
     */
    default MemorySegment asSlice(long offset) {
        return asSlice(offset, byteSize() - offset);
    }

    /**
     * {@return {@code true}, if this segment is read-only}
     * @see #asReadOnly()
     */
    boolean isReadOnly();

    /**
     * Returns a read-only view of this segment. The resulting segment will be identical to this one, but
     * attempts to overwrite the contents of the returned segment will cause runtime exceptions.
     * @return a read-only view of this segment
     * @see #isReadOnly()
     */
    MemorySegment asReadOnly();

    /**
     * Returns {@code true} if this segment is a native segment. A native memory segment is
     * created using the {@link #allocateNative(long, MemorySession)} (and related) factory, or a buffer segment
     * derived from a {@linkplain ByteBuffer#allocateDirect(int) direct byte buffer} using the {@link #ofByteBuffer(ByteBuffer)} factory,
     * or if this is a {@linkplain #isMapped() mapped} segment.
     * @return {@code true} if this segment is native segment.
     */
    boolean isNative();

    /**
     * Returns {@code true} if this segment is a mapped segment. A mapped memory segment is
     * created using the {@link FileChannel#map(FileChannel.MapMode, long, long, MemorySession)} factory, or a buffer segment
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
     * {@linkplain #isNative() native} segment to overlap with a heap segment; in
     * this case, or when no overlap occurs, {@code null} is returned.
     *
     * @param other the segment to test for an overlap with this segment.
     * @return a slice of this segment (where overlapping occurs).
     */
    Optional<MemorySegment> asOverlappingSlice(MemorySegment other);

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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code src} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws UnsupportedOperationException if this segment is read-only (see {@link #isReadOnly()}).
     * @return this segment.
     */
    default MemorySegment copyFrom(MemorySegment src) {
        MemorySegment.copy(src, 0, this, 0, src.byteSize());
        return this;
    }

    /**
     * Finds and returns the offset, in bytes, of the first mismatch between
     * this segment and the given other segment. The offset is relative to the
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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code other} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     */
    long mismatch(MemorySegment other);

    /**
     * Determines whether the contents of this mapped segment is resident in physical
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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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
     * after the memory session associated with this segment has been closed (see {@link MemorySession#close()}), will throw an {@link IllegalStateException}.
     * <p>
     * If this segment is associated with a confined memory session, calling read/write I/O operations on the resulting buffer
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
     * Copy the contents of this memory segment into a new byte array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new byte array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code byte[]} instance,
     * e.g. its size is greater than {@link Integer#MAX_VALUE}.
     */
    byte[] toArray(ValueLayout.OfByte elementLayout);

    /**
     * Copy the contents of this memory segment into a new short array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new short array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code short[]} instance,
     * e.g. because {@code byteSize() % 2 != 0}, or {@code byteSize() / 2 > Integer#MAX_VALUE}
     */
    short[] toArray(ValueLayout.OfShort elementLayout);

    /**
     * Copy the contents of this memory segment into a new char array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new char array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code char[]} instance,
     * e.g. because {@code byteSize() % 2 != 0}, or {@code byteSize() / 2 > Integer#MAX_VALUE}.
     */
    char[] toArray(ValueLayout.OfChar elementLayout);

    /**
     * Copy the contents of this memory segment into a new int array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new int array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code int[]} instance,
     * e.g. because {@code byteSize() % 4 != 0}, or {@code byteSize() / 4 > Integer#MAX_VALUE}.
     */
    int[] toArray(ValueLayout.OfInt elementLayout);

    /**
     * Copy the contents of this memory segment into a new float array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new float array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code float[]} instance,
     * e.g. because {@code byteSize() % 4 != 0}, or {@code byteSize() / 4 > Integer#MAX_VALUE}.
     */
    float[] toArray(ValueLayout.OfFloat elementLayout);

    /**
     * Copy the contents of this memory segment into a new long array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new long array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code long[]} instance,
     * e.g. because {@code byteSize() % 8 != 0}, or {@code byteSize() / 8 > Integer#MAX_VALUE}.
     */
    long[] toArray(ValueLayout.OfLong elementLayout);

    /**
     * Copy the contents of this memory segment into a new double array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new double array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code double[]} instance,
     * e.g. because {@code byteSize() % 8 != 0}, or {@code byteSize() / 8 > Integer#MAX_VALUE}.
     */
    double[] toArray(ValueLayout.OfDouble elementLayout);

    /**
     * Reads a UTF-8 encoded, null-terminated string from this segment at the given offset.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a Java string constructed from the bytes read from the given starting address up to (but not including)
     * the first {@code '\0'} terminator character (assuming one is found).
     * @throws IllegalArgumentException if the size of the UTF-8 string is greater than the largest string supported by the platform.
     * @throws IndexOutOfBoundsException if {@code S + offset > byteSize()}, where {@code S} is the size of the UTF-8
     * string (including the terminator character).
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     */
    default String getUtf8String(long offset) {
        return SharedUtils.toJavaStringInternal(this, offset);
    }

    /**
     * Writes the given string into this segment at the given offset, converting it to a null-terminated byte sequence using UTF-8 encoding.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param str the Java string to be written into this segment.
     * @throws IndexOutOfBoundsException if {@code str.getBytes().length() + offset >= byteSize()}.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     */
    default void setUtf8String(long offset, String str) {
        Utils.toCString(str.getBytes(StandardCharsets.UTF_8), SegmentAllocator.prefixAllocator(asSlice(offset)));
    }


    /**
     * Creates a buffer memory segment that models the memory associated with the given byte
     * buffer. The segment starts relative to the buffer's position (inclusive)
     * and ends relative to the buffer's limit (exclusive).
     * <p>
     * If the buffer is {@linkplain ByteBuffer#isReadOnly() read-only}, the resulting segment will also be
     * {@linkplain ByteBuffer#isReadOnly() read-only}. The memory session associated with this segment can either be the
     * {@linkplain MemorySession#global() global} memory session, in case the buffer has been created independently,
     * or some other memory session, in case the buffer has been obtained using {@link #asByteBuffer()}.
     * <p>
     * The resulting memory segment keeps a reference to the backing buffer, keeping it <em>reachable</em>.
     *
     * @param bb the byte buffer backing the buffer memory segment.
     * @return a buffer memory segment.
     */
    static MemorySegment ofByteBuffer(ByteBuffer bb) {
        return AbstractMemorySegmentImpl.ofBuffer(bb);
    }

    /**
     * Creates an array memory segment that models the memory associated with the given heap-allocated byte array.
     * The returned segment is associated with the {@linkplain MemorySession#global() global} memory session.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return an array memory segment.
     */
    static MemorySegment ofArray(byte[] arr) {
        return HeapMemorySegmentImpl.OfByte.fromArray(arr);
    }

    /**
     * Creates an array memory segment that models the memory associated with the given heap-allocated char array.
     * The returned segment is associated with the {@linkplain MemorySession#global() global} memory session.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return an array memory segment.
     */
    static MemorySegment ofArray(char[] arr) {
        return HeapMemorySegmentImpl.OfChar.fromArray(arr);
    }

    /**
     * Creates an array memory segment that models the memory associated with the given heap-allocated short array.
     * The returned segment is associated with the {@linkplain MemorySession#global() global} memory session.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return an array memory segment.
     */
    static MemorySegment ofArray(short[] arr) {
        return HeapMemorySegmentImpl.OfShort.fromArray(arr);
    }

    /**
     * Creates an array memory segment that models the memory associated with the given heap-allocated int array.
     * The returned segment is associated with the {@linkplain MemorySession#global() global} memory session.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return an array memory segment.
     */
    static MemorySegment ofArray(int[] arr) {
        return HeapMemorySegmentImpl.OfInt.fromArray(arr);
    }

    /**
     * Creates an array memory segment that models the memory associated with the given heap-allocated float array.
     * The returned segment is associated with the {@linkplain MemorySession#global() global} memory session.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return an array memory segment.
     */
    static MemorySegment ofArray(float[] arr) {
        return HeapMemorySegmentImpl.OfFloat.fromArray(arr);
    }

    /**
     * Creates an array memory segment that models the memory associated with the given heap-allocated long array.
     * The returned segment is associated with the {@linkplain MemorySession#global() global} memory session.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return an array memory segment.
     */
    static MemorySegment ofArray(long[] arr) {
        return HeapMemorySegmentImpl.OfLong.fromArray(arr);
    }

    /**
     * Creates an array memory segment that models the memory associated with the given heap-allocated double array.
     * The returned segment is associated with the {@linkplain MemorySession#global() global} memory session.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return an array memory segment.
     */
    static MemorySegment ofArray(double[] arr) {
        return HeapMemorySegmentImpl.OfDouble.fromArray(arr);
    }


    /**
     * Creates a native memory segment with the given size, base address, and memory session.
     * This method can be useful when interacting with custom memory sources (e.g. custom allocators),
     * where an address to some underlying memory region is typically obtained from foreign code
     * (often as a plain {@code long} value).
     * <p>
     * The returned segment is not read-only (see {@link MemorySegment#isReadOnly()}), and is associated with the
     * provided memory session.
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
     * @param session the native segment memory session.
     * @return a native memory segment with the given base address, size and memory session.
     * @throws IllegalArgumentException if {@code bytesSize < 0}.
     * @throws IllegalStateException if {@code session} is not {@linkplain MemorySession#isAlive() alive}, or if access occurs from
     * a thread other than the thread {@linkplain MemorySession#ownerThread() owning} {@code session}.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is specified, but does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static MemorySegment ofAddress(MemoryAddress address, long bytesSize, MemorySession session) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), MemorySegment.class, "ofAddress");
        Objects.requireNonNull(address);
        Objects.requireNonNull(session);
        if (bytesSize < 0) {
            throw new IllegalArgumentException("Invalid size : " + bytesSize);
        }
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(address, bytesSize, session);
    }

    /**
     * Creates a native memory segment with the given layout and memory session.
     * A client is responsible for ensuring that the memory session associated with the returned segment is closed
     * when the segment is no longer in use. Failure to do so will result in off-heap memory leaks.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang=java :
     * allocateNative(layout.bytesSize(), layout.bytesAlignment(), session);
     * }
     * <p>
     * The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     *
     * @param layout the layout of the off-heap memory block backing the native memory segment.
     * @param session the segment memory session.
     * @return a new native memory segment.
     * @throws IllegalStateException if {@code session} is not {@linkplain MemorySession#isAlive() alive}, or if access occurs from
     * a thread other than the thread {@linkplain MemorySession#ownerThread() owning} {@code session}.
     */
    static MemorySegment allocateNative(MemoryLayout layout, MemorySession session) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(layout);
        return allocateNative(layout.byteSize(), layout.byteAlignment(), session);
    }

    /**
     * Creates a native memory segment with the given size (in bytes) and memory session.
     * A client is responsible for ensuring that the memory session associated with the returned segment is closed
     * when the segment is no longer in use. Failure to do so will result in off-heap memory leaks.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang=java :
     * allocateNative(bytesSize, 1, session);
     * }
     * <p>
     * The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     *
     * @param bytesSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param session the segment temporal bounds.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}.
     * @throws IllegalStateException if {@code session} is not {@linkplain MemorySession#isAlive() alive}, or if access occurs from
     * a thread other than the thread {@linkplain MemorySession#ownerThread() owning} {@code session}.
     */
    static MemorySegment allocateNative(long bytesSize, MemorySession session) {
        return allocateNative(bytesSize, 1, session);
    }

    /**
     * Creates a native memory segment with the given size (in bytes), alignment constraint (in bytes) and memory session.
     * A client is responsible for ensuring that the memory session associated with the returned segment is closed when the
     * segment is no longer in use. Failure to do so will result in off-heap memory leaks.
     * <p>
     * The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     *
     * @param bytesSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param alignmentBytes the alignment constraint (in bytes) of the off-heap memory block backing the native memory segment.
     * @param session the segment memory session.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}, {@code alignmentBytes <= 0}, or if {@code alignmentBytes}
     * is not a power of 2.
     * @throws IllegalStateException if {@code session} is not {@linkplain MemorySession#isAlive() alive}, or if access occurs from
     * a thread other than the thread {@linkplain MemorySession#ownerThread() owning} {@code session}.
     */
    static MemorySegment allocateNative(long bytesSize, long alignmentBytes, MemorySession session) {
        Objects.requireNonNull(session);
        if (bytesSize < 0) {
            throw new IllegalArgumentException("Invalid allocation size : " + bytesSize);
        }

        if (alignmentBytes <= 0 ||
                ((alignmentBytes & (alignmentBytes - 1)) != 0L)) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + alignmentBytes);
        }

        return NativeMemorySegmentImpl.makeNativeSegment(bytesSize, alignmentBytes, session);
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
     * For example, this may occur if the same file is {@linkplain FileChannel#map mapped} to two segments.
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
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code srcSegment} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code dstSegment} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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
     * For example, this may occur if the same file is {@linkplain FileChannel#map mapped} to two segments.
     * @param srcSegment the source segment.
     * @param srcElementLayout the element layout associated with the source segment.
     * @param srcOffset the starting offset, in bytes, of the source segment.
     * @param dstSegment the destination segment.
     * @param dstElementLayout the element layout associated with the destination segment.
     * @param dstOffset the starting offset, in bytes, of the destination segment.
     * @param elementCount the number of elements to be copied.
     * @throws IllegalArgumentException if the element layouts have different sizes, if the source (resp. destination) segment/offset are
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the source
     * (resp. destination) element layout, or if the source (resp. destination) element layout alignment is greater than its size.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code srcSegment} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code dstSegment} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
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

    /**
     * Reads a byte at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a byte value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default byte get(ValueLayout.OfByte layout, long offset) {
        return (byte)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a byte at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the byte value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfByte layout, long offset, byte value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a boolean at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a boolean value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default boolean get(ValueLayout.OfBoolean layout, long offset) {
        return (boolean)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a boolean at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the boolean value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfBoolean layout, long offset, boolean value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a char at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a char value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default char get(ValueLayout.OfChar layout, long offset) {
        return (char)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a char at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the char value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfChar layout, long offset, char value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a short at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a short value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default short get(ValueLayout.OfShort layout, long offset) {
        return (short)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a short at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the short value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfShort layout, long offset, short value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads an int at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return an int value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default int get(ValueLayout.OfInt layout, long offset) {
        return (int)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes an int at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the int value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfInt layout, long offset, int value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a float at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a float value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default float get(ValueLayout.OfFloat layout, long offset) {
        return (float)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a float at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the float value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfFloat layout, long offset, float value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a long at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a long value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default long get(ValueLayout.OfLong layout, long offset) {
        return (long)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a long at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the long value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfLong layout, long offset, long value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads a double at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return a double value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default double get(ValueLayout.OfDouble layout, long offset) {
        return (double)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes a double at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the double value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfDouble layout, long offset, double value) {
        layout.accessHandle().set(this, offset, value);
    }

    /**
     * Reads an address at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @return an address value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default MemoryAddress get(ValueLayout.OfAddress layout, long offset) {
        return (MemoryAddress)layout.accessHandle().get(this, offset);
    }

    /**
     * Writes an address at the given offset from this segment, with the given layout.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + offset}.
     * @param value the address value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfAddress layout, long offset, Addressable value) {
        layout.accessHandle().set(this, offset, value.address());
    }

    /**
     * Reads a char from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a char value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default char getAtIndex(ValueLayout.OfChar layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (char)layout.accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a char to this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the char value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfChar layout, long index, char value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        layout.accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a short from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a short value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default short getAtIndex(ValueLayout.OfShort layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (short)layout.accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a short to this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the short value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfShort layout, long index, short value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        layout.accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads an int from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return an int value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default int getAtIndex(ValueLayout.OfInt layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (int)layout.accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes an int to this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the int value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfInt layout, long index, int value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        layout.accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a float from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a float value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default float getAtIndex(ValueLayout.OfFloat layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (float)layout.accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a float to this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the float value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfFloat layout, long index, float value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        layout.accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a long from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a long value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default long getAtIndex(ValueLayout.OfLong layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (long)layout.accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a long to this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the long value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfLong layout, long index, long value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        layout.accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a double from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return a double value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default double getAtIndex(ValueLayout.OfDouble layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (double)layout.accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a double to this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the double value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfDouble layout, long index, double value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        layout.accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads an address from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this read operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @return an address value read from this address.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default MemoryAddress getAtIndex(ValueLayout.OfAddress layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (MemoryAddress)layout.accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes an address to this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index (relative to this segment). For instance, if this segment is a {@linkplain #isNative() native} segment,
     *               the final address of this write operation can be expressed as {@code address().toRowLongValue() + (index * layout.byteSize())}.
     * @param value the address value to be written.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with this segment is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws IllegalArgumentException if the dereference operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the dereference operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfAddress layout, long index, Addressable value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        layout.accessHandle().set(this, index * layout.byteSize(), value.address());
    }

    /**
     * Compares the specified object with this memory segment for equality. Returns {@code true} if and only if the specified
     * object is also a memory segment, and if that segment refers to the same memory region as this segment. More specifically,
     * for two segments to be considered equals, all the following must be true:
     * <ul>
     *     <li>the two segments must be of the same kind; either both are {@linkplain #isNative() native segments},
     *     backed by off-heap memory, or both are backed by on-heap memory;
     *     <li>if the two segments are {@linkplain #isNative() native segments}, their {@link #address() base address}
     *     must be {@linkplain MemoryAddress#equals(Object) equal}. Otherwise, the two segments must wrap the
     *     same Java array instance, at the same starting offset;</li>
     *     <li>the two segments must have the same {@linkplain #byteSize() size}; and</li>
     *     <li>the two segments must have the {@linkplain MemorySession#equals(Object) same} {@linkplain #session() temporal bounds}.
     * </ul>
     * @apiNote This method does not perform a structural comparison of the contents of the two memory segments. Clients can
     * compare memory segments structurally by using the {@link #mismatch(MemorySegment)} method instead.
     *
     * @param that the object to be compared for equality with this memory segment.
     * @return {@code true} if the specified object is equal to this memory segment.
     * @see #mismatch(MemorySegment)
     * @see #asOverlappingSlice(MemorySegment)
     */
    @Override
    boolean equals(Object that);

    /**
     * {@return the hash code value for this memory segment}
     */
    @Override
    int hashCode();


    /**
     * Copies a number of elements from a source memory segment to a destination array. The elements, whose size and alignment
     * constraints are specified by the given layout, are read from the source segment, starting at the given offset
     * (expressed in bytes), and are copied into the destination array, at the given index.
     * Supported array types are {@code byte[]}, {@code char[]}, {@code short[]}, {@code int[]}, {@code float[]}, {@code long[]} and {@code double[]}.
     * @param srcSegment the source segment.
     * @param srcLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @param srcOffset the starting offset, in bytes, of the source segment.
     * @param dstArray the destination array.
     * @param dstIndex the starting index of the destination array.
     * @param elementCount the number of array elements to be copied.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code srcSegment} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws  IllegalArgumentException if {@code dstArray} is not an array, or if it is an array but whose type is not supported,
     * if the destination array component type does not match the carrier of the source element layout, if the source
     * segment/offset are <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the source element layout,
     * or if the destination element layout alignment is greater than its size.
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

    /**
     * Copies a number of elements from a source array to a destination memory segment. The elements, whose size and alignment
     * constraints are specified by the given layout, are read from the source array, starting at the given index,
     * and are copied into the destination segment, at the given offset (expressed in bytes).
     * Supported array types are {@code byte[]}, {@code char[]}, {@code short[]}, {@code int[]}, {@code float[]}, {@code long[]} and {@code double[]}.
     * @param srcArray the source array.
     * @param srcIndex the starting index of the source array.
     * @param dstSegment the destination segment.
     * @param dstLayout the destination element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @param dstOffset the starting offset, in bytes, of the destination segment.
     * @param elementCount the number of array elements to be copied.
     * @throws IllegalStateException if the {@linkplain #session() session} associated with {@code dstSegment} is not
     * {@linkplain MemorySession#isAlive() alive}, or if access occurs from a thread other than the thread owning that session.
     * @throws  IllegalArgumentException if {@code srcArray} is not an array, or if it is an array but whose type is not supported,
     * if the source array component type does not match the carrier of the destination element layout, if the destination
     * segment/offset are <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraints</a> in the destination element layout,
     * or if the destination element layout alignment is greater than its size.
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
