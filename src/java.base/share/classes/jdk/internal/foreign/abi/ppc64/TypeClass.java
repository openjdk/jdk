/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
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
package jdk.internal.foreign.abi.ppc64;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.ArrayList;

public enum TypeClass {
    STRUCT_REGISTER,
    STRUCT_HFA, // Homogeneous Float Aggregate
    POINTER,
    INTEGER,
    FLOAT;

    private static final int MAX_RETURN_AGGREGATE_REGS_SIZE = 2;

    private static TypeClass classifyValueType(ValueLayout type) {
        Class<?> carrier = type.carrier();
        if (carrier == boolean.class || carrier == byte.class || carrier == char.class ||
            carrier == short.class || carrier == int.class || carrier == long.class) {
            return INTEGER;
        } else if (carrier == float.class || carrier == double.class) {
            return FLOAT;
        } else if (carrier == MemorySegment.class) {
            return POINTER;
        } else {
            throw new IllegalStateException("Cannot get here: " + carrier.getName());
        }
    }

    static boolean isReturnRegisterAggregate(MemoryLayout type) {
        return type.byteSize() <= MAX_RETURN_AGGREGATE_REGS_SIZE * 8;
    }

    static List<MemoryLayout> scalarLayouts(GroupLayout gl) {
        List<MemoryLayout> out = new ArrayList<>();
        scalarLayoutsInternal(out, gl);
        return out;
    }

    private static void scalarLayoutsInternal(List<MemoryLayout> out, GroupLayout gl) {
        for (MemoryLayout member : gl.memberLayouts()) {
            if (member instanceof GroupLayout memberGl) {
                scalarLayoutsInternal(out, memberGl);
            } else if (member instanceof SequenceLayout memberSl) {
                for (long i = 0; i < memberSl.elementCount(); i++) {
                    out.add(memberSl.elementLayout());
                }
            } else {
                // padding or value layouts
                out.add(member);
            }
        }
    }

    static boolean isHomogeneousFloatAggregate(MemoryLayout type, boolean useABIv2) {
        List<MemoryLayout> scalarLayouts = scalarLayouts((GroupLayout) type);

        final int numElements = scalarLayouts.size();
        if (numElements > (useABIv2 ? 8 : 1) || numElements == 0)
            return false;

        MemoryLayout baseType = scalarLayouts.get(0);

        if (!(baseType instanceof ValueLayout))
            return false;

        TypeClass baseArgClass = classifyValueType((ValueLayout) baseType);
        if (baseArgClass != FLOAT)
           return false;

        for (MemoryLayout elem : scalarLayouts) {
            if (!(elem instanceof ValueLayout))
                return false;

            TypeClass argClass = classifyValueType((ValueLayout) elem);
            if (elem.byteSize() != baseType.byteSize() ||
                    elem.byteAlignment() != baseType.byteAlignment() ||
                    baseArgClass != argClass) {
                return false;
            }
        }

        return true;
    }

    private static TypeClass classifyStructType(MemoryLayout layout, boolean useABIv2, boolean isAIX) {
        if (!isAIX && isHomogeneousFloatAggregate(layout, useABIv2)) {
            return TypeClass.STRUCT_HFA;
        }
        return TypeClass.STRUCT_REGISTER;
    }

    static boolean isStructHFAorReturnRegisterAggregate(MemoryLayout layout, boolean useABIv2) {
        if (!(layout instanceof GroupLayout) || !useABIv2) return false;
        return isHomogeneousFloatAggregate(layout, true) || isReturnRegisterAggregate(layout);
    }

    public static TypeClass classifyLayout(MemoryLayout type, boolean useABIv2, boolean isAIX) {
        if (type instanceof ValueLayout) {
            return classifyValueType((ValueLayout) type);
        } else if (type instanceof GroupLayout) {
            return classifyStructType(type, useABIv2, isAIX);
        } else {
            throw new IllegalArgumentException("Unhandled type " + type);
        }
    }
}
