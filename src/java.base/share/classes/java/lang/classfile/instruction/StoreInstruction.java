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
 * Models a local variable store instruction in the {@code code} array of a
 * {@code Code} attribute.  Corresponding opcodes have a {@linkplain Opcode#kind() kind} of
 * {@link Opcode.Kind#STORE}.  Delivered as a {@link CodeElement} when
 * traversing the elements of a {@link CodeModel}.
 * <p>
 * Conceptually, a local variable store instruction is a record:
 * {@snippet lang=text :
 * // @link region substring="StoreInstruction" target="#of(TypeKind, int)"
 * // @link substring="TypeKind" target="#typeKind" :
 * StoreInstruction(TypeKind, int slot) // @link substring="int slot" target="#slot"
 * // @end
 * }
 * where the {@code TypeKind} is {@linkplain TypeKind##computational-type
 * computational}.  Multiple instructions, such as {@code astore_0}, {@code
 * astore 0}, and {@code wide astore 0}, may match such a record, but they
 * are functionally equivalent.
 * <p>
 * Physically, store variable instructions are polymorphic, discriminated by
 * their opcode:
 * {@snippet lang=text :
 * StoreInstruction(Opcode) // @link substring="Opcode" target="#opcode"
 * // @link region substring="StoreInstruction" target="#of(Opcode, int)"
 * // @link substring="Opcode" target="#opcode" :
 * StoreInstruction(Opcode, int slot) // @link substring="int slot" target="#slot"
 * // @end
 * }
 * the first form requires the {@code slot} to be intrinsic to the {@code Opcode};
 * such opcodes have an {@linkplain Opcode#sizeIfFixed() instruction size} of {@code 1}.
 * Otherwise, the {@code slot} must be compatible with the {@code Opcode}, such
 * that if the opcode is not {@linkplain Opcode#isWide() wide}, the {@code slot}
 * must be no greater than {@code 255}.
 * <p>
 * {@code astore} series of instructions can operate on the {@code returnAddress}
 * type from {@linkplain DiscontinuedInstruction.JsrInstruction jump subroutine
 * instructions}.
 *
 * @see CodeBuilder#storeLocal CodeBuilder::storeLocal
 * @since 24
 */
public sealed interface StoreInstruction extends Instruction
        permits AbstractInstruction.BoundStoreInstruction, AbstractInstruction.UnboundStoreInstruction {

    /**
     * {@return the local variable slot to store to}
     */
    int slot();

    /**
     * {@return the {@linkplain TypeKind##computational-type computational type}
     * of the value to be stored}  The {@link TypeKind#REFERENCE reference}
     * type store instructions also operate on the {@code returnAddress} type,
     * which does not apply to {@code reference} type load instructions.
     */
    TypeKind typeKind();

    /**
     * {@return a local variable store instruction}
     *
     * @param kind the type of the value to be stored
     * @param slot the local variable slot to store to
     * @throws IllegalArgumentException if {@code kind} is {@link
     *         TypeKind#VOID void} or {@code slot} is out of range
     */
    static StoreInstruction of(TypeKind kind, int slot) {
        var opcode = BytecodeHelpers.storeOpcode(kind, slot); // validates slot
        return new AbstractInstruction.UnboundStoreInstruction(opcode, slot);
    }

    /**
     * {@return a local variable store instruction}
     *
     * @apiNote
     * The explicit {@code op} argument allows creating {@code wide} or
     * regular store instructions when the {@code slot} can be encoded
     * with more optimized store instructions.
     *
     * @param op the opcode for the specific type of store instruction,
     *           which must be of kind {@link Opcode.Kind#STORE}
     * @param slot the local variable slot to store to
     * @throws IllegalArgumentException if the opcode kind is not
     *         {@link Opcode.Kind#STORE} or {@code slot} is out of range
     */
    static StoreInstruction of(Opcode op, int slot) {
        Util.checkKind(op, Opcode.Kind.STORE);
        BytecodeHelpers.validateSlot(op, slot, false);
        return new AbstractInstruction.UnboundStoreInstruction(op, slot);
    }
}
