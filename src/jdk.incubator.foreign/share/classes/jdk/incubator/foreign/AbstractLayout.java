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

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

abstract class AbstractLayout implements MemoryLayout {
    private final OptionalLong size;
    final long alignment;
    private final Optional<String> name;

    public AbstractLayout(OptionalLong size, long alignment, Optional<String> name) {
        this.size = size;
        this.alignment = alignment;
        this.name = name;
    }

    Optional<String> optName() {
        return name;
    }

    @Override
    public AbstractLayout withName(String name) {
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
    public boolean hasSize() {
        return size.isPresent();
    }

    @Override
    public long bitSize() {
        return size.orElseThrow(this::badSizeException);
    }

    static OptionalLong optSize(MemoryLayout layout) {
        return ((AbstractLayout)layout).size;
    }

    private UnsupportedOperationException badSizeException() {
        return new UnsupportedOperationException("Cannot compute size of a layout which is, or depends on a sequence layout with unspecified size");
    }

    String decorateLayoutString(String s) {
        if (name.isPresent()) {
            s = String.format("%s(%s)", s, name.get());
        }
        if (!hasNaturalAlignment()) {
            s = alignment + "%" + s;
        }
        return s;
    }

    boolean hasNaturalAlignment() {
        return size.isPresent() && size.getAsLong() == alignment;
    }

    @Override
    public int hashCode() {
        return name.hashCode() << Long.hashCode(alignment);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof AbstractLayout)) {
            return false;
        }

        return Objects.equals(name, ((AbstractLayout)other).name) &&
                Objects.equals(alignment, ((AbstractLayout)other).alignment);
    }

    /*** Helper constants for implementing Layout::describeConstable ***/

    static final DirectMethodHandleDesc BSM_GET_STATIC_FINAL
            = ConstantDescs.ofConstantBootstrap(ConstantDescs.CD_ConstantBootstraps, "getStaticFinal",
            ConstantDescs.CD_Object, ConstantDescs.CD_Class);

    static final ClassDesc CD_LAYOUT = MemoryLayout.class.describeConstable().get();

    static final ClassDesc CD_VALUE_LAYOUT = ValueLayout.class.describeConstable().get();

    static final ClassDesc CD_SEQUENCE_LAYOUT = SequenceLayout.class.describeConstable().get();

    static final ClassDesc CD_GROUP_LAYOUT = GroupLayout.class.describeConstable().get();

    static final ClassDesc CD_BYTEORDER = ByteOrder.class.describeConstable().get();

    static final ConstantDesc BIG_ENDIAN = DynamicConstantDesc.ofNamed(BSM_GET_STATIC_FINAL, "BIG_ENDIAN", CD_BYTEORDER, CD_BYTEORDER);

    static final ConstantDesc LITTLE_ENDIAN = DynamicConstantDesc.ofNamed(BSM_GET_STATIC_FINAL, "LITTLE_ENDIAN", CD_BYTEORDER, CD_BYTEORDER);

    static final MethodHandleDesc MH_PADDING = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, CD_LAYOUT, "ofPaddingBits",
                MethodTypeDesc.of(CD_LAYOUT, ConstantDescs.CD_long));

    static final MethodHandleDesc MH_VALUE = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, CD_LAYOUT, "ofValueBits",
                MethodTypeDesc.of(CD_VALUE_LAYOUT, ConstantDescs.CD_long, CD_BYTEORDER));

    static final MethodHandleDesc MH_SIZED_SEQUENCE = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, CD_LAYOUT, "ofSequence",
                MethodTypeDesc.of(CD_SEQUENCE_LAYOUT, ConstantDescs.CD_long, CD_LAYOUT));

    static final MethodHandleDesc MH_UNSIZED_SEQUENCE = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, CD_LAYOUT, "ofSequence",
                MethodTypeDesc.of(CD_SEQUENCE_LAYOUT, CD_LAYOUT));

    static final MethodHandleDesc MH_STRUCT = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, CD_LAYOUT, "ofStruct",
                MethodTypeDesc.of(CD_GROUP_LAYOUT, CD_LAYOUT.arrayType()));

    static final MethodHandleDesc MH_UNION = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, CD_LAYOUT, "ofUnion",
                MethodTypeDesc.of(CD_GROUP_LAYOUT, CD_LAYOUT.arrayType()));
}
