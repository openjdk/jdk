/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign.layout;

import jdk.internal.ValueBased;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Function;

/**
 * A JDK-internal utility class that provides useful layout transformations.
 */
public final class LayoutTransformers {

    private LayoutTransformers() {}

    /**
     * {@return a function that recursively will remove all member names in memory layouts}
     */
    public static Function<MemoryLayout, MemoryLayout> removeNames() {
        return LayoutTransformerImpl.REMOVE_NAMES::deepTransform;
    }

    /**
     * {@return a transformer that recursively will set byte ordering to the provided
     * {@code byteOrder}}
     */
    public static Function<MemoryLayout, MemoryLayout> setByteOrder(ByteOrder byteOrder) {
        return LayoutTransformerImpl.setByteOrder(byteOrder)::deepTransform;
    }

    // Internal classes

    /**
     * A layout transformer that can be used to apply functions on selected types
     * of memory layouts.
     * <p>
     * A layout transformer can be used to convert byte ordering for all sub-members
     * or remove all names, for example.
     *
     * @param <T> the type of MemoryLayout for which transformation is to be made
     */
    sealed interface LayoutTransformer<T extends MemoryLayout>
            permits LayoutTransformerImpl {

        /**
         * {@return a transformed version of the provided {@code layout}}.
         * @param layout to transform
         */
        MemoryLayout transform(T layout);

        /**
         * {@return a transformed version of the provided {@code layout} by recursively
         * applying this transformer (breadth first) on all sub-members}
         * @param layout to transform
         */
        MemoryLayout deepTransform(MemoryLayout layout);

        /**
         * {@return a layout transformer that transforms layouts of the given {@code type}
         * using the provided {@code transformation}}
         * @param type to transform
         * @param transformation to apply
         * @param <T> the type of memory layout to transform
         */
        static <T extends MemoryLayout>
        LayoutTransformer<T> of(Class<T> type,
                                Function<? super T, ? extends MemoryLayout> transformation) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(type);
            return LayoutTransformerImpl.of(type, transformation);
        }

        /**
         * {@return a transformer that will remove all member names in memory layouts}
         */
        static LayoutTransformer<MemoryLayout> removeName() {
            return LayoutTransformerImpl.REMOVE_NAMES;
        }

