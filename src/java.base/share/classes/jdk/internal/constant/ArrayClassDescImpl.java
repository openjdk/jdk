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

public final class ArrayClassDescImpl implements ClassDesc {
    private final ClassDesc element;
    private final int rank;
    private @Stable String cachedDescriptorString;

    public static ArrayClassDescImpl ofValidatedDescriptor(String desc) {
        var lastChar = desc.charAt(desc.length() - 1);
        if (lastChar != ';') {
            var ret = ofValidated(Wrapper.forBasicType(lastChar).basicClassDescriptor(), desc.length() - 1);
            ret.cachedDescriptorString = desc;
            return ret;
        }
        int level = ConstantUtils.arrayDepth(desc);
        var ret = ofValidated(ReferenceClassDescImpl.ofValidated(desc.substring(level)), level);
        ret.cachedDescriptorString = desc;
        return ret;
    }

    public static ArrayClassDescImpl ofValidated(ClassDesc element, int rank) {
        assert !element.isArray() && element != CD_void;
        assert rank > 0 && rank <= MAX_ARRAY_TYPE_DESC_DIMENSIONS;

        return new ArrayClassDescImpl(element, rank);
    }

    private ArrayClassDescImpl(ClassDesc element, int rank) {
        this.element = element;
        this.rank = rank;
    }

    @Override
    public ClassDesc arrayType() {
        if (rank == MAX_ARRAY_TYPE_DESC_DIMENSIONS)
            throw new IllegalStateException(
                "Cannot create an array type descriptor with more than "
                        + MAX_ARRAY_TYPE_DESC_DIMENSIONS + " dimensions");
        return new ArrayClassDescImpl(element, rank + 1);
    }

    @Override
    public ClassDesc arrayType(int rank) {
        if (rank <= 0) {
            throw new IllegalArgumentException("rank " + rank + " is not a positive value");
        }
        rank += this.rank;
        ConstantUtils.validateArrayDepth(rank);
        return new ArrayClassDescImpl(element, rank);
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public ClassDesc componentType() {
        return rank == 1 ? element : new ArrayClassDescImpl(element, rank - 1);
    }

    @Override
    public String displayName() {
        return componentType().displayName() + "[]".repeat(rank);
    }

    @Override
    public String descriptorString() {
        var desc = cachedDescriptorString;
        if (desc != null)
            return desc;

        return cachedDescriptorString = computeDescriptor();
    }

    private String computeDescriptor() {
        var componentDesc = element.descriptorString();
        StringBuilder sb = new StringBuilder(rank + componentDesc.length());
        sb.repeat('[', rank);
        sb.append(componentDesc);
        return sb.toString();
    }

    @Override
    public Class<?> resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
        if (element.isPrimitive()) {
            return lookup.findClass(descriptorString());
        }
        // Class.forName is slow on class or interface arrays
        Class<?> clazz = element.resolveConstantDesc(lookup);
        for (int i = 0; i < rank; i++)
            clazz = clazz.arrayType();
        return clazz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ArrayClassDescImpl constant) {
            return element.equals(constant.element) && rank == constant.rank;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return descriptorString().hashCode();
    }

    @Override
    public String toString() {
        return String.format("ArrayClassDesc[%s, %d]", element.displayName(), rank);
    }
}
