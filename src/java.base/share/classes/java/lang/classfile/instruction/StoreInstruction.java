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
package java.lang.classfile.instruction;

import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.BytecodeHelpers;
import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a local variable store instruction in the {@code code} array of a
 * {@code Code} attribute.  Corresponding opcodes will have a {@code kind} of
 * {@link Opcode.Kind#STORE}.  Delivered as a {@link CodeElement} when
 * traversing the elements of a {@link CodeModel}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface StoreInstruction extends Instruction
        permits AbstractInstruction.BoundStoreInstruction, AbstractInstruction.UnboundStoreInstruction {

    /**
     * {@return the local variable slot to store to}
     */
    int slot();

    /**
     * {@return the type of the value to be stored}
     */
    TypeKind typeKind();

    /**
     * {@return a local variable store instruction}
     *
     * @param kind the type of the value to be stored
     * @param slot the local variable slot to store to
     */
    static StoreInstruction of(TypeKind kind, int slot) {
        return of(BytecodeHelpers.storeOpcode(kind, slot), slot);
    }

    /**
     * {@return a local variable store instruction}
     *
     * @param op the opcode for the specific type of store instruction,
     *           which must be of kind {@link Opcode.Kind#STORE}
     * @param slot the local variable slot to store to
     * @throws IllegalArgumentException if the opcode kind is not
     *         {@link Opcode.Kind#STORE}.
     */
    static StoreInstruction of(Opcode op, int slot) {
        Util.checkKind(op, Opcode.Kind.STORE);
        return new AbstractInstruction.UnboundStoreInstruction(op, slot);
    }
}
