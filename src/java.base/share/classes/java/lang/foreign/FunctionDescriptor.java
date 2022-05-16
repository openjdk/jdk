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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.internal.javac.PreviewFeature;

/**
 * A function descriptor is made up of zero or more argument layouts and zero or one return layout. A function descriptor
 * is used to model the signature of foreign functions when creating
 * {@linkplain Linker#downcallHandle(Addressable, FunctionDescriptor) downcall method handles} or
 * {@linkplain Linker#upcallStub(MethodHandle, FunctionDescriptor, MemorySession) upcall stubs}.
 *
 * @implSpec
 * This class is immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @see MemoryLayout
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed class FunctionDescriptor permits FunctionDescriptor.VariadicFunction {

    private final MemoryLayout resLayout;
    private final List<MemoryLayout> argLayouts;

    private FunctionDescriptor(MemoryLayout resLayout, List<MemoryLayout> argLayouts) {
        this.resLayout = resLayout;
        this.argLayouts = argLayouts;
    }

    /**
     * {@return the return layout (if any) associated with this function descriptor}
     */
    public Optional<MemoryLayout> returnLayout() {
        return Optional.ofNullable(resLayout);
    }

    /**
     * {@return the argument layouts associated with this function descriptor (as an immutable list)}.
     */
    public List<MemoryLayout> argumentLayouts() {
        return Collections.unmodifiableList(argLayouts);
    }

    /**
     * Creates a function descriptor with the given return and argument layouts.
     * @param resLayout the return layout.
     * @param argLayouts the argument layouts.
     * @return the new function descriptor.
     */
    public static FunctionDescriptor of(MemoryLayout resLayout, MemoryLayout... argLayouts) {
        Objects.requireNonNull(resLayout);
        Objects.requireNonNull(argLayouts);
        Arrays.stream(argLayouts).forEach(Objects::requireNonNull);
        return new FunctionDescriptor(resLayout, List.of(argLayouts));
    }

    /**
     * Creates a function descriptor with the given argument layouts and no return layout.
     * @param argLayouts the argument layouts.
     * @return the new function descriptor.
     */
    public static FunctionDescriptor ofVoid(MemoryLayout... argLayouts) {
        Objects.requireNonNull(argLayouts);
        Arrays.stream(argLayouts).forEach(Objects::requireNonNull);
        return new FunctionDescriptor(null, List.of(argLayouts));
    }

    /**
     * Creates a specialized variadic function descriptor, by appending given variadic layouts to this
     * function descriptor argument layouts. The resulting function descriptor can report the position
     * of the {@linkplain #firstVariadicArgumentIndex() first variadic argument}, and cannot be altered
     * in any way: for instance, calling {@link #changeReturnLayout(MemoryLayout)} on the resulting descriptor
     * will throw an {@link UnsupportedOperationException}.
     * @param variadicLayouts the variadic argument layouts to be appended to this descriptor argument layouts.
     * @return a variadic function descriptor, or this descriptor if {@code variadicLayouts.length == 0}.
     */
    public FunctionDescriptor asVariadic(MemoryLayout... variadicLayouts) {
        Objects.requireNonNull(variadicLayouts);
        Arrays.stream(variadicLayouts).forEach(Objects::requireNonNull);
        return variadicLayouts.length == 0 ? this : new VariadicFunction(this, variadicLayouts);
    }

    /**
     * The index of the first variadic argument layout (where defined).
     * @return The index of the first variadic argument layout, or {@code -1} if this is not a
     * {@linkplain #asVariadic(MemoryLayout...) variadic} layout.
     */
    public int firstVariadicArgumentIndex() {
        return -1;
    }

    /**
     * Returns a function descriptor with the given argument layouts appended to the argument layout array
     * of this function descriptor.
     * @param addedLayouts the argument layouts to append.
     * @return the new function descriptor.
     */
    public FunctionDescriptor appendArgumentLayouts(MemoryLayout... addedLayouts) {
        return insertArgumentLayouts(argLayouts.size(), addedLayouts);
    }

    /**
     * Returns a function descriptor with the given argument layouts inserted at the given index, into the argument
     * layout array of this function descriptor.
     * @param index the index at which to insert the arguments
     * @param addedLayouts the argument layouts to insert at given index.
     * @return the new function descriptor.
     * @throws IllegalArgumentException if {@code index < 0 || index > argumentLayouts().size()}.
     */
    public FunctionDescriptor insertArgumentLayouts(int index, MemoryLayout... addedLayouts) {
        if (index < 0 || index > argLayouts.size())
            throw new IllegalArgumentException("Index out of bounds: " + index);
        List<MemoryLayout> added = List.of(addedLayouts); // null check on array and its elements
        List<MemoryLayout> newLayouts = new ArrayList<>(argLayouts.size() + addedLayouts.length);
        newLayouts.addAll(argLayouts.subList(0, index));
        newLayouts.addAll(added);
        newLayouts.addAll(argLayouts.subList(index, argLayouts.size()));
        return new FunctionDescriptor(resLayout, newLayouts);
    }

    /**
     * Returns a function descriptor with the given memory layout as the new return layout.
     * @param newReturn the new return layout.
     * @return the new function descriptor.
     */
    public FunctionDescriptor changeReturnLayout(MemoryLayout newReturn) {
        Objects.requireNonNull(newReturn);
        return new FunctionDescriptor(newReturn, argLayouts);
    }

    /**
     * Returns a function descriptor with the return layout dropped. This is useful to model functions
     * which return no values.
     * @return the new function descriptor.
     */
    public FunctionDescriptor dropReturnLayout() {
        return new FunctionDescriptor(null, argLayouts);
    }

    /**
     * {@return the string representation of this function descriptor}
     */
    @Override
    public String toString() {
        return String.format("(%s)%s",
                IntStream.range(0, argLayouts.size())
                        .mapToObj(i -> (i == firstVariadicArgumentIndex() ?
                                "..." : "") + argLayouts.get(i))
                        .collect(Collectors.joining()),
                returnLayout().map(Object::toString).orElse("v"));
    }

    /**
     * Compares the specified object with this function descriptor for equality. Returns {@code true} if and only if the specified
     * object is also a function descriptor, and all the following conditions are met:
     * <ul>
     *     <li>the two function descriptors have equals return layouts (see {@link MemoryLayout#equals(Object)}), or both have no return layout;</li>
     *     <li>the two function descriptors have argument layouts that are pair-wise {@linkplain MemoryLayout#equals(Object) equal}; and</li>
     *     <li>the two function descriptors have the same leading {@linkplain #firstVariadicArgumentIndex() variadic argument index}</li>
     * </ul>
     *
     * @param other the object to be compared for equality with this function descriptor.
     * @return {@code true} if the specified object is equal to this function descriptor.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof FunctionDescriptor f &&
                Objects.equals(resLayout, f.resLayout) &&
                Objects.equals(argLayouts, f.argLayouts) &&
                firstVariadicArgumentIndex() == f.firstVariadicArgumentIndex();
    }

    /**
     * {@return the hash code value for this function descriptor}
     */
    @Override
    public int hashCode() {
        return Objects.hash(argLayouts, resLayout, firstVariadicArgumentIndex());
    }

    static final class VariadicFunction extends FunctionDescriptor {

        private final int firstVariadicIndex;

        public VariadicFunction(FunctionDescriptor descriptor, MemoryLayout... argLayouts) {
            super(descriptor.returnLayout().orElse(null),
                    Stream.concat(descriptor.argumentLayouts().stream(), Stream.of(argLayouts)).toList());
            this.firstVariadicIndex = descriptor.argumentLayouts().size();
        }

        @Override
        public int firstVariadicArgumentIndex() {
            return firstVariadicIndex;
        }

        @Override
        public FunctionDescriptor appendArgumentLayouts(MemoryLayout... addedLayouts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FunctionDescriptor insertArgumentLayouts(int index, MemoryLayout... addedLayouts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FunctionDescriptor changeReturnLayout(MemoryLayout newReturn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FunctionDescriptor dropReturnLayout() {
            throw new UnsupportedOperationException();
        }
    }
}
