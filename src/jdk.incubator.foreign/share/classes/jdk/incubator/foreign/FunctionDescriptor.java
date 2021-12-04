/*
 *  Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.foreign;

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A function descriptor is made up of zero or more argument layouts and zero or one return layout. A function descriptor
 * is used to model the signature of foreign functions.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 */
public sealed class FunctionDescriptor implements Constable permits FunctionDescriptor.VariadicFunction {

    private final MemoryLayout resLayout;
    private final List<MemoryLayout> argLayouts;

    private FunctionDescriptor(MemoryLayout resLayout, List<MemoryLayout> argLayouts) {
        this.resLayout = resLayout;
        this.argLayouts = argLayouts;
    }

    /**
     * Returns the return layout associated with this function.
     * @return the return layout.
     */
    public Optional<MemoryLayout> returnLayout() {
        return Optional.ofNullable(resLayout);
    }

    /**
     * Returns the argument layouts associated with this function.
     * @return the argument layouts.
     */
    public List<MemoryLayout> argumentLayouts() {
        return argLayouts;
    }

    /**
     * Create a function descriptor with given return and argument layouts.
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
     * Create a function descriptor with given argument layouts and no return layout.
     * @param argLayouts the argument layouts.
     * @return the new function descriptor.
     */
    public static FunctionDescriptor ofVoid(MemoryLayout... argLayouts) {
        Objects.requireNonNull(argLayouts);
        Arrays.stream(argLayouts).forEach(Objects::requireNonNull);
        return new FunctionDescriptor(null, List.of(argLayouts));
    }

    /**
     * Obtain a specialized variadic function descriptor, by appending given variadic layouts to this
     * function descriptor argument layouts. The resulting function descriptor can report the position
     * of the {@linkplain #firstVariadicArgumentIndex() first variadic argument}, and cannot be altered
     * in any way: for instance, calling {@link #changeReturnLayout(MemoryLayout)} on the resulting descriptor
     * will throw an {@link UnsupportedOperationException}.
     * @param variadicLayouts the variadic argument layouts to be appended to this descriptor argument layouts.
     * @return a new variadic function descriptor, or this descriptor if {@code variadicLayouts.length == 0}.
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
     * Create a new function descriptor with the given argument layouts appended to the argument layout array
     * of this function descriptor.
     * @param addedLayouts the argument layouts to append.
     * @return the new function descriptor.
     */
    public FunctionDescriptor appendArgumentLayouts(MemoryLayout... addedLayouts) {
        return insertArgumentLayouts(argLayouts.size(), addedLayouts);
    }

    /**
     * Create a new function descriptor with the given argument layouts inserted at the given index, into the argument
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
     * Create a new function descriptor with the given memory layout as the new return layout.
     * @param newReturn the new return layout.
     * @return the new function descriptor.
     */
    public FunctionDescriptor changeReturnLayout(MemoryLayout newReturn) {
        Objects.requireNonNull(newReturn);
        return new FunctionDescriptor(newReturn, argLayouts);
    }

    /**
     * Create a new function descriptor with the return layout dropped. This is useful to model functions
     * which return no values.
     * @return the new function descriptor.
     */
    public FunctionDescriptor dropReturnLayout() {
        return new FunctionDescriptor(null, argLayouts);
    }

    /**
     * Returns a string representation of this function descriptor.
     * @return a string representation of this function descriptor.
     */
    @Override
    public String toString() {
        return String.format("(%s)%s",
                Stream.of(argLayouts)
                        .map(Object::toString)
                        .collect(Collectors.joining()),
                returnLayout().map(Object::toString).orElse("v"));
    }

    /**
     * Compares the specified object with this function descriptor for equality. Returns {@code true} if and only if the specified
     * object is also a function descriptor, and all the following conditions are met:
     * <ul>
     *     <li>the two function descriptors have equals return layouts (see {@link MemoryLayout#equals(Object)}), or both have no return layout</li>
     *     <li>the two function descriptors have argument layouts that are pair-wise equal (see {@link MemoryLayout#equals(Object)})
     * </ul>
     *
     * @param other the object to be compared for equality with this function descriptor.
     * @return {@code true} if the specified object is equal to this function descriptor.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FunctionDescriptor f)) {
            return false;
        }
        return Objects.equals(resLayout, f.resLayout) && Objects.equals(argLayouts, f.argLayouts);
    }

    /**
     * Returns the hash code value for this function descriptor.
     * @return the hash code value for this function descriptor.
     */
    @Override
    public int hashCode() {
        int hashCode = Objects.hashCode(argLayouts);
        return resLayout == null ? hashCode : resLayout.hashCode() ^ hashCode;
    }

    /**
     * Returns an {@link Optional} containing the nominal descriptor for this
     * function descriptor, if one can be constructed, or an empty {@link Optional}
     * if one cannot be constructed.
     *
     * @return An {@link Optional} containing the resulting nominal descriptor,
     * or an empty {@link Optional} if one cannot be constructed.
     */
    @Override
    public Optional<DynamicConstantDesc<FunctionDescriptor>> describeConstable() {
        List<ConstantDesc> constants = new ArrayList<>();
        constants.add(resLayout == null ? AbstractLayout.MH_VOID_FUNCTION : AbstractLayout.MH_FUNCTION);
        if (resLayout != null) {
            constants.add(resLayout.describeConstable().get());
        }
        for (MemoryLayout argLayout : argLayouts) {
            constants.add(argLayout.describeConstable().get());
        }
        return Optional.of(DynamicConstantDesc.ofNamed(
                ConstantDescs.BSM_INVOKE, "function", AbstractLayout.CD_FUNCTION_DESC, constants.toArray(new ConstantDesc[0])));
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

        @Override
        public Optional<DynamicConstantDesc<FunctionDescriptor>> describeConstable() {
            return Optional.empty();
        }
    }
}
