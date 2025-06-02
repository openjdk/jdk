/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.*;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.invoke.MethodHandleInfo;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static jdk.internal.classfile.impl.RawBytecodeHelper.*;

/**
 * Note: This class switches on opcode.bytecode for code size
 */
public class BytecodeHelpers {

    private BytecodeHelpers() {
    }

    public static IllegalArgumentException cannotConvertException(TypeKind from, TypeKind to) {
        return new IllegalArgumentException(String.format("convert %s -> %s", from, to));
    }

    public static IllegalArgumentException slotOutOfBounds(int slot) {
        return new IllegalArgumentException("Invalid slot index :".concat(Integer.toString(slot)));
    }

    public static IllegalArgumentException slotOutOfBounds(Opcode opcode, int slot) {
        return new IllegalArgumentException("Invalid slot index %d for %s".formatted(slot, opcode));
    }

    public static Opcode loadOpcode(TypeKind tk, int slot) {
        return switch (tk) {
            case INT, SHORT, BYTE, CHAR, BOOLEAN
                           -> iload(slot);
            case LONG      -> lload(slot);
            case DOUBLE    -> dload(slot);
            case FLOAT     -> fload(slot);
            case REFERENCE -> aload(slot);
            case VOID      -> throw new IllegalArgumentException("void");
        };
    }

