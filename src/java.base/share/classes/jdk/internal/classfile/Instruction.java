/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.classfile;

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.instruction.ArrayLoadInstruction;
import jdk.internal.classfile.instruction.ArrayStoreInstruction;
import jdk.internal.classfile.instruction.BranchInstruction;
import jdk.internal.classfile.instruction.ConstantInstruction;
import jdk.internal.classfile.instruction.ConvertInstruction;
import jdk.internal.classfile.instruction.DiscontinuedInstruction;
import jdk.internal.classfile.instruction.FieldInstruction;
import jdk.internal.classfile.instruction.IncrementInstruction;
import jdk.internal.classfile.instruction.InvokeDynamicInstruction;
import jdk.internal.classfile.instruction.InvokeInstruction;
import jdk.internal.classfile.instruction.LoadInstruction;
import jdk.internal.classfile.instruction.LookupSwitchInstruction;
import jdk.internal.classfile.instruction.MonitorInstruction;
import jdk.internal.classfile.instruction.NewMultiArrayInstruction;
import jdk.internal.classfile.instruction.NewObjectInstruction;
import jdk.internal.classfile.instruction.NewPrimitiveArrayInstruction;
import jdk.internal.classfile.instruction.NewReferenceArrayInstruction;
import jdk.internal.classfile.instruction.NopInstruction;
import jdk.internal.classfile.instruction.OperatorInstruction;
import jdk.internal.classfile.instruction.ReturnInstruction;
import jdk.internal.classfile.instruction.StackInstruction;
import jdk.internal.classfile.instruction.StoreInstruction;
import jdk.internal.classfile.instruction.TableSwitchInstruction;
import jdk.internal.classfile.instruction.ThrowInstruction;
import jdk.internal.classfile.instruction.TypeCheckInstruction;

/**
 * Models an executable instruction in a method body.
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
     * {@return the opcode of this instruction}
     */
    Opcode opcode();

    /**
     * {@return the size in bytes of this instruction}
     */
    int sizeInBytes();
}
