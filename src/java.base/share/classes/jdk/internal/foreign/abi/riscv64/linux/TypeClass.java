/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Institute of Software, Chinese Academy of Sciences. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package jdk.internal.foreign.abi.riscv64.linux;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

public enum TypeClass {
    /*
     * STRUCT_REFERENCE: Struct and its width > 16B, it will be replaced by its reference.
     *     The reference shall be passed by integer register when a register is available,
     *     otherwise the reference will be passed by stack.
     *
     * STRUCT_FA: Struct contains one or two floating-point fields and its width <= 16B.
     *     Should be passed by one or two float-pointing argument registers, if registers available,
     *     otherwise be passed by stack.
     *
     * STRUCT_BOTH: Struct contains both an integer field and a floating-point field
     *     and its width <= 16B, shall be passed by both a floating-point argument register
     *     and an integer argument register where both a float register and an integer is available.
     *
     * STRUCT_A: Struct and its width <= 16B.
     *     Should be passed by one or two integer argument register if registers are available,
     *     otherwise it will be passed by stack.
     *
     * See https://github.com/riscv-non-isa/riscv-elf-psabi-doc
     * */
    INTEGER_8,
    INTEGER_16,
    INTEGER_32,
    INTEGER_64,
    FLOAT_32,
    FLOAT_64,
    POINTER,
    STRUCT_A,
    STRUCT_FA,
    STRUCT_BOTH,
    STRUCT_REFERENCE;

    /*
     * Struct will be flattened while classifying and therefore should count its fields recursively.
     *
     * struct{struct{int, double}} will be treated same as struct{int, double}
     * struct{int[2]} will be treated same as struct{int, int}
     * */
    private static record FieldCounter(long integerCnt, long floatCnt, long pointerCnt) {
        static final FieldCounter EMPTY = new FieldCounter(0, 0, 0);
        static final FieldCounter SINGLE_INTEGER = new FieldCounter(1, 0, 0);
        static final FieldCounter SINGLE_FLOAT = new FieldCounter(0, 1, 0);
        static final FieldCounter SINGLE_POINTER = new FieldCounter(0, 0, 1);

        static FieldCounter flatten(MemoryLayout layout) {
            if (layout instanceof ValueLayout valueLayout) {
                return switch (classifyValueType(valueLayout)) {
                    case INTEGER_8, INTEGER_16, INTEGER_32, INTEGER_64 -> FieldCounter.SINGLE_INTEGER;
                    case FLOAT_32, FLOAT_64 -> FieldCounter.SINGLE_FLOAT;
                    case POINTER -> FieldCounter.SINGLE_POINTER;
                    default -> {
                        assert false : "should not reach here.";
                        yield null; /* should not reach here. */
                    }
                };
            } else if (layout instanceof GroupLayout groupLayout) {
                FieldCounter currCounter = FieldCounter.EMPTY;
                for (MemoryLayout memberLayout : groupLayout.memberLayouts()) {
                    if (memberLayout.isPadding()) continue;
                    currCounter = currCounter.add(flatten(memberLayout));
                }
                return currCounter;
            } else if (layout instanceof SequenceLayout sequenceLayout) {
                long elementCount = sequenceLayout.elementCount();
                if (elementCount == 0) return FieldCounter.EMPTY;
                return flatten(sequenceLayout.elementLayout()).mul(elementCount);
            } else {
                throw new IllegalStateException("Cannot get here: " + layout);
            }
        }

        FieldCounter mul(long m) {
            return new FieldCounter(integerCnt * m,
                                    floatCnt * m,
                                    pointerCnt * m);
        }

        FieldCounter add(FieldCounter other) {
            return new FieldCounter(integerCnt + other.integerCnt,
                                    floatCnt + other.floatCnt,
                                    pointerCnt + other.pointerCnt);
        }

