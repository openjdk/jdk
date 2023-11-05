/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

import jdk.internal.foreign.FunctionDescriptorImpl;

/**
 * A function descriptor models the signature of a foreign function. A function descriptor is made up of zero or more
 * argument layouts, and zero or one return layout. A function descriptor is used to create
 * {@linkplain Linker#downcallHandle(MemorySegment, FunctionDescriptor, Linker.Option...) downcall method handles} and
 * {@linkplain Linker#upcallStub(MethodHandle, FunctionDescriptor, Arena, Linker.Option...) upcall stubs}.
 *
 * @implSpec
 * Implementing classes are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @see MemoryLayout
 * @since 22
 */
public sealed interface FunctionDescriptor permits FunctionDescriptorImpl {

    /**
     * {@return the return layout (if any) of this function descriptor}
     */
    Optional<MemoryLayout> returnLayout();

    /**
     * {@return the argument layouts of this function descriptor (as an unmodifiable list)}.
     */
    List<MemoryLayout> argumentLayouts();

    /**
     * Returns a function descriptor with the given argument layouts appended to the argument layouts
     * of this function descriptor.
     * @param addedLayouts the argument layouts to append.
     * @throws IllegalArgumentException if one of the layouts in {@code addedLayouts} is a padding layout
     * @return a new function descriptor, with the provided additional argument layouts.
     */
    FunctionDescriptor appendArgumentLayouts(MemoryLayout... addedLayouts);

    /**
     * Returns a function descriptor with the given argument layouts inserted at the given index, into the argument
     * layout array of this function descriptor.
     * @param index the index at which to insert the arguments
     * @param addedLayouts the argument layouts to insert at given index.
     * @return a new function descriptor, with the provided additional argument layouts.
     * @throws IllegalArgumentException if one of the layouts in {@code addedLayouts} is a padding layout
     * @throws IllegalArgumentException if {@code index < 0 || index > argumentLayouts().size()}
     */
    FunctionDescriptor insertArgumentLayouts(int index, MemoryLayout... addedLayouts);

    /**
     * Returns a function descriptor with the provided return layout.
     * @param newReturn the new return layout.
     * @throws IllegalArgumentException if {@code newReturn} is a padding layout
     * @return a new function descriptor, with the provided return layout.
     */
    FunctionDescriptor changeReturnLayout(MemoryLayout newReturn);

    /**
     * {@return a new function descriptor, with no return layout}
     */
    FunctionDescriptor dropReturnLayout();

    /**
     * Returns the method type consisting of the carrier types of the layouts in this function descriptor.
     * <p>
     * The carrier type of a layout {@code L} is determined as follows:
     * <ul>
     * <li>If {@code L} is a {@link ValueLayout} the carrier type is determined through {@link ValueLayout#carrier()}.</li>
     * <li>If {@code L} is a {@link GroupLayout} or a {@link SequenceLayout}, the carrier type is {@link MemorySegment}.</li>
     * </ul>
     *
     * @apiNote A function descriptor cannot, by construction, contain any padding layouts. As such, it is not
     * necessary to specify how padding layout should be mapped to carrier types.
     *
     * @return the method type consisting of the carrier types of the layouts in this function descriptor.
     */
    MethodType toMethodType();

    /**
     * Creates a function descriptor with the given return and argument layouts.
     * @param resLayout the return layout.
     * @param argLayouts the argument layouts.
     * @throws IllegalArgumentException if {@code resLayout} is a padding layout
     * @throws IllegalArgumentException if one of the layouts in {@code argLayouts} is a padding layout
     * @return a new function descriptor with the provided return and argument layouts.
     */
    static FunctionDescriptor of(MemoryLayout resLayout, MemoryLayout... argLayouts) {
        Objects.requireNonNull(resLayout);
        // Null checks are implicit in List.of(argLayouts)
        return FunctionDescriptorImpl.of(resLayout, List.of(argLayouts));
    }

    /**
     * Creates a function descriptor with the given argument layouts and no return layout.  This is useful to model functions
     * that return no values.
     * @param argLayouts the argument layouts.
     * @throws IllegalArgumentException if one of the layouts in {@code argLayouts} is a padding layout
     * @return a new function descriptor with the provided argument layouts.
     */
    static FunctionDescriptor ofVoid(MemoryLayout... argLayouts) {
        // Null checks are implicit in List.of(argLayouts)
        return FunctionDescriptorImpl.ofVoid(List.of(argLayouts));
    }
}
