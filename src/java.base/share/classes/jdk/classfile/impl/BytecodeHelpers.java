/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.classfile.impl;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.classfile.BootstrapMethodEntry;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.constantpool.ConstantDynamicEntry;
import jdk.classfile.constantpool.ConstantPoolBuilder;
import jdk.classfile.Opcode;
import jdk.classfile.TypeKind;
import jdk.classfile.constantpool.LoadableConstantEntry;
import jdk.classfile.constantpool.MemberRefEntry;
import jdk.classfile.constantpool.MethodHandleEntry;
import jdk.classfile.constantpool.NameAndTypeEntry;

import static java.lang.invoke.MethodHandleInfo.REF_putStatic;
import static jdk.classfile.Opcode.IFEQ;
import static jdk.classfile.Opcode.IFGE;
import static jdk.classfile.Opcode.IFGT;
import static jdk.classfile.Opcode.IFLE;
import static jdk.classfile.Opcode.IFLT;
import static jdk.classfile.Opcode.IFNE;
import static jdk.classfile.Opcode.IFNONNULL;
import static jdk.classfile.Opcode.IFNULL;
import static jdk.classfile.Opcode.IF_ACMPEQ;
import static jdk.classfile.Opcode.IF_ACMPNE;
import static jdk.classfile.Opcode.IF_ICMPEQ;
import static jdk.classfile.Opcode.IF_ICMPGE;
import static jdk.classfile.Opcode.IF_ICMPGT;
import static jdk.classfile.Opcode.IF_ICMPLE;
import static jdk.classfile.Opcode.IF_ICMPLT;
import static jdk.classfile.Opcode.IF_ICMPNE;

/**
 * BytecodeHelpers
 */
public class BytecodeHelpers {
    public static Map<ConstantDesc, Opcode> constantsToOpcodes = new HashMap<>(16);

    public BytecodeHelpers() {
    }

    public static Opcode loadOpcode(TypeKind tk, int slot) {
        return switch (tk) {
            case IntType, ShortType, ByteType, CharType, BooleanType -> switch (slot) {
                case 0 -> Opcode.ILOAD_0;
                case 1 -> Opcode.ILOAD_1;
                case 2 -> Opcode.ILOAD_2;
                case 3 -> Opcode.ILOAD_3;
                default -> (slot < 256) ? Opcode.ILOAD : Opcode.ILOAD_W;
            };
            case LongType -> switch (slot) {
                case 0 -> Opcode.LLOAD_0;
                case 1 -> Opcode.LLOAD_1;
                case 2 -> Opcode.LLOAD_2;
                case 3 -> Opcode.LLOAD_3;
                default -> (slot < 256) ? Opcode.LLOAD : Opcode.LLOAD_W;
            };
            case DoubleType -> switch (slot) {
                case 0 -> Opcode.DLOAD_0;
                case 1 -> Opcode.DLOAD_1;
                case 2 -> Opcode.DLOAD_2;
                case 3 -> Opcode.DLOAD_3;
                default -> (slot < 256) ? Opcode.DLOAD : Opcode.DLOAD_W;
            };
            case FloatType -> switch (slot) {
                case 0 -> Opcode.FLOAD_0;
                case 1 -> Opcode.FLOAD_1;
                case 2 -> Opcode.FLOAD_2;
                case 3 -> Opcode.FLOAD_3;
                default -> (slot < 256) ? Opcode.FLOAD : Opcode.FLOAD_W;
            };
            case ReferenceType -> switch (slot) {
                case 0 -> Opcode.ALOAD_0;
                case 1 -> Opcode.ALOAD_1;
                case 2 -> Opcode.ALOAD_2;
                case 3 -> Opcode.ALOAD_3;
                default -> (slot < 256) ? Opcode.ALOAD : Opcode.ALOAD_W;
            };
            case VoidType -> throw new IllegalArgumentException("void");
        };
    }

