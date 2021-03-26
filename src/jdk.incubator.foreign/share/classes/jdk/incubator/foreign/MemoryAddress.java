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

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemoryAddressImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.Utils;

import java.lang.ref.Cleaner;

/**
 * A memory address models a reference into a memory location. Memory addresses are typically obtained using the
 * {@link MemorySegment#address()} method, and can refer to either off-heap or on-heap memory.
 * Given an address, it is possible to compute its offset relative to a given segment, which can be useful
 * when performing memory dereference operations using a memory access var handle (see {@link MemoryHandles}).
 * <p>
 * All implementations of this interface must be <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>;
 * programmers should treat instances that are {@linkplain #equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may occur. For example, in a future release,
 * synchronization may fail. The {@code equals} method should be used for comparisons.
 * <p>
 * Non-platform classes should not implement {@linkplain MemoryAddress} directly.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * @apiNote In the future, if the Java language permits, {@link MemoryAddress}
 * may become a {@code sealed} interface, which would prohibit subclassing except by
 * explicitly permitted types.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public interface MemoryAddress extends Addressable {

    @Override
    default MemoryAddress address() {
        return this;
    }

    /**
     * Creates a new memory address with given offset (in bytes), which might be negative, from current one.
     * @param offset specified offset (in bytes), relative to this address, which should be used to create the new address.
     * @return a new memory address with given offset from current one.
     */
    MemoryAddress addOffset(long offset);

    /**
     * Returns the offset of this memory address into the given segment. More specifically, if both the segment's
     * base address and this address are off-heap addresses, the result is computed as
     * {@code this.toRawLongValue() - segment.address().toRawLongValue()}. Otherwise, if both addresses in the form
     * {@code (B, O1)}, {@code (B, O2)}, where {@code B} is the same base heap object and {@code O1}, {@code O2}
     * are byte offsets (relative to the base object) associated with this address and the segment's base address,
     * the result is computed as {@code O1 - O2}.
     * <p>
     * If the segment's base address and this address are both heap addresses, but with different base objects, the result is undefined
     * and an exception is thrown. Similarly, if the segment's base address is an heap address (resp. off-heap) and
     * this address is an off-heap (resp. heap) address, the result is undefined and an exception is thrown.
     * Otherwise, the result is a byte offset {@code SO}. If this address falls within the
     * spatial bounds of the given segment, then {@code 0 <= SO < segment.byteSize()}; otherwise, {@code SO < 0 || SO > segment.byteSize()}.
     * @return the offset of this memory address into the given segment.
     * @param segment the segment relative to which this address offset should be computed
     * @throws IllegalArgumentException if {@code segment} is not compatible with this address; this can happen, for instance,
     * when {@code segment} models an heap memory region, while this address models an off-heap memory address.
     */
    long segmentOffset(MemorySegment segment);

    /**
     * Returns a new confined native memory segment with given size, and whose base address is this address; the returned segment has its own temporal
     * bounds, and can therefore be closed. This method can be useful when interacting with custom native memory sources (e.g. custom allocators),
     * where an address to some underlying memory region is typically obtained from native code (often as a plain {@code long} value).
     * <p>
     * The returned segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link MemorySegment#ALL_ACCESS}), and its confinement thread is the current thread (see {@link Thread#currentThread()}).
     * <p>
     * Clients should ensure that the address and bounds refers to a valid region of memory that is accessible for reading and,
     * if appropriate, writing; an attempt to access an invalid memory location from Java code will either return an arbitrary value,
     * have no visible effect, or cause an unspecified exception to be thrown.
     * <p>
     * Calling {@link MemorySegment#close()} on the returned segment will <em>not</em> result in releasing any
     * memory resources which might implicitly be associated with the segment. This method is equivalent to the following code:
     * <pre>{@code
    asSegmentRestricted(byteSize, null, null);
     * }</pre>
     * This method is <em>restricted</em>. Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param bytesSize the desired size.
     * @return a new confined native memory segment with given base address and size.
     * @throws IllegalArgumentException if {@code bytesSize <= 0}.
     * @throws UnsupportedOperationException if this address is an heap address.
     * @throws IllegalAccessError if the runtime property {@code foreign.restricted} is not set to either
     * {@code permit}, {@code warn} or {@code debug} (the default value is set to {@code deny}).
     */
    default MemorySegment asSegmentRestricted(long bytesSize) {
        return asSegmentRestricted(bytesSize, null, null);
    }

    /**
     * Returns a new confined native memory segment with given size, and whose base address is this address; the returned segment has its own temporal
     * bounds, and can therefore be closed. This method can be useful when interacting with custom native memory sources (e.g. custom allocators),
     * where an address to some underlying memory region is typically obtained from native code (often as a plain {@code long} value).
     * <p>
     * The returned segment will feature all <a href="#access-modes">access modes</a>
     * (see {@link MemorySegment#ALL_ACCESS}), and its confinement thread is the current thread (see {@link Thread#currentThread()}).
     * Moreover, the returned segment will keep a strong reference to the supplied attachment object (if any), which can
     * be useful in cases where the lifecycle of the segment is dependent on that of some other external resource.
     * <p>
     * Clients should ensure that the address and bounds refers to a valid region of memory that is accessible for reading and,
     * if appropriate, writing; an attempt to access an invalid memory location from Java code will either return an arbitrary value,
     * have no visible effect, or cause an unspecified exception to be thrown.
     * <p>
     * Calling {@link MemorySegment#close()} on the returned segment will <em>not</em> result in releasing any
     * memory resources which might implicitly be associated with the segment, but will result in calling the
     * provided cleanup action (if any).
     * <p>
     * Both the cleanup action and the attachment object (if any) will be preserved under terminal operations such as
     * {@link MemorySegment#handoff(Thread)}, {@link MemorySegment#share()} and {@link MemorySegment#registerCleaner(Cleaner)}.
     * <p>
     * This method is <em>restricted</em>. Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param bytesSize the desired size.
     * @param cleanupAction the cleanup action; can be {@code null}.
     * @param attachment an attachment object that will be kept strongly reachable by the returned segment; can be {@code null}.
     * @return a new confined native memory segment with given base address and size.
     * @throws IllegalArgumentException if {@code bytesSize <= 0}.
     * @throws UnsupportedOperationException if this address is an heap address.
     * @throws IllegalAccessError if the runtime property {@code foreign.restricted} is not set to either
     * {@code permit}, {@code warn} or {@code debug} (the default value is set to {@code deny}).
     */
    MemorySegment asSegmentRestricted(long bytesSize, Runnable cleanupAction, Object attachment);

    /**
     * Returns the raw long value associated with this memory address.
     * @return The raw long value associated with this memory address.
     * @throws UnsupportedOperationException if this memory address is an heap address.
     */
    long toRawLongValue();

    /**
     * Compares the specified object with this address for equality. Returns {@code true} if and only if the specified
     * object is also an address, and it refers to the same memory location as this address.
     *
     * @apiNote two addresses might be considered equal despite their associated segments differ. This
     * can happen, for instance, if the segment associated with one address is a <em>slice</em>
     * (see {@link MemorySegment#asSlice(long, long)}) of the segment associated with the other address. Moreover,
     * two addresses might be considered equals despite differences in the temporal bounds associated with their
     * corresponding segments.
     *
     * @param that the object to be compared for equality with this address.
     * @return {@code true} if the specified object is equal to this address.
     */
    @Override
    boolean equals(Object that);

    /**
     * Returns the hash code value for this address.
     * @return the hash code value for this address.
     */
    @Override
    int hashCode();

    /**
     * The off-heap memory address instance modelling the {@code NULL} address.
     */
    MemoryAddress NULL = new MemoryAddressImpl(null,  0L);

    /**
     * Obtain an off-heap memory address instance from given long address.
     * @param value the long address.
     * @return the new memory address instance.
     */
    static MemoryAddress ofLong(long value) {
        return value == 0 ?
                NULL :
                new MemoryAddressImpl(null, value);
    }
}
