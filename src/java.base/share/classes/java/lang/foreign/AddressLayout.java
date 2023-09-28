/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.layout.ValueLayouts;
import jdk.internal.javac.Restricted;
import jdk.internal.reflect.CallerSensitive;

import java.lang.foreign.Linker.Option;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * A value layout used to model the address of some region of memory. The carrier associated with an address layout is
 * {@code MemorySegment.class}. The size and alignment of an address layout are platform-dependent
 * (e.g. on a 64-bit platform, the size and alignment of an address layout are set to 8 bytes).
 * <p>
 * An address layout may optionally feature a {@linkplain #targetLayout() target layout}. An address layout with
 * target layout {@code T} can be used to model the address of a region of memory whose layout is {@code T}.
 * For instance, an address layout with target layout {@link ValueLayout#JAVA_INT} can be used to model the address
 * of a region of memory that is 4 bytes long. Specifying a target layout can be useful in the following situations:
 * <ul>
 *     <li>When accessing a memory segment that has been obtained by reading an address from another
 *     memory segment, e.g. using {@link MemorySegment#getAtIndex(AddressLayout, long)};</li>
 *     <li>When creating a downcall method handle, using {@link Linker#downcallHandle(FunctionDescriptor, Option...)};
 *     <li>When creating an upcall stub, using {@link Linker#upcallStub(MethodHandle, FunctionDescriptor, Arena, Option...)}.
 * </ul>
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @see #ADDRESS
 * @see #ADDRESS_UNALIGNED
 * @since 22
 */
public sealed interface AddressLayout extends ValueLayout permits ValueLayouts.OfAddressImpl {

    /**
     * {@inheritDoc}
     */
    @Override
    AddressLayout withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    AddressLayout withoutName();

    /**
     * {@inheritDoc}
     */
    @Override
    AddressLayout withByteAlignment(long byteAlignment);

    /**
     * {@inheritDoc}
     */
    @Override
    AddressLayout withOrder(ByteOrder order);

    /**
     * Returns an address layout with the same carrier, alignment constraint, name and order as this address layout,
     * but associated with the specified target layout. The returned address layout allows raw addresses to be accessed
     * as {@linkplain MemorySegment memory segments} whose size is set to the size of the specified layout. Moreover,
     * if the accessed raw address is not compatible with the alignment constraint in the provided layout,
     * {@linkplain IllegalArgumentException} will be thrown.
     * @apiNote
     * This method can also be used to create an address layout which, when used, creates native memory
     * segments with maximal size (e.g. {@linkplain Long#MAX_VALUE}). This can be done by using a target sequence
     * layout with unspecified size, as follows:
     * {@snippet lang = java:
     * AddressLayout addressLayout   = ...
     * AddressLayout unboundedLayout = addressLayout.withTargetLayout(
     *         MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));
     *}
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption.
     *
     * @param layout the target layout.
     * @return an address layout with same characteristics as this layout, but with the provided target layout.
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     * @see #targetLayout()
     */
    @CallerSensitive
    @Restricted
    AddressLayout withTargetLayout(MemoryLayout layout);

    /**
     * Returns an address layout with the same carrier, alignment constraint, name and order as this address layout,
     * but with no target layout.
     *
     * @apiNote This can be useful to compare two address layouts that have different target layouts, but are otherwise equal.
     *
     * @return an address layout with same characteristics as this layout, but with no target layout.
     * @see #targetLayout()
     */
    AddressLayout withoutTargetLayout();

    /**
     * {@return the target layout associated with this address layout (if any)}.
     */
    Optional<MemoryLayout> targetLayout();

}