    public static Opcode storeOpcode(TypeKind tk, int slot) {
        return switch (tk) {
            case IntType, ShortType, ByteType, CharType, BooleanType -> switch (slot) {
                case 0 -> Opcode.ISTORE_0;
                case 1 -> Opcode.ISTORE_1;
                case 2 -> Opcode.ISTORE_2;
                case 3 -> Opcode.ISTORE_3;
                default -> (slot < 256) ? Opcode.ISTORE : Opcode.ISTORE_W;
            };
            case LongType -> switch (slot) {
                case 0 -> Opcode.LSTORE_0;
                case 1 -> Opcode.LSTORE_1;
                case 2 -> Opcode.LSTORE_2;
                case 3 -> Opcode.LSTORE_3;
                default -> (slot < 256) ? Opcode.LSTORE : Opcode.LSTORE_W;
            };
            case DoubleType -> switch (slot) {
                case 0 -> Opcode.DSTORE_0;
                case 1 -> Opcode.DSTORE_1;
                case 2 -> Opcode.DSTORE_2;
                case 3 -> Opcode.DSTORE_3;
                default -> (slot < 256) ? Opcode.DSTORE : Opcode.DSTORE_W;
            };
            case FloatType -> switch (slot) {
                case 0 -> Opcode.FSTORE_0;
                case 1 -> Opcode.FSTORE_1;
                case 2 -> Opcode.FSTORE_2;
                case 3 -> Opcode.FSTORE_3;
                default -> (slot < 256) ? Opcode.FSTORE : Opcode.FSTORE_W;
            };
            case ReferenceType -> switch (slot) {
                case 0 -> Opcode.ASTORE_0;
                case 1 -> Opcode.ASTORE_1;
                case 2 -> Opcode.ASTORE_2;
                case 3 -> Opcode.ASTORE_3;
                default -> (slot < 256) ? Opcode.ASTORE : Opcode.ASTORE_W;
            };
            case VoidType -> throw new IllegalArgumentException("void");
        };
    }

    public static Opcode returnOpcode(TypeKind tk) {
        return switch (tk) {
            case ByteType, ShortType, IntType, CharType, BooleanType -> Opcode.IRETURN;
            case FloatType -> Opcode.FRETURN;
            case LongType -> Opcode.LRETURN;
            case DoubleType -> Opcode.DRETURN;
            case ReferenceType -> Opcode.ARETURN;
            case VoidType -> Opcode.RETURN;
        };
    }

    public static Opcode arrayLoadOpcode(TypeKind tk) {
        return switch (tk) {
            case ByteType, BooleanType -> Opcode.BALOAD;
            case ShortType -> Opcode.SALOAD;
            case IntType -> Opcode.IALOAD;
            case FloatType -> Opcode.FALOAD;
            case LongType -> Opcode.LALOAD;
            case DoubleType -> Opcode.DALOAD;
            case ReferenceType -> Opcode.AALOAD;
            case CharType -> Opcode.CALOAD;
            case VoidType -> throw new IllegalArgumentException("void not an allowable array type");
        };
    }

    public static Opcode arrayStoreOpcode(TypeKind tk) {
        return switch (tk) {
            case ByteType, BooleanType -> Opcode.BASTORE;
            case ShortType -> Opcode.SASTORE;
            case IntType -> Opcode.IASTORE;
            case FloatType -> Opcode.FASTORE;
            case LongType -> Opcode.LASTORE;
            case DoubleType -> Opcode.DASTORE;
            case ReferenceType -> Opcode.AASTORE;
            case CharType -> Opcode.CASTORE;
            case VoidType -> throw new IllegalArgumentException("void not an allowable array type");
        };
    }

    public static Opcode reverseBranchOpcode(Opcode op) {
        return switch (op) {
            case IFEQ -> IFNE;
            case IFNE -> IFEQ;
            case IFLT -> IFGE;
            case IFGE -> IFLT;
            case IFGT -> IFLE;
            case IFLE -> IFGT;
            case IF_ICMPEQ -> IF_ICMPNE;
            case IF_ICMPNE -> IF_ICMPEQ;
            case IF_ICMPLT -> IF_ICMPGE;
            case IF_ICMPGE -> IF_ICMPLT;
            case IF_ICMPGT -> IF_ICMPLE;
            case IF_ICMPLE -> IF_ICMPGT;
            case IF_ACMPEQ -> IF_ACMPNE;
            case IF_ACMPNE -> IF_ACMPEQ;
            case IFNULL -> IFNONNULL;
            case IFNONNULL -> IFNULL;
            default -> throw new IllegalArgumentException("Unknown branch instruction: " + op);
        };
    }