        boolean isSTRUCT_FA() {
            return integerCnt == 0 && pointerCnt == 0 &&
                    (floatCnt == 1 || floatCnt == 2);
        }

        boolean isSTRUCT_BOTH() {
            return integerCnt == 1 && floatCnt == 1 && pointerCnt == 0;
        }
    }

    public static record FlattenedFieldDesc(TypeClass typeClass, long offset, ValueLayout layout) {

    }

    private static List<FlattenedFieldDesc> getFlattenedFieldsInner(long offset, MemoryLayout layout) {
        if (layout instanceof ValueLayout valueLayout) {
            TypeClass typeClass = classifyValueType(valueLayout);
            return List.of(switch (typeClass) {
                case INTEGER_8, INTEGER_16, INTEGER_32, INTEGER_64, FLOAT_32, FLOAT_64 ->
                        new FlattenedFieldDesc(typeClass, offset, valueLayout);
                default -> {
                    assert false : "should not reach here.";
                    yield null; /* should not reach here. */
                }
            });
        } else if (layout instanceof GroupLayout groupLayout) {
            List<FlattenedFieldDesc> fields = new ArrayList<>();
            for (MemoryLayout memberLayout : groupLayout.memberLayouts()) {
                if (memberLayout.isPadding()) {
                    offset += memberLayout.byteSize();
                    continue;
                }
                fields.addAll(getFlattenedFieldsInner(offset, memberLayout));
                offset += memberLayout.byteSize();
            }
            return fields;
        } else if (layout instanceof SequenceLayout sequenceLayout) {
            List<FlattenedFieldDesc> fields = new ArrayList<>();
            MemoryLayout elementLayout = sequenceLayout.elementLayout();
            for (long i = 0; i < sequenceLayout.elementCount(); i++) {
                fields.addAll(getFlattenedFieldsInner(offset, elementLayout));
                offset += elementLayout.byteSize();
            }
            return fields;
        } else {
            throw new IllegalStateException("Cannot get here: " + layout);
        }
    }

    public static List<FlattenedFieldDesc> getFlattenedFields(GroupLayout layout) {
        return getFlattenedFieldsInner(0, layout);
    }

    // ValueLayout will be classified by its carrier type.
    private static TypeClass classifyValueType(ValueLayout type) {
        Class<?> carrier = type.carrier();
        if (carrier == boolean.class || carrier == byte.class) {
            return INTEGER_8;
        } else if (carrier == char.class || carrier == short.class) {
            return INTEGER_16;
        } else if (carrier == int.class) {
            return INTEGER_32;
        } else if (carrier == long.class) {
            return INTEGER_64;
        } else if (carrier == float.class) {
            return FLOAT_32;
        } else if (carrier == double.class) {
            return FLOAT_64;
        } else if (carrier == MemoryAddress.class) {
            return POINTER;
        } else {
            throw new IllegalArgumentException("Unhandled type " + type);
        }
    }

    private static boolean isRegisterAggregate(MemoryLayout type) {
        return type.byteSize() <= 16L;
    }

    private static TypeClass classifyStructType(GroupLayout layout) {
        if (layout.isUnion()) {
            return isRegisterAggregate(layout) ? STRUCT_A : STRUCT_REFERENCE;
        }

        // classify struct by its fields.
        FieldCounter counter = FieldCounter.flatten(layout);

        if (!isRegisterAggregate(layout)) return STRUCT_REFERENCE;
        else if (counter.isSTRUCT_FA()) return STRUCT_FA;
        else if (counter.isSTRUCT_BOTH()) return STRUCT_BOTH;
        else return STRUCT_A;
    }

    // Classify argument pass style.
    static TypeClass classifyLayout(MemoryLayout type) {
        if (type instanceof ValueLayout vt) {
            return classifyValueType(vt);
        } else if (type instanceof GroupLayout gt) {
            return classifyStructType(gt);
        } else {
            throw new IllegalArgumentException("Unhandled type " + type);
        }
    }
}
