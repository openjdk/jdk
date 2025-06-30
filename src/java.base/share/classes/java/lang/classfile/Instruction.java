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

package java.lang.classfile;

import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.*;

import jdk.internal.classfile.impl.AbstractInstruction;

/**
 * Models an executable instruction in the {@code code} array of the {@link
 * CodeAttribute Code} attribute of a method.  The order of instructions in
 * a {@link CodeModel} is significant.
 * <p>
 * The {@link #opcode() opcode} identifies the operation of an instruction.
 * Each {@linkplain Opcode#kind() kind} of opcode has its own modeling interface
 * for instructions.
 *
 * @see Opcode
 * @jvms 6.5 Instructions
 * @sealedGraph
 * @since 24
 */
public sealed interface Instruction extends CodeElement
        permits ArrayLoadInstruction, ArrayStoreInstruction, BranchInstruction,
                ConstantInstruction, ConvertInstruction, DiscontinuedInstruction,
                FieldInstruction, InvokeDynamicInstruction, InvokeInstruction,
                LoadInstruction, StoreInstruction, IncrementInstruction,
                LookupSwitchInstruction, MonitorInstruction, NewMultiArrayInstruction,
                NewObjectInstruction, NewPrimitiveArrayInstruction, NewReferenceArrayInstruction,
                NopInstruction, OperatorInstruction, ReturnInstruction,
                StackInstruction, TableSwitchInstruction,
                ThrowInstruction, TypeCheckInstruction, AbstractInstruction {

    /**
     * {@return the operation of this instruction}
     */
    Opcode opcode();

    /**
     * {@return the size in bytes of this instruction}
     * This value is equal to {@link Opcode#sizeIfFixed()
     * opcode().sizeIfFixed()} if it is not {@code -1}.
     */
    int sizeInBytes();
}
