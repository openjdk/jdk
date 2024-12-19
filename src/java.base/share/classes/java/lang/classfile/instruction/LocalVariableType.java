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
import java.lang.classfile.attribute.LocalVariableTypeInfo;
import java.lang.classfile.attribute.LocalVariableTypeTableAttribute;
import java.lang.classfile.constantpool.Utf8Entry;

import jdk.internal.classfile.impl.AbstractPseudoInstruction;
import jdk.internal.classfile.impl.BoundLocalVariableType;
import jdk.internal.classfile.impl.TemporaryConstantPool;

/**
 * A pseudo-instruction which models a single entry in the {@link
 * LocalVariableTypeTableAttribute LocalVariableTypeTable} attribute.  Delivered
 * as a {@link CodeElement} during traversal of the elements of a {@link CodeModel},
 * according to the setting of the {@link ClassFile.DebugElementsOption} option.
 * <p>
 * A local variable type entry is composite:
 * {@snippet lang=text :
 * // @link substring="LocalVariableType" target="#of(int, String, Signature, Label, Label)" :
 * LocalVariableType(
 *     int slot, // @link substring="slot" target="#slot"
 *     String name, // @link substring="name" target="#name"
 *     Signature signature, // @link substring="signature" target="#signatureSymbol"
 *     Label startScope, // @link substring="startScope" target="#startScope"
 *     Label endScope // @link substring="endScope" target="#endScope"
 * )
 * }
 * Where {@code slot} is within {@code [0, 65535]}.
 * <p>
 * Another model, {@link LocalVariableTypeInfo}, also models a local variable
 * type entry; it has no dependency on a {@code CodeModel} and represents of bci
 * values as {@code int}s instead of {@code Label}s, and is used as components
 * of a {@link LocalVariableTypeTableAttribute}.
 *
 * @apiNote
 * {@code LocalVariableType} is used if a local variable has a parameterized
 * type, a type argument, or an array type of one of the previous types as its
 * type.  A {@link LocalVariable} with the erased type should still be created
 * for that local variable.
 *
 * @see LocalVariableTypeInfo
 * @see CodeBuilder#localVariableType CodeBuilder::localVariableType
 * @see ClassFile.DebugElementsOption
 * @since 24
 */
public sealed interface LocalVariableType extends PseudoInstruction
        permits AbstractPseudoInstruction.UnboundLocalVariableType, BoundLocalVariableType {
    /**
     * {@return the local variable slot}
     * The value is within {@code [0, 65535]}.
     */
    int slot();

    /**
     * {@return the local variable name}
     */
    Utf8Entry name();

    /**
     * {@return the local variable generic signature string}
     *
     * @apiNote
     * A symbolic generic signature of the local variable is available
     * through {@link #signatureSymbol() signatureSymbol()}.
     */
    Utf8Entry signature();

    /**
     * {@return the local variable generic signature}
     */
    default Signature signatureSymbol() {
        return Signature.parseFrom(signature().stringValue());
    }

    /**
     * {@return the start range of the local variable scope}
     */
    Label startScope();

    /**
     * {@return the end range of the local variable scope}
     */
    Label endScope();

    /**
     * {@return a local variable type pseudo-instruction}
     * {@code slot} must be within {@code [0, 65535]}.
     *
     * @param slot the local variable slot
     * @param nameEntry the local variable name
     * @param signatureEntry the local variable signature
     * @param startScope the start range of the local variable scope
     * @param endScope the end range of the local variable scope
     * @throws IllegalArgumentException if {@code slot} is out of range
     */
    static LocalVariableType of(int slot, Utf8Entry nameEntry, Utf8Entry signatureEntry, Label startScope, Label endScope) {
        return new AbstractPseudoInstruction.UnboundLocalVariableType(slot, nameEntry, signatureEntry,
                                                                      startScope, endScope);
    }

    /**
     * {@return a local variable type pseudo-instruction}
     * {@code slot} must be within {@code [0, 65535]}.
     *
     * @param slot the local variable slot
     * @param name the local variable name
     * @param signature the local variable signature
     * @param startScope the start range of the local variable scope
     * @param endScope the end range of the local variable scope
     * @throws IllegalArgumentException if {@code slot} is out of range
     */
    static LocalVariableType of(int slot, String name, Signature signature, Label startScope, Label endScope) {
        return of(slot,
                  TemporaryConstantPool.INSTANCE.utf8Entry(name),
                  TemporaryConstantPool.INSTANCE.utf8Entry(signature.signatureString()),
                  startScope, endScope);
    }
}
