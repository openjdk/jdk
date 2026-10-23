/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
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

    static final ValueLayout SIZE_T = layoutFor((int)ADDRESS.byteSize()).withSign(false);
    private static final ValueLayout UNSIGNED_SHORT = JAVA_SHORT.withSign(false);
    private static final StructLayout LAYOUT = Utils.computePaddedStructLayout(
            SIZE_T, UNSIGNED_SHORT, UNSIGNED_SHORT.withName("type"), ADDRESS.withName("elements"));

    private static final VarHandle VH_TYPE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("type"));
    private static final VarHandle VH_ELEMENTS = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("elements"));
    private static final VarHandle VH_SIZE_T = SIZE_T.varHandle();

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
        VH_TYPE.set(ffiType, 0L, LibFallback.structTag());
        VH_ELEMENTS.set(ffiType, 0L, elementsSeg);

        return ffiType;
    }

    private static final Map<Long, MemorySegment> UNSIGNED_INTEGRAL_TYPES = Map.of(
            1L, LibFallback.uint8Type(),
            2L, LibFallback.uint16Type(),
            4L, LibFallback.uint32Type(),
            8L, LibFallback.uint64Type());

    private static final Map<Long, MemorySegment> SIGNED_INTEGRAL_TYPES = Map.of(
            1L, LibFallback.sint8Type(),
            2L, LibFallback.sint16Type(),
            4L, LibFallback.sint32Type(),
            8L, LibFallback.sint64Type());

    static MemorySegment toFFIType(MemoryLayout layout, FFIABI abi, Arena scope) {
        return switch (layout) {
            case GroupLayout grpl -> {
                if (grpl instanceof StructLayout strl) {
                    // libffi doesn't want our padding
                    List<MemoryLayout> filteredLayouts = strl.memberLayouts().stream()
                            .filter(Predicate.not(PaddingLayout.class::isInstance))
                            .toList();
                    MemorySegment structType = make(filteredLayouts, abi, scope);
                    verifyStructType(strl, filteredLayouts, structType, abi);
                    yield structType;
                }
                assert grpl instanceof UnionLayout;
                // JDK-8301800
                throw new IllegalArgumentException("Fallback linker does not support by-value unions: " + grpl);
            }
            case SequenceLayout sl -> {
                List<MemoryLayout> elements = Collections.nCopies(Math.toIntExact(sl.elementCount()), sl.elementLayout());
                yield make(elements, abi, scope);
            }
            case ValueLayout.OfFloat _ -> LibFallback.floatType();
            case ValueLayout.OfDouble _ -> LibFallback.doubleType();
            case AddressLayout _ -> LibFallback.pointerType();
            case ValueLayout.OfBoolean _ -> LibFallback.uint8Type();
            case ValueLayout.OfIntegral integral ->
                (integral.isSigned() ? SIGNED_INTEGRAL_TYPES : UNSIGNED_INTEGRAL_TYPES).get(layout.byteSize());
            case null, default -> throw new IllegalArgumentException("Unsupported value layout type: " + layout);
        };
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
                    long ffiOffset = sizeTAtIndex(offsetsOut, offsetIdx++);
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

    static ValueLayout.OfIntegral layoutFor(int byteSize) {
        return switch (byteSize) {
            case 1 -> JAVA_BYTE;
            case 2 -> JAVA_SHORT;
            case 4 -> JAVA_INT;
            case 8 -> JAVA_LONG;
            default -> throw new IllegalStateException("Unsupported size: " + byteSize);
        };
    }

    private static long sizeTAtIndex(MemorySegment segment, int index) {
        long offset = SIZE_T.scale(0, index);
        if (VH_SIZE_T.varType() == long.class) {
            return (long) VH_SIZE_T.get(segment, offset);
        } else {
            return (int) VH_SIZE_T.get(segment, offset); // 'erase' to long
        }
    }
}
