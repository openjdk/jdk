/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.BytecodeHelpers;
import jdk.internal.classfile.impl.Util;

/**
 * Models a local variable increment instruction in the {@code code} array of a
 * {@code Code} attribute.  Corresponding opcodes have a {@linkplain Opcode#kind()
 * kind} of {@link Opcode.Kind#INCREMENT}.  Delivered as a {@link CodeElement} when
 * traversing the elements of a {@link CodeModel}.
 * <p>
 * A local variable increment instruction is composite:
 * {@snippet lang=text :
 * // @link substring="IncrementInstruction" target="#of" :
 * IncrementInstruction(
 *     int slot, // @link substring="slot" target="#slot()"
 *     int constant // @link substring="constant" target="#constant()"
 * )
 * }
 * where
 * <ul>
 * <li>{@code slot} must be {@link java.lang.classfile##u2 u2}.
 * <li>{@code constant} must be within {@code [-32768, 32767]}.
 * </ul>
 *
 * @see Opcode.Kind#INCREMENT
 * @see CodeBuilder#iinc CodeBuilder::iinc
 * @jvms 6.5.iinc <em>iinc</em>
 * @since 24
 */
public sealed interface IncrementInstruction extends Instruction
        permits AbstractInstruction.BoundIncrementInstruction,
                AbstractInstruction.UnboundIncrementInstruction {
    /**
     * {@return the local variable slot to increment}
     */
    int slot();

    /**
     * {@return the value to increment by}
     */
    int constant();

    /**
     * {@return an increment instruction}
     * <ul>
     * <li>{@code slot} must be {@link java.lang.classfile##u2 u2}.
     * <li>{@code constant} must be within {@code [-32768, 32767]}.
     * </ul>
     *
     * @param slot the local variable slot to increment
     * @param constant the value to increment by
     * @throws IllegalArgumentException if {@code slot} or {@code constant} is out of range
     */
    static IncrementInstruction of(int slot, int constant) {
        var opcode = BytecodeHelpers.validateAndIsWideIinc(slot, constant) ? Opcode.IINC_W: Opcode.IINC;
        return new AbstractInstruction.UnboundIncrementInstruction(opcode, slot, constant);
    }

    /**
     * {@return an increment instruction}
     * <p>
     * {@code slot} must be {@link java.lang.classfile##u1 u1} and
     * {@code constant} must be within {@code [-128, 127]} for
     * {@link Opcode#IINC iinc}, or {@code slot} must be
     * {@link java.lang.classfile##u2 u2} and {@code constant} must be
     * within {@code [-32768, 32767]} for {@link Opcode#IINC_W wide iinc}.
     *
     * @apiNote
     * The explicit {@code op} argument allows creating {@code wide} or
     * regular increment instructions when {@code slot} and
     * {@code constant} can be encoded with more optimized
     * increment instructions.
     *
     * @param op the opcode for the specific type of increment instruction,
     *           which must be of kind {@link Opcode.Kind#INCREMENT}
     * @param slot the local variable slot to increment
     * @param constant the increment constant
     * @throws IllegalArgumentException if the opcode kind is not
     *         {@link Opcode.Kind#INCREMENT} or {@code slot} or
     *         {@code constant} is out of range
     * @since 27
     */
    static IncrementInstruction of(Opcode op, int slot, int constant) {
        Util.checkKind(op, Opcode.Kind.INCREMENT);
        BytecodeHelpers.validateIncrement(op, slot, constant);
        return new AbstractInstruction.UnboundIncrementInstruction(op, slot, constant);
    }
}
