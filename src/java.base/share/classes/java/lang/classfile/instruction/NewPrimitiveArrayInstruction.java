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
import java.lang.classfile.constantpool.ClassEntry;

import jdk.internal.classfile.impl.AbstractInstruction;

/**
 * Models a {@link Opcode#NEWARRAY newarray} instruction in the {@code code}
 * array of a {@code Code} attribute.  Delivered as a {@link CodeElement}
 * when traversing the elements of a {@link CodeModel}.
 * <p>
 * Conceptually, a {@code newarray} instruction is a record:
 * {@snippet lang=text :
 * // @link substring="NewPrimitiveArrayInstruction" target="#of" :
 * NewPrimitiveArrayInstruction(TypeKind) // @link substring="TypeKind" target="#typeKind"
 * }
 * where the {@code TypeKind} is primitive and not {@code void}.
 * <p>
 * Physically, a {@code newarray} instruction is a record:
 * {@snippet lang=text :
 * // @link substring="NewPrimitiveArrayInstruction" target="#of" :
 * NewPrimitiveArrayInstruction(Opcode.NEWARRAY, int code) // @link substring="int code" target="TypeKind#newarrayCode()"
 * }
 * where the code is a valid new array code.
 *
 * @see CodeBuilder#newarray CodeBuilder::newarray
 * @jvms 6.5.newarray <em>newarray</em>
 * @since 24
 */
public sealed interface NewPrimitiveArrayInstruction extends Instruction
        permits AbstractInstruction.BoundNewPrimitiveArrayInstruction,
                AbstractInstruction.UnboundNewPrimitiveArrayInstruction {
    /**
     * {@return the component type of the array}
     *
     * @apiNote
     * The backing array code for this instruction is available through
     * {@link TypeKind#newarrayCode() typeKind().newarrayCode()}.
     */
    TypeKind typeKind();

    /**
     * {@return a new primitive array instruction}
     *
     * @param typeKind the component type of the array
     * @throws IllegalArgumentException when the {@code typeKind} is not a legal
     *                                  primitive array component type
     */
    static NewPrimitiveArrayInstruction of(TypeKind typeKind) {
        // Implicit null-check:
        if (typeKind.newarrayCode() < 0) {
            throw new IllegalArgumentException("Illegal component type for primitive array: " + typeKind.name());
        }
        return new AbstractInstruction.UnboundNewPrimitiveArrayInstruction(typeKind);
    }
}
