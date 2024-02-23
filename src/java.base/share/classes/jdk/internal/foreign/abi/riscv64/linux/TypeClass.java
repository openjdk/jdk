/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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
 *
 */

package jdk.internal.foreign.abi.riscv64.linux;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

public enum TypeClass {
    /*
     * STRUCT_REFERENCE: Aggregates larger than 2 * XLEN bits are passed by reference and are replaced
     *     in the argument list with the address. The address will be passed in a register if at least
     *     one register is available, otherwise it will be passed on the stack.
     *
     * STRUCT_REGISTER_F: A struct containing just one floating-point real is passed as though it were
     *     a standalone floating-point real. A struct containing two floating-point reals is passed in two
     *     floating-point registers, if neither real is more than ABI_FLEN bits wide and at least two
     *     floating-point argument registers are available. (The registers need not be an aligned pair.)
     *     Otherwise, it is passed according to the integer calling convention.
     *
     * STRUCT_REGISTER_XF: A struct containing one floating-point real and one integer (or bitfield), in either
     *     order, is passed in a floating-point register and an integer register, provided the floating-point real
     *     is no more than ABI_FLEN bits wide and the integer is no more than XLEN bits wide, and at least one
     *     floating-point argument register and at least one integer argument register is available. If the struct
     *     is not passed in this manner, then it is passed according to the integer calling convention.
     *
     * STRUCT_REGISTER_X: Aggregates whose total size is no more than XLEN bits are passed in a register, with the
     *     fields laid out as though they were passed in memory. If no register is available, the aggregate is
     *     passed on the stack. Aggregates whose total size is no more than 2 * XLEN bits are passed in a pair of
     *     registers; if only one register is available, the first XLEN bits are passed in a register and the
     *     remaining bits are passed on the stack. If no registers are available, the aggregate is passed on the stack.
     *
     * See https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc
     * */
    INTEGER,
    FLOAT,
    POINTER,
    STRUCT_REFERENCE,
    STRUCT_REGISTER_F,
    STRUCT_REGISTER_XF,
    STRUCT_REGISTER_X;

    private static final int MAX_AGGREGATE_REGS_SIZE = 2;

    /*
     * Struct will be flattened while classifying. That is, struct{struct{int, double}} will be treated
     * same as struct{int, double} and struct{int[2]} will be treated same as struct{int, int}.
     * */
    private record FieldCounter(long integerCnt, long floatCnt, long pointerCnt) {
        static final FieldCounter EMPTY = new FieldCounter(0, 0, 0);
        static final FieldCounter SINGLE_INTEGER = new FieldCounter(1, 0, 0);
        static final FieldCounter SINGLE_FLOAT = new FieldCounter(0, 1, 0);
        static final FieldCounter SINGLE_POINTER = new FieldCounter(0, 0, 1);

        static FieldCounter flatten(MemoryLayout layout) {
            switch (layout) {
                case ValueLayout valueLayout -> {
                    return switch (classifyValueType(valueLayout)) {
                        case INTEGER -> FieldCounter.SINGLE_INTEGER;
                        case FLOAT   -> FieldCounter.SINGLE_FLOAT;
                        case POINTER -> FieldCounter.SINGLE_POINTER;
                        default -> throw new IllegalStateException("Should not reach here.");
                    };
                }
                case GroupLayout groupLayout -> {
                    FieldCounter currCounter = FieldCounter.EMPTY;
                    for (MemoryLayout memberLayout : groupLayout.memberLayouts()) {
                        if (memberLayout instanceof PaddingLayout) {
                            continue;
                        }
                        currCounter = currCounter.add(flatten(memberLayout));
                    }
                    return currCounter;
                }
                case SequenceLayout sequenceLayout -> {
                    long elementCount = sequenceLayout.elementCount();
                    if (elementCount == 0) {
                        return FieldCounter.EMPTY;
                    }
                    return flatten(sequenceLayout.elementLayout()).mul(elementCount);
                }
                default -> throw new IllegalStateException("Cannot get here: " + layout);
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
    }

    public record FlattenedFieldDesc(TypeClass typeClass, long offset, ValueLayout layout) { }

    private static List<FlattenedFieldDesc> getFlattenedFieldsInner(long offset, MemoryLayout layout) {
        return switch (layout) {
            case ValueLayout valueLayout -> {
                TypeClass typeClass = classifyValueType(valueLayout);
                yield List.of(switch (typeClass) {
                    case INTEGER, FLOAT -> new FlattenedFieldDesc(typeClass, offset, valueLayout);
                    default -> throw new IllegalStateException("Should not reach here.");
                });
            }
            case GroupLayout groupLayout -> {
                List<FlattenedFieldDesc> fields = new ArrayList<>();
                for (MemoryLayout memberLayout : groupLayout.memberLayouts()) {
                    if (memberLayout instanceof PaddingLayout) {
                        offset += memberLayout.byteSize();
                        continue;
                    }
                    fields.addAll(getFlattenedFieldsInner(offset, memberLayout));
                    offset += memberLayout.byteSize();
                }
                yield fields;
            }
            case SequenceLayout sequenceLayout -> {
                List<FlattenedFieldDesc> fields = new ArrayList<>();
                MemoryLayout elementLayout = sequenceLayout.elementLayout();
                for (long i = 0; i < sequenceLayout.elementCount(); i++) {
                    fields.addAll(getFlattenedFieldsInner(offset, elementLayout));
                    offset += elementLayout.byteSize();
                }
                yield fields;
            }
            case null, default -> throw new IllegalStateException("Cannot get here: " + layout);
        };
    }

    public static List<FlattenedFieldDesc> getFlattenedFields(GroupLayout layout) {
        return getFlattenedFieldsInner(0, layout);
    }

    // ValueLayout will be classified by its carrier type.
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

    private static boolean isRegisterAggregate(MemoryLayout type) {
        return type.byteSize() <= MAX_AGGREGATE_REGS_SIZE * 8;
    }

    private static TypeClass classifyStructType(GroupLayout layout) {
        if (layout instanceof UnionLayout) {
            return isRegisterAggregate(layout) ? STRUCT_REGISTER_X : STRUCT_REFERENCE;
        }

        if (!isRegisterAggregate(layout)) {
            return STRUCT_REFERENCE;
        }

        // classify struct by its fields.
        FieldCounter counter = FieldCounter.flatten(layout);
        if (counter.integerCnt == 0 && counter.pointerCnt == 0 &&
            (counter.floatCnt == 1 || counter.floatCnt == 2)) {
            return STRUCT_REGISTER_F;
        } else if (counter.integerCnt == 1 && counter.floatCnt == 1 &&
                   counter.pointerCnt == 0) {
            return STRUCT_REGISTER_XF;
        } else {
            return STRUCT_REGISTER_X;
        }
    }

    public static TypeClass classifyLayout(MemoryLayout type) {
        if (type instanceof ValueLayout vt) {
            return classifyValueType(vt);
        } else if (type instanceof GroupLayout gt) {
            return classifyStructType(gt);
        } else {
            throw new IllegalArgumentException("Unsupported layout: " + type);
        }
    }
}
