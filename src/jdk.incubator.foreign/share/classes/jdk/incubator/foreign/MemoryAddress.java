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

import jdk.internal.foreign.MemoryAddressImpl;

/**
 * A memory address models a reference into a memory location. Memory addresses are typically obtained using the
 * {@link MemorySegment#baseAddress()} method; such addresses are said to be <em>checked</em>, and can be expressed
 * as <em>offsets</em> into some underlying memory segment (see {@link #segment()} and {@link #segmentOffset()}).
 * Since checked memory addresses feature both spatial and temporal bounds, these addresses can <em>safely</em> be
 * dereferenced using a memory access var handle (see {@link MemoryHandles}).
 * <p>
 * If an address does not have any associated segment, it is said to be <em>unchecked</em>. Unchecked memory
 * addresses do not feature known spatial or temporal bounds; as such, attempting a memory dereference operation
 * using an unchecked memory address will result in a runtime exception. Unchecked addresses can be obtained
 * e.g. by calling the {@link #ofLong(long)} method.
 * <p>
 * All implementations of this interface must be <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>;
 * use of identity-sensitive operations (including reference equality ({@code ==}), identity hash code, or synchronization) on
 * instances of {@code MemoryAddress} may have unpredictable results and should be avoided. The {@code equals} method should
 * be used for comparisons.
 * <p>
 * Non-platform classes should not implement {@linkplain MemoryAddress} directly.
 *
 * @apiNote In the future, if the Java language permits, {@link MemoryAddress}
 * may become a {@code sealed} interface, which would prohibit subclassing except by
 * explicitly permitted types.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public interface MemoryAddress {
    /**
     * Creates a new memory address with given offset (in bytes), which might be negative, from current one.
     * @param offset specified offset (in bytes), relative to this address, which should be used to create the new address.
     * @return a new memory address with given offset from current one.
     */
    MemoryAddress addOffset(long offset);

    /**
     * Returns the offset of this memory address into the underlying segment (if any).
     * @return the offset of this memory address into the underlying segment (if any).
     * @throws UnsupportedOperationException if no segment is associated with this memory address,
     * e.g. if {@code segment() == null}.
     */
    long segmentOffset();

    /**
     * Returns the raw long value associated to this memory address.
     * @return The raw long value associated to this memory address.
     * @throws UnsupportedOperationException if this memory address is associated with an heap segment.
     */
    long toRawLongValue();

    /**
     * Returns the memory segment (if any) this address belongs to.
     * @return The memory segment this address belongs to, or {@code null} if no such segment exists.
     */
    MemorySegment segment();

    /**
     * Reinterpret this address as an offset into the provided segment.
     * @param segment the segment to be rebased
     * @return a new address pointing to the same memory location through the provided segment
     * @throws IllegalArgumentException if the provided segment is not a valid rebase target for this address. This
     * can happen, for instance, if an heap-based addressed is rebased to an off-heap memory segment.
     */
    MemoryAddress rebase(MemorySegment segment);

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
     * The <em>unchecked</em> memory address instance modelling the {@code NULL} address. This address is <em>not</em> backed by
     * a memory segment and hence it cannot be dereferenced.
     */
    MemoryAddress NULL = new MemoryAddressImpl( 0L);

    /**
     * Obtain a new <em>unchecked</em> memory address instance from given long address. The returned address is <em>not</em> backed by
     * a memory segment and hence it cannot be dereferenced.
     * @param value the long address.
     * @return the new memory address instance.
     */
    static MemoryAddress ofLong(long value) {
        return value == 0 ?
                NULL :
                new MemoryAddressImpl(value);
    }

}
