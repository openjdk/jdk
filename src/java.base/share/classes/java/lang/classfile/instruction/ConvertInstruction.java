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

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.BytecodeHelpers;
import jdk.internal.classfile.impl.Util;

/**
 * Models a primitive conversion instruction in the {@code code} array of a
 * {@code Code} attribute, such as {@link Opcode#I2L i2l}.  Corresponding opcodes
 * have a {@linkplain Opcode#kind() kind} of {@link Opcode.Kind#CONVERT}.
 * Delivered as a {@link CodeElement} when traversing the elements of a {@link CodeModel}.
 * <p>
 * A primitive conversion instruction is composite:
 * {@snippet lang=text :
 * // @link substring="ConvertInstruction" target="#of(TypeKind, TypeKind)" :
 * ConvertInstruction(
 *     TypeKind fromType, // @link substring="fromType" target="#fromType"
 *     TypeKind toType // @link substring="toType" target="#toType"
 * )
 * }
 * where these conversions are valid:
 * <ul>
 * <li>Between {@code int}, {@code long}, {@code float}, and {@code double}, where
 * {@code fromType != toType};
 * <li>From {@code int} to {@code byte}, {@code char}, and {@code short}.
 * </ul>
 *
 * @see Opcode.Kind#CONVERT
 * @see CodeBuilder#conversion CodeBuilder::conversion
 * @since 24
 */
public sealed interface ConvertInstruction extends Instruction
        permits AbstractInstruction.UnboundConvertInstruction {
    /**
     * {@return the source type to convert from}
     */
    TypeKind fromType();

    /**
     * {@return the destination type to convert to}
     */
    TypeKind toType();

    /**
     * {@return a conversion instruction}  Valid conversions are:
     * <ul>
     * <li>Between {@code int}, {@code long}, {@code float}, and {@code double},
     * where {@code fromType != toType};
     * <li>From {@code int} to {@code byte}, {@code char}, and {@code short}.
     * </ul>
     *
     * @param fromType the type to convert from
     * @param toType the type to convert to
     * @throws IllegalArgumentException if this is not a valid conversion
     */
    static ConvertInstruction of(TypeKind fromType, TypeKind toType) {
        return of(BytecodeHelpers.convertOpcode(fromType, toType));
    }

    /**
     * {@return a conversion instruction}
     *
     * @param op the opcode for the specific type of conversion instruction,
     *           which must be of kind {@link Opcode.Kind#CONVERT}
     * @throws IllegalArgumentException if the opcode kind is not
     *         {@link Opcode.Kind#CONVERT}.
     */
    static ConvertInstruction of(Opcode op) {
        Util.checkKind(op, Opcode.Kind.CONVERT);
        return new AbstractInstruction.UnboundConvertInstruction(op);
    }
}
