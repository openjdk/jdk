/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.LayoutPath;
import jdk.internal.foreign.LayoutPath.PathElementImpl.PathKind;
import jdk.internal.foreign.Utils;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public abstract sealed class AbstractLayout<L extends AbstractLayout<L> & MemoryLayout>
        permits AbstractGroupLayout, PaddingLayoutImpl, SequenceLayoutImpl, ValueLayouts.AbstractValueLayout {

    private final long byteSize;
    private final long byteAlignment;
    private final Optional<String> name;

    AbstractLayout(long byteSize, long byteAlignment, Optional<String> name) {
        this.byteSize = MemoryLayoutUtil.requireByteSizeValid(byteSize, true);
        this.byteAlignment = requirePowerOfTwoAndGreaterOrEqualToOne(byteAlignment);
        this.name = Objects.requireNonNull(name);
    }

    public final L withName(String name) {
        return dup(byteAlignment(), Optional.of(name));
    }

    @SuppressWarnings("unchecked")
    public final L withoutName() {
        return name.isPresent() ? dup(byteAlignment(), Optional.empty()) : (L) this;
    }

    public final Optional<String> name() {
        return name;
    }

    public L withByteAlignment(long byteAlignment) {
        return dup(byteAlignment, name);
    }

    public final long byteAlignment() {
        return byteAlignment;
    }

    public final long byteSize() {
        return byteSize;
    }

    public boolean hasNaturalAlignment() {
        return byteSize == byteAlignment;
    }

    // the following methods have to copy the same Javadoc as in MemoryLayout, or subclasses will just show
    // the Object methods javadoc

    /**
     * {@return the hash code value for this layout}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, byteSize, byteAlignment);
    }

    /**
     * Compares the specified object with this layout for equality. Returns {@code true} if and only if the specified
     * object is also a layout, and it is equal to this layout. Two layouts are considered equal if they are of
     * the same kind, have the same size, name and alignment constraints. Furthermore, depending on the layout kind, additional
     * conditions must be satisfied:
     * <ul>
     *     <li>two value layouts are considered equal if they have the same {@linkplain ValueLayout#order() order},
     *     and {@linkplain ValueLayout#carrier() carrier}</li>
     *     <li>two sequence layouts are considered equal if they have the same element count (see {@link SequenceLayout#elementCount()}), and
     *     if their element layouts (see {@link SequenceLayout#elementLayout()}) are also equal</li>
     *     <li>two group layouts are considered equal if they are of the same type (see {@link StructLayout},
     *     {@link UnionLayout}) and if their member layouts (see {@link GroupLayout#memberLayouts()}) are also equal</li>
     * </ul>
     *
     * @param other the object to be compared for equality with this layout.
     * @return {@code true} if the specified object is equal to this layout.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof AbstractLayout<?> otherLayout &&
                name.equals(otherLayout.name) &&
                byteSize == otherLayout.byteSize &&
                byteAlignment == otherLayout.byteAlignment;
    }

    /**
     * {@return the string representation of this layout}
     */
    @Override
    public abstract String toString();

    abstract L dup(long byteAlignment, Optional<String> name);

    String decorateLayoutString(String s) {
        if (name().isPresent()) {
            s = String.format("%s(%s)", s, name().get());
        }
        if (!hasNaturalAlignment()) {
            s = byteAlignment() + "%" + s;
        }
        return s;
    }

    private static long requirePowerOfTwoAndGreaterOrEqualToOne(long value) {
        if (!Utils.isPowerOfTwo(value) || // value must be a power of two
                value < 1) { // value must be greater or equal to 1
            throw new IllegalArgumentException("Invalid alignment: " + value);
        }
        return value;
    }

    public long scale(long offset, long index) {
        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        }
        if (index < 0) {
            throw new IllegalArgumentException("Negative index: " + index);
        }

        return Math.addExact(offset, Math.multiplyExact(byteSize(), index));
    }

    public MethodHandle scaleHandle() {
        class Holder {
            static final MethodHandle MH_SCALE;
            static {
                try {
                    MH_SCALE = MethodHandles.lookup().findVirtual(MemoryLayout.class, "scale",
                            MethodType.methodType(long.class, long.class, long.class));
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }
        }
        return Holder.MH_SCALE.bindTo(this);
    }


    public long byteOffset(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath((MemoryLayout) this), LayoutPath::offset,
                EnumSet.of(PathKind.SEQUENCE_ELEMENT, PathKind.SEQUENCE_RANGE, PathKind.DEREF_ELEMENT), elements);
    }

    public MethodHandle byteOffsetHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath((MemoryLayout) this), LayoutPath::offsetHandle,
                EnumSet.of(PathKind.DEREF_ELEMENT), elements);
    }

    public VarHandle varHandle(PathElement... elements) {
        Objects.requireNonNull(elements);
        if (this instanceof ValueLayout vl && elements.length == 0) {
            return vl.varHandle(); // fast path
        }
        return computePathOp(LayoutPath.rootPath((MemoryLayout) this), LayoutPath::dereferenceHandle,
                Set.of(), elements);
    }

    public VarHandle arrayElementVarHandle(PathElement... elements) {
        return MethodHandles.collectCoordinates(varHandle(elements), 1, scaleHandle());
    }

    public MethodHandle sliceHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath((MemoryLayout) this), LayoutPath::sliceHandle,
                Set.of(PathKind.DEREF_ELEMENT), elements);
    }

    public MemoryLayout select(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath((MemoryLayout) this), LayoutPath::layout,
                EnumSet.of(PathKind.SEQUENCE_ELEMENT_INDEX, PathKind.SEQUENCE_RANGE, PathKind.DEREF_ELEMENT), elements);
    }

    private static <Z> Z computePathOp(LayoutPath path, Function<LayoutPath, Z> finalizer,
                                       Set<PathKind> badKinds, PathElement... elements) {
        Objects.requireNonNull(elements);
        for (PathElement e : elements) {
            LayoutPath.PathElementImpl pathElem = (LayoutPath.PathElementImpl)Objects.requireNonNull(e);
            if (badKinds.contains(pathElem.kind())) {
                throw new IllegalArgumentException(String.format("Invalid %s selection in layout path", pathElem.kind().description()));
            }
            path = pathElem.apply(path);
        }
        return finalizer.apply(path);
    }
}
