/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi.fallback;

import jdk.internal.foreign.Utils;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * typedef struct _ffi_type
 * {
 *   size_t size;
 *   unsigned short alignment;
 *   unsigned short type;
 *   struct _ffi_type **elements;
 * } ffi_type;
 */
class FFIType {
    private static final ValueLayout SIZE_T = switch ((int) ADDRESS.bitSize()) {
            case 64 -> JAVA_LONG;
            case 32 -> JAVA_INT;
            default -> throw new IllegalStateException("Address size not supported: " + ADDRESS.byteSize());
        };
    private static final ValueLayout UNSIGNED_SHORT = JAVA_SHORT;
    private static final StructLayout LAYOUT = Utils.computePaddedStructLayout(
            SIZE_T, UNSIGNED_SHORT, UNSIGNED_SHORT.withName("type"), ADDRESS.withName("elements"));

    private static final VarHandle VH_TYPE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("type"));
    private static final VarHandle VH_ELEMENTS = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("elements"));
    private static final VarHandle VH_SIZE_T_ARRAY = SIZE_T.arrayElementVarHandle();

    private static MemorySegment make(List<MemoryLayout> elements, FFIABI abi, Arena scope) {
        MemorySegment elementsSeg = scope.allocate((elements.size() + 1) * ADDRESS.byteSize());
        int i = 0;
        for (; i < elements.size(); i++) {
            MemoryLayout elementLayout = elements.get(i);
            MemorySegment elementType = toFFIType(elementLayout, abi, scope);
            elementsSeg.setAtIndex(ADDRESS, i, elementType);
        }
        // elements array is null-terminated
        elementsSeg.setAtIndex(ADDRESS, i, MemorySegment.NULL);

        MemorySegment ffiType = scope.allocate(LAYOUT);
        VH_TYPE.set(ffiType, LibFallback.STRUCT_TAG);
        VH_ELEMENTS.set(ffiType, elementsSeg);

        return ffiType;
    }

    private static final Map<Class<?>, MemorySegment> CARRIER_TO_TYPE = Map.of(
        boolean.class, LibFallback.UINT8_TYPE,
        byte.class, LibFallback.SINT8_TYPE,
        short.class, LibFallback.SINT16_TYPE,
        char.class, LibFallback.UINT16_TYPE,
        int.class, LibFallback.SINT32_TYPE,
        long.class, LibFallback.SINT64_TYPE,
        float.class, LibFallback.FLOAT_TYPE,
        double.class, LibFallback.DOUBLE_TYPE,
        MemorySegment.class, LibFallback.POINTER_TYPE
    );

    static MemorySegment toFFIType(MemoryLayout layout, FFIABI abi, Arena scope) {
        if (layout instanceof GroupLayout grpl) {
            if (grpl instanceof StructLayout strl) {
                // libffi doesn't want our padding
                List<MemoryLayout> filteredLayouts = strl.memberLayouts().stream()
                        .filter(Predicate.not(PaddingLayout.class::isInstance))
                        .toList();
                MemorySegment structType = make(filteredLayouts, abi, scope);
                verifyStructType(strl, filteredLayouts, structType, abi);
                return structType;
            }
            assert grpl instanceof UnionLayout;
            throw new IllegalArgumentException("Fallback linker does not support by-value unions: " + grpl);
        } else if (layout instanceof SequenceLayout sl) {
            List<MemoryLayout> elements = Collections.nCopies(Math.toIntExact(sl.elementCount()), sl.elementLayout());
            return make(elements, abi, scope);
        }
        return Objects.requireNonNull(CARRIER_TO_TYPE.get(((ValueLayout) layout).carrier()));
    }

    // verify layout against what libffi sets
    private static void verifyStructType(StructLayout structLayout, List<MemoryLayout> filteredLayouts, MemorySegment structType,
                                         FFIABI abi) {
        try (Arena verifyArena = Arena.ofConfined()) {
            MemorySegment offsetsOut = verifyArena.allocate(SIZE_T.byteSize() * filteredLayouts.size());
            LibFallback.getStructOffsets(structType, offsetsOut, abi);
            long expectedOffset = 0;
            int offsetIdx = 0;
            for (MemoryLayout element : structLayout.memberLayouts()) {
                if (!(element instanceof PaddingLayout)) {
                    long ffiOffset = (long) VH_SIZE_T_ARRAY.get(offsetsOut, offsetIdx++);
                    if (ffiOffset != expectedOffset) {
                        throw new IllegalArgumentException("Invalid group layout." +
                                " Offset of '" + element.name().orElse("<unnamed>")
                                + "': " + expectedOffset + " != " + ffiOffset);
                    }
                }
                expectedOffset += element.byteSize();
            }
        }
    }
}
