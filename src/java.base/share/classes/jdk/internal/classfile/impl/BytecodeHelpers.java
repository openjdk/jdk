/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandleInfo;
import java.util.ArrayList;
import java.util.List;

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;

import static java.lang.classfile.ClassFile.*;

/**
 * Note: This class switches on opcode.bytecode for code size
 */
public class BytecodeHelpers {

    private BytecodeHelpers() {
    }

    public static IllegalArgumentException cannotConvertException(TypeKind from, TypeKind to) {
        return new IllegalArgumentException(String.format("convert %s -> %s", from, to));
    }

    public static Opcode loadOpcode(TypeKind tk, int slot) {
        return switch (tk) {
            case INT, SHORT, BYTE, CHAR, BOOLEAN -> switch (slot) {
                case 0 -> Opcode.ILOAD_0;
                case 1 -> Opcode.ILOAD_1;
                case 2 -> Opcode.ILOAD_2;
                case 3 -> Opcode.ILOAD_3;
                default -> (slot < 256) ? Opcode.ILOAD : Opcode.ILOAD_W;
            };
            case LONG -> switch (slot) {
                case 0 -> Opcode.LLOAD_0;
                case 1 -> Opcode.LLOAD_1;
                case 2 -> Opcode.LLOAD_2;
                case 3 -> Opcode.LLOAD_3;
                default -> (slot < 256) ? Opcode.LLOAD : Opcode.LLOAD_W;
            };
            case DOUBLE -> switch (slot) {
                case 0 -> Opcode.DLOAD_0;
                case 1 -> Opcode.DLOAD_1;
                case 2 -> Opcode.DLOAD_2;
                case 3 -> Opcode.DLOAD_3;
                default -> (slot < 256) ? Opcode.DLOAD : Opcode.DLOAD_W;
            };
            case FLOAT -> switch (slot) {
                case 0 -> Opcode.FLOAD_0;
                case 1 -> Opcode.FLOAD_1;
                case 2 -> Opcode.FLOAD_2;
                case 3 -> Opcode.FLOAD_3;
                default -> (slot < 256) ? Opcode.FLOAD : Opcode.FLOAD_W;
            };
            case REFERENCE -> switch (slot) {
                case 0 -> Opcode.ALOAD_0;
                case 1 -> Opcode.ALOAD_1;
                case 2 -> Opcode.ALOAD_2;
                case 3 -> Opcode.ALOAD_3;
                default -> (slot < 256) ? Opcode.ALOAD : Opcode.ALOAD_W;
            };
            case VOID -> throw new IllegalArgumentException("void");
        };
    }

    public static Opcode storeOpcode(TypeKind tk, int slot) {
        return switch (tk) {
            case INT, SHORT, BYTE, CHAR, BOOLEAN -> switch (slot) {
                case 0 -> Opcode.ISTORE_0;
                case 1 -> Opcode.ISTORE_1;
                case 2 -> Opcode.ISTORE_2;
                case 3 -> Opcode.ISTORE_3;
                default -> (slot < 256) ? Opcode.ISTORE : Opcode.ISTORE_W;
            };
            case LONG -> switch (slot) {
                case 0 -> Opcode.LSTORE_0;
                case 1 -> Opcode.LSTORE_1;
                case 2 -> Opcode.LSTORE_2;
                case 3 -> Opcode.LSTORE_3;
                default -> (slot < 256) ? Opcode.LSTORE : Opcode.LSTORE_W;
            };
            case DOUBLE -> switch (slot) {
                case 0 -> Opcode.DSTORE_0;
                case 1 -> Opcode.DSTORE_1;
                case 2 -> Opcode.DSTORE_2;
                case 3 -> Opcode.DSTORE_3;
                default -> (slot < 256) ? Opcode.DSTORE : Opcode.DSTORE_W;
            };
            case FLOAT -> switch (slot) {
                case 0 -> Opcode.FSTORE_0;
                case 1 -> Opcode.FSTORE_1;
                case 2 -> Opcode.FSTORE_2;
                case 3 -> Opcode.FSTORE_3;
                default -> (slot < 256) ? Opcode.FSTORE : Opcode.FSTORE_W;
            };
            case REFERENCE -> switch (slot) {
                case 0 -> Opcode.ASTORE_0;
                case 1 -> Opcode.ASTORE_1;
                case 2 -> Opcode.ASTORE_2;
                case 3 -> Opcode.ASTORE_3;
                default -> (slot < 256) ? Opcode.ASTORE : Opcode.ASTORE_W;
            };
            case VOID -> throw new IllegalArgumentException("void");
        };
    }

