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
package java.lang.classfile.instruction;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.constant.ConstantDesc;

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.BytecodeHelpers;
import jdk.internal.classfile.impl.Util;

/**
 * Models a constant-load instruction in the {@code code} array of a {@code
 * Code} attribute, including {@linkplain IntrinsicConstantInstruction
 * "intrinsic"}, {@linkplain ArgumentConstantInstruction "argument"}, and
 * {@linkplain LoadConstantInstruction "load"} constant instructions.
 * Corresponding opcodes have a {@linkplain Opcode#kind() kind} of {@link
 * Opcode.Kind#CONSTANT}.  Delivered as a {@link CodeElement} when traversing
 * the elements of a {@link CodeModel}.
 * <p>
 * Conceptually, a constant-load instruction is a record:
 * {@snippet lang=text :
 * // @link substring="ConstantInstruction" target="CodeBuilder#loadConstant(ConstantDesc)" :
 * ConstantInstruction(ConstantDesc) // @link substring="ConstantDesc" target="#constantValue()"
 * }
 * The {@linkplain #typeKind() computational type} is derived from the {@code
 * ConstantDesc}.
 * <p>
 * Physically, a constant-load instruction is polymorphic; nested interfaces in
 * this interface model different constant instructions as records.
 *
 * @see CodeBuilder#loadConstant(ConstantDesc) CodeBuilder::loadConstant
 * @sealedGraph
 * @since 24
 */
public sealed interface ConstantInstruction extends Instruction {

    /**
     * {@return the constant value}
     */
    ConstantDesc constantValue();

    /**
     * {@return the {@linkplain TypeKind##computational-type computational type} of the constant}
     */
    TypeKind typeKind();

    /**
     * Models an "intrinsic constant" instruction, which encodes
     * the constant value in its opcode. Examples include {@link
     * Opcode#ACONST_NULL aconst_null} and {@link
     * Opcode#ICONST_0 iconst_0}.
     * <p>
     * Conceptually, an intrinsic constant instruction is a record:
     * {@snippet lang=text :
     * // @link substring="IntrinsicConstantInstruction" target="#ofIntrinsic" :
     * IntrinsicConstantInstruction(Opcode) // @link substring="Opcode" target="#opcode()"
     * }
     * where the {@code Opcode} is of the constant kind and has a fixed size
     * of 1.  The constant value and the computational type are intrinsic to
     * the {@code Opcode}.
     * <p>
     * Physically, an intrinsic constant instruction is the same as its
     * conceptual representation.
     *
     * @since 24
     */
    sealed interface IntrinsicConstantInstruction extends ConstantInstruction
            permits AbstractInstruction.UnboundIntrinsicConstantInstruction {

        @Override
        default TypeKind typeKind() {
            return BytecodeHelpers.intrinsicConstantType(opcode());
        }
    }

    /**
     * Models an "argument constant" instruction, which encodes the
     * constant value in the instruction directly. Includes {@link
     * Opcode#BIPUSH bipush} and {@link Opcode#SIPUSH sipush} instructions.
     * <p>
     * Physically, an argument constant instruction is a record:
     * {@snippet lang=text :
     * // @link region substring="ArgumentConstantInstruction" target="#ofArgument"
     * // @link substring="Opcode" target="#opcode()" :
     * ArgumentConstantInstruction(Opcode, int) // @link substring="int" target="#constantValue()"
     * // @end
     * }
     * where the {@code Opcode} is either {@code bipush} or {@code sipush}.
     * The {@code int} must be in the range of {@code byte} for {@code bipush},
     * and in the range of {@code short} for {@code sipush}.  The {@linkplain
     * #typeKind() computational type} is trivially {@code int}.
     *
     * @see CodeBuilder#loadConstant(int) CodeBuilder::loadConstant(int)
     * @see CodeBuilder#bipush CodeBuilder::bipush
     * @see CodeBuilder#sipush CodeBuilder::sipush
     * @since 24
     */
    sealed interface ArgumentConstantInstruction extends ConstantInstruction
            permits AbstractInstruction.BoundArgumentConstantInstruction,
                    AbstractInstruction.UnboundArgumentConstantInstruction {

        @Override
        Integer constantValue();

        @Override
        default TypeKind typeKind() {
            return TypeKind.INT;
        }
    }

