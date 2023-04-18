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
import java.lang.invoke.MethodHandles;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.HeapMemorySegmentImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.layout.ValueLayouts;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;

/**
 * A memory segment provides access to a contiguous region of memory.
 * <p>
 * There are two kinds of memory segments:
 * <ul>
 *     <li>A <em>heap segment</em> is backed by, and provides access to, a region of memory inside the Java heap (an "on-heap" region).</li>
 *     <li>A <em>native segment</em> is backed by, and provides access to, a region of memory outside the Java heap (an "off-heap" region).</li>
 * </ul>
 * Heap segments can be obtained by calling one of the {@link MemorySegment#ofArray(int[])} factory methods.
 * These methods return a memory segment backed by the on-heap region that holds the specified Java array.
 * <p>
 * Native segments can be obtained by calling one of the {@link MemorySegment#allocateNative(long, long, SegmentScope)}
 * factory methods, which return a memory segment backed by a newly allocated off-heap region with the given size
 * and aligned to the given alignment constraint. Alternatively, native segments can be obtained by
 * {@link FileChannel#map(MapMode, long, long, SegmentScope) mapping} a file into a new off-heap region
 * (in some systems, this operation is sometimes referred to as {@code mmap}).
 * Segments obtained in this way are called <em>mapped</em> segments, and their contents can be {@linkplain #force() persisted} and
 * {@linkplain #load() loaded} to and from the underlying memory-mapped file.
 * <p>
 * Both kinds of segments are read and written using the same methods, known as <a href="#segment-deref">access operations</a>.
 * An access operation on a memory segment always and only provides access to the region for which the segment was obtained.
 *
 * <h2 id="segment-characteristics">Characteristics of memory segments</h2>
 *
 * Every memory segment has an {@linkplain #address() address}, expressed as a {@code long} value.
 * The nature of a segment's address depends on the kind of the segment:
 * <ul>
 * <li>The address of a heap segment is not a physical address, but rather an offset within the region of memory
 * which backs the segment. The region is inside the Java heap, so garbage collection might cause the region to be
 * relocated in physical memory over time, but this is not exposed to clients of the {@code MemorySegment} API who
 * see a stable <em>virtualized</em> address for a heap segment backed by the region.
 * A heap segment obtained from one of the {@link #ofArray(int[])} factory methods has an address of zero.</li>
 * <li>The address of a native segment (including mapped segments) denotes the physical address of the region of
 * memory which backs the segment.</li>
 * </ul>
 * <p>
 * Every memory segment has a {@linkplain #byteSize() size}. The size of a heap segment is derived from the Java array
 * from which it is obtained. This size is predictable across Java runtimes.
 * The size of a native segment is either passed explicitly
 * (as in {@link MemorySegment#allocateNative(long, SegmentScope)}) or derived from a {@link MemoryLayout}
 * (as in {@link MemorySegment#allocateNative(MemoryLayout, SegmentScope)}). The size of a memory segment is typically
 * a positive number but may be <a href="#wrapping-addresses">zero</a>, but never negative.
 * <p>
 * The address and size of a memory segment jointly ensure that access operations on the segment cannot fall
 * <em>outside</em> the boundaries of the region of memory which backs the segment.
 * That is, a memory segment has <em>spatial bounds</em>.
 * <p>
 * Every memory segment is associated with a {@linkplain SegmentScope scope}. This ensures that access operations
 * on a memory segment cannot occur when the region of memory which backs the memory segment is no longer available
 * (e.g., after the scope associated with the accessed memory segment is no longer {@linkplain SegmentScope#isAlive() alive}).
 * That is, a memory segment has <em>temporal bounds</em>.
 * <p>
 * Finally, access operations on a memory segment are subject to the thread-confinement checks enforced by the associated
 * scope; that is, if the segment is associated with the {@linkplain SegmentScope#global() global scope} or an {@linkplain SegmentScope#auto() automatic scope},
 * it can be accessed by multiple threads. If the segment is associated with an arena scope, then it can only be
 * accessed compatibly with the <a href="Arena.html#thread-confinement">arena confinement characteristics</a>.
 *
 * <h2 id="segment-deref">Accessing memory segments</h2>
 *
 * A memory segment can be read or written using various access operations provided in this class (e.g. {@link #get(ValueLayout.OfInt, long)}).
 * Each access operation takes a {@linkplain ValueLayout value layout}, which specifies the size and shape of the value,
 * and an offset, expressed in bytes.
 * For instance, to read an int from a segment, using {@linkplain ByteOrder#nativeOrder() default endianness}, the following code can be used:
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * int value = segment.get(ValueLayout.JAVA_INT, 0);
 * }
 *
 * If the value to be read is stored in memory using {@linkplain ByteOrder#BIG_ENDIAN big-endian} encoding, the access operation
 * can be expressed as follows:
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * int value = segment.get(ValueLayout.JAVA_INT.withOrder(BIG_ENDIAN), 0);
 * }
 *
 * For more complex access operations (e.g. structured memory access), clients can obtain a
 * {@linkplain MethodHandles#memorySegmentViewVarHandle(ValueLayout) var handle}
 * that accepts a segment and a {@code long} offset. More complex var handles
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
 * Alternatively, complex var handles can can be obtained
 * from {@linkplain MemoryLayout#varHandle(MemoryLayout.PathElement...) memory layouts}
 * by providing a so called <a href="MemoryLayout.html#layout-paths"><em>layout path</em></a>:
 *
 * {@snippet lang=java :
 * MemorySegment segment = ...
 * VarHandle intHandle = ValueLayout.JAVA_INT.arrayElementVarHandle();
 * intHandle.get(segment, 3L); // get int element at offset 3 * 4 = 12
 * }
 *
 * <h2 id="slicing">Slicing memory segments</h2>
 *
 * Memory segments support {@linkplain MemorySegment#asSlice(long, long) slicing}. Slicing a memory segment
 * returns a new memory segment that is backed by the same region of memory as the original. The address of the sliced
 * segment is derived from the address of the original segment, by adding an offset (expressed in bytes). The size of
 * the sliced segment is either derived implicitly (by subtracting the specified offset from the size of the original segment),
 * or provided explicitly. In other words, a sliced segment has <em>stricter</em> spatial bounds than those of the original segment:
 * {@snippet lang=java :
 * Arena arena = ...
 * MemorySegment segment = arena.allocate(100);
 * MemorySegment slice = segment.asSlice(50, 10);
 * slice.get(ValueLayout.JAVA_INT, 20); // Out of bounds!
 * arena.close();
 * slice.get(ValueLayout.JAVA_INT, 0); // Already closed!
 * }
 * The above code creates a native segment that is 100 bytes long; then, it creates a slice that starts at offset 50
 * of {@code segment}, and is 10 bytes long. That is, the address of the {@code slice} is {@code segment.address() + 50},
 * and its size is 10. As a result, attempting to read an int value at offset 20 of the
 * {@code slice} segment will result in an exception. The {@linkplain SegmentScope temporal bounds} of the original segment
 * is inherited by its slices; that is, when the scope associated with {@code segment} is no longer {@linkplain SegmentScope#isAlive() alive},
 * {@code slice} will also be become inaccessible.
 * <p>
 * A client might obtain a {@link Stream} from a segment, which can then be used to slice the segment (according to a given
 * element layout) and even allow multiple threads to work in parallel on disjoint segment slices
 * (to do this, the segment has to be associated with a scope that allows {@linkplain SegmentScope#isAccessibleBy(Thread) access}
 * from multiple threads). The following code can be used to sum all int values in a memory segment in parallel:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.openShared()) {
 *     SequenceLayout SEQUENCE_LAYOUT = MemoryLayout.sequenceLayout(1024, ValueLayout.JAVA_INT);
 *     MemorySegment segment = arena.allocate(SEQUENCE_LAYOUT);
 *     int sum = segment.elements(ValueLayout.JAVA_INT).parallel()
 *                      .mapToInt(s -> s.get(ValueLayout.JAVA_INT, 0))
 *                      .sum();
 * }
 *}
 *
 * <h2 id="segment-alignment">Alignment</h2>
 *
 * Access operations on a memory segment are constrained not only by the spatial and temporal bounds of the segment,
 * but also by the <em>alignment constraint</em> of the value layout specified to the operation. An access operation can
 * access only those offsets in the segment that denote addresses in physical memory which are <em>aligned</em> according
 * to the layout. An address in physical memory is <em>aligned</em> according to a layout if the address is an integer
 * multiple of the layout's alignment constraint. For example, the address 1000 is aligned according to an 8-byte alignment
 * constraint (because 1000 is an integer multiple of 8), and to a 4-byte alignment constraint, and to a 2-byte alignment
 * constraint; in contrast, the address 1004 is aligned according to a 4-byte alignment constraint, and to a 2-byte alignment
 * constraint, but not to an 8-byte alignment constraint.
 * Access operations are required to respect alignment because it can impact the performance of access operations, and
 * can also determine which access operations are available at a given physical address. For instance,
 * {@linkplain java.lang.invoke.VarHandle#compareAndSet(Object...) atomic access operations} operations using
 * {@link java.lang.invoke.VarHandle} are only permitted at aligned addresses. In addition, alignment
 * applies to an access operation whether the segment being accessed is a native segment or a heap segment.
 * <p>
 * If the segment being accessed is a native segment, then its {@linkplain #address() address} in physical memory can be
 * combined with the offset to obtain the <em>target address</em> in physical memory. The pseudo-function below demonstrates this:
 *
 * {@snippet lang = java:
 * boolean isAligned(MemorySegment segment, long offset, MemoryLayout layout) {
 *   return ((segment.address() + offset) % layout.byteAlignment()) == 0;
 * }
 * }
 *
 * For example:
 * <ul>
 * <li>A native segment with address 1000 can be accessed at offsets 0, 8, 16, 24, etc under an 8-byte alignment constraint,
 * because the target addresses (1000, 1008, 1016, 1024) are 8-byte aligned.
 * Access at offsets 1-7 or 9-15 or 17-23 is disallowed because the target addresses would not be 8-byte aligned.</li>
 * <li>A native segment with address 1000 can be accessed at offsets 0, 4, 8, 12, etc under a 4-byte alignment constraint,
 * because the target addresses (1000, 1004, 1008, 1012) are 4-byte aligned.
 * Access at offsets 1-3 or 5-7 or 9-11 is disallowed because the target addresses would not be 4-byte aligned.</li>
 * <li>A native segment with address 1000 can be accessed at offsets 0, 2, 4, 6, etc under a 2-byte alignment constraint,
 * because the target addresses (1000, 1002, 1004, 1006) are 2-byte aligned.
 * Access at offsets 1 or 3 or 5 is disallowed because the target addresses would not be 2-byte aligned.</li>
 * <li>A native segment with address 1004 can be accessed at offsets 0, 4, 8, 12, etc under a 4-byte alignment constraint,
 * and at offsets 0, 2, 4, 6, etc under a 2-byte alignment constraint.
 * Under an 8-byte alignment constraint, it can be accessed at offsets 4, 12, 20, 28, etc.</li>
 * <li>A native segment with address 1006 can be accessed at offsets 0, 2, 4, 6, etc under a 2-byte alignment constraint.
 * Under a 4-byte alignment constraint, it can be accessed at offsets 2, 6, 10, 14, etc.
 * Under an 8-byte alignment constraint, it can be accessed at offsets 2, 10, 18, 26, etc.
 * <li>A native segment with address 1007 can be accessed at offsets 0, 1, 2, 3, etc under a 1-byte alignment constraint.
 * Under a 2-byte alignment constraint, it can be accessed at offsets 1, 3, 5, 7, etc.
 * Under a 4-byte alignment constraint, it can be accessed at offsets 1, 5, 9, 13, etc.
 * Under an 8-byte alignment constraint, it can be accessed at offsets 1, 9, 17, 25, etc.</li>
 * </ul>
 * <p>
 * The alignment constraint used to access a segment is typically dictated by the shape of the data structure stored
 * in the segment. For example, if the programmer wishes to store a sequence of 8-byte values in a native segment, then
 * the segment should be allocated by specifying a 8-byte alignment constraint, either via {@link #allocateNative(long, long, SegmentScope)}
 * or {@link #allocateNative(MemoryLayout, SegmentScope)}. These factories ensure that the off-heap region of memory backing
 * the returned segment has a starting address that is 8-byte aligned. Subsequently, the programmer can access the
 * segment at the offsets of interest -- 0, 8, 16, 24, etc -- in the knowledge that every such access is aligned.
 * <p>
 * If the segment being accessed is a heap segment, then determining whether access is aligned is more complex.
 * The address of the segment in physical memory is not known, and is not even fixed (it may change when the segment
 * is relocated during garbage collection). This means that the address cannot be combined with the specified offset to
 * determine a target address in physical memory. Since the alignment constraint <em>always</em> refers to alignment of
 * addresses in physical memory, it is not possible in principle to determine if any offset in a heap segment is aligned.
 * For example, suppose the programmer chooses a 8-byte alignment constraint and tries
 * to access offset 16 in a heap segment. If the heap segment's address 0 corresponds to physical address 1000,
 * then the target address (1016) would be aligned, but if address 0 corresponds to physical address 1004,
 * then the target address (1020) would not be aligned. It is undesirable to allow access to target addresses that are
 * aligned according to the programmer's chosen alignment constraint, but might not be predictably aligned in physical memory
 * (e.g. because of platform considerations and/or garbage collection behavior).
 * <p>
 * In practice, the Java runtime lays out arrays in memory so that each n-byte element occurs at an n-byte
 * aligned physical address. The runtime preserves this invariant even if the array is relocated during garbage
 * collection. Access operations rely on this invariant to determine if the specified offset in a heap segment refers
 * to an aligned address in physical memory. For example:
 * <ul>
 * <li>The starting physical address of a {@code long[]} array will be 8-byte aligned (e.g. 1000), so that successive long elements
 * occur at 8-byte aligned addresses (e.g., 1000, 1008, 1016, 1024, etc.) A heap segment backed by a {@code long[]} array
 * can be accessed at offsets 0, 8, 16, 24, etc under an 8-byte alignment constraint. In addition, the segment can be
 * accessed at offsets 0, 4, 8, 12, etc under a 4-byte alignment constraint, because the target addresses
 * (1000, 1004, 1008, 1012) are 4-byte aligned. And, the segment can be accessed at offsets 0, 2, 4, 6, etc under a
 * 2-byte alignment constraint, because the target addresses (e.g. 1000, 1002, 1004, 1006) are 2-byte aligned.</li>
 * <li>The starting physical address of a {@code short[]} array will be 2-byte aligned (e.g. 1006) so that successive
 * short elements occur at 2-byte aligned addresses (e.g. 1006, 1008, 1010, 1012, etc). A heap segment backed by a
 * {@code short[]} array can be accessed at offsets 0, 2, 4, 6, etc under a 2-byte alignment constraint. The segment cannot
 * be accessed at <em>any</em> offset under a 4-byte alignment constraint, because there is no guarantee that the target
 * address would be 4-byte aligned, e.g., offset 0 would correspond to physical address 1006 while offset 1 would correspond
 * to physical address 1007. Similarly, the segment cannot be accessed at any offset under an 8-byte alignment constraint,
 * because because there is no guarantee that the target address would be 8-byte aligned, e.g., offset 2 would correspond
 * to physical address 1008 but offset 4 would correspond to physical address 1010.</li>
 * </ul>
 * <p>
 * In other words, heap segments feature a <em>maximum</em> alignment which is derived from the size of the elements of
 * the Java array backing the segment, as shown in the following table:
 *
 * <blockquote><table class="plain">
 * <caption style="display:none">Maximum alignment of heap segments</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Array type (of backing region)</th>
 *     <th scope="col">Maximum supported alignment (in bytes)</th>
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
 * Heap segments can only be accessed using a layout whose alignment is smaller or equal to the
 * maximum alignment associated with the heap segment. Attempting to access a heap segment using a layout
 * whose alignment is greater than the maximum alignment associated with the heap segment will fail,
 * as demonstrated in the following example:
 *
 * {@snippet lang=java :
 * MemorySegment byteSegment = MemorySegment.ofArray(new byte[10]);
 * byteSegment.get(ValueLayout.JAVA_INT, 0); // fails: layout alignment is 4, segment max alignment is 1
 * }
 *
 * In such circumstances, clients have two options. They can use a heap segment backed by a different array
 * type (e.g. {@code long[]}), capable of supporting greater maximum alignment:
 *
 * {@snippet lang=java :
 * MemorySegment longSegment = MemorySegment.ofArray(new long[10]);
 * longSegment.get(ValueLayout.JAVA_INT, 0); // ok: layout alignment is 4, segment max alignment is 8
 * }
 *
 * Alternatively, they can invoke the access operation with an <em>unaligned layout</em>.
 * All unaligned layout constants (e.g. {@link ValueLayout#JAVA_INT_UNALIGNED}) have their alignment constraint set to 1:
 * {@snippet lang=java :
 * MemorySegment byteSegment = MemorySegment.ofArray(new byte[10]);
 * byteSegment.get(ValueLayout.JAVA_INT_UNALIGNED, 0); // ok: layout alignment is 1, segment max alignment is 1
 * }
 *
 * <h2 id="wrapping-addresses">Zero-length memory segments</h2>
 *
 * When interacting with <a href="package-summary.html#ffa">foreign functions</a>, it is common for those functions
 * to allocate a region of memory and return a pointer to that region. Modeling the region of memory with a memory segment
 * is challenging because the Java runtime has no insight into the size of the region. Only the address of the start of
 * the region, stored in the pointer, is available. For example, a C function with return type {@code char*} might return
 * a pointer to a region containing a single {@code char} value, or to a region containing an array of {@code char} values,
 * where the size of the array might be provided in a separate parameter. The size of the array is not readily apparent
 * to the code calling the foreign function and hoping to use its result.
 * <p>
 * The {@link Linker} represents a pointer returned from a foreign function with a <em>zero-length memory segment</em>.
 * The address of the segment is the address stored in the pointer. The size of the segment is zero. Similarly, when a
 * client reads an <em>address</em> from a memory segment, a zero-length memory segment is returned.
 * <p>
 * Since a zero-length segment features trivial spatial bounds, any attempt to access these segments will fail with
 * {@link IndexOutOfBoundsException}. This is a crucial safety feature: as these segments are associated with a region
 * of memory whose size is not known, any access operations involving these segments cannot be validated.
 * In effect, a zero-length memory segment <em>wraps</em> an address, and it cannot be used without explicit intent.
 * <p>
 * Zero-length memory segments obtained when interacting with foreign functions are associated with the
 * {@link SegmentScope#global() global scope}. This is because the Java runtime, in addition to having no insight
 * into the size of the region of memory backing a pointer returned from a foreign function, also has no insight
 * into the lifetime intended for said region of memory by the foreign function that allocated it. The global scope
 * ensures that the obtained segment can be passed, opaquely, to other pointer-accepting foreign functions.
 * <p>
 * To access native zero-length memory segments, clients have two options, both of which are <em>unsafe</em>. Clients
 * can {@linkplain java.lang.foreign.MemorySegment#ofAddress(long, long, SegmentScope) obtain}
 * a <em>new</em> native segment, with new spatial and temporal bounds, as follows:
 *
 * {@snippet lang = java:
 * SegmentScope scope = ... // obtains a scope
 * MemorySegment foreign = someSegment.get(ValueLayout.ADDRESS, 0); // wrap address into segment (size = 0)
 * MemorySegment segment = MemorySegment.ofAddress(foreign.address(), 4, scope); // create new segment (size = 4)
 * int x = segment.get(ValueLayout.JAVA_INT, 0); //ok
 *}
 *
 * Alternatively, clients can obtain an {@linkplain java.lang.foreign.ValueLayout.OfAddress#asUnbounded() unbounded}
 * address value layout. When an access operation, or a function descriptor that is passed to a downcall method handle,
 * uses an unbounded address value layouts, the runtime will wrap any corresponding raw addresses with native segments
 * with <em>maximal</em> size (i.e. {@linkplain java.lang.Long#MAX_VALUE}). As such, these segments can be accessed directly, as follows:
 *
 * {@snippet lang = java:
 * MemorySegment foreign = someSegment.get(ValueLayout.ADDRESS.asUnbounded(), 0); // wrap address into segment (size = Long.MAX_VALUE)
 * int x = foreign.get(ValueLayout.JAVA_INT, 0); //ok
 *}
 *
 * Both {@link #ofAddress(long, long, SegmentScope)} and {@link ValueLayout.OfAddress#asUnbounded()} are
 * <a href="package-summary.html#restricted"><em>restricted</em></a> methods, and should be used with caution:
 * for instance, sizing a segment incorrectly could result in a VM crash when attempting to access the memory segment.
 * <p>
 * Which approach is taken largely depends on the information that a client has available when obtaining a memory segment
 * wrapping a native pointer. For instance, if such pointer points to a C struct, the client might prefer to resize the
 * segment unsafely, to match the size of the struct (so that out-of-bounds access will be detected by the API).
 * In other instances, however, there will be no, or little information as to what spatial and/or temporal bounds should
 * be associated with a given native pointer. In these cases using an unbounded address layout might be preferable.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface MemorySegment permits AbstractMemorySegmentImpl {

    /**
     * {@return the address of this memory segment}
     */
    long address();

    /**
     * {@return the Java array associated with this memory segment, if any}
     */
    Optional<Object> array();

    /**
     * Returns a spliterator for this memory segment. The returned spliterator reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, {@link Spliterator#IMMUTABLE}, {@link Spliterator#NONNULL} and {@link Spliterator#ORDERED}
     * characteristics.
     * <p>
     * The returned spliterator splits this segment according to the specified element layout; that is,
     * if the supplied layout has size N, then calling {@link Spliterator#trySplit()} will result in a spliterator serving
     * approximately {@code S/N} elements (depending on whether N is even or not), where {@code S} is the size of
     * this segment. As such, splitting is possible as long as {@code S/N >= 2}. The spliterator returns segments that
     * are associated with the same scope as that associated with this segment.
     * <p>
     * The returned spliterator effectively allows to slice this segment into disjoint {@linkplain #asSlice(long, long) slices},
     * which can then be processed in parallel by multiple threads.
     *
     * @param elementLayout the layout to be used for splitting.
     * @return the element spliterator for this segment
     * @throws IllegalArgumentException if the {@code elementLayout} size is zero, or the segment size modulo the
     * {@code elementLayout} size is greater than zero, if this segment is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
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
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the {@code elementLayout} alignment is greater than its size.
     */
    Stream<MemorySegment> elements(MemoryLayout elementLayout);

    /**
     * {@return the scope associated with this memory segment}
     */
    SegmentScope scope();

    /**
     * {@return the size (in bytes) of this memory segment}
     */
    long byteSize();

    /**
     * Returns a slice of this memory segment, at the given offset. The returned segment's address is the address
     * of this segment plus the given offset; its size is specified by the given argument.
     *
     * @see #asSlice(long)
     *
     * @param offset The new segment base offset (relative to the address of this segment), specified in bytes.
     * @param newSize The new segment size, specified in bytes.
     * @return a slice of this memory segment.
     * @throws IndexOutOfBoundsException if {@code offset < 0}, {@code offset > byteSize()}, {@code newSize < 0}, or {@code newSize > byteSize() - offset}
     */
    MemorySegment asSlice(long offset, long newSize);

    /**
     * Returns a slice of this memory segment, at the given offset. The returned segment's address is the address
     * of this segment plus the given offset; its size is computed by subtracting the specified offset from this segment size.
     * <p>
     * Equivalent to the following code:
     * {@snippet lang=java :
     * asSlice(offset, byteSize() - offset);
     * }
     *
     * @see #asSlice(long, long)
     *
     * @param offset The new segment base offset (relative to the address of this segment), specified in bytes.
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
     * Returns {@code true} if this segment is a native segment. A native segment is
     * created e.g. using the {@link #allocateNative(long, SegmentScope)} (and related) factory, or by
     * {@linkplain #ofBuffer(Buffer) wrapping} a {@linkplain ByteBuffer#allocateDirect(int) direct buffer}.
     * @return {@code true} if this segment is native segment.
     */
    boolean isNative();

    /**
     * Returns {@code true} if this segment is a mapped segment. A mapped memory segment is created e.g. using the
     * {@link FileChannel#map(FileChannel.MapMode, long, long, SegmentScope)} factory, or by
     * {@linkplain #ofBuffer(Buffer) wrapping} a {@linkplain java.nio.MappedByteBuffer mapped byte buffer}.
     * @return {@code true} if this segment is a mapped segment.
     */
    boolean isMapped();

    /**
     * Returns a slice of this segment that is the overlap between this and
     * the provided segment.
     *
     * <p>Two segments {@code S1} and {@code S2} are said to overlap if it is possible to find
     * at least two slices {@code L1} (from {@code S1}) and {@code L2} (from {@code S2}) that are backed by the
     * same region of memory. As such, it is not possible for a
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
     * <p>The offset is relative to the address of this segment and can be
     * a negative or positive value. For instance, if both segments are native
     * segments, or heap segments backed by the same array, the resulting offset
     * can be computed as follows:
     *
     * {@snippet lang=java :
     * other.address() - segment.address()
     * }
     *
     * If the segments share the same address, {@code 0} is returned. If
     * {@code other} is a slice of this segment, the offset is always
     * {@code 0 <= x < this.byteSize()}.
     *
     * @param other the segment to retrieve an offset to.
     * @throws UnsupportedOperationException if the two segments cannot be compared, e.g. because they are of
     * a different kind, or because they are backed by different Java arrays.
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code src} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code src.scope().isAccessibleBy(T) == false}.
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
     * {@linkplain #address() address} of each segment and will be in the
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code other} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code other.scope().isAccessibleBy(T) == false}.
     */
    default long mismatch(MemorySegment other) {
        Objects.requireNonNull(other);
        return MemorySegment.mismatch(this, 0, byteSize(), other, 0, other.byteSize());
    }

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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws UnsupportedOperationException if this segment is not a mapped memory segment, e.g. if
     * {@code isMapped() == false}.
     * @throws UncheckedIOException if there is an I/O error writing the contents of this segment to the associated storage device
     */
    void force();

    /**
     * Wraps this segment in a {@link ByteBuffer}. Some properties of the returned buffer are linked to
     * the properties of this segment. For instance, if this segment is <em>immutable</em>
     * (e.g. the segment is a read-only segment, see {@link #isReadOnly()}), then the resulting buffer is <em>read-only</em>
     * (see {@link ByteBuffer#isReadOnly()}). Additionally, if this is a native segment, the resulting buffer is
     * <em>direct</em> (see {@link ByteBuffer#isDirect()}).
     * <p>
     * The returned buffer's position (see {@link ByteBuffer#position()}) is initially set to zero, while
     * the returned buffer's capacity and limit (see {@link ByteBuffer#capacity()} and {@link ByteBuffer#limit()}, respectively)
     * are set to this segment' size (see {@link MemorySegment#byteSize()}). For this reason, a byte buffer cannot be
     * returned if this segment' size is greater than {@link Integer#MAX_VALUE}.
     * <p>
     * The life-cycle of the returned buffer will be tied to that of this segment. That is, accessing the returned buffer
     * after the scope associated with this segment is no longer {@linkplain SegmentScope#isAlive() alive}, will
     * throw an {@link IllegalStateException}. Similarly, accessing the returned buffer from a thread {@code T}
     * such that {@code scope().isAccessible(T) == false} will throw a {@link WrongThreadException}.
     * <p>
     * If this segment is associated with a scope that can only be accessed from a single thread, calling read/write I/O
     * operations on the resulting buffer might result in an unspecified exception being thrown. Examples of such problematic operations are
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code byte[]} instance,
     * e.g. its size is greater than {@link Integer#MAX_VALUE}.
     */
    byte[] toArray(ValueLayout.OfByte elementLayout);

    /**
     * Copy the contents of this memory segment into a new short array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new short array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code short[]} instance,
     * e.g. because {@code byteSize() % 2 != 0}, or {@code byteSize() / 2 > Integer#MAX_VALUE}
     */
    short[] toArray(ValueLayout.OfShort elementLayout);

    /**
     * Copy the contents of this memory segment into a new char array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new char array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code char[]} instance,
     * e.g. because {@code byteSize() % 2 != 0}, or {@code byteSize() / 2 > Integer#MAX_VALUE}.
     */
    char[] toArray(ValueLayout.OfChar elementLayout);

    /**
     * Copy the contents of this memory segment into a new int array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new int array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code int[]} instance,
     * e.g. because {@code byteSize() % 4 != 0}, or {@code byteSize() / 4 > Integer#MAX_VALUE}.
     */
    int[] toArray(ValueLayout.OfInt elementLayout);

    /**
     * Copy the contents of this memory segment into a new float array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new float array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code float[]} instance,
     * e.g. because {@code byteSize() % 4 != 0}, or {@code byteSize() / 4 > Integer#MAX_VALUE}.
     */
    float[] toArray(ValueLayout.OfFloat elementLayout);

    /**
     * Copy the contents of this memory segment into a new long array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new long array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this segment's contents cannot be copied into a {@code long[]} instance,
     * e.g. because {@code byteSize() % 8 != 0}, or {@code byteSize() / 8 > Integer#MAX_VALUE}.
     */
    long[] toArray(ValueLayout.OfLong elementLayout);

    /**
     * Copy the contents of this memory segment into a new double array.
     * @param elementLayout the source element layout. If the byte order associated with the layout is
     * different from the {@linkplain ByteOrder#nativeOrder native order}, a byte swap operation will be performed on each array element.
     * @return a new double array whose contents are copied from this memory segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
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
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a Java string constructed from the bytes read from the given starting address up to (but not including)
     * the first {@code '\0'} terminator character (assuming one is found).
     * @throws IllegalArgumentException if the size of the UTF-8 string is greater than the largest string supported by the platform.
     * @throws IndexOutOfBoundsException if {@code offset < 0} or {@code S + offset > byteSize()}, where {@code S} is the size of the UTF-8
     * string (including the terminator character).
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
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
     * <p>
     * If the given string contains any {@code '\0'} characters, they will be
     * copied as well. This means that, depending on the method used to read
     * the string, such as {@link MemorySegment#getUtf8String(long)}, the string
     * will appear truncated when read again.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     *               the final address of this write operation can be expressed as {@code address() + offset}.
     * @param str the Java string to be written into this segment.
     * @throws IndexOutOfBoundsException if {@code offset < 0} or {@code str.getBytes().length() + offset >= byteSize()}.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     */
    default void setUtf8String(long offset, String str) {
        Utils.toCString(str.getBytes(StandardCharsets.UTF_8), SegmentAllocator.prefixAllocator(asSlice(offset)));
    }


    /**
     * Creates a memory segment that is backed by the same region of memory that backs the given {@link Buffer} instance.
     * The segment starts relative to the buffer's position (inclusive) and ends relative to the buffer's limit (exclusive).
     * <p>
     * If the buffer is {@linkplain Buffer#isReadOnly() read-only}, the resulting segment will also be
     * {@linkplain ByteBuffer#isReadOnly() read-only}. Moreover, if the buffer is a {@linkplain Buffer#isDirect() direct buffer},
     * the returned segment is a native segment; otherwise the returned memory segment is a heap segment.
     * <p>
     * The scope {@code S} associated with the returned segment is computed as follows:
     * <ul>
     *     <li>if the buffer has been obtained by calling {@link #asByteBuffer()} on a memory segment whose scope
     *     is {@code S'}, then {@code S = S'}; or</li>
     *     <li>if the buffer is a heap buffer, then {@code S} is the {@linkplain SegmentScope#global() global scope}; or
     *     <li>if the buffer is a direct buffer, then {@code S} is a scope that is always alive and which keeps the buffer reachable.
     *     Therefore, the off-heap region of memory backing the buffer instance will remain available as long as the
     *     returned segment is reachable.</li>
     * </ul>
     *
     * @param buffer the buffer instance to be turned into a new memory segment.
     * @return a memory segment, derived from the given buffer instance.
     * @throws IllegalArgumentException if the provided {@code buffer} is a heap buffer but is not backed by an array.
     *                                  For example, buffers directly or indirectly obtained via
     *                                  ({@link CharBuffer#wrap(CharSequence)} or {@link CharBuffer#wrap(char[], int, int)}
     *                                  are not backed by an array.
     */
    static MemorySegment ofBuffer(Buffer buffer) {
        return AbstractMemorySegmentImpl.ofBuffer(buffer);
    }

    /**
     * Creates a heap segment backed by the on-heap region of memory that holds the given byte array.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}, and
     * its {@link #address()} is set to zero.
     *
     * @param byteArray the primitive array backing the heap memory segment.
     * @return a heap memory segment backed by a byte array.
     */
    static MemorySegment ofArray(byte[] byteArray) {
        return HeapMemorySegmentImpl.OfByte.fromArray(byteArray);
    }

    /**
     * Creates a heap segment backed by the on-heap region of memory that holds the given char array.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}, and
     * its {@link #address()} is set to zero.
     *
     * @param charArray the primitive array backing the heap segment.
     * @return a heap memory segment backed by a char array.
     */
    static MemorySegment ofArray(char[] charArray) {
        return HeapMemorySegmentImpl.OfChar.fromArray(charArray);
    }

    /**
     * Creates a heap segment backed by the on-heap region of memory that holds the given short array.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}, and
     * its {@link #address()} is set to zero.
     *
     * @param shortArray the primitive array backing the heap segment.
     * @return a heap memory segment backed by a short array.
     */
    static MemorySegment ofArray(short[] shortArray) {
        return HeapMemorySegmentImpl.OfShort.fromArray(shortArray);
    }

    /**
     * Creates a heap segment backed by the on-heap region of memory that holds the given int array.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}, and
     * its {@link #address()} is set to zero.
     *
     * @param intArray the primitive array backing the heap segment.
     * @return a heap memory segment backed by an int array.
     */
    static MemorySegment ofArray(int[] intArray) {
        return HeapMemorySegmentImpl.OfInt.fromArray(intArray);
    }

    /**
     * Creates a heap segment backed by the on-heap region of memory that holds the given float array.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}, and
     * its {@link #address()} is set to zero.
     *
     * @param floatArray the primitive array backing the heap segment.
     * @return a heap memory segment backed by a float array.
     */
    static MemorySegment ofArray(float[] floatArray) {
        return HeapMemorySegmentImpl.OfFloat.fromArray(floatArray);
    }

    /**
     * Creates a heap segment backed by the on-heap region of memory that holds the given long array.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}, and
     * its {@link #address()} is set to zero.
     *
     * @param longArray the primitive array backing the heap segment.
     * @return a heap memory segment backed by a long array.
     */
    static MemorySegment ofArray(long[] longArray) {
        return HeapMemorySegmentImpl.OfLong.fromArray(longArray);
    }

    /**
     * Creates a heap segment backed by the on-heap region of memory that holds the given double array.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}, and
     * its {@link #address()} is set to zero.
     *
     * @param doubleArray the primitive array backing the heap segment.
     * @return a heap memory segment backed by a double array.
     */
    static MemorySegment ofArray(double[] doubleArray) {
        return HeapMemorySegmentImpl.OfDouble.fromArray(doubleArray);
    }

    /**
     * A zero-length native segment modelling the {@code NULL} address.
     */
    MemorySegment NULL = NativeMemorySegmentImpl.makeNativeSegmentUnchecked(0L, 0);

    /**
     * Creates a zero-length native segment from the given {@linkplain #address() address value}.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang = java:
     * ofAddress(address, 0);
     *}
     * @param address the address of the returned native segment.
     * @return a zero-length native segment with the given address.
     */
    static MemorySegment ofAddress(long address) {
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(address, 0);
    }

    /**
     * Creates a native segment with the given size and {@linkplain #address() address value}.
     * The returned segment is associated with the {@linkplain SegmentScope#global() global scope}.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang = java:
     * ofAddress(address, byteSize, SegmentScope.global());
     *}
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     * @param address the address of the returned native segment.
     * @param byteSize the size (in bytes) of the returned native segment.
     * @return a zero-length native segment with the given address and size.
     * @throws IllegalArgumentException if {@code byteSize < 0}.
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     */
    @CallerSensitive
    static MemorySegment ofAddress(long address, long byteSize) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), MemorySegment.class, "ofAddress");
        return MemorySegment.ofAddress(address, byteSize, SegmentScope.global());
    }

    /**
     * Creates a native segment with the given size, address, and scope.
     * This method can be useful when interacting with custom memory sources (e.g. custom allocators),
     * where an address to some underlying region of memory is typically obtained from foreign code
     * (often as a plain {@code long} value).
     * <p>
     * The returned segment is not read-only (see {@link MemorySegment#isReadOnly()}), and is associated with the
     * provided scope.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang = java:
     * ofAddress(address, byteSize, scope, null);
     *}
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     * @param address the returned segment's address.
     * @param byteSize the desired size.
     * @param scope the scope associated with the returned native segment.
     * @return a native segment with the given address, size and scope.
     * @throws IllegalArgumentException if {@code byteSize < 0}.
     * @throws IllegalStateException if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     */
    @CallerSensitive
    @ForceInline
    static MemorySegment ofAddress(long address, long byteSize, SegmentScope scope) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), MemorySegment.class, "ofAddress");
        Objects.requireNonNull(scope);
        Utils.checkAllocationSizeAndAlign(byteSize, 1);
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(address, byteSize, scope, null);
    }

    /**
     * Creates a native segment with the given size, address, and scope.
     * This method can be useful when interacting with custom memory sources (e.g. custom allocators),
     * where an address to some underlying region of memory is typically obtained from foreign code
     * (often as a plain {@code long} value).
     * <p>
     * The returned segment is not read-only (see {@link MemorySegment#isReadOnly()}), and is associated with the
     * provided scope.
     * <p>
     * The provided cleanup action (if any) will be invoked when the scope becomes not {@linkplain SegmentScope#isAlive() alive}.
     * <p>
     * Clients should ensure that the address and bounds refer to a valid region of memory that is accessible for reading and,
     * if appropriate, writing; an attempt to access an invalid address from Java code will either return an arbitrary value,
     * have no visible effect, or cause an unspecified exception to be thrown.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     *
     * @param address the returned segment's address.
     * @param byteSize the desired size.
     * @param scope the scope associated with the returned native segment.
     * @param cleanupAction the custom cleanup action to be associated to the returned segment (can be null).
     * @return a native segment with the given address, size and scope.
     * @throws IllegalArgumentException if {@code byteSize < 0}.
     * @throws IllegalStateException if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     */
    @CallerSensitive
    static MemorySegment ofAddress(long address, long byteSize, SegmentScope scope, Runnable cleanupAction) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), MemorySegment.class, "ofAddress");
        Objects.requireNonNull(scope);
        Utils.checkAllocationSizeAndAlign(byteSize, 1);
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(address, byteSize, scope, cleanupAction);
    }

    /**
     * Creates a native segment with the given layout and scope.
     * <p>
     * The lifetime off-heap region of memory associated with the returned native segment is determined by the
     * provided scope. The off-heap memory region is deallocated when the scope becomes not
     * {@linkplain SegmentScope#isAlive() alive}. If the scope has been obtained using an {@link Arena},
     * clients are responsible for ensuring that the arena is closed when the returned segment is no longer in use
     * Failure to do so will result in off-heap memory leaks. As an alternative, an {@linkplain SegmentScope#auto() automatic scope}
     * can be used, allowing the off-heap memory region associated with the returned native segment to be
     * automatically released some unspecified time after the scope is no longer referenced.
     * <p>
     * The {@linkplain #address() address} of the returned memory segment is the starting address of
     * the newly allocated off-heap region backing the segment. Moreover, the {@linkplain #address() address}
     * of the returned segment will be aligned according to the alignment constraint of the provided layout.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang=java :
     * allocateNative(layout.bytesSize(), layout.bytesAlignment(), scope);
     * }
     * <p>
     * The region of off-heap region backing the returned native segment is initialized to zero.
     *
     * @param layout the layout of the off-heap memory region backing the native segment.
     * @param scope the scope associated with the returned native segment.
     * @return a new native segment.
     * @throws IllegalStateException if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     */
    static MemorySegment allocateNative(MemoryLayout layout, SegmentScope scope) {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(scope);
        return allocateNative(layout.byteSize(), layout.byteAlignment(), scope);
    }

    /**
     * Creates a native segment with the given size (in bytes) and scope.
     * <p>
     * The lifetime off-heap region of memory associated with the returned native segment is determined by the
     * provided scope. The off-heap memory region is deallocated when the scope becomes not
     * {@linkplain SegmentScope#isAlive() alive}. If the scope has been obtained using an {@link Arena},
     * clients are responsible for ensuring that the arena is closed when the returned segment is no longer in use
     * Failure to do so will result in off-heap memory leaks. As an alternative, an {@linkplain SegmentScope#auto() automatic scope}
     * can be used, allowing the off-heap memory region associated with the returned native segment to be
     * automatically released some unspecified time after the scope is no longer referenced.
     * <p>
     * The {@linkplain #address() address} of the returned memory segment is the starting address of
     * the newly allocated off-heap region backing the segment. Moreover, the {@linkplain #address() address}
     * of the returned segment is guaranteed to be at least 1-byte aligned.
     * <p>
     * This is equivalent to the following code:
     * {@snippet lang=java :
     * allocateNative(bytesSize, 1, scope);
     * }
     * <p>
     * The region of off-heap region backing the returned native segment is initialized to zero.
     *
     * @param byteSize the size (in bytes) of the off-heap memory region of memory backing the native memory segment.
     * @param scope the scope associated with the returned native segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code byteSize < 0}.
     * @throws IllegalStateException if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     */
    static MemorySegment allocateNative(long byteSize, SegmentScope scope) {
        return allocateNative(byteSize, 1, scope);
    }

    /**
     * Creates a native segment with the given size (in bytes), alignment (in bytes) and scope.
     * <p>
     * The lifetime off-heap region of memory associated with the returned native segment is determined by the
     * provided scope. The off-heap memory region is deallocated when the scope becomes not
     * {@linkplain SegmentScope#isAlive() alive}. If the scope has been obtained using an {@link Arena},
     * clients are responsible for ensuring that the arena is closed when the returned segment is no longer in use
     * Failure to do so will result in off-heap memory leaks. As an alternative, an {@linkplain SegmentScope#auto() automatic scope}
     * can be used, allowing the off-heap memory region associated with the returned native segment to be
     * automatically released some unspecified time after the scope is no longer referenced.
     * <p>
     * The {@linkplain #address() address} of the returned memory segment is the starting address of
     * the newly allocated off-heap region backing the segment. Moreover, the {@linkplain #address() address}
     * of the returned segment will be aligned according to the provided alignment constraint.
     * <p>
     * The region of off-heap region backing the returned native segment is initialized to zero.
     *
     * @param byteSize the size (in bytes) of the off-heap region of memory backing the native memory segment.
     * @param byteAlignment the alignment constraint (in bytes) of the off-heap region of memory backing the native memory segment.
     * @param scope the scope associated with the returned native segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code byteSize < 0}, {@code byteAlignment <= 0}, or if {@code byteAlignment}
     *                                  is not a power of 2.
     * @throws IllegalStateException if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     */
    static MemorySegment allocateNative(long byteSize, long byteAlignment, SegmentScope scope) {
        Objects.requireNonNull(scope);
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        return NativeMemorySegmentImpl.makeNativeSegment(byteSize, byteAlignment, scope);
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code srcSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code srcSegment.scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code dstSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code dstSegment.scope().isAccessibleBy(T) == false}.
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
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the source
     * (resp. destination) element layout, or if the source (resp. destination) element layout alignment is greater than its size.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code srcSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code srcSegment().scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code dstSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code dstSegment().scope().isAccessibleBy(T) == false}.
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
     * Reads a byte from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a byte value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default byte get(ValueLayout.OfByte layout, long offset) {
        return (byte) ((ValueLayouts.OfByteImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes a byte into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the byte value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfByte layout, long offset, byte value) {
        ((ValueLayouts.OfByteImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads a boolean from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a boolean value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default boolean get(ValueLayout.OfBoolean layout, long offset) {
        return (boolean) ((ValueLayouts.OfBooleanImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes a boolean into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the boolean value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfBoolean layout, long offset, boolean value) {
        ((ValueLayouts.OfBooleanImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads a char from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a char value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default char get(ValueLayout.OfChar layout, long offset) {
        return (char) ((ValueLayouts.OfCharImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes a char into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the char value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfChar layout, long offset, char value) {
        ((ValueLayouts.OfCharImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads a short from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a short value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default short get(ValueLayout.OfShort layout, long offset) {
        return (short) ((ValueLayouts.OfShortImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes a short into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the short value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfShort layout, long offset, short value) {
        ((ValueLayouts.OfShortImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads an int from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return an int value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default int get(ValueLayout.OfInt layout, long offset) {
        return (int) ((ValueLayouts.OfIntImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes an int into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the int value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfInt layout, long offset, int value) {
        ((ValueLayouts.OfIntImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads a float from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a float value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default float get(ValueLayout.OfFloat layout, long offset) {
        return (float)((ValueLayouts.OfFloatImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes a float into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the float value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfFloat layout, long offset, float value) {
        ((ValueLayouts.OfFloatImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads a long from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a long value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default long get(ValueLayout.OfLong layout, long offset) {
        return (long) ((ValueLayouts.OfLongImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes a long into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the long value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfLong layout, long offset, long value) {
        ((ValueLayouts.OfLongImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads a double from this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a double value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default double get(ValueLayout.OfDouble layout, long offset) {
        return (double) ((ValueLayouts.OfDoubleImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes a double into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the double value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void set(ValueLayout.OfDouble layout, long offset, double value) {
        ((ValueLayouts.OfDoubleImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads an address from this segment at the given offset, with the given layout. The read address is wrapped in
     * a native segment, associated with the {@linkplain SegmentScope#global() global scope}. Under normal conditions,
     * the size of the returned segment is {@code 0}. However, if the provided layout is an
     * {@linkplain ValueLayout.OfAddress#asUnbounded() unbounded} address layout, then the size of the returned
     * segment is {@code Long.MAX_VALUE}.
     * @param layout the layout of the region of memory to be read.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @return a native segment wrapping an address read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default MemorySegment get(ValueLayout.OfAddress layout, long offset) {
        return (MemorySegment) ((ValueLayouts.OfAddressImpl) layout).accessHandle().get(this, offset);
    }

    /**
     * Writes an address into this segment at the given offset, with the given layout.
     *
     * @param layout the layout of the region of memory to be written.
     * @param offset offset in bytes (relative to this segment address) at which this access operation will occur.
     * @param value the address value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     * @throws UnsupportedOperationException if {@code value} is not a {@linkplain #isNative() native} segment.
     */
    @ForceInline
    default void set(ValueLayout.OfAddress layout, long offset, MemorySegment value) {
        ((ValueLayouts.OfAddressImpl) layout).accessHandle().set(this, offset, value);
    }

    /**
     * Reads a char from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be read.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @return a char value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default char getAtIndex(ValueLayout.OfChar layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (char) ((ValueLayouts.OfCharImpl) layout).accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a char into this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be written.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @param value the char value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfChar layout, long index, char value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        ((ValueLayouts.OfCharImpl) layout).accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a short from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be read.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @return a short value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default short getAtIndex(ValueLayout.OfShort layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (short) ((ValueLayouts.OfShortImpl) layout).accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a short into this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be written.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @param value the short value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfShort layout, long index, short value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        ((ValueLayouts.OfShortImpl) layout).accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads an int from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be read.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @return an int value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default int getAtIndex(ValueLayout.OfInt layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (int) ((ValueLayouts.OfIntImpl) layout).accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes an int into this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be written.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @param value the int value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfInt layout, long index, int value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        ((ValueLayouts.OfIntImpl) layout).accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a float from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be read.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @return a float value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default float getAtIndex(ValueLayout.OfFloat layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (float) ((ValueLayouts.OfFloatImpl) layout).accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a float into this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be written.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @param value the float value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfFloat layout, long index, float value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        ((ValueLayouts.OfFloatImpl) layout).accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a long from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be read.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @return a long value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default long getAtIndex(ValueLayout.OfLong layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (long) ((ValueLayouts.OfLongImpl) layout).accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a long into this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be written.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @param value the long value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfLong layout, long index, long value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        ((ValueLayouts.OfLongImpl) layout).accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads a double from this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be read.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @return a double value read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default double getAtIndex(ValueLayout.OfDouble layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (double) ((ValueLayouts.OfDoubleImpl) layout).accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes a double into this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be written.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @param value the double value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfDouble layout, long index, double value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        ((ValueLayouts.OfDoubleImpl) layout).accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Reads an address from this segment at the given at the given index, scaled by the given layout size. The read address is wrapped in
     * a native segment, associated with the {@linkplain SegmentScope#global() global scope}. Under normal conditions,
     * the size of the returned segment is {@code 0}. However, if the provided layout is an
     * {@linkplain ValueLayout.OfAddress#asUnbounded() unbounded} address layout, then the size of the returned
     * segment is {@code Long.MAX_VALUE}.
     *
     * @param layout the layout of the region of memory to be read.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @return a native segment wrapping an address read from this segment.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     */
    @ForceInline
    default MemorySegment getAtIndex(ValueLayout.OfAddress layout, long index) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        return (MemorySegment) ((ValueLayouts.OfAddressImpl) layout).accessHandle().get(this, index * layout.byteSize());
    }

    /**
     * Writes an address into this segment at the given index, scaled by the given layout size.
     *
     * @param layout the layout of the region of memory to be written.
     * @param index a logical index. The offset in bytes (relative to this segment address) at which the access operation
     *              will occur can be expressed as {@code (index * layout.byteSize())}.
     * @param value the address value to be written.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with this segment is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @throws IllegalArgumentException if the access operation is
     * <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the provided layout,
     * or if the layout alignment is greater than its size.
     * @throws IndexOutOfBoundsException when the access operation falls outside the <em>spatial bounds</em> of the
     * memory segment.
     * @throws UnsupportedOperationException if this segment is {@linkplain #isReadOnly() read-only}.
     * @throws UnsupportedOperationException if {@code value} is not a {@linkplain #isNative() native} segment.
     */
    @ForceInline
    default void setAtIndex(ValueLayout.OfAddress layout, long index, MemorySegment value) {
        Utils.checkElementAlignment(layout, "Layout alignment greater than its size");
        // note: we know size is a small value (as it comes from ValueLayout::byteSize())
        ((ValueLayouts.OfAddressImpl) layout).accessHandle().set(this, index * layout.byteSize(), value);
    }

    /**
     * Compares the specified object with this memory segment for equality. Returns {@code true} if and only if the specified
     * object is also a memory segment, and if the two segments refer to the same location, in some region of memory.
     * More specifically, for two segments {@code s1} and {@code s2} to be considered equals, all the following must be true:
     * <ul>
     *     <li>{@code s1.array().equals(s2.array())}, that is, the two segments must be of the same kind;
     *     either both are {@linkplain #isNative() native segments}, backed by off-heap memory, or both are backed by
     *     the same on-heap Java array;
     *     <li>{@code s1.address() == s2.address()}, that is, the address of the two segments should be the same.
     *     This means that the two segments either refer to the same location in some off-heap region, or they refer
     *     to the same position inside their associated Java array instance.</li>
     * </ul>
     * @apiNote This method does not perform a structural comparison of the contents of the two memory segments. Clients can
     * compare memory segments structurally by using the {@link #mismatch(MemorySegment)} method instead. Note that this
     * method does <em>not</em> compare the temporal and spatial bounds of two segments. As such it is suitable
     * to perform address checks, such as checking if a native segment has the {@code NULL} address.
     *
     * @param that the object to be compared for equality with this memory segment.
     * @return {@code true} if the specified object is equal to this memory segment.
     * @see #mismatch(MemorySegment)
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code srcSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code srcSegment().isAccessibleBy(T) == false}.
     * @throws  IllegalArgumentException if {@code dstArray} is not an array, or if it is an array but whose type is not supported,
     * if the destination array component type does not match the carrier of the source element layout, if the source
     * segment/offset are <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the source element layout,
     * or if the destination element layout alignment is greater than its size.
     */
    @ForceInline
    static void copy(
            MemorySegment srcSegment, ValueLayout srcLayout, long srcOffset,
            Object dstArray, int dstIndex, int elementCount) {
        Objects.requireNonNull(srcSegment);
        Objects.requireNonNull(dstArray);
        Objects.requireNonNull(srcLayout);

        AbstractMemorySegmentImpl.copy(srcSegment, srcLayout, srcOffset,
                dstArray, dstIndex,
                elementCount);
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
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code dstSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code dstSegment().isAccessibleBy(T) == false}.
     * @throws  IllegalArgumentException if {@code srcArray} is not an array, or if it is an array but whose type is not supported,
     * if the source array component type does not match the carrier of the destination element layout, if the destination
     * segment/offset are <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a> in the destination element layout,
     * or if the destination element layout alignment is greater than its size.
     */
    @ForceInline
    static void copy(
            Object srcArray, int srcIndex,
            MemorySegment dstSegment, ValueLayout dstLayout, long dstOffset, int elementCount) {
        Objects.requireNonNull(srcArray);
        Objects.requireNonNull(dstSegment);
        Objects.requireNonNull(dstLayout);

        AbstractMemorySegmentImpl.copy(srcArray, srcIndex,
                dstSegment, dstLayout, dstOffset,
                elementCount);
    }

    /**
     * Finds and returns the relative offset, in bytes, of the first mismatch between the source and the destination
     * segments. More specifically, the bytes at offset {@code srcFromOffset} through {@code srcToOffset - 1} in the
     * source segment are compared against the bytes at offset {@code dstFromOffset} through {@code dstToOffset - 1}
     * in the destination segment.
     * <p>
     * If the two segments, over the specified ranges, share a common prefix then the returned offset is the length
     * of the common prefix, and it follows that there is a mismatch between the two segments at that relative offset
     * within the respective segments. If one segment is a proper prefix of the other, over the specified ranges,
     * then the returned offset is the smallest range, and it follows that the relative offset is only
     * valid for the segment with the larger range. Otherwise, there is no mismatch and {@code -1} is returned.
     *
     * @param srcSegment the source segment.
     * @param srcFromOffset the offset (inclusive) of the first byte in the source segment to be tested.
     * @param srcToOffset the offset (exclusive) of the last byte in the source segment to be tested.
     * @param dstSegment the destination segment.
     * @param dstFromOffset the offset (inclusive) of the first byte in the destination segment to be tested.
     * @param dstToOffset the offset (exclusive) of the last byte in the destination segment to be tested.
     * @return the relative offset, in bytes, of the first mismatch between the source and destination segments,
     * otherwise -1 if no mismatch.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code srcSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code srcSegment.scope().isAccessibleBy(T) == false}.
     * @throws IllegalStateException if the {@linkplain #scope() scope} associated with {@code dstSegment} is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code dstSegment.scope().isAccessibleBy(T) == false}.
     * @throws IndexOutOfBoundsException if {@code srcFromOffset < 0}, {@code srcToOffset < srcFromOffset} or
     * {@code srcToOffset > srcSegment.byteSize()}
     * @throws IndexOutOfBoundsException if {@code dstFromOffset < 0}, {@code dstToOffset < dstFromOffset} or
     * {@code dstToOffset > dstSegment.byteSize()}
     *
     * @see MemorySegment#mismatch(MemorySegment)
     * @see Arrays#mismatch(Object[], int, int, Object[], int, int)
     */
    static long mismatch(MemorySegment srcSegment, long srcFromOffset, long srcToOffset,
                         MemorySegment dstSegment, long dstFromOffset, long dstToOffset) {
        return AbstractMemorySegmentImpl.mismatch(srcSegment, srcFromOffset, srcToOffset,
                dstSegment, dstFromOffset, dstToOffset);
    }

}