    public static Opcode returnOpcode(TypeKind tk) {
        return switch (tk) {
            case BYTE, SHORT, INT, CHAR, BOOLEAN -> Opcode.IRETURN;
            case FLOAT -> Opcode.FRETURN;
            case LONG -> Opcode.LRETURN;
            case DOUBLE -> Opcode.DRETURN;
            case REFERENCE -> Opcode.ARETURN;
            case VOID -> Opcode.RETURN;
        };
    }

    public static Opcode arrayLoadOpcode(TypeKind tk) {
        return switch (tk) {
            case BYTE, BOOLEAN -> Opcode.BALOAD;
            case SHORT -> Opcode.SALOAD;
            case INT -> Opcode.IALOAD;
            case FLOAT -> Opcode.FALOAD;
            case LONG -> Opcode.LALOAD;
            case DOUBLE -> Opcode.DALOAD;
            case REFERENCE -> Opcode.AALOAD;
            case CHAR -> Opcode.CALOAD;
            case VOID -> throw new IllegalArgumentException("void not an allowable array type");
        };
    }

    public static Opcode arrayStoreOpcode(TypeKind tk) {
        return switch (tk) {
            case BYTE, BOOLEAN -> Opcode.BASTORE;
            case SHORT -> Opcode.SASTORE;
            case INT -> Opcode.IASTORE;
            case FLOAT -> Opcode.FASTORE;
            case LONG -> Opcode.LASTORE;
            case DOUBLE -> Opcode.DASTORE;
            case REFERENCE -> Opcode.AASTORE;
            case CHAR -> Opcode.CASTORE;
            case VOID -> throw new IllegalArgumentException("void not an allowable array type");
        };
    }

    public static Opcode reverseBranchOpcode(Opcode op) {
        return switch (op.bytecode()) {
            case IFEQ -> Opcode.IFNE;
            case IFNE -> Opcode.IFEQ;
            case IFLT -> Opcode.IFGE;
            case IFGE -> Opcode.IFLT;
            case IFGT -> Opcode.IFLE;
            case IFLE -> Opcode.IFGT;
            case IF_ICMPEQ -> Opcode.IF_ICMPNE;
            case IF_ICMPNE -> Opcode.IF_ICMPEQ;
            case IF_ICMPLT -> Opcode.IF_ICMPGE;
            case IF_ICMPGE -> Opcode.IF_ICMPLT;
            case IF_ICMPGT -> Opcode.IF_ICMPLE;
            case IF_ICMPLE -> Opcode.IF_ICMPGT;
            case IF_ACMPEQ -> Opcode.IF_ACMPNE;
            case IF_ACMPNE -> Opcode.IF_ACMPEQ;
            case IFNULL -> Opcode.IFNONNULL;
            case IFNONNULL -> Opcode.IFNULL;
            default -> throw Util.badOpcodeKindException(op, Opcode.Kind.BRANCH);
        };
    }

    public static Opcode convertOpcode(TypeKind from, TypeKind to) {
        return switch (from) {
            case INT ->
                    switch (to) {
                        case LONG -> Opcode.I2L;
                        case FLOAT -> Opcode.I2F;
                        case DOUBLE -> Opcode.I2D;
                        case BYTE -> Opcode.I2B;
                        case CHAR -> Opcode.I2C;
                        case SHORT -> Opcode.I2S;
                        default -> throw cannotConvertException(from, to);
                    };
            case LONG ->
                    switch (to) {
                        case FLOAT -> Opcode.L2F;
                        case DOUBLE -> Opcode.L2D;
                        case INT -> Opcode.L2I;
                        default -> throw cannotConvertException(from, to);
                    };
            case DOUBLE ->
                    switch (to) {
                        case FLOAT -> Opcode.D2F;
                        case LONG -> Opcode.D2L;
                        case INT -> Opcode.D2I;
                        default -> throw cannotConvertException(from, to);
                    };
            case FLOAT ->
                    switch (to) {
                        case LONG -> Opcode.F2L;
                        case DOUBLE -> Opcode.F2D;
                        case INT -> Opcode.F2I;
                        default -> throw cannotConvertException(from, to);
                    };
            default -> throw cannotConvertException(from, to);
        };
    }