    /**
     * Models a "load constant" instruction, which encodes the
     * constant value in the constant pool.  Includes {@link
     * Opcode#LDC ldc} and {@link Opcode#LDC_W ldc_w}, and
     * {@link Opcode#LDC2_W ldc2_w} instructions.
     * <p>
     * Conceptually, besides the more generic record for all constant-load
     * instructions, a load constant instruction are these records:
     * {@snippet lang=text :
     * // @link substring="LoadConstantInstruction" target="CodeBuilder#ldc(ConstantDesc)" :
     * LoadConstantInstruction(ConstantDesc) // @link substring="ConstantDesc" target="#constantValue()"
     * // @link substring="LoadConstantInstruction" target="CodeBuilder#ldc(LoadableConstantEntry)" :
     * LoadConstantInstruction(LoadableConstantEntry) // @link substring="LoadableConstantEntry" target="#constantEntry()"
     * }
     * <p>
     * Note that though both the generic constant-load record and the "load
     * constant" record holds a {@code ConstantDesc}, the physical representation
     * for the generic instruction may be more optimized, avoiding extra constant
     * pool entries and using smaller instructions.
     * <p>
     * Physically, a load constant instruction is a record:
     * {@snippet lang=text :
     * // @link region substring="LoadConstantInstruction" target="#ofLoad"
     * // @link substring="Opcode" target="#opcode()" :
     * LoadConstantInstruction(Opcode, LoadableConstantEntry) // @link substring="LoadableConstantEntry" target="#constantEntry()"
     * // @end
     * }
     * where the {@code Opcode} must be {@code ldc} or {@code ldc_w} if the
     * entry is not of {@code long} or {@code double} types, or it must be
     * {@code ldc2_w} if the entry is of {@code long} or {@code double} type.
     * Additionally, {@code ldc} cannot be used if the {@linkplain
     * LoadableConstantEntry#index() index} of the {@code LoadableConstantEntry}
     * is not in the range of {@code [0, 255]}.
     *
     * @see CodeBuilder#ldc(LoadableConstantEntry) CodeBuilder::ldc
     * @since 24
     */
    sealed interface LoadConstantInstruction extends ConstantInstruction
            permits AbstractInstruction.BoundLoadConstantInstruction,
                    AbstractInstruction.UnboundLoadConstantInstruction {

        /**
         * {@return the constant value}
         */
        LoadableConstantEntry constantEntry();

        @Override
        default TypeKind typeKind() {
            return constantEntry().typeKind();
        }
    }

    /**
     * {@return an intrinsic constant instruction}
     *
     * @param op the opcode for the specific type of intrinsic constant instruction,
     *           which must be of kind {@link Opcode.Kind#CONSTANT}
     * @throws IllegalArgumentException if the opcode does not represent a constant
     *                                  with implicit value
     */
    static IntrinsicConstantInstruction ofIntrinsic(Opcode op) {
        Util.checkKind(op, Opcode.Kind.CONSTANT);
        if (op.sizeIfFixed() != 1)
            throw new IllegalArgumentException(String.format("Wrong opcode specified; found %s, expected xCONST_val", op));
        return new AbstractInstruction.UnboundIntrinsicConstantInstruction(op);
    }

    /**
     * {@return an argument constant instruction}
     *
     * @param op the opcode for the specific type of argument constant instruction,
     *           which must be {@link Opcode#BIPUSH} or {@link Opcode#SIPUSH}
     * @param value the constant value
     * @throws IllegalArgumentException if the opcode is not {@link Opcode#BIPUSH}
     *         or {@link Opcode#SIPUSH}, or if the constant value is out of range
     *         for the opcode
     */
    static ArgumentConstantInstruction ofArgument(Opcode op, int value) {
        if (op == Opcode.BIPUSH) {
            BytecodeHelpers.validateBipush(value);
        } else if (op == Opcode.SIPUSH) {
            BytecodeHelpers.validateSipush(value);
        } else {
            throw new IllegalArgumentException(String.format("Wrong opcode specified; found %s, expected BIPUSH or SIPUSH", op));
        }
        return new AbstractInstruction.UnboundArgumentConstantInstruction(op, value);
    }

    /**
     * {@return a load constant instruction}
     *
     * @param op the opcode for the specific type of load constant instruction,
     *           which must be of kind {@link Opcode.Kind#CONSTANT}
     * @param constant the constant value
     * @throws IllegalArgumentException if the opcode is not {@link Opcode#LDC},
     *                                  {@link Opcode#LDC_W}, or {@link Opcode#LDC2_W}
     */
    static LoadConstantInstruction ofLoad(Opcode op, LoadableConstantEntry constant) {
        Util.checkKind(op, Opcode.Kind.CONSTANT);
        if (op != Opcode.LDC && op != Opcode.LDC_W && op != Opcode.LDC2_W)
            throw new IllegalArgumentException(String.format("Wrong opcode specified; found %s, expected LDC, LDC_W or LDC2_W", op));
        return new AbstractInstruction.UnboundLoadConstantInstruction(op, constant);
    }
}
