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
import java.lang.classfile.constantpool.ClassEntry;

import jdk.internal.classfile.impl.AbstractInstruction;

/**
 * Models a {@link Opcode#ANEWARRAY anewarray} instruction in the {@code code}
 * array of a {@code Code} attribute.  Delivered as a {@link CodeElement}
 * when traversing the elements of a {@link CodeModel}.
 * <p>
 * Conceptually, an {@code anewarray} instruction is a record:
 * {@snippet lang=text :
 * // @link substring="NewReferenceArrayInstruction" target="CodeBuilder#anewarray(ClassDesc)" :
 * NewReferenceArrayInstruction(ClassDesc) // @link substring="ClassDesc" target="#componentType"
 * }
 * where the {@code ClassDesc} is not primitive.
 * <p>
 * Physically, an {@code anewarray} instruction is a record:
 * {@snippet lang=text :
 * // @link substring="NewReferenceArrayInstruction" target="#of" :
 * NewReferenceArrayInstruction(Opcode.ANEWARRAY, ClassEntry) // @link substring="ClassEntry" target="#componentType"
 * }
 *
 * @see CodeBuilder#newarray CodeBuilder::anewarray
 * @jvms 6.5.anewarray <em>anewarray</em>
 * @since 24
 */
public sealed interface NewReferenceArrayInstruction extends Instruction
        permits AbstractInstruction.BoundNewReferenceArrayInstruction, AbstractInstruction.UnboundNewReferenceArrayInstruction {
    /**
     * {@return the component type of the array}
     *
     * @apiNote
     * A symbolic descriptor for the component type of the array is available
     * through {@link ClassEntry#asSymbol() componentType().asSymbol()}.
     */
    ClassEntry componentType();

    /**
     * {@return a new reference array instruction}
     *
     * @param componentType the component type of the array
     */
    static NewReferenceArrayInstruction of(ClassEntry componentType) {
        return new AbstractInstruction.UnboundNewReferenceArrayInstruction(componentType);
    }
}