    public static TypeKind convertFromType(Opcode opcode) {
        return switch (opcode.bytecode()) {
            case I2D, I2F, I2L, I2B, I2C, I2S -> TypeKind.IntType;
            case L2D, L2F, L2I -> TypeKind.LongType;
            case F2D, F2I, F2L -> TypeKind.FloatType;
            case D2F, D2I, D2L -> TypeKind.DoubleType;
            default -> throw Util.badOpcodeKindException(opcode, Opcode.Kind.CONVERT);
        };
    }

    public static TypeKind convertToType(Opcode opcode) {
        return switch (opcode.bytecode()) {
            case I2B -> TypeKind.ByteType;
            case I2C -> TypeKind.CharType;
            case I2S -> TypeKind.ShortType;
            case L2I, F2I, D2I -> TypeKind.IntType;
            case I2L, F2L, D2L -> TypeKind.LongType;
            case I2F, L2F, D2F -> TypeKind.FloatType;
            case I2D, L2D, F2D -> TypeKind.DoubleType;
            default -> throw Util.badOpcodeKindException(opcode, Opcode.Kind.CONVERT);
        };
    }

    static void validateSipush(long value) {
        if (value < Short.MIN_VALUE || Short.MAX_VALUE < value)
            throw new IllegalArgumentException(
                    "SIPUSH: value must be within: Short.MIN_VALUE <= value <= Short.MAX_VALUE, found: "
                            .concat(Long.toString(value)));
    }

    static void validateBipush(long value) {
        if (value < Byte.MIN_VALUE || Byte.MAX_VALUE < value)
            throw new IllegalArgumentException(
                    "BIPUSH: value must be within: Byte.MIN_VALUE <= value <= Byte.MAX_VALUE, found: "
                            .concat(Long.toString(value)));
    }

    static void validateSipush(ConstantDesc d) {
        if (d instanceof Integer iVal) {
            validateSipush(iVal.longValue());
        } else if (d instanceof Long lVal) {
            validateSipush(lVal.longValue());
        } else {
            throw new IllegalArgumentException("SIPUSH: not an integral number: ".concat(d.toString()));
        }
    }

    static void validateBipush(ConstantDesc d) {
        if (d instanceof Integer iVal) {
            validateBipush(iVal.longValue());
        } else if (d instanceof Long lVal) {
            validateBipush(lVal.longValue());
        } else {
            throw new IllegalArgumentException("BIPUSH: not an integral number: ".concat(d.toString()));
        }
    }

    public static MethodHandleEntry handleDescToHandleInfo(ConstantPoolBuilder constantPool, DirectMethodHandleDesc bootstrapMethod) {
        ClassEntry bsOwner = constantPool.classEntry(bootstrapMethod.owner());
        NameAndTypeEntry bsNameAndType = constantPool.nameAndTypeEntry(constantPool.utf8Entry(bootstrapMethod.methodName()),
                                                               constantPool.utf8Entry(bootstrapMethod.lookupDescriptor()));
        int bsRefKind = bootstrapMethod.refKind();
        MemberRefEntry bsReference = toBootstrapMemberRef(constantPool, bsRefKind, bsOwner, bsNameAndType, bootstrapMethod.isOwnerInterface());

        return constantPool.methodHandleEntry(bsRefKind, bsReference);
    }

    static MemberRefEntry toBootstrapMemberRef(ConstantPoolBuilder constantPool, int bsRefKind, ClassEntry owner, NameAndTypeEntry nat, boolean isOwnerInterface) {
        return isOwnerInterface
               ? constantPool.interfaceMethodRefEntry(owner, nat)
               : bsRefKind <= MethodHandleInfo.REF_putStatic
                 ? constantPool.fieldRefEntry(owner, nat)
                 : constantPool.methodRefEntry(owner, nat);
    }

    static ConstantDynamicEntry handleConstantDescToHandleInfo(ConstantPoolBuilder constantPool, DynamicConstantDesc<?> desc) {
        ConstantDesc[] bootstrapArgs = desc.bootstrapArgs();
        List<LoadableConstantEntry> staticArgs = new ArrayList<>(bootstrapArgs.length);
        for (ConstantDesc bootstrapArg : bootstrapArgs)
            staticArgs.add(constantPool.loadableConstantEntry(bootstrapArg));
        MethodHandleEntry methodHandleEntry = handleDescToHandleInfo(constantPool, desc.bootstrapMethod());
        BootstrapMethodEntry bme = constantPool.bsmEntry(methodHandleEntry, staticArgs);
        return constantPool.constantDynamicEntry(bme,
                                                 constantPool.nameAndTypeEntry(desc.constantName(),
                                                                       desc.constantType()));
    }