        /**
         * {@return a transformer that will set member byte ordering to the provided
         * {@code byteOrder}}
         */
        static LayoutTransformer<ValueLayout> setByteOrder(ByteOrder byteOrder) {
            return LayoutTransformerImpl.setByteOrder(byteOrder);
        }

    }

    @ValueBased
    static final class LayoutTransformerImpl<T extends MemoryLayout>
            implements LayoutTransformer<T> {

        private final Class<T> type;
        private final Function<? super T, ? extends MemoryLayout> op;

        private LayoutTransformerImpl(Class<T> type,
                                      Function<? super T, ? extends MemoryLayout> op) {
            this.type = type;
            this.op = op;
        }

        @Override
        public MemoryLayout transform(T layout) {
            Objects.requireNonNull(layout);
            return op.apply(layout);
        }

        @Override
        public MemoryLayout deepTransform(MemoryLayout layout) {
            Objects.requireNonNull(layout);

            // Breadth first
            MemoryLayout outer = transformFlat(this, layout);

            // Handle element transformation
            return switch (outer) {
                case SequenceLayout sl -> MemoryLayout.sequenceLayout(sl.elementCount(), deepTransform(sl.elementLayout()));
                case StructLayout sl   -> MemoryLayout.structLayout(applyRecursively(sl));
                case UnionLayout gl    -> MemoryLayout.unionLayout(applyRecursively(gl));
                default -> outer;
            };
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + op + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, op);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof LayoutTransformerImpl<?> other &&
                    type.equals(other.type) &&
                    op.equals(other.op);
        }

        @SuppressWarnings("unchecked")
        private static <T extends MemoryLayout> MemoryLayout transformFlat(LayoutTransformer<T> transformer, MemoryLayout l) {
            LayoutTransformerImpl<T> t = (LayoutTransformerImpl<T>) transformer;
            return switch (t.type) {
                case Class<?> c when c.equals(MemoryLayout.class) ->
                        ((LayoutTransformerImpl<MemoryLayout>)t).transform(l);
                case Class<?> c when c.equals(SequenceLayout.class) &&
                        l instanceof SequenceLayout sl ->
                        ((LayoutTransformerImpl<SequenceLayout>)t).transform(sl);
                case Class<?> c when c.equals(GroupLayout.class) &&
                        l instanceof GroupLayout gl ->
                        ((LayoutTransformerImpl<GroupLayout>)t).transform(gl);
                case Class<?> c when c.equals(StructLayout.class) &&
                        l instanceof StructLayout se ->
                        ((LayoutTransformerImpl<StructLayout>)t).transform(se);
                case Class<?> c when c.equals(UnionLayout.class) &&
                        l instanceof UnionLayout uel ->
                        ((LayoutTransformerImpl<UnionLayout>)t).transform(uel);
                case Class<?> c when c.equals(PaddingLayout.class) &&
                        l instanceof PaddingLayout pl ->
                        ((LayoutTransformerImpl<PaddingLayout>)t).transform(pl);
                case Class<?> c when c.equals(ValueLayout.class) &&
                        l instanceof ValueLayout vl ->
                        ((LayoutTransformerImpl<ValueLayout>)t).transform(vl);
                case Class<?> c when c.equals(ValueLayout.OfBoolean.class) &&
                        l instanceof ValueLayout.OfBoolean bl ->
                        ((LayoutTransformerImpl<ValueLayout.OfBoolean>)t).transform(bl);
                case Class<?> c when c.equals(ValueLayout.OfByte.class) &&
                        l instanceof ValueLayout.OfByte by ->
                        ((LayoutTransformerImpl<ValueLayout.OfByte>)t).transform(by);
                case Class<?> c when c.equals(ValueLayout.OfChar.class) &&
                        l instanceof ValueLayout.OfChar ch ->
                        ((LayoutTransformerImpl<ValueLayout.OfChar>)t).transform(ch);
                case Class<?> c when c.equals(ValueLayout.OfShort.class) &&
                        l instanceof ValueLayout.OfShort sh ->
                        ((LayoutTransformerImpl<ValueLayout.OfShort>)t).transform(sh);
                case Class<?> c when c.equals(ValueLayout.OfInt.class) &&
                        l instanceof ValueLayout.OfInt in ->
                        ((LayoutTransformerImpl<ValueLayout.OfInt>)t).transform(in);
                case Class<?> c when c.equals(ValueLayout.OfLong.class) &&
                        l instanceof ValueLayout.OfLong lo ->
                        ((LayoutTransformerImpl<ValueLayout.OfLong>)t).transform(lo);
                case Class<?> c when c.equals(ValueLayout.OfFloat.class) &&
                        l instanceof ValueLayout.OfFloat fl ->
                        ((LayoutTransformerImpl<ValueLayout.OfFloat>)t).transform(fl);
                case Class<?> c when c.equals(ValueLayout.OfDouble.class) &&
                        l instanceof ValueLayout.OfDouble db ->
                        ((LayoutTransformerImpl<ValueLayout.OfDouble>)t).transform(db);
                case Class<?> c when c.equals(AddressLayout.class) &&
                        l instanceof AddressLayout ad ->
                        ((LayoutTransformerImpl<AddressLayout>)t).transform(ad);
                // No transformation
                default -> l;
            };
        }

        private MemoryLayout[] applyRecursively(GroupLayout groupLayout) {
            return groupLayout.memberLayouts().stream()
                    .map(this::deepTransform)
                    .toArray(MemoryLayout[]::new);
        }

        static final LayoutTransformer<MemoryLayout> REMOVE_NAMES =
                LayoutTransformer.of(MemoryLayout.class, LayoutTransformerImpl::removeMemberName);

        @SuppressWarnings("restricted")
        private static MemoryLayout removeMemberName(MemoryLayout vl) {
            return switch (vl) {
                case AddressLayout al -> al.targetLayout()
                        .map(tl -> al.withoutName().withTargetLayout(REMOVE_NAMES.deepTransform(tl))) // restricted
                        .orElseGet(al::withoutName);
                default -> vl.withoutName();
            };
        }

        static LayoutTransformer<ValueLayout> setByteOrder(ByteOrder byteOrder) {
            return LayoutTransformer.of(ValueLayout.class, vl -> vl.withOrder(byteOrder));
        }

        static <T extends MemoryLayout> LayoutTransformer<T> of(Class<T> type,
                                                                Function<? super T, ? extends MemoryLayout> op) {
            return new LayoutTransformerImpl<>(type, op);
        }

    }
}
