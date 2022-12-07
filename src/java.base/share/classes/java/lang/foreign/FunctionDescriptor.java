/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */
package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

import jdk.internal.foreign.FunctionDescriptorImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A function descriptor models the signature of foreign functions. A function descriptor is made up of zero or more
 * argument layouts and zero or one return layout. A function descriptor is typically used when creating
 * {@linkplain Linker#downcallHandle(MemorySegment, FunctionDescriptor, Linker.Option...) downcall method handles} or
 * {@linkplain Linker#upcallStub(MethodHandle, FunctionDescriptor, SegmentScope) upcall stubs}.
 *
 * @implSpec
 * Implementing classes are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @see MemoryLayout
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface FunctionDescriptor permits FunctionDescriptorImpl {

    /**
     * {@return the return layout (if any) associated with this function descriptor}
     */
    Optional<MemoryLayout> returnLayout();

    /**
     * {@return the argument layouts associated with this function descriptor (as an immutable list)}.
     */
    List<MemoryLayout> argumentLayouts();

    /**
     * Returns a function descriptor with the given argument layouts appended to the argument layout array
     * of this function descriptor.
     * @param addedLayouts the argument layouts to append.
     * @return the new function descriptor.
     */
    FunctionDescriptor appendArgumentLayouts(MemoryLayout... addedLayouts);

    /**
     * Returns a function descriptor with the given argument layouts inserted at the given index, into the argument
     * layout array of this function descriptor.
     * @param index the index at which to insert the arguments
     * @param addedLayouts the argument layouts to insert at given index.
     * @return the new function descriptor.
     * @throws IllegalArgumentException if {@code index < 0 || index > argumentLayouts().size()}.
     */
    FunctionDescriptor insertArgumentLayouts(int index, MemoryLayout... addedLayouts);

    /**
     * Returns a function descriptor with the given memory layout as the new return layout.
     * @param newReturn the new return layout.
     * @return the new function descriptor.
     */
    FunctionDescriptor changeReturnLayout(MemoryLayout newReturn);

    /**
     * Returns a function descriptor with the return layout dropped. This is useful to model functions
     * which return no values.
     * @return the new function descriptor.
     */
    FunctionDescriptor dropReturnLayout();

    /**
     * Returns the method type consisting of the carrier types of the layouts in this function descriptor.
     * <p>
     * The carrier type of a layout is determined as follows:
     * <ul>
     * <li>If the layout is a {@link ValueLayout} the carrier type is determined through {@link ValueLayout#carrier()}.</li>
     * <li>If the layout is a {@link GroupLayout} the carrier type is {@link MemorySegment}.</li>
     * <li>If the layout is a {@link PaddingLayout}, or {@link SequenceLayout} an {@link IllegalArgumentException} is thrown.</li>
     * </ul>
     *
     * @return the method type consisting of the carrier types of the layouts in this function descriptor
     * @throws IllegalArgumentException if one or more layouts in the function descriptor can not be mapped to carrier
     *                                  types (e.g. if they are sequence layouts or padding layouts).
     */
    MethodType toMethodType();

    /**
     * Creates a function descriptor with the given return and argument layouts.
     * @param resLayout the return layout.
     * @param argLayouts the argument layouts.
     * @return the new function descriptor.
     */
    static FunctionDescriptor of(MemoryLayout resLayout, MemoryLayout... argLayouts) {
        Objects.requireNonNull(resLayout);
        // Null checks are implicit in List.of(argLayouts)
        return FunctionDescriptorImpl.of(resLayout, List.of(argLayouts));
    }

    /**
     * Creates a function descriptor with the given argument layouts and no return layout.
     * @param argLayouts the argument layouts.
     * @return the new function descriptor.
     */
    static FunctionDescriptor ofVoid(MemoryLayout... argLayouts) {
        // Null checks are implicit in List.of(argLayouts)
        return FunctionDescriptorImpl.ofVoid(List.of(argLayouts));
    }
}
