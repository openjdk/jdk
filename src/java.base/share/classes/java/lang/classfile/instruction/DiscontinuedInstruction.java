/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.StackMapTableAttribute;

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.BytecodeHelpers;
import jdk.internal.classfile.impl.Util;

/**
 * Marker interface for instruction discontinued from the {@code code} array of
 * a {@code Code} attribute.  Delivered as a {@link CodeElement} when traversing
 * the elements of a {@link CodeModel}.
 *
 * @apiNote
 * While most instructions have convenience factory methods in {@link
 * CodeBuilder}, discontinued instructions can only be supplied to code builders
 * explicitly with {@link CodeBuilder#with CodeBuilder::with} to discourage
 * their use.
 *
 * @jvms 4.9.1 Static Constraints
 * @sealedGraph
 * @since 24
 */
public sealed interface DiscontinuedInstruction extends Instruction {

    /**
     * Models jump subroutine instructions discontinued from the {@code code}
     * array of a {@code Code} attribute since class file major version {@value
     * ClassFile#JAVA_7_VERSION} (JVMS {@jvms 4.9.1}).  Corresponding opcodes
     * have a {@linkplain Opcode#kind() kind} of {@link Opcode.Kind#DISCONTINUED_JSR}.
     * Delivered as a {@link CodeElement} when traversing the elements of a
     * {@link CodeModel}.
     * <p>
     * A jump subroutine instruction is composite:
     * {@snippet lang=text :
     * // @link substring="JsrInstruction" target="#of(Label)" :
     * JsrInstruction(Label target) // @link substring="target" target="#target()"
     * }
     * <p>
     * Due to physical restrictions, {@link Opcode#JSR jsr} instructions cannot
     * encode labels too far away in the list of code elements.  In such cases,
     * the {@link ClassFile.ShortJumpsOption} controls how an invalid {@code jsr}
     * instruction model is written by a {@link CodeBuilder}.
     * <p>
     * Jump subroutine instructions push a {@link TypeKind##returnAddress
     * returnAddress} value to the operand stack, and {@link StoreInstruction
     * astore} series of instructions can then store this value to a local
     * variable slot.
     *
     * @apiNote
     * Jump subroutine instructions are discontinued to enforce verification by
     * type checking (JVMS {@jvms 4.10.1}) using the {@link StackMapTableAttribute
     * StackMapTable} attribute.
     *
     * @see Opcode.Kind#DISCONTINUED_JSR
     * @see StackMapTableAttribute
     * @since 24
     */
    sealed interface JsrInstruction extends DiscontinuedInstruction
            permits AbstractInstruction.BoundJsrInstruction,
                    AbstractInstruction.UnboundJsrInstruction {

        /**
         * {@return the target of the jump subroutine instruction}
         */
        Label target();

        /**
         * {@return a jump subroutine instruction}
         *
         * @apiNote
         * The explicit {@code op} argument allows creating {@link Opcode#JSR_W
         * jsr_w} instructions to avoid short jumps.
         *
         * @param op the opcode for the specific type of jump subroutine instruction,
         *           which must be of kind {@link Opcode.Kind#DISCONTINUED_JSR}
         * @param target target label of the subroutine
         * @throws IllegalArgumentException if the opcode kind is not
         *         {@link Opcode.Kind#DISCONTINUED_JSR}.
         */
        static JsrInstruction of(Opcode op, Label target) {
            Util.checkKind(op, Opcode.Kind.DISCONTINUED_JSR);
            return new AbstractInstruction.UnboundJsrInstruction(op, target);
        }

        /**
         * {@return a jump subroutine instruction}
         *
         * @param target target label of the subroutine
         */
        static JsrInstruction of(Label target) {
            return of(Opcode.JSR, target);
        }
    }

    /**
     * Models return from subroutine instructions discontinued from the {@code
     * code} array of a {@code Code} attribute since class file major version
     * {@value ClassFile#JAVA_7_VERSION} (JVMS {@jvms 4.9.1}).
     * Corresponding opcodes have a {@linkplain Opcode#kind() kind} of
     * {@link Opcode.Kind#DISCONTINUED_RET}.  Delivered as a {@link CodeElement}
     * when traversing the elements of a {@link CodeModel}.
     * <p>
     * A return from subroutine instruction is composite:
     * {@snippet lang=text :
     * // @link substring="RetInstruction" target="#of(int)" :
     * RetInstruction(int slot) // @link substring="slot" target="#slot()"
     * }
     * where {@code slot} must be within {@code [0, 65535]}.
     * <p>
     * {@link StoreInstruction astore} series of instructions store a {@link
     * TypeKind##returnAddress returnAddress} value to a local variable slot,
     * making the slot usable by a return from subroutine instruction.
     *
     * @apiNote
     * Return from subroutine instructions are discontinued to enforce
     * verification by type checking (JVMS {@jvms 4.10.1}) using the {@link
     * StackMapTableAttribute StackMapTable} attribute.
     *
     * @jvms 6.5.ret <em>ret</em>
     * @see Opcode.Kind#DISCONTINUED_RET
     * @see StackMapTableAttribute
     * @since 24
     */
    sealed interface RetInstruction extends DiscontinuedInstruction
            permits AbstractInstruction.BoundRetInstruction,
                    AbstractInstruction.UnboundRetInstruction {

        /**
         * {@return the local variable slot with return address}
         * The value is within {@code [0, 65535]}.
         */
        int slot();

        /**
         * {@return a return from subroutine instruction}
         * <p>
         * {@code slot} must be in the closed range of {@code [0, 255]} for
         * {@link Opcode#RET ret}, or within {@code [0, 65535]} for {@link
         * Opcode#RET_W wide ret}.
         *
         * @apiNote
         * The explicit {@code op} argument allows creating {@code wide ret}
         * instructions with {@code slot} in the range of regular {@code ret}
         * instructions.
         *
         * @param op the opcode for the specific type of return from subroutine instruction,
         *           which must be of kind {@link Opcode.Kind#DISCONTINUED_RET}
         * @param slot the local variable slot to load return address from
         * @throws IllegalArgumentException if the opcode kind is not
         *         {@link Opcode.Kind#DISCONTINUED_RET} or if {@code slot} is out of range
         */
        static RetInstruction of(Opcode op, int slot) {
            BytecodeHelpers.validateRet(op, slot);
            return new AbstractInstruction.UnboundRetInstruction(op, slot);
        }

        /**
         * {@return a return from subroutine instruction}
         * <p>
         * {@code slot} must be within {@code [0, 65535]}.
         *
         * @param slot the local variable slot to load return address from
         * @throws IllegalArgumentException if {@code slot} is out of range
         */
        static RetInstruction of(int slot) {
            return of(slot < 256 ? Opcode.RET : Opcode.RET_W, slot);
        }
    }
}