    public static Opcode convertOpcode(TypeKind from, TypeKind to) {
        return switch (from) {
            case IntType ->
                    switch (to) {
                        case LongType -> Opcode.I2L;
                        case FloatType -> Opcode.I2F;
                        case DoubleType -> Opcode.I2D;
                        case ByteType -> Opcode.I2B;
                        case CharType -> Opcode.I2C;
                        case ShortType -> Opcode.I2S;
                        default -> throw new IllegalArgumentException(String.format("convert %s -> %s", from, to));
                    };
            case LongType ->
                    switch (to) {
                        case FloatType -> Opcode.L2F;
                        case DoubleType -> Opcode.L2D;
                        case IntType -> Opcode.L2I;
                        default -> throw new IllegalArgumentException(String.format("convert %s -> %s", from, to));
                    };
            case DoubleType ->
                    switch (to) {
                        case FloatType -> Opcode.D2F;
                        case LongType -> Opcode.D2L;
                        case IntType -> Opcode.D2I;
                        default -> throw new IllegalArgumentException(String.format("convert %s -> %s", from, to));
                    };
            case FloatType ->
                    switch (to) {
                        case LongType -> Opcode.F2L;
                        case DoubleType -> Opcode.F2D;
                        case IntType -> Opcode.F2I;
                        default -> throw new IllegalArgumentException(String.format("convert %s -> %s", from, to));
                    };
            default -> throw new IllegalArgumentException(String.format("convert %s -> %s", from, to));
        };
    }

    static void validateSIPUSH(ConstantDesc d) {
        if (d instanceof Integer iVal && Short.MIN_VALUE <= iVal && iVal <= Short.MAX_VALUE)
            return;

        if (d instanceof Long lVal && Short.MIN_VALUE <= lVal && Short.MAX_VALUE <= lVal)
            return;

        throw new IllegalArgumentException("SIPUSH: value must be within: Short.MIN_VALUE <= value <= Short.MAX_VALUE"
                                           + ", found: " + d);
    }

    static void validateBIPUSH(ConstantDesc d) {
        if (d instanceof Integer iVal && Byte.MIN_VALUE <= iVal && iVal <= Byte.MAX_VALUE)
            return;

        if (d instanceof Long lVal && Byte.MIN_VALUE <= lVal && Byte.MAX_VALUE <= lVal)
            return;

        throw new IllegalArgumentException("BIPUSH: value must be within: Byte.MIN_VALUE <= value <= Byte.MAX_VALUE"
                                           + ", found: " + d);
    }

    public static MethodHandleEntry handleDescToHandleInfo(ConstantPoolBuilder constantPool, DirectMethodHandleDesc bootstrapMethod) {
        ClassEntry bsOwner = constantPool.classEntry(bootstrapMethod.owner());
        NameAndTypeEntry bsNameAndType = constantPool.natEntry(constantPool.utf8Entry(bootstrapMethod.methodName()),
                                                               constantPool.utf8Entry(bootstrapMethod.lookupDescriptor()));
        int bsRefKind = bootstrapMethod.refKind();
        MemberRefEntry bsReference = toBootstrapMemberRef(constantPool, bsRefKind, bsOwner, bsNameAndType, bootstrapMethod.isOwnerInterface());

        return constantPool.methodHandleEntry(bsRefKind, bsReference);
    }

    static MemberRefEntry toBootstrapMemberRef(ConstantPoolBuilder constantPool, int bsRefKind, ClassEntry owner, NameAndTypeEntry nat, boolean isOwnerInterface) {
        return isOwnerInterface
               ? constantPool.interfaceMethodRefEntry(owner, nat)
               : bsRefKind <= REF_putStatic
                 ? constantPool.fieldRefEntry(owner, nat)
                 : constantPool.methodRefEntry(owner, nat);
    }

