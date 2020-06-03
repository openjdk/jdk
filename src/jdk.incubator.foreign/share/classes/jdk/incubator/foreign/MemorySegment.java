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

package jdk.incubator.foreign;

import java.nio.ByteBuffer;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.HeapMemorySegmentImpl;
import jdk.internal.foreign.MappedMemorySegmentImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.Utils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A memory segment models a contiguous region of memory. A memory segment is associated with both spatial
 * and temporal bounds. Spatial bounds ensure that memory access operations on a memory segment cannot affect a memory location
 * which falls <em>outside</em> the boundaries of the memory segment being accessed. Temporal checks ensure that memory access
 * operations on a segment cannot occur after a memory segment has been closed (see {@link MemorySegment#close()}).
 * <p>
 * All implementations of this interface must be <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>;
 * use of identity-sensitive operations (including reference equality ({@code ==}), identity hash code, or synchronization) on
 * instances of {@code MemorySegment} may have unpredictable results and should be avoided. The {@code equals} method should
 * be used for comparisons.
 * <p>
 * Non-platform classes should not implement {@linkplain MemorySegment} directly.
 *
 * <h2>Constructing memory segments from different sources</h2>
 *
 * There are multiple ways to obtain a memory segment. First, memory segments backed by off-heap memory can
 * be allocated using one of the many factory methods provided (see {@link MemorySegment#allocateNative(MemoryLayout)},
 * {@link MemorySegment#allocateNative(long)} and {@link MemorySegment#allocateNative(long, long)}). Memory segments obtained
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
 * <p>
 * Finally, it is also possible to obtain a memory segment backed by a memory-mapped file using the factory method
 * {@link MemorySegment#mapFromPath(Path, long, long, FileChannel.MapMode)}. Such memory segments are called <em>mapped memory segments</em>
 * (see {@link MappedMemorySegment}).
 *
 * <h2>Closing a memory segment</h2>
 *
 * Memory segments are closed explicitly (see {@link MemorySegment#close()}). In general when a segment is closed, all off-heap
 * resources associated with it are released; this has different meanings depending on the kind of memory segment being
 * considered:
 * <ul>
 *     <li>closing a native memory segment results in <em>freeing</em> the native memory associated with it</li>
 *     <li>closing a mapped memory segment results in the backing memory-mapped file to be unmapped</li>
 *     <li>closing a buffer, or a heap segment does not have any side-effect, other than marking the segment
 *     as <em>not alive</em> (see {@link MemorySegment#isAlive()}). Also, since the buffer and heap segments might keep
 *     strong references to the original buffer or array instance, it is the responsibility of clients to ensure that
 *     these segments are discarded in a timely manner, so as not to prevent garbage collection to reclaim the underlying
 *     objects.</li>
 * </ul>
 *
 * <h2><a id = "thread-confinement">Thread confinement</a></h2>
 *
 * Memory segments support strong thread-confinement guarantees. Upon creation, they are assigned an <em>owner thread</em>,
 * typically the thread which initiated the creation operation. After creation, only the owner thread will be allowed
 * to directly manipulate the memory segment (e.g. close the memory segment) or access the underlying memory associated with
 * the segment using a memory access var handle. Any attempt to perform such operations from a thread other than the
 * owner thread will result in a runtime failure.
 * <p>
 * Memory segments support <em>serial thread confinement</em>; that is, ownership of a memory segment can change (see
 * {@link #withOwnerThread(Thread)}). This allows, for instance, for two threads {@code A} and {@code B} to share
 * a segment in a controlled, cooperative and race-free fashion.
 * <p>
 * In some cases, it might be useful for multiple threads to process the contents of the same memory segment concurrently
 * (e.g. in the case of parallel processing); while memory segments provide strong confinement guarantees, it is possible
 * to obtain a {@link Spliterator} from a segment, which can be used to slice the segment and allow multiple thread to
 * work in parallel on disjoint segment slices (this assumes that the access mode {@link #ACQUIRE} is set).
 * For instance, the following code can be used to sum all int values in a memory segment in parallel:
 * <blockquote><pre>{@code
MemorySegment segment = ...
SequenceLayout SEQUENCE_LAYOUT = MemoryLayout.ofSequence(1024, MemoryLayouts.JAVA_INT);
VarHandle VH_int = SEQUENCE_LAYOUT.elementLayout().varHandle(int.class);
int sum = StreamSupport.stream(MemorySegment.spliterator(segment, SEQUENCE_LAYOUT), true)
            .mapToInt(s -> (int)VH_int.get(s.baseAddress()))
            .sum();
 * }</pre></blockquote>
 *
 * <h2><a id = "access-modes">Access modes</a></h2>
 *
 * Memory segments supports zero or more <em>access modes</em>. Supported access modes are {@link #READ},
 * {@link #WRITE}, {@link #CLOSE}, {@link #ACQUIRE} and {@link #HANDOFF}. The set of access modes supported by a segment alters the
 * set of operations that are supported by that segment. For instance, attempting to call {@link #close()} on
 * a segment which does not support the {@link #CLOSE} access mode will result in an exception.
 * <p>
 * The set of supported access modes can only be made stricter (by supporting <em>fewer</em> access modes). This means
 * that restricting the set of access modes supported by a segment before sharing it with other clients
 * is generally a good practice if the creator of the segment wants to retain some control over how the segment
 * is going to be accessed.
 *
 * <h2>Memory segment views</h2>
 *
 * Memory segments support <em>views</em>. For instance, it is possible to alter the set of supported access modes,
 * by creating an <em>immutable</em> view of a memory segment, as follows:
 * <blockquote><pre>{@code
MemorySegment segment = ...
MemorySegment roSegment = segment.withAccessModes(segment.accessModes() & ~WRITE);
 * }</pre></blockquote>
 * It is also possible to create views whose spatial bounds are stricter than the ones of the original segment
 * (see {@link MemorySegment#asSlice(long, long)}).
 * <p>
 * Temporal bounds of the original segment are inherited by the view; that is, closing a segment view, such as a sliced
 * view, will cause the original segment to be closed; as such special care must be taken when sharing views
 * between multiple clients. If a client want to protect itself against early closure of a segment by
 * another actor, it is the responsibility of that client to take protective measures, such as removing {@link #CLOSE}
 * from the set of supported access modes, before sharing the view with another client.
 * <p>
 * To allow for interoperability with existing code, a byte buffer view can be obtained from a memory segment
 * (see {@link #asByteBuffer()}). This can be useful, for instance, for those clients that want to keep using the
 * {@link ByteBuffer} API, but need to operate on large memory segments. Byte buffers obtained in such a way support
 * the same spatial and temporal access restrictions associated to the memory segment from which they originated.
 *
 * @apiNote In the future, if the Java language permits, {@link MemorySegment}
 * may become a {@code sealed} interface, which would prohibit subclassing except by
 * {@link MappedMemorySegment} and other explicitly permitted subtypes.
 *
 * @implSpec
 * Implementations of this interface are immutable and thread-safe.
 */
public interface MemorySegment extends AutoCloseable {

    /**
     * The base memory address associated with this memory segment. The returned address is
     * a <em>checked</em> memory address and can therefore be used in derefrence operations
     * (see {@link MemoryAddress}).
     * @return The base memory address.
     */
    MemoryAddress baseAddress();

    /**
     * Returns a spliterator for the given memory segment. The returned spliterator reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, {@link Spliterator#IMMUTABLE}, {@link Spliterator#NONNULL} and {@link Spliterator#ORDERED}
     * characteristics.
     * <p>
     * The returned spliterator splits the segment according to the specified sequence layout; that is,
     * if the supplied layout is a sequence layout whose element count is {@code N}, then calling {@link Spliterator#trySplit()}
     * will result in a spliterator serving approximatively {@code N/2} elements (depending on whether N is even or not).
     * As such, splitting is possible as long as {@code N >= 2}. The spliterator returns segments that feature the same
     * <a href="#access-modes">access modes</a> as the given segment less the {@link #CLOSE} access mode.
     * <p>
     * The returned spliterator effectively allows to slice a segment into disjoint sub-segments, which can then
     * be processed in parallel by multiple threads (if the access mode {@link #ACQUIRE} is set).
     * While closing the segment (see {@link #close()}) during pending concurrent execution will generally
     * fail with an exception, it is possible to close a segment when a spliterator has been obtained but no thread
     * is actively working on it using {@link Spliterator#tryAdvance(Consumer)}; in such cases, any subsequent call
     * to {@link Spliterator#tryAdvance(Consumer)} will fail with an exception.
     * @param segment the segment to be used for splitting.
     * @param layout the layout to be used for splitting.
     * @param <S> the memory segment type
     * @return the element spliterator for this segment
     * @throws IllegalStateException if the segment is not <em>alive</em>, or if access occurs from a thread other than the
     * thread owning this segment
     */
    static <S extends MemorySegment> Spliterator<S> spliterator(S segment, SequenceLayout layout) {
        return AbstractMemorySegmentImpl.spliterator(segment, layout);
    }

    /**
     * The thread owning this segment.
     * @return the thread owning this segment.
     */
    Thread ownerThread();

    /**
     * Obtains a new memory segment backed by the same underlying memory region as this segment,
     * but with different owner thread. As a side-effect, this segment will be marked as <em>not alive</em>,
     * and subsequent operations on this segment will result in runtime errors.
     * <p>
     * Write accesses to the segment's content <a href="../../../java/util/concurrent/package-summary.html#MemoryVisibility"><i>happens-before</i></a>
     * hand-over from the current owner thread to the new owner thread, which in turn <i>happens before</i> read accesses to the segment's contents on
     * the new owner thread.
     *
     * @param newOwner the new owner thread.
     * @return a new memory segment backed by the same underlying memory region as this segment,
     *      owned by {@code newOwner}.
     * @throws IllegalStateException if this segment is not <em>alive</em>, or if access occurs from a thread other than the
     * thread owning this segment, or if the segment cannot be closed because it is being operated upon by a different
     * thread (see {@link #spliterator(MemorySegment, SequenceLayout)}).
     * @throws NullPointerException if {@code newOwner == null}
     * @throws IllegalArgumentException if the segment is already a confined segment owner by {@code newOnwer}.
     * @throws UnsupportedOperationException if this segment does not support the {@link #HANDOFF} access mode.
     */
    MemorySegment withOwnerThread(Thread newOwner);

    /**
     * The size (in bytes) of this memory segment.
     * @return The size (in bytes) of this memory segment.
     */
    long byteSize();

    /**
     * Obtains a segment view with specific <a href="#access-modes">access modes</a>. Supported access modes are {@link #READ}, {@link #WRITE},
     * {@link #CLOSE}, {@link #ACQUIRE} and {@link #HANDOFF}. It is generally not possible to go from a segment with stricter access modes
     * to one with less strict access modes. For instance, attempting to add {@link #WRITE} access mode to a read-only segment
     * will be met with an exception.
     * @param accessModes an ORed mask of zero or more access modes.
     * @return a segment view with specific access modes.
     * @throws IllegalArgumentException when {@code mask} is an access mask which is less strict than the one supported by this
     * segment, or when {@code mask} contains bits not associated with any of the supported access modes.
     */
    MemorySegment withAccessModes(int accessModes);

    /**
     * Does this segment support a given set of access modes?
     * @param accessModes an ORed mask of zero or more access modes.
     * @return true, if the access modes in {@code accessModes} are stricter than the ones supported by this segment.
     * @throws IllegalArgumentException when {@code mask} contains bits not associated with any of the supported access modes.
     */
    boolean hasAccessModes(int accessModes);

    /**
     * Returns the <a href="#access-modes">access modes</a> associated with this segment; the result is represented as ORed values from
     * {@link #READ}, {@link #WRITE}, {@link #CLOSE}, {@link #ACQUIRE} and {@link #HANDOFF}.
     * @return the access modes associated with this segment.
     */
    int accessModes();

    /**
     * Obtains a new memory segment view whose base address is the same as the base address of this segment plus a given offset,
     * and whose new size is specified by the given argument.
     * @param offset The new segment base offset (relative to the current segment base address), specified in bytes.
     * @param newSize The new segment size, specified in bytes.
     * @return a new memory segment view with updated base/limit addresses.
     * @throws IndexOutOfBoundsException if {@code offset < 0}, {@code offset > byteSize()}, {@code newSize < 0}, or {@code newSize > byteSize() - offset}
     */
    MemorySegment asSlice(long offset, long newSize);

    /**
     * Is this segment alive?
     * @return true, if the segment is alive.
     * @see MemorySegment#close()
     */
    boolean isAlive();

    /**
     * Closes this memory segment. Once a memory segment has been closed, any attempt to use the memory segment,
     * or to access the memory associated with the segment will fail with {@link IllegalStateException}. Depending on
     * the kind of memory segment being closed, calling this method further trigger deallocation of all the resources
     * associated with the memory segment.
     * @throws IllegalStateException if this segment is not <em>alive</em>, or if access occurs from a thread other than the
     * thread owning this segment, or if the segment cannot be closed because it is being operated upon by a different
     * thread (see {@link #spliterator(MemorySegment, SequenceLayout)}).
     * @throws UnsupportedOperationException if this segment does not support the {@link #CLOSE} access mode.
     */
    void close();

    /**
     * Fills a value into this memory segment.
     * <p>
     * More specifically, the given value is filled into each address of this
     * segment. Equivalent to (but likely more efficient than) the following code:
     *
     * <pre>{@code
byteHandle = MemoryLayout.ofSequence(MemoryLayouts.JAVA_BYTE)
         .varHandle(byte.class, MemoryLayout.PathElement.sequenceElement());
for (long l = 0; l < segment.byteSize(); l++) {
     byteHandle.set(segment.baseAddress(), l, value);
}
     * }</pre>
     *
     * without any regard or guarantees on the ordering of particular memory
     * elements being set.
     * <p>
     * Fill can be useful to initialize or reset the memory of a segment.
     *
     * @param value the value to fill into this segment
     * @return this memory segment
     * @throws IllegalStateException if this segment is not <em>alive</em>, or if access occurs from a thread other than the
     * thread owning this segment
     * @throws UnsupportedOperationException if this segment does not support the {@link #WRITE} access mode
     */
    MemorySegment fill(byte value);

    /**
     * Performs a bulk copy from given source segment to this segment. More specifically, the bytes at
     * offset {@code 0} through {@code src.byteSize() - 1} in the source segment are copied into this segment
     * at offset {@code 0} through {@code src.byteSize() - 1}.
     * If the source segment overlaps with this segment, then the copying is performed as if the bytes at
     * offset {@code 0} through {@code src.byteSize() - 1} in the source segment were first copied into a
     * temporary segment with size {@code bytes}, and then the contents of the temporary segment were copied into
     * this segment at offset {@code 0} through {@code src.byteSize() - 1}.
     * <p>
     * The result of a bulk copy is unspecified if, in the uncommon case, the source segment and this segment
     * do not overlap, but refer to overlapping regions of the same backing storage using different addresses.
     * For example, this may occur if the same file is {@link MemorySegment#mapFromPath mapped} to two segments.
     *
     * @param src the source segment.
     * @throws IndexOutOfBoundsException if {@code src.byteSize() > this.byteSize()}.
     * @throws IllegalStateException if either the source segment or this segment have been already closed,
     * or if access occurs from a thread other than the thread owning either segment.
     * @throws UnsupportedOperationException if either the source segment or this segment do not feature required access modes;
     * more specifically, {@code src} should feature at least the {@link MemorySegment#READ} access mode,
     * while this segment should feature at least the {@link MemorySegment#WRITE} access mode.
     */
    void copyFrom(MemorySegment src);

    /**
     * Finds and returns the offset, in bytes, of the first mismatch between
     * this segment and a given other segment. The offset is relative to the
     * {@link #baseAddress() base address} of each segment and will be in the
     * range of 0 (inclusive) up to the {@link #byteSize() size} (in bytes) of
     * the smaller memory segment (exclusive).
     * <p>
     * If the two segments share a common prefix then the returned offset is
     * the length of the common prefix and it follows that there is a mismatch
     * between the two segments at that offset within the respective segments.
     * If one segment is a proper prefix of the other then the returned offset is
     * the smaller of the segment sizes, and it follows that the offset is only
     * valid for the larger segment. Otherwise, there is no mismatch and {@code
     * -1} is returned.
     *
     * @param other the segment to be tested for a mismatch with this segment
     * @return the relative offset, in bytes, of the first mismatch between this
     * and the given other segment, otherwise -1 if no mismatch
     * @throws IllegalStateException if either this segment of the other segment
     * have been already closed, or if access occurs from a thread other than the
     * thread owning either segment
     * @throws UnsupportedOperationException if either this segment or the other
     * segment does not feature at least the {@link MemorySegment#READ} access mode
     */
    long mismatch(MemorySegment other);

    /**
     * Wraps this segment in a {@link ByteBuffer}. Some of the properties of the returned buffer are linked to
     * the properties of this segment. For instance, if this segment is <em>immutable</em>
     * (e.g. the segment has access mode {@link #READ} but not {@link #WRITE}), then the resulting buffer is <em>read-only</em>
     * (see {@link ByteBuffer#isReadOnly()}. Additionally, if this is a native memory segment, the resulting buffer is
     * <em>direct</em> (see {@link ByteBuffer#isDirect()}).
     * <p>
     * The life-cycle of the returned buffer will be tied to that of this segment. That means that if the this segment
     * is closed (see {@link MemorySegment#close()}, accessing the returned
     * buffer will throw an {@link IllegalStateException}.
     * <p>
     * The resulting buffer's byte order is {@link java.nio.ByteOrder#BIG_ENDIAN}; this can be changed using
     * {@link ByteBuffer#order(java.nio.ByteOrder)}.
     *
     * @return a {@link ByteBuffer} view of this memory segment.
     * @throws UnsupportedOperationException if this segment cannot be mapped onto a {@link ByteBuffer} instance,
     * e.g. because it models an heap-based segment that is not based on a {@code byte[]}), or if its size is greater
     * than {@link Integer#MAX_VALUE}, or if the segment does not support the {@link #READ} access mode.
     */
    ByteBuffer asByteBuffer();

    /**
     * Copy the contents of this memory segment into a fresh byte array.
     * @return a fresh byte array copy of this memory segment.
     * @throws UnsupportedOperationException if this segment does not feature the {@link #READ} access mode, or if this
     * segment's contents cannot be copied into a {@link byte[]} instance, e.g. its size is greater than {@link Integer#MAX_VALUE},
     * @throws IllegalStateException if this segment has been closed, or if access occurs from a thread other than the
     * thread owning this segment.
     */
    byte[] toByteArray();

    /**
     * Creates a new buffer memory segment that models the memory associated with the given byte
     * buffer. The segment starts relative to the buffer's position (inclusive)
     * and ends relative to the buffer's limit (exclusive).
     * <p>
     * The segment will feature all <a href="#access-modes">access modes</a> (see {@link #ALL_ACCESS}),
     * unless the given buffer is {@linkplain ByteBuffer#isReadOnly() read-only} in which case the segment will
     * not feature the {@link #WRITE} access mode.
     * <p>
     * The resulting memory segment keeps a reference to the backing buffer, to ensure it remains <em>reachable</em>
     * for the life-time of the segment.
     *
     * @param bb the byte buffer backing the buffer memory segment.
     * @return a new buffer memory segment.
     */
    static MemorySegment ofByteBuffer(ByteBuffer bb) {
        return AbstractMemorySegmentImpl.ofBuffer(bb);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated byte array.
     * <p>
     * The resulting memory segment keeps a reference to the backing array, to ensure it remains <em>reachable</em>
     * for the life-time of the segment. The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(byte[] arr) {
        return HeapMemorySegmentImpl.makeArraySegment(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated char array.
     * <p>
     * The resulting memory segment keeps a reference to the backing array, to ensure it remains <em>reachable</em>
     * for the life-time of the segment. The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(char[] arr) {
        return HeapMemorySegmentImpl.makeArraySegment(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated short array.
     * <p>
     * The resulting memory segment keeps a reference to the backing array, to ensure it remains <em>reachable</em>
     * for the life-time of the segment. The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(short[] arr) {
        return HeapMemorySegmentImpl.makeArraySegment(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated int array.
     * <p>
     * The resulting memory segment keeps a reference to the backing array, to ensure it remains <em>reachable</em>
     * for the life-time of the segment. The segment will feature all <a href="#access-modes">access modes</a>.
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(int[] arr) {
        return HeapMemorySegmentImpl.makeArraySegment(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated float array.
     * <p>
     * The resulting memory segment keeps a reference to the backing array, to ensure it remains <em>reachable</em>
     * for the life-time of the segment. The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(float[] arr) {
        return HeapMemorySegmentImpl.makeArraySegment(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated long array.
     * <p>
     * The resulting memory segment keeps a reference to the backing array, to ensure it remains <em>reachable</em>
     * for the life-time of the segment. The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(long[] arr) {
        return HeapMemorySegmentImpl.makeArraySegment(arr);
    }

    /**
     * Creates a new array memory segment that models the memory associated with a given heap-allocated double array.
     * <p>
     * The resulting memory segment keeps a reference to the backing array, to ensure it remains <em>reachable</em>
     * for the life-time of the segment. The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     *
     * @param arr the primitive array backing the array memory segment.
     * @return a new array memory segment.
     */
    static MemorySegment ofArray(double[] arr) {
        return HeapMemorySegmentImpl.makeArraySegment(arr);
    }

    /**
     * Creates a new native memory segment that models a newly allocated block of off-heap memory with given layout.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    allocateNative(layout.bytesSize(), layout.bytesAlignment());
     * }</pre></blockquote>
     *
     * @implNote The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     * Moreover, a client is responsible to call the {@link MemorySegment#close()} on a native memory segment,
     * to make sure the backing off-heap memory block is deallocated accordingly. Failure to do so will result in off-heap memory leaks.
     *
     * @param layout the layout of the off-heap memory block backing the native memory segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if the specified layout has illegal size or alignment constraint.
     */
    static MemorySegment allocateNative(MemoryLayout layout) {
        return allocateNative(layout.byteSize(), layout.byteAlignment());
    }

    /**
     * Creates a new native memory segment that models a newly allocated block of off-heap memory with given size (in bytes).
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
allocateNative(bytesSize, 1);
     * }</pre></blockquote>
     *
     * @implNote The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     * Moreover, a client is responsible to call the {@link MemorySegment#close()} on a native memory segment,
     * to make sure the backing off-heap memory block is deallocated accordingly. Failure to do so will result in off-heap memory leaks.
     *
     * @param bytesSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}.
     */
    static MemorySegment allocateNative(long bytesSize) {
        return allocateNative(bytesSize, 1);
    }

    /**
     * Creates a new mapped memory segment that models a memory-mapped region of a file from a given path.
     * <p>
     * The segment will feature all <a href="#access-modes">access modes</a> (see {@link #ALL_ACCESS}),
     * unless the given mapping mode is {@linkplain FileChannel.MapMode#READ_ONLY READ_ONLY}, in which case
     * the segment will not feature the {@link #WRITE} access mode.
     *
     * @implNote When obtaining a mapped segment from a newly created file, the initialization state of the contents of the block
     * of mapped memory associated with the returned mapped memory segment is unspecified and should not be relied upon.
     *
     * @param path the path to the file to memory map.
     * @param bytesOffset the offset (expressed in bytes) within the file at which the mapped segment is to start.
     * @param bytesSize the size (in bytes) of the mapped memory backing the memory segment.
     * @param mapMode a file mapping mode, see {@link FileChannel#map(FileChannel.MapMode, long, long)}; the chosen mapping mode
     *                might affect the behavior of the returned memory mapped segment (see {@link MappedMemorySegment#force()}).
     * @return a new mapped memory segment.
     * @throws IllegalArgumentException if {@code bytesOffset < 0}.
     * @throws IllegalArgumentException if {@code bytesSize < 0}.
     * @throws UnsupportedOperationException if an unsupported map mode is specified.
     * @throws IOException if the specified path does not point to an existing file, or if some other I/O error occurs.
     */
    static MappedMemorySegment mapFromPath(Path path, long bytesOffset, long bytesSize, FileChannel.MapMode mapMode) throws IOException {
        return MappedMemorySegmentImpl.makeMappedSegment(path, bytesOffset, bytesSize, mapMode);
    }

    /**
     * Creates a new native memory segment that models a newly allocated block of off-heap memory with given size and
     * alignment constraint (in bytes). The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     *
     * @implNote The block of off-heap memory associated with the returned native memory segment is initialized to zero.
     * Moreover, a client is responsible to call the {@link MemorySegment#close()} on a native memory segment,
     * to make sure the backing off-heap memory block is deallocated accordingly. Failure to do so will result in off-heap memory leaks.
     *
     * @param bytesSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param alignmentBytes the alignment constraint (in bytes) of the off-heap memory block backing the native memory segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}, {@code alignmentBytes < 0}, or if {@code alignmentBytes}
     * is not a power of 2.
     */
    static MemorySegment allocateNative(long bytesSize, long alignmentBytes) {
        if (bytesSize <= 0) {
            throw new IllegalArgumentException("Invalid allocation size : " + bytesSize);
        }

        if (alignmentBytes < 0 ||
                ((alignmentBytes & (alignmentBytes - 1)) != 0L)) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + alignmentBytes);
        }

        return NativeMemorySegmentImpl.makeNativeSegment(bytesSize, alignmentBytes);
    }

    /**
     * Returns a new native memory segment with given base address and size; the returned segment has its own temporal
     * bounds, and can therefore be closed; closing such a segment can optionally result in calling an user-provided cleanup
     * action. This method can be very useful when interacting with custom native memory sources (e.g. custom allocators,
     * GPU memory, etc.), where an address to some underlying memory region is typically obtained from native code
     * (often as a plain {@code long} value). The segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link #ALL_ACCESS}).
     * <p>
     * This method is <em>restricted</em>. Restricted method are unsafe, and, if used incorrectly, their use might crash
     * the JVM crash or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param addr the desired base address
     * @param bytesSize the desired size.
     * @param owner the desired owner thread. If {@code owner == null}, the returned segment is <em>not</em> confined.
     * @param cleanup a cleanup action to be executed when the {@link MemorySegment#close()} method is called on the
     *                returned segment. If {@code cleanup == null}, no cleanup action is executed.
     * @param attachment an object that must be kept alive by the returned segment; this can be useful when
     *                   the returned segment depends on memory which could be released if a certain object
     *                   is determined to be unreacheable. In most cases this will be set to {@code null}.
     * @return a new native memory segment with given base address, size, owner, cleanup action and object attachment.
     * @throws IllegalArgumentException if {@code bytesSize <= 0}.
     * @throws UnsupportedOperationException if {@code addr} is associated with an heap segment.
     * @throws IllegalAccessError if the runtime property {@code foreign.restricted} is not set to either
     * {@code permit}, {@code warn} or {@code debug} (the default value is set to {@code deny}).
     * @throws NullPointerException if {@code addr == null}.
     */
    static MemorySegment ofNativeRestricted(MemoryAddress addr, long bytesSize, Thread owner, Runnable cleanup, Object attachment) {
        Objects.requireNonNull(addr);
        if (bytesSize <= 0) {
            throw new IllegalArgumentException("Invalid size : " + bytesSize);
        }
        Utils.checkRestrictedAccess("MemorySegment.ofNativeRestricted");
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(addr, bytesSize, owner, cleanup, attachment);
    }

    // access mode masks

    /**
     * Read access mode; read operations are supported by a segment which supports this access mode.
     * @see MemorySegment#accessModes()
     * @see MemorySegment#withAccessModes(int)
     */
    int READ = 1;

    /**
     * Write access mode; write operations are supported by a segment which supports this access mode.
     * @see MemorySegment#accessModes()
     * @see MemorySegment#withAccessModes(int)
     */
    int WRITE = READ << 1;

    /**
     * Close access mode; calling {@link #close()} is supported by a segment which supports this access mode.
     * @see MemorySegment#accessModes()
     * @see MemorySegment#withAccessModes(int)
     */
    int CLOSE = WRITE << 1;

    /**
     * Acquire access mode; this segment support sharing with threads other than the owner thread, via spliterator
     * (see {@link #spliterator(MemorySegment, SequenceLayout)}).
     * @see MemorySegment#accessModes()
     * @see MemorySegment#withAccessModes(int)
     */
    int ACQUIRE = CLOSE << 1;

    /**
     * Handoff access mode; this segment support serial thread-confinement via thread ownership changes
     * (see {@link #withOwnerThread(Thread)}).
     * @see MemorySegment#accessModes()
     * @see MemorySegment#withAccessModes(int)
     */
    int HANDOFF = ACQUIRE << 1;

    /**
     * Default access mode; this is a union of all the access modes supported by memory segments.
     * @see MemorySegment#accessModes()
     * @see MemorySegment#withAccessModes(int)
     */
    int ALL_ACCESS = READ | WRITE | CLOSE | ACQUIRE | HANDOFF;
}