    public static void validateValue(Opcode opcode, ConstantDesc v) {
        switch (opcode.bytecode()) {
            case ACONST_NULL -> {
                if (v != null && v != ConstantDescs.NULL)
                    throw new IllegalArgumentException("value must be null or ConstantDescs.NULL with opcode ACONST_NULL");
            }
            case SIPUSH ->
                    validateSipush(v);
            case BIPUSH ->
                    validateBipush(v);
            case LDC, LDC_W, LDC2_W -> {
                if (v == null)
                    throw new IllegalArgumentException("`null` must use ACONST_NULL");
            }
            default -> {
                var exp = intrinsicConstantValue(opcode);
                if (v == null || !(v.equals(exp) || (exp instanceof Long l && v.equals(l.intValue())))) {
                    var t = (exp instanceof Long) ? "L" : (exp instanceof Float) ? "f" : (exp instanceof Double) ? "d" : "";
                    throw new IllegalArgumentException("value must be " + exp + t + " with opcode " + opcode.name());
                }
            }
        }
    }

    public static Opcode ldcOpcode(LoadableConstantEntry entry) {
        return entry.typeKind().slotSize() == 2 ? Opcode.LDC2_W
                : entry.index() > 0xff ? Opcode.LDC_W
                : Opcode.LDC;
    }

