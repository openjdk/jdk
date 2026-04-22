/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.constant;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;

import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;

import static java.lang.constant.ConstantDescs.CD_void;
import static jdk.internal.constant.ConstantUtils.MAX_ARRAY_TYPE_DESC_DIMENSIONS;

/**
 * An array class descriptor.
 * Restrictions: <ul>
 * <li>{@code rank} must be in {@code [1, 255]}
 * <li>{@code element} must not be void or array
 * </ul>
 */
public final class ArrayClassDescImpl implements ClassDesc {
    private final ClassDesc elementType;
    private final int rank;
    private @Stable String cachedDescriptorString;

    public static ArrayClassDescImpl ofValidatedDescriptor(String desc) {
        assert desc.charAt(0) == '[';
        var lastChar = desc.charAt(desc.length() - 1);
        ArrayClassDescImpl ret;
        if (lastChar != ';') {
            // Primitive element arrays
            ret = ofValidated(Wrapper.forBasicType(lastChar).basicClassDescriptor(), desc.length() - 1);
        } else {
            int level = ConstantUtils.arrayDepth(desc, 0);
            ret = ofValidated(ClassOrInterfaceDescImpl.ofValidated(desc.substring(level)), level);
        }
        ret.cachedDescriptorString = desc;
        return ret;
    }

    public static ArrayClassDescImpl ofValidated(ClassDesc elementType, int rank) {
        assert !elementType.isArray() && elementType != CD_void;
        assert rank > 0 && rank <= MAX_ARRAY_TYPE_DESC_DIMENSIONS;

        return new ArrayClassDescImpl(elementType, rank);
    }

    private ArrayClassDescImpl(ClassDesc elementType, int rank) {
        this.elementType = elementType;
        this.rank = rank;
    }

    @Override
    public ClassDesc arrayType() {
        int rank = this.rank + 1;
        if (rank > MAX_ARRAY_TYPE_DESC_DIMENSIONS)
            throw new IllegalStateException(ConstantUtils.invalidArrayRankMessage(rank));
        return new ArrayClassDescImpl(elementType, rank);
    }

    @Override
    public ClassDesc arrayType(int rank) {
        if (rank <= 0) {
            throw new IllegalArgumentException("rank " + rank + " is not a positive value");
        }
        rank += this.rank;
        ConstantUtils.validateArrayRank(rank);
        return new ArrayClassDescImpl(elementType, rank);
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public ClassDesc componentType() {
        return rank == 1 ? elementType : new ArrayClassDescImpl(elementType, rank - 1);
    }

    @Override
    public String displayName() {
        return elementType.displayName() + "[]".repeat(rank);
    }

    @Override
    public String descriptorString() {
        var desc = cachedDescriptorString;
        if (desc != null)
            return desc;

        return cachedDescriptorString = computeDescriptor();
    }

    private String computeDescriptor() {
        return "[".repeat(rank).concat(elementType.descriptorString());
    }

    @Override
    public Class<?> resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
        if (elementType.isPrimitive()) {
            return lookup.findClass(descriptorString());
        }
        // Class.forName is slow on class or interface arrays
        Class<?> clazz = elementType.resolveConstantDesc(lookup);
        for (int i = 0; i < rank; i++)
            clazz = clazz.arrayType();
        return clazz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ArrayClassDescImpl constant) {
            return elementType.equals(constant.elementType) && rank == constant.rank;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return descriptorString().hashCode();
    }

    @Override
    public String toString() {
        return String.format("ArrayClassDesc[%s, %d]", elementType.displayName(), rank);
    }
}
