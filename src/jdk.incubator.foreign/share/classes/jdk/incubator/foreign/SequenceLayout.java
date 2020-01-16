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

import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A sequence layout. A sequence layout is used to denote a repetition of a given layout, also called the sequence layout's <em>element layout</em>.
 * The repetition count, where it exists (e.g. for <em>finite</em> sequence layouts) is said to be the the sequence layout's <em>element count</em>.
 * A finite sequence layout can be thought of as a group layout where the sequence layout's element layout is repeated a number of times
 * that is equal to the sequence layout's element count. In other words this layout:
 *
 * <pre>{@code
MemoryLayout.ofSequence(3, MemoryLayout.ofValueBits(32, ByteOrder.BIG_ENDIAN));
 * }</pre>
 *
 * is equivalent to the following layout:
 *
 * <pre>{@code
MemoryLayout.ofStruct(
    MemoryLayout.ofValueBits(32, ByteOrder.BIG_ENDIAN),
    MemoryLayout.ofValueBits(32, ByteOrder.BIG_ENDIAN),
    MemoryLayout.ofValueBits(32, ByteOrder.BIG_ENDIAN));
 * }</pre>
 *
 * <p>
 * This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; use of identity-sensitive operations (including reference equality
 * ({@code ==}), identity hash code, or synchronization) on instances of
 * {@code SequenceLayout} may have unpredictable results and should be avoided.
 * The {@code equals} method should be used for comparisons.
 *
 * @implSpec
 * This class is immutable and thread-safe.
 */
public final class SequenceLayout extends AbstractLayout {

    private final OptionalLong elemCount;
    private final MemoryLayout elementLayout;

    SequenceLayout(OptionalLong elemCount, MemoryLayout elementLayout) {
        this(elemCount, elementLayout, elementLayout.bitAlignment(), Optional.empty());
    }

    SequenceLayout(OptionalLong elemCount, MemoryLayout elementLayout, long alignment, Optional<String> name) {
        super(elemCount.isPresent() && AbstractLayout.optSize(elementLayout).isPresent() ?
                OptionalLong.of(elemCount.getAsLong() * elementLayout.bitSize()) :
                OptionalLong.empty(), alignment, name);
        this.elemCount = elemCount;
        this.elementLayout = elementLayout;
    }

    /**
     * Returns the element layout associated with this sequence layout.
     *
     * @return The element layout associated with this sequence layout.
     */
    public MemoryLayout elementLayout() {
        return elementLayout;
    }

    /**
     * Returns the element count of this sequence layout (if any).
     *
     * @return the element count of this sequence layout (if any).
     */
    public OptionalLong elementCount() {
        return elemCount;
    }

    /**
     * Obtains a new sequence layout with same element layout, alignment constraints and name as this sequence layout
     * but with the new specified element count.
     * @param elementCount the new element count.
     * @return a new sequence with given element count.
     * @throws IllegalArgumentException if {@code elementCount < 0}.
     */
    public SequenceLayout withElementCount(long elementCount) {
        AbstractLayout.checkSize(elementCount, true);
        return new SequenceLayout(OptionalLong.of(elementCount), elementLayout, alignment, name());
    }

    @Override
    public String toString() {
        return decorateLayoutString(String.format("[%s:%s]",
                elemCount.isPresent() ? elemCount.getAsLong() : "", elementLayout));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof SequenceLayout)) {
            return false;
        }
        SequenceLayout s = (SequenceLayout)other;
        return elemCount.equals(s.elemCount) && elementLayout.equals(s.elementLayout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), elemCount, elementLayout);
    }

    @Override
    SequenceLayout dup(long alignment, Optional<String> name) {
        return new SequenceLayout(elementCount(), elementLayout, alignment, name);
    }

    @Override
    boolean hasNaturalAlignment() {
        return alignment == elementLayout.bitAlignment();
    }

    @Override
    public Optional<DynamicConstantDesc<SequenceLayout>> describeConstable() {
        return elemCount.isPresent() ?
                Optional.of(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_INVOKE, "value",
                        CD_SEQUENCE_LAYOUT, MH_SIZED_SEQUENCE, elemCount.getAsLong(), elementLayout.describeConstable().get())) :
                Optional.of(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_INVOKE, "value",
                        CD_SEQUENCE_LAYOUT, MH_UNSIZED_SEQUENCE, elementLayout.describeConstable().get()));
    }

    //hack: the declarations below are to make javadoc happy; we could have used generics in AbstractLayout
    //but that causes issues with javadoc, see JDK-8224052

    /**
     * {@inheritDoc}
     */
    @Override
    public SequenceLayout withName(String name) {
        return (SequenceLayout)super.withName(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SequenceLayout withBitAlignment(long alignmentBits) {
        return (SequenceLayout)super.withBitAlignment(alignmentBits);
    }
}
