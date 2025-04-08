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
import jdk.internal.classfile.impl.BytecodeHelpers;

/**
 * Models a {@link Opcode#MULTIANEWARRAY multianewarray} instruction in the {@code code}
 * array of a {@code Code} attribute.  Delivered as a {@link CodeElement}
 * when traversing the elements of a {@link CodeModel}.
 * <p>
 * A new multi-dimensional array instruction is composite:
 * {@snippet lang=text :
 * // @link substring="NewMultiArrayInstruction" target="#of" :
 * NewMultiArrayInstruction(
 *     ClassEntry arrayType, // @link substring="arrayType" target="#arrayType"
 *     int dimensions // @link substring="dimensions" target="#dimensions"
 * )
 * }
 * where the {@code arrayType} is an array class.
 *
 * @see Opcode.Kind#NEW_MULTI_ARRAY
 * @see CodeBuilder#multianewarray CodeBuilder::multianewarray
 * @jvms 6.5.multianewarray <em>multianewarray</em>
 * @since 24
 */
public sealed interface NewMultiArrayInstruction extends Instruction
        permits AbstractInstruction.BoundNewMultidimensionalArrayInstruction,
                AbstractInstruction.UnboundNewMultidimensionalArrayInstruction {

    /**
     * {@return the type of the array}
     */
    ClassEntry arrayType();

    /**
     * {@return the number of dimensions of the array}
     */
    int dimensions();

    /**
     * {@return a new multi-dimensional array instruction}
     *
     * @param arrayTypeEntry the type of the array
     * @param dimensions the number of dimensions of the array
     * @throws IllegalArgumentException if {@code dimensions} is out of range
     */
    static NewMultiArrayInstruction of(ClassEntry arrayTypeEntry,
                                       int dimensions) {
        BytecodeHelpers.validateMultiArrayDimensions(dimensions);
        return new AbstractInstruction.UnboundNewMultidimensionalArrayInstruction(arrayTypeEntry, dimensions);
    }
}
