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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongBinaryOperator;
import java.util.stream.Collectors;
import jdk.internal.javac.PreviewFeature;

/**
 * A compound layout that aggregates multiple <em>member layouts</em>. There are two ways in which member layouts
 * can be combined: if member layouts are laid out one after the other, the resulting group layout is said to be a <em>struct</em>
 * (see {@link MemoryLayout#structLayout(MemoryLayout...)}); conversely, if all member layouts are laid out at the same starting offset,
 * the resulting group layout is said to be a <em>union</em> (see {@link MemoryLayout#unionLayout(MemoryLayout...)}).
 *
 * @implSpec
 * This class is immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public final class GroupLayout extends AbstractLayout implements MemoryLayout {

    /**
     * The group kind.
     */
    enum Kind {
        /**
         * A 'struct' kind.
         */
        STRUCT("", Math::addExact),
        /**
         * A 'union' kind.
         */
        UNION("|", Math::max);

        final String delimTag;
        final LongBinaryOperator sizeOp;

        Kind(String delimTag, LongBinaryOperator sizeOp) {
            this.delimTag = delimTag;
            this.sizeOp = sizeOp;
        }

        long sizeof(List<MemoryLayout> elems) {
            long size = 0;
            for (MemoryLayout elem : elems) {
                size = sizeOp.applyAsLong(size, elem.bitSize());
            }
            return size;
        }

        long alignof(List<MemoryLayout> elems) {
            return elems.stream().mapToLong(MemoryLayout::bitAlignment).max() // max alignment in case we have member layouts
                    .orElse(1); // or minimal alignment if no member layout is given
        }
    }

    private final Kind kind;
    private final List<MemoryLayout> elements;

    GroupLayout(Kind kind, List<MemoryLayout> elements) {
        this(kind, elements, kind.alignof(elements), Optional.empty());
    }

    GroupLayout(Kind kind, List<MemoryLayout> elements, long alignment, Optional<String> name) {
        super(kind.sizeof(elements), alignment, name);
        this.kind = kind;
        this.elements = elements;
    }

    /**
     * Returns the member layouts associated with this group.
     *
     * @apiNote the order in which member layouts are returned is the same order in which member layouts have
     * been passed to one of the group layout factory methods (see {@link MemoryLayout#structLayout(MemoryLayout...)},
     * {@link MemoryLayout#unionLayout(MemoryLayout...)}).
     *
     * @return the member layouts associated with this group.
     */
    public List<MemoryLayout> memberLayouts() {
        return Collections.unmodifiableList(elements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return decorateLayoutString(elements.stream()
                .map(Object::toString)
                .collect(Collectors.joining(kind.delimTag, "[", "]")));
    }

    /**
     * {@return {@code true}, if this group layout is a struct layout}
     */
    public boolean isStruct() {
        return kind == Kind.STRUCT;
    }

    /**
     * {@return {@code true}, if this group layout is a union layout}
     */
    public boolean isUnion() {
        return kind == Kind.UNION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!super.equals(other)) {
            return false;
        }
        return other instanceof GroupLayout otherGroup &&
                kind == otherGroup.kind &&
                elements.equals(otherGroup.elements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), kind, elements);
    }

    @Override
    GroupLayout dup(long alignment, Optional<String> name) {
        return new GroupLayout(kind, elements, alignment, name);
    }

    @Override
    boolean hasNaturalAlignment() {
        return alignment == kind.alignof(elements);
    }

    //hack: the declarations below are to make javadoc happy; we could have used generics in AbstractLayout
    //but that causes issues with javadoc, see JDK-8224052

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupLayout withName(String name) {
        return (GroupLayout)super.withName(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupLayout withBitAlignment(long alignmentBits) {
        return (GroupLayout)super.withBitAlignment(alignmentBits);
    }
}