    public static Opcode aload(int slot) {
        return switch (slot) {
            case 0 -> Opcode.ALOAD_0;
            case 1 -> Opcode.ALOAD_1;
            case 2 -> Opcode.ALOAD_2;
            case 3 -> Opcode.ALOAD_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.ALOAD;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.ALOAD_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode fload(int slot) {
        return switch (slot) {
            case 0 -> Opcode.FLOAD_0;
            case 1 -> Opcode.FLOAD_1;
            case 2 -> Opcode.FLOAD_2;
            case 3 -> Opcode.FLOAD_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.FLOAD;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.FLOAD_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode dload(int slot) {
        return switch (slot) {
            case 0 -> Opcode.DLOAD_0;
            case 1 -> Opcode.DLOAD_1;
            case 2 -> Opcode.DLOAD_2;
            case 3 -> Opcode.DLOAD_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.DLOAD;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.DLOAD_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode lload(int slot) {
        return switch (slot) {
            case 0 -> Opcode.LLOAD_0;
            case 1 -> Opcode.LLOAD_1;
            case 2 -> Opcode.LLOAD_2;
            case 3 -> Opcode.LLOAD_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.LLOAD;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.LLOAD_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode iload(int slot) {
        return switch (slot) {
            case 0 -> Opcode.ILOAD_0;
            case 1 -> Opcode.ILOAD_1;
            case 2 -> Opcode.ILOAD_2;
            case 3 -> Opcode.ILOAD_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.ILOAD;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.ILOAD_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode storeOpcode(TypeKind tk, int slot) {
        return switch (tk) {
            case INT, SHORT, BYTE, CHAR, BOOLEAN
                           -> istore(slot);
            case LONG      -> lstore(slot);
            case DOUBLE    -> dstore(slot);
            case FLOAT     -> fstore(slot);
            case REFERENCE -> astore(slot);
            case VOID      -> throw new IllegalArgumentException("void");
        };
    }

    public static Opcode astore(int slot) {
        return switch (slot) {
            case 0 -> Opcode.ASTORE_0;
            case 1 -> Opcode.ASTORE_1;
            case 2 -> Opcode.ASTORE_2;
            case 3 -> Opcode.ASTORE_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.ASTORE;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.ASTORE_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode fstore(int slot) {
        return switch (slot) {
            case 0 -> Opcode.FSTORE_0;
            case 1 -> Opcode.FSTORE_1;
            case 2 -> Opcode.FSTORE_2;
            case 3 -> Opcode.FSTORE_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.FSTORE;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.FSTORE_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode dstore(int slot) {
        return switch (slot) {
            case 0 -> Opcode.DSTORE_0;
            case 1 -> Opcode.DSTORE_1;
            case 2 -> Opcode.DSTORE_2;
            case 3 -> Opcode.DSTORE_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.DSTORE;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.DSTORE_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode lstore(int slot) {
        return switch (slot) {
            case 0 -> Opcode.LSTORE_0;
            case 1 -> Opcode.LSTORE_1;
            case 2 -> Opcode.LSTORE_2;
            case 3 -> Opcode.LSTORE_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.LSTORE;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.LSTORE_W;
                throw slotOutOfBounds(slot);
            }
        };
    }

    public static Opcode istore(int slot) {
        return switch (slot) {
            case 0 -> Opcode.ISTORE_0;
            case 1 -> Opcode.ISTORE_1;
            case 2 -> Opcode.ISTORE_2;
            case 3 -> Opcode.ISTORE_3;
            default -> {
                if ((slot & ~0xFF) == 0)
                    yield Opcode.ISTORE;
                if ((slot & ~0xFFFF) == 0)
                    yield Opcode.ISTORE_W;
                throw slotOutOfBounds(slot);
            }
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

    public static int returnBytecode(TypeKind tk) {
        int kind = Math.max(0, tk.ordinal() - 4); // BYTE, SHORT, CHAR, BOOLEAN becomes INT
        return IRETURN + kind;
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

    public static int arrayLoadBytecode(TypeKind tk) {
        return switch (tk) {
            case BYTE, BOOLEAN -> BALOAD;
            case SHORT -> SALOAD;
            case INT -> IALOAD;
            case FLOAT -> FALOAD;
            case LONG -> LALOAD;
            case DOUBLE -> DALOAD;
            case REFERENCE -> AALOAD;
            case CHAR -> CALOAD;
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

    public static int arrayStoreBytecode(TypeKind tk) {
        return switch (tk) {
            case BYTE, BOOLEAN -> BASTORE;
            case SHORT -> SASTORE;
            case INT -> IASTORE;
            case FLOAT -> FASTORE;
            case LONG -> LASTORE;
            case DOUBLE -> DASTORE;
            case REFERENCE -> AASTORE;
            case CHAR -> CASTORE;
            case VOID -> throw new IllegalArgumentException("void not an allowable array type");
        };
    }

    public static Opcode reverseBranchOpcode(Opcode op) {
        return switch (op) {
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

    public static int reverseBranchOpcode(int bytecode) {
        return switch (bytecode) {
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
            default -> throw new IllegalArgumentException(
                    String.format("Wrong opcode kind specified; found %d, expected %s", bytecode, Opcode.Kind.BRANCH));
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
        return switch (opcode) {
            case I2D, I2F, I2L, I2B, I2C, I2S -> TypeKind.INT;
            case L2D, L2F, L2I -> TypeKind.LONG;
            case F2D, F2I, F2L -> TypeKind.FLOAT;
            case D2F, D2I, D2L -> TypeKind.DOUBLE;
            default -> throw Util.badOpcodeKindException(opcode, Opcode.Kind.CONVERT);
        };
    }

    public static TypeKind convertToType(Opcode opcode) {
        return switch (opcode) {
            case I2B -> TypeKind.BYTE;
            case I2C -> TypeKind.CHAR;
            case I2S -> TypeKind.SHORT;
            case L2I, F2I, D2I -> TypeKind.INT;
            case I2L, F2L, D2L -> TypeKind.LONG;
            case I2F, L2F, D2F -> TypeKind.FLOAT;
            case I2D, L2D, F2D -> TypeKind.DOUBLE;
            default -> throw Util.badOpcodeKindException(opcode, Opcode.Kind.CONVERT);
        };
    }

    public static void validateSlot(Opcode opcode, int slot, boolean load) {
        int size = opcode.sizeIfFixed();
        if (size == 1 && slot == (load ? intrinsicLoadSlot(opcode) : intrinsicStoreSlot(opcode)) ||
                size == 2 && (slot & ~0xFF) == 0 ||
                size == 4 && (slot & ~0xFFFF) == 0)
            return;
        throw slotOutOfBounds(opcode, slot);
    }

    public static void validateSlot(int slot) {
        if ((slot & ~0xFFFF) != 0)
            throw slotOutOfBounds(slot);
    }

    public static boolean validateAndIsWideIinc(int slot, int val) {
        var ret = false;
        if ((slot & ~0xFF) != 0) {
            validateSlot(slot);
            ret = true;
        }
        if ((byte) val != val) {
            if ((short) val != val) {
                throw new IllegalArgumentException("cannot encode as S2: ".concat(String.valueOf(val)));
            }
            ret = true;
        }
        return ret;
    }

    public static void validateRet(Opcode opcode, int slot) {
        if (opcode == Opcode.RET && (slot & ~0xFF) == 0 ||
                opcode == Opcode.RET_W && (slot & ~0xFFFF) == 0)
            return;
        requireNonNull(opcode);
        throw slotOutOfBounds(opcode, slot);
    }

    public static void validateMultiArrayDimensions(int value) {
        if (value < 1 || value > 0xFF)
            throw new IllegalArgumentException("Not a valid array dimension: ".concat(String.valueOf(value)));
    }

    public static void validateSipush(int value) {
        if (value != (short) value)
            throw new IllegalArgumentException(
                    "SIPUSH: value must be within: Short.MIN_VALUE <= value <= Short.MAX_VALUE, found: "
                            .concat(Long.toString(value)));
    }

    public static void validateBipush(int value) {
        if (value != (byte) value)
            throw new IllegalArgumentException(
                    "BIPUSH: value must be within: Byte.MIN_VALUE <= value <= Byte.MAX_VALUE, found: "
                            .concat(Long.toString(value)));
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

    public static Opcode ldcOpcode(LoadableConstantEntry entry) {
        return entry.typeKind().slotSize() == 2 ? Opcode.LDC2_W
                : entry.index() > 0xff ? Opcode.LDC_W
                : Opcode.LDC;
    }

    public static ConstantDesc intrinsicConstantValue(Opcode opcode) {
        return switch (opcode) {
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
        return switch (opcode) {
            case ACONST_NULL -> TypeKind.REFERENCE;
            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 -> TypeKind.INT;
            case LCONST_0, LCONST_1 -> TypeKind.LONG;
            case FCONST_0, FCONST_1, FCONST_2 -> TypeKind.FLOAT;
            case DCONST_0, DCONST_1 -> TypeKind.DOUBLE;
            default -> throw Util.badOpcodeKindException(opcode, Opcode.Kind.CONSTANT);
        };
    }

    public static boolean isUnconditionalBranch(Opcode opcode) {
        return switch (opcode) {
            case GOTO, ATHROW, GOTO_W, LOOKUPSWITCH, TABLESWITCH -> true;
            default -> opcode.kind() == Opcode.Kind.RETURN;
        };
    }

    // Must check Opcode.sizeIfFixed() == 1 before call!
    public static int intrinsicLoadSlot(Opcode loadOpcode) {
        return switch (loadOpcode) {
            case ILOAD_0, LLOAD_0, FLOAD_0, DLOAD_0, ALOAD_0 -> 0;
            case ILOAD_1, LLOAD_1, FLOAD_1, DLOAD_1, ALOAD_1 -> 1;
            case ILOAD_2, LLOAD_2, FLOAD_2, DLOAD_2, ALOAD_2 -> 2;
            case ILOAD_3, LLOAD_3, FLOAD_3, DLOAD_3, ALOAD_3 -> 3;
            default -> throw Util.badOpcodeKindException(loadOpcode, Opcode.Kind.LOAD);
        };
    }

    // Must check Opcode.sizeIfFixed() == 1 before call!
    public static int intrinsicStoreSlot(Opcode storeOpcode) {
        return switch (storeOpcode) {
            case ISTORE_0, LSTORE_0, FSTORE_0, DSTORE_0, ASTORE_0 -> 0;
            case ISTORE_1, LSTORE_1, FSTORE_1, DSTORE_1, ASTORE_1 -> 1;
            case ISTORE_2, LSTORE_2, FSTORE_2, DSTORE_2, ASTORE_2 -> 2;
            case ISTORE_3, LSTORE_3, FSTORE_3, DSTORE_3, ASTORE_3 -> 3;
            default -> throw Util.badOpcodeKindException(storeOpcode, Opcode.Kind.STORE);
        };
    }

    public static TypeKind loadType(Opcode loadOpcode) {
        // Note: 0xFF handles wide pseudo-opcodes
        return switch (loadOpcode.bytecode() & 0xFF) {
            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 -> TypeKind.INT;
            case LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3 -> TypeKind.LONG;
            case FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3 -> TypeKind.FLOAT;
            case DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3 -> TypeKind.DOUBLE;
            case ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 -> TypeKind.REFERENCE;
            default -> throw Util.badOpcodeKindException(loadOpcode, Opcode.Kind.LOAD);
        };
    }

    public static TypeKind storeType(Opcode storeOpcode) {
        // Note: 0xFF handles wide pseudo-opcodes
        return switch (storeOpcode.bytecode() & 0xFF) {
            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> TypeKind.INT;
            case LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3 -> TypeKind.LONG;
            case FSTORE, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3 -> TypeKind.FLOAT;
            case DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3 -> TypeKind.DOUBLE;
            case ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> TypeKind.REFERENCE;
            default -> throw Util.badOpcodeKindException(storeOpcode, Opcode.Kind.STORE);
        };
    }

    public static TypeKind arrayLoadType(Opcode arrayLoadOpcode) {
        return switch (arrayLoadOpcode) {
            case IALOAD -> TypeKind.INT;
            case LALOAD -> TypeKind.LONG;
            case FALOAD -> TypeKind.FLOAT;
            case DALOAD -> TypeKind.DOUBLE;
            case AALOAD -> TypeKind.REFERENCE;
            case BALOAD -> TypeKind.BYTE;
            case CALOAD -> TypeKind.CHAR;
            case SALOAD -> TypeKind.SHORT;
            default -> throw Util.badOpcodeKindException(arrayLoadOpcode, Opcode.Kind.ARRAY_LOAD);
        };
    }

    public static TypeKind arrayStoreType(Opcode arrayStoreOpcode) {
        return switch (arrayStoreOpcode) {
            case IASTORE -> TypeKind.INT;
            case LASTORE -> TypeKind.LONG;
            case FASTORE -> TypeKind.FLOAT;
            case DASTORE -> TypeKind.DOUBLE;
            case AASTORE -> TypeKind.REFERENCE;
            case BASTORE -> TypeKind.BYTE;
            case CASTORE -> TypeKind.CHAR;
            case SASTORE -> TypeKind.SHORT;
            default -> throw Util.badOpcodeKindException(arrayStoreOpcode, Opcode.Kind.ARRAY_STORE);
        };
    }

    public static TypeKind returnType(Opcode returnOpcode) {
        return switch (returnOpcode) {
            case IRETURN -> TypeKind.INT;
            case LRETURN -> TypeKind.LONG;
            case FRETURN -> TypeKind.FLOAT;
            case DRETURN -> TypeKind.DOUBLE;
            case ARETURN -> TypeKind.REFERENCE;
            case RETURN -> TypeKind.VOID;
            default -> throw Util.badOpcodeKindException(returnOpcode, Opcode.Kind.RETURN);
        };
    }

    public static TypeKind operatorOperandType(Opcode operationOpcode) {
        return switch (operationOpcode) {
            case IADD, ISUB, IMUL, IDIV, IREM, INEG,
                 ISHL, ISHR, IUSHR, IAND, IOR, IXOR,
                 ARRAYLENGTH -> TypeKind.INT;
            case LADD, LSUB, LMUL, LDIV, LREM, LNEG,
                 LSHL, LSHR, LUSHR, LAND, LOR, LXOR,
                 LCMP -> TypeKind.LONG;
            case FADD, FSUB, FMUL, FDIV, FREM, FNEG,
                 FCMPL, FCMPG -> TypeKind.FLOAT;
            case DADD, DSUB, DMUL, DDIV, DREM, DNEG,
                 DCMPL, DCMPG -> TypeKind.DOUBLE;
            default -> throw Util.badOpcodeKindException(operationOpcode, Opcode.Kind.OPERATOR);
        };
    }
}
