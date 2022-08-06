/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.foreign;

import java.util.Objects;
import java.util.Optional;
import jdk.internal.foreign.Utils;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

abstract non-sealed class AbstractLayout implements MemoryLayout {

    private final long size;
    final long alignment;
    private final Optional<String> name;
    @Stable
    long cachedSize;

    public AbstractLayout(long size, long alignment, Optional<String> name) {
        this.size = size;
        this.alignment = alignment;
        this.name = name;
    }

    @Override
    public AbstractLayout withName(String name) {
        Objects.requireNonNull(name);
        return dup(alignment, Optional.of(name));
    }

    @Override
    public final Optional<String> name() {
        return name;
    }

    abstract AbstractLayout dup(long alignment, Optional<String> name);

    @Override
    public AbstractLayout withBitAlignment(long alignmentBits) {
        checkAlignment(alignmentBits);
        return dup(alignmentBits, name);
    }

    void checkAlignment(long alignmentBitCount) {
        if (((alignmentBitCount & (alignmentBitCount - 1)) != 0L) || //alignment must be a power of two
                (alignmentBitCount < 8)) { //alignment must be greater than 8
            throw new IllegalArgumentException("Invalid alignment: " + alignmentBitCount);
        }
    }

    static void checkSize(long size) {
        checkSize(size, false);
    }

    static void checkSize(long size, boolean includeZero) {
        if (size < 0 || (!includeZero && size == 0)) {
            throw new IllegalArgumentException("Invalid size for layout: " + size);
        }
    }

    @Override
    public final long bitAlignment() {
        return alignment;
    }

    @Override
    @ForceInline
    public long byteSize() {
        if (cachedSize == 0) {
            cachedSize = Utils.bitsToBytesOrThrow(bitSize(),
                    () -> new UnsupportedOperationException("Cannot compute byte size; bit size is not a multiple of 8"));
        }
        return cachedSize;
    }

    @Override
    public long bitSize() {
        return size;
    }

    String decorateLayoutString(String s) {
        if (name().isPresent()) {
            s = String.format("%s(%s)", s, name().get());
        }
        if (!hasNaturalAlignment()) {
            s = alignment + "%" + s;
        }
        return s;
    }

    boolean hasNaturalAlignment() {
        return size == alignment;
    }

    @Override
    public boolean isPadding() {
        return this instanceof PaddingLayout;
    }

    // the following methods have to copy the same Javadoc as in MemoryLayout, or subclasses will just show
    // the Object methods javadoc

    /**
     * {@return the hash code value for this layout}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, size, alignment);
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
     *     <li>two group layouts are considered equal if they are of the same kind (see {@link GroupLayout#isStruct()},
     *     {@link GroupLayout#isUnion()}) and if their member layouts (see {@link GroupLayout#memberLayouts()}) are also equal</li>
     * </ul>
     *
     * @param other the object to be compared for equality with this layout.
     * @return {@code true} if the specified object is equal to this layout.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof AbstractLayout otherLayout &&
                name.equals(otherLayout.name) &&
                size == otherLayout.size &&
                alignment == otherLayout.alignment;
    }

    /**
     * {@return the string representation of this layout}
     */
    public abstract String toString();
}
