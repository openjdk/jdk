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

import jdk.internal.classfile.impl.AbstractInstruction;

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
 * <li>{@code slot} must be within {@code [0, 65535]}.
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
     * <li>{@code slot} must be within {@code [0, 65535]}.
     * <li>{@code constant} must be within {@code [-32768, 32767]}.
     * </ul>
     *
     * @param slot the local variable slot to increment
     * @param constant the value to increment by
     * @throws IllegalArgumentException if {@code slot} or {@code constant} is out of range
     */
    static IncrementInstruction of(int slot, int constant) {
        return new AbstractInstruction.UnboundIncrementInstruction(slot, constant);
    }
}