    public static LoadableConstantEntry constantEntry(ConstantPoolBuilder constantPool,
                                                      ConstantDesc constantValue) {
        // this method is invoked during JVM bootstrap - cannot use pattern switch
        if (constantValue instanceof Integer value) {
            return constantPool.intEntry(value);
        }
        if (constantValue instanceof String value) {
            return constantPool.stringEntry(value);
        }
        if (constantValue instanceof ClassDesc value && !value.isPrimitive()) {
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

    public static ConstantDesc intrinsicConstantValue(Opcode opcode) {
        return switch (opcode.bytecode()) {
            case ACONST_NULL -> ConstantDescs.NULL;
            case ICONST_M1 -> -1;
            case ICONST_0 -> 0;
            case ICONST_1 -> 1;
            case ICONST_2 -> 2;
            case ICONST_3 -> 3;
            case ICONST_4 -> 4;
            case ICONST_5 -> 5;
            case LCONST_0 -> 0L;
            case LCONST_1 -> 1L;
            case FCONST_0 -> 0F;
            case FCONST_1 -> 1F;
            case FCONST_2 -> 2F;
            case DCONST_0 -> 0D;
            case DCONST_1 -> 1D;
            default -> throw Util.badOpcodeKindException(opcode, Opcode.Kind.CONSTANT);
        };
    }

    public static TypeKind intrinsicConstantType(Opcode opcode) {
        return switch (opcode.bytecode()) {
            case ACONST_NULL -> TypeKind.ReferenceType;
            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 -> TypeKind.IntType;
            case LCONST_0, LCONST_1 -> TypeKind.LongType;
            case FCONST_0, FCONST_1, FCONST_2 -> TypeKind.FloatType;
            case DCONST_0, DCONST_1 -> TypeKind.DoubleType;
            default -> throw Util.badOpcodeKindException(opcode, Opcode.Kind.CONSTANT);
        };
    }

    public static boolean isUnconditionalBranch(Opcode opcode) {
        return switch (opcode.bytecode()) {
            case GOTO, ATHROW, GOTO_W, LOOKUPSWITCH, TABLESWITCH -> true;
            default -> opcode.kind() == Opcode.Kind.RETURN;
        };
    }

    // Must check Opcode.sizeIfFixed() == 1 before call!
    public static int intrinsicLoadSlot(Opcode loadOpcode) {
        return switch (loadOpcode.bytecode()) {
            case ILOAD_0, LLOAD_0, FLOAD_0, DLOAD_0, ALOAD_0 -> 0;
            case ILOAD_1, LLOAD_1, FLOAD_1, DLOAD_1, ALOAD_1 -> 1;
            case ILOAD_2, LLOAD_2, FLOAD_2, DLOAD_2, ALOAD_2 -> 2;
            case ILOAD_3, LLOAD_3, FLOAD_3, DLOAD_3, ALOAD_3 -> 3;
            default -> throw Util.badOpcodeKindException(loadOpcode, Opcode.Kind.LOAD);
        };
    }

    // Must check Opcode.sizeIfFixed() == 1 before call!
    public static int intrinsicStoreSlot(Opcode storeOpcode) {
        return switch (storeOpcode.bytecode()) {
            case ISTORE_0, LSTORE_0, FSTORE_0, DSTORE_0, ASTORE_0 -> 0;
            case ISTORE_1, LSTORE_1, FSTORE_1, DSTORE_1, ASTORE_1 -> 1;
            case ISTORE_2, LSTORE_2, FSTORE_2, DSTORE_2, ASTORE_2 -> 2;
            case ISTORE_3, LSTORE_3, FSTORE_3, DSTORE_3, ASTORE_3 -> 3;
            default -> throw Util.badOpcodeKindException(storeOpcode, Opcode.Kind.STORE);
        };
    }

    public static TypeKind loadType(Opcode loadOpcode) {
        // Note: 0xFF handles wide opcodes
        return switch (loadOpcode.bytecode() & 0xFF) {
            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 -> TypeKind.IntType;
            case LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3 -> TypeKind.LongType;
            case FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3 -> TypeKind.FloatType;
            case DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3 -> TypeKind.DoubleType;
            case ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 -> TypeKind.ReferenceType;
            default -> throw Util.badOpcodeKindException(loadOpcode, Opcode.Kind.LOAD);
        };
    }

    public static TypeKind storeType(Opcode storeOpcode) {
        // Note: 0xFF handles wide opcodes
        return switch (storeOpcode.bytecode() & 0xFF) {
            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> TypeKind.IntType;
            case LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3 -> TypeKind.LongType;
            case FSTORE, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3 -> TypeKind.FloatType;
            case DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3 -> TypeKind.DoubleType;
            case ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> TypeKind.ReferenceType;
            default -> throw Util.badOpcodeKindException(storeOpcode, Opcode.Kind.STORE);
        };
    }

    public static TypeKind arrayLoadType(Opcode arrayLoadOpcode) {
        return switch (arrayLoadOpcode.bytecode()) {
            case IALOAD -> TypeKind.IntType;
            case LALOAD -> TypeKind.LongType;
            case FALOAD -> TypeKind.FloatType;
            case DALOAD -> TypeKind.DoubleType;
            case AALOAD -> TypeKind.ReferenceType;
            case BALOAD -> TypeKind.ByteType;
            case CALOAD -> TypeKind.CharType;
            case SALOAD -> TypeKind.ShortType;
            default -> throw Util.badOpcodeKindException(arrayLoadOpcode, Opcode.Kind.ARRAY_LOAD);
        };
    }

    public static TypeKind arrayStoreType(Opcode arrayStoreOpcode) {
        return switch (arrayStoreOpcode.bytecode()) {
            case IASTORE -> TypeKind.IntType;
            case LASTORE -> TypeKind.LongType;
            case FASTORE -> TypeKind.FloatType;
            case DASTORE -> TypeKind.DoubleType;
            case AASTORE -> TypeKind.ReferenceType;
            case BASTORE -> TypeKind.ByteType;
            case CASTORE -> TypeKind.CharType;
            case SASTORE -> TypeKind.ShortType;
            default -> throw Util.badOpcodeKindException(arrayStoreOpcode, Opcode.Kind.ARRAY_STORE);
        };
    }

    public static TypeKind returnType(Opcode returnOpcode) {
        return switch (returnOpcode.bytecode()) {
            case IRETURN -> TypeKind.IntType;
            case LRETURN -> TypeKind.LongType;
            case FRETURN -> TypeKind.FloatType;
            case DRETURN -> TypeKind.DoubleType;
            case ARETURN -> TypeKind.ReferenceType;
            case RETURN -> TypeKind.VoidType;
            default -> throw Util.badOpcodeKindException(returnOpcode, Opcode.Kind.RETURN);
        };
    }

    public static TypeKind operatorOperandType(Opcode operationOpcode) {
        return switch (operationOpcode.bytecode()) {
            case IADD, ISUB, IMUL, IDIV, IREM, INEG,
                 ISHL, ISHR, IUSHR, IAND, IOR, IXOR,
                 ARRAYLENGTH -> TypeKind.IntType;
            case LADD, LSUB, LMUL, LDIV, LREM, LNEG,
                 LSHL, LSHR, LUSHR, LAND, LOR, LXOR,
                 LCMP -> TypeKind.LongType;
            case FADD, FSUB, FMUL, FDIV, FREM, FNEG,
                 FCMPL, FCMPG -> TypeKind.FloatType;
            case DADD, DSUB, DMUL, DDIV, DREM, DNEG,
                 DCMPL, DCMPG -> TypeKind.DoubleType;
            default -> throw Util.badOpcodeKindException(operationOpcode, Opcode.Kind.OPERATOR);
        };
    }
}
