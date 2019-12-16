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
 * A memory address encodes an offset within a given {@link MemorySegment}. Memory addresses are typically obtained
 * using the {@link MemorySegment#baseAddress()} method; such addresses can then be adjusted as required,
 * using {@link MemoryAddress#offset(long)}.
 * <p>
 * A memory address is typically used as the first argument in a memory access var handle call, to perform some operation
 * on the underlying memory backing a given memory segment. Since a memory address is always associated with a memory segment,
 * such access operations are always subject to spatial and temporal checks as enforced by the address' owning memory region.
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
 * Implementations of this interface are immutable and thread-safe.
 */
public interface MemoryAddress {
    /**
     * Creates a new memory address with given offset (in bytes) from current one.
     * @param l specified offset (in bytes), relative to this address, which should be used to create the new address.
     * @return a new memory address with given offset from current one.
     */
    MemoryAddress offset(long l);

    /**
     * The offset of this memory address into the underlying segment.
     *
     * @return the offset
     */
    long offset();

    /**
     * The memory segment this address belongs to.
     * @return The memory segment this address belongs to.
     */
    MemorySegment segment();

    /**
     * Compares the specified object with this address for equality. Returns {@code true} if and only if the specified
     * object is also a address, and it is equal to this address.
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
     * Perform bulk copy from source address to target address. More specifically, the bytes at addresses {@code src}
     * through {@code src.offset(bytes - 1)} are copied into addresses {@code dst} through {@code dst.offset(bytes - 1)}.
     * If the source and address ranges overlap, then the copying is performed as if the bytes at addresses {@code src}
     * through {@code src.offset(bytes - 1)} were first copied into a temporary segment with size {@code bytes},
     * and then the contents of the temporary segment were copied into the bytes at addresses {@code dst} through {@code dst.offset(bytes - 1)}.
     * @param src the source address.
     * @param dst the target address.
     * @param bytes the number of bytes to be copied.
     * @throws IndexOutOfBoundsException if {@code bytes < 0}, or if it is greater than the size of the segments
     * associated with either {@code src} or {@code dst}.
     * @throws IllegalStateException if either the source address or the target address belong to memory segments
     * which have been already closed, or if access occurs from a thread other than the thread owning either segment.
     * @throws UnsupportedOperationException if {@code dst} is associated with a read-only segment (see {@link MemorySegment#isReadOnly()}).
     */
    static void copy(MemoryAddress src, MemoryAddress dst, long bytes) {
        MemoryAddressImpl.copy((MemoryAddressImpl)src, (MemoryAddressImpl)dst, bytes);
    }

}
