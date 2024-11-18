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
 * LocalVariableTypeTableAttribute}.  Delivered as a {@link CodeElement} during
 * traversal of the elements of a {@link CodeModel}, according to the setting of
 * the {@link ClassFile.DebugElementsOption} option.
 * <p>
 * Conceptually, a local variable type table entry is a record:
 * {@snippet lang=text :
 * // @link region=0 substring="LocalVariableType" target="#of(int, String, Signature, Label, Label)"
 * // @link region=1 substring="int slot" target="#slot"
 * // @link region=2 substring="String name" target="#name"
 * // @link region=3 substring="Signature signature" target="#signatureSymbol"
 * // @link substring="Label startScope" target="#startScope" :
 * LocalVariableType(int slot, String name, Signature signature, Label startScope, Label endScope) // @link substring="Label endScope" target="#endScope"
 * // @end region=0
 * // @end region=1
 * // @end region=2
 * // @end region=3
 * }
 * Where {@code signature} must be non-{@code void}.
 * <p>
 * Physically, a local variable type table entry modeled by a {@link LocalVariableTypeInfo}.
 * It is a record:
 * {@snippet lang=text :
 * // @link region=0 substring="LocalVariableType" target="#of(int, Utf8Entry, Utf8Entry, Label, Label)"
 * // @link region=1 substring="int slot" target="#slot"
 * // @link region=2 substring="Utf8Entry name" target="#name"
 * // @link region=3 substring="Utf8Entry signature" target="#signature"
 * // @link substring="Label startScope" target="#startScope" :
 * LocalVariableType(Label startScope, Label endScope, Utf8Entry name, Utf8Entry signature, int slot) // @link substring="Label endScope" target="#endScope"
 * // @end region=0
 * // @end region=1
 * // @end region=2
 * // @end region=3
 * }
 * Where the {@code endScope} is encoded as a nonnegative bci offset to
 * {@code startScope}, a bci value.
 *
 * @apiNote
 * Local variable type table entry is used if a local variable has a parameterized
 * type, a type argument, or an array type of one of the previous types as its type.
 * A local variable table entry with the erased type should still be created for
 * that local variable.
 *
 * @see LocalVariableTypeTableAttribute
 * @see LocalVariableTypeInfo
 * @see CodeBuilder#localVariableType CodeBuilder::localVariableType
 * @since 24
 */
public sealed interface LocalVariableType extends PseudoInstruction
        permits AbstractPseudoInstruction.UnboundLocalVariableType, BoundLocalVariableType {
    /**
     * {@return the local variable slot}
     */
    int slot();

    /**
     * {@return the local variable name}
     *
     * @apiNote
     * A string value for the name is available through {@link
     * Utf8Entry#stringValue() name().stringValue()}.
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
