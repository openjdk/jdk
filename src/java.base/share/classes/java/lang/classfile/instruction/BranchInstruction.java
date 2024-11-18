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

import java.lang.classfile.*;

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.Util;

/**
 * Models a branching instruction (conditional or unconditional) in the {@code
 * code} array of a {@code Code} attribute.  Corresponding opcodes have a
 * {@linkplain Opcode#kind() kind} of {@link Opcode.Kind#BRANCH}.  Delivered as
 * a {@link CodeElement} when traversing the elements of a {@link CodeModel}.
 * <p>
 * Conceptually, a branch instruction is a record:
 * {@snippet lang=text :
 * // @link region substring="BranchInstruction" target="#of"
 * // @link substring="Opcode" target="#opcode()" :
 * BranchInstruction(Opcode, Label) // @link substring="Label" target="#target()"
 * // @end
 * }
 * where the {@code Opcode} is of the branch kind.
 * <p>
 * Physically, a branch instruction has the same structure; however, some types
 * of instructions use a {@code s2} to encode the target, which is insufficient
 * to encode targets with bci offsets less than {@code -32768} or greater than
 * {@code 32767}.  Such instructions have a {@linkplain Opcode#sizeIfFixed()
 * size} of {@code 3} bytes.
 * <p>
 * In such cases, if the {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS
 * FIX_SHORT_JUMPS} option is set, a {@link CodeBuilder} will convert this
 * instruction to other instructions to achieve the same effect.  Otherwise,
 * {@link ClassFile.ShortJumpsOption#FAIL_ON_SHORT_JUMPS FAIL_ON_SHORT_JUMPS}
 * option can ensure the physical accuracy of the generated {@code class} file
 * and fail if an exact representation is not possible.
 *
 * @see CodeBuilder#branch CodeBuilder::branch
 * @since 24
 */
public sealed interface BranchInstruction extends Instruction
        permits AbstractInstruction.BoundBranchInstruction,
                AbstractInstruction.UnboundBranchInstruction {
    /**
     * {@return the target of the branch}
     */
    Label target();

    /**
     * {@return a branch instruction}
     *
     * @param op the opcode for the specific type of branch instruction,
     *           which must be of kind {@link Opcode.Kind#BRANCH}
     * @param target the target of the branch
     * @throws IllegalArgumentException if the opcode kind is not
     *         {@link Opcode.Kind#BRANCH}
     */
    static BranchInstruction of(Opcode op, Label target) {
        Util.checkKind(op, Opcode.Kind.BRANCH);
        return new AbstractInstruction.UnboundBranchInstruction(op, target);
    }
}
