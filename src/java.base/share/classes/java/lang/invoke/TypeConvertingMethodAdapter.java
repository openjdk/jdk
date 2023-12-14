/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.MethodRefEntry;
import static java.lang.constant.ConstantDescs.*;

class TypeConvertingMethodAdapter {

    private static final ConstantPoolBuilder CP = ConstantPoolBuilder.of();

    private static MethodRefEntry box(ClassDesc primitive, ClassDesc target) {
        return CP.methodRefEntry(target, "valueOf", MethodTypeDesc.of(target, primitive));
    }

    private static final MethodRefEntry BOX_BOOLEAN = box(CD_boolean, CD_Boolean),
                                        BOX_BYTE    = box(CD_byte, CD_Byte),
                                        BOX_SHORT   = box(CD_short, CD_Short),
                                        BOX_CHAR    = box(CD_char, CD_Character),
                                        BOX_INT     = box(CD_int, CD_Integer),
                                        BOX_LONG    = box(CD_long, CD_Long),
                                        BOX_FLOAT   = box(CD_float, CD_Float),
                                        BOX_DOUBLE  = box(CD_double, CD_Double);

    private static MethodRefEntry unbox(ClassDesc owner, String methodName, ClassDesc primitiveTarget) {
        return CP.methodRefEntry(owner, methodName, MethodTypeDesc.of(primitiveTarget));
    }

    private static final MethodRefEntry UNBOX_BOOLEAN = unbox(CD_Boolean, "booleanValue", CD_boolean),
                                        UNBOX_BYTE    = unbox(CD_Number, "byteValue", CD_byte),
                                        UNBOX_SHORT   = unbox(CD_Number, "shortValue", CD_short),
                                        UNBOX_CHAR    = unbox(CD_Character, "charValue", CD_char),
                                        UNBOX_INT     = unbox(CD_Number, "intValue", CD_int),
                                        UNBOX_LONG    = unbox(CD_Number, "longValue", CD_long),
                                        UNBOX_FLOAT   = unbox(CD_Number, "floatValue", CD_float),
                                        UNBOX_DOUBLE  = unbox(CD_Number, "doubleValue", CD_double);

    static private TypeKind primitiveTypeKindFromClass(Class<?> type) {
        return switch (type.getName().length()) {
            case 14 -> type == Long.class ? TypeKind.LongType
                     : type == Byte.class ? TypeKind.ByteType
                     : null;
            case 15 -> type == Short.class ? TypeKind.ShortType
                     : type == Float.class ? TypeKind.FloatType
                     : null;
            case 16 -> type == Double.class ? TypeKind.DoubleType
                     : null;
            case 17 -> type == Integer.class ? TypeKind.IntType
                     : type == Boolean.class ? TypeKind.BooleanType
                     : null;
            case 19 -> type == Character.class ? TypeKind.CharType
                     : null;
            default -> null;
        };
    }

    static void boxIfTypePrimitive(CodeBuilder cob, TypeKind tk) {
        box(cob, tk);
    }

    static void widen(CodeBuilder cob, TypeKind ws, TypeKind wt) {
        ws = ws.asLoadable();
        wt = wt.asLoadable();
        if (ws != wt) {
            cob.convertInstruction(ws, wt);
        }
    }

    static void box(CodeBuilder cob, TypeKind tk) {
        switch (tk) {
            case BooleanType -> cob.invokestatic(BOX_BOOLEAN);
            case ByteType    -> cob.invokestatic(BOX_BYTE);
            case CharType    -> cob.invokestatic(BOX_CHAR);
            case DoubleType  -> cob.invokestatic(BOX_DOUBLE);
            case FloatType   -> cob.invokestatic(BOX_FLOAT);
            case IntType     -> cob.invokestatic(BOX_INT);
            case LongType    -> cob.invokestatic(BOX_LONG);
            case ShortType   -> cob.invokestatic(BOX_SHORT);
        }
    }

    static void unbox(CodeBuilder cob, TypeKind to) {
        switch (to) {
            case BooleanType -> cob.invokevirtual(UNBOX_BOOLEAN);
            case ByteType    -> cob.invokevirtual(UNBOX_BYTE);
            case CharType    -> cob.invokevirtual(UNBOX_CHAR);
            case DoubleType  -> cob.invokevirtual(UNBOX_DOUBLE);
            case FloatType   -> cob.invokevirtual(UNBOX_FLOAT);
            case IntType     -> cob.invokevirtual(UNBOX_INT);
            case LongType    -> cob.invokevirtual(UNBOX_LONG);
            case ShortType   -> cob.invokevirtual(UNBOX_SHORT);
        }
    }

    static void cast(CodeBuilder cob, ClassDesc dt) {
        if (!dt.equals(CD_Object)) {
            cob.checkcast(dt);
        }
    }

    /**
     * Convert an argument of type 'arg' to be passed to 'target' assuring that it is 'functional'.
     * Insert the needed conversion instructions in the method code.
     * @param arg
     * @param target
     * @param functional
     */
    static void convertType(CodeBuilder cob, Class<?> arg, Class<?> target, Class<?> functional) {
        if (arg.equals(target) && arg.equals(functional)) {
            return;
        }
        if (arg == Void.TYPE || target == Void.TYPE) {
            return;
        }
        if (arg.isPrimitive()) {
            if (target.isPrimitive()) {
                // Both primitives: widening
                widen(cob, TypeKind.from(arg), TypeKind.from(target));
            } else {
                // Primitive argument to reference target
                TypeKind wPrimTk = primitiveTypeKindFromClass(target);
                if (wPrimTk != null) {
                    // The target is a boxed primitive type, widen to get there before boxing
                    widen(cob, TypeKind.from(arg), wPrimTk);
                    box(cob, wPrimTk);
                } else {
                    // Otherwise, box and cast
                    box(cob, TypeKind.from(arg));
                    cast(cob, classDesc(target));
                }
            }
        } else {
            Class<?> src;
            if (arg == functional || functional.isPrimitive()) {
                src = arg;
            } else {
                // Cast to convert to possibly more specific type, and generate CCE for invalid arg
                src = functional;
                cast(cob, classDesc(functional));
            }
            if (target.isPrimitive()) {
                // Reference argument to primitive target
                TypeKind wps = primitiveTypeKindFromClass(src);
                if (wps != null) {
                    if (src != Character.class && src != Boolean.class) {
                        // Boxed number to primitive
                        unbox(cob, TypeKind.from(target));
                    } else {
                        // Character or Boolean
                        unbox(cob, wps);
                        widen(cob, wps, TypeKind.from(target));
                    }
                } else {
                    // Source type is reference type, but not boxed type,
                    // assume it is super type of target type
                    if (target == char.class) {
                        cast(cob, CD_Character);
                    } else if (target == boolean.class) {
                        cast(cob, CD_Boolean);
                    } else {
                        // Boxed number to primitive
                        cast(cob, CD_Number);
                    }
                    unbox(cob, TypeKind.from(target));
                }
            } else {
                // Both reference types: just case to target type
                if (src != target) {
                    cast(cob, classDesc(target));
                }
            }
        }
    }

    static ClassDesc classDesc(Class<?> cls) {
        return cls == Object.class ? CD_Object
             : cls == String.class ? CD_String
             : ClassDesc.ofDescriptor(cls.descriptorString());
    }
}