    static ConstantDynamicEntry handleConstantDescToHandleInfo(ConstantPoolBuilder constantPool, DynamicConstantDesc<?> desc) {
        ConstantDesc[] bootstrapArgs = desc.bootstrapArgs();
        List<LoadableConstantEntry> staticArgs = new ArrayList<>(bootstrapArgs.length);
        for (ConstantDesc bootstrapArg : bootstrapArgs)
            staticArgs.add(constantPool.loadableConstantEntry(bootstrapArg));

        var bootstrapDesc = desc.bootstrapMethod();
        ClassEntry bsOwner = constantPool.classEntry(bootstrapDesc.owner());
        NameAndTypeEntry bsNameAndType = constantPool.natEntry(bootstrapDesc.methodName(),
                                                               bootstrapDesc.invocationType());
        int bsRefKind = bootstrapDesc.refKind();

        MemberRefEntry memberRefEntry = toBootstrapMemberRef(constantPool, bsRefKind, bsOwner, bsNameAndType, bootstrapDesc.isOwnerInterface());
        MethodHandleEntry methodHandleEntry = constantPool.methodHandleEntry(bsRefKind, memberRefEntry);
        BootstrapMethodEntry bme = constantPool.bsmEntry(methodHandleEntry, staticArgs);
        return constantPool.constantDynamicEntry(bme,
                                                 constantPool.natEntry(desc.constantName(),
                                                                       desc.constantType()));
    }

    public static void validateValue(Opcode opcode, ConstantDesc v) {
        switch (opcode) {
            case ACONST_NULL -> {
                if (v != null)
                    throw new IllegalArgumentException("value must be null with opcode ACONST_NULL");
            }
            case SIPUSH ->
                    validateSIPUSH(v);
            case BIPUSH ->
                    validateBIPUSH(v);
            case LDC, LDC_W, LDC2_W -> {
                if (v == null)
                    throw new IllegalArgumentException("`null` must use ACONST_NULL");
            }
            default -> {
                var exp = opcode.constantValue();
                if (exp == null)
                    throw new IllegalArgumentException("Can not use Opcode: " + opcode + " with constant()");
                if (v == null || !(v.equals(exp) || (exp instanceof Long l && v.equals(l.intValue())))) {
                    var t = (exp instanceof Long) ? "L" : (exp instanceof Float) ? "f" : (exp instanceof Double) ? "d" : "";
                    throw new IllegalArgumentException("value must be " + exp + t + " with opcode " + opcode.name());
                }
            }
        }
    }

    public static LoadableConstantEntry constantEntry(ConstantPoolBuilder constantPool,
                                                      ConstantDesc constantValue) {
        // @@@ Pattern switch
        if (constantValue instanceof Integer value) {
            return constantPool.intEntry(value);
        }
        if (constantValue instanceof String value) {
            return constantPool.stringEntry(value);
        }
        if (constantValue instanceof ClassDesc value) {
            return constantPool.classEntry(value);
        }
        if (constantValue instanceof Long value) {
            return constantPool.longEntry(value);
        }
        if (constantValue instanceof Float value) {
            return constantPool.floatEntry(value);
        }
        if (constantValue instanceof Double value) {
            return constantPool.doubleEntry(value);
        }
        if (constantValue instanceof MethodTypeDesc value) {
            return constantPool.methodTypeEntry(value);
        }
        if (constantValue instanceof DirectMethodHandleDesc value) {
            return handleDescToHandleInfo(constantPool, value);
        } if (constantValue instanceof DynamicConstantDesc<?> value) {
            return handleConstantDescToHandleInfo(constantPool, value);
        }
        throw new UnsupportedOperationException("not yet: " + constantValue);
    }

    private static void constantMapping(ConstantDesc c, Opcode o) {
        constantsToOpcodes.put(c, o);
    }

    static {
        constantMapping(-1, Opcode.ICONST_M1);
        constantMapping(0, Opcode.ICONST_0);
        constantMapping(1, Opcode.ICONST_1);
        constantMapping(2, Opcode.ICONST_2);
        constantMapping(3, Opcode.ICONST_3);
        constantMapping(4, Opcode.ICONST_4);
        constantMapping(5, Opcode.ICONST_5);
        constantMapping(0L, Opcode.LCONST_0);
        constantMapping(1L, Opcode.LCONST_1);
        constantMapping(0.0f, Opcode.FCONST_0);
        constantMapping(1.0f, Opcode.FCONST_1);
        constantMapping(2.0f, Opcode.FCONST_2);
        constantMapping(0.0d, Opcode.DCONST_0);
        constantMapping(1.0d, Opcode.DCONST_1);
    }
}
