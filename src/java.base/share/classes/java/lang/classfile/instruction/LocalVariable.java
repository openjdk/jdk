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

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Label;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;

import jdk.internal.classfile.impl.AbstractPseudoInstruction;
import jdk.internal.classfile.impl.BoundLocalVariable;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.Util;

/**
 * A pseudo-instruction which models a single entry in the
 * {@link LocalVariableTableAttribute}.  Delivered as a {@link CodeElement}
 * during traversal of the elements of a {@link CodeModel}, according to
 * the setting of the {@link ClassFile.DebugElementsOption} option.
 * <p>
 * Conceptually, a local variable table entry is a record:
 * {@snippet lang=text :
 * // @link region=0 substring="LocalVariable" target="#of(int, String, ClassDesc, Label, Label)"
 * // @link region=1 substring="int slot" target="#slot"
 * // @link region=2 substring="String name" target="#name"
 * // @link region=3 substring="ClassDesc type" target="#typeSymbol"
 * // @link substring="Label startScope" target="#startScope" :
 * LocalVariable(int slot, String name, ClassDesc type, Label startScope, Label endScope) // @link substring="Label endScope" target="#endScope"
 * // @end region=0
 * // @end region=1
 * // @end region=2
 * // @end region=3
 * }
 * Where {@code type} must be non-{@code void}.
 * <p>
 * Physically, a local variable table entry modeled by a {@link LocalVariableInfo}.
 * It is a record:
 * {@snippet lang=text :
 * // @link region=0 substring="LocalVariable" target="#of(int, Utf8Entry, Utf8Entry, Label, Label)"
 * // @link region=1 substring="int slot" target="#slot"
 * // @link region=2 substring="Utf8Entry name" target="#name"
 * // @link region=3 substring="Utf8Entry type" target="#type"
 * // @link substring="Label startScope" target="#startScope" :
 * LocalVariable(Label startScope, Label endScope, Utf8Entry name, Utf8Entry type, int slot) // @link substring="Label endScope" target="#endScope"
 * // @end region=0
 * // @end region=1
 * // @end region=2
 * // @end region=3
 * }
 * Where the {@code endScope} is encoded as a nonnegative bci offset to
 * {@code startScope}, a bci value.
 *
 * @apiNote
 * Local variable table entries are used for all local variables in Java source
 * code.  If a local variable has a parameterized type, a type argument, or an
 * array type of one of the previous types, a local variable type table entry is
 * created for that local variable as well.
 *
 * @see LocalVariableTableAttribute
 * @see LocalVariableInfo
 * @see CodeBuilder#localVariable CodeBuilder::localVariable
 * @since 24
 */
public sealed interface LocalVariable extends PseudoInstruction
        permits AbstractPseudoInstruction.UnboundLocalVariable, BoundLocalVariable {
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
     * {@return the local variable field descriptor string}
     *
     * @apiNote
     * A symbolic descriptor for the type of the local variable is available
     * through {@link #typeSymbol() typeSymbol()}.
     */
    Utf8Entry type();

    /**
     * {@return the local variable type, as a symbolic descriptor}
     */
    default ClassDesc typeSymbol() {
        return Util.fieldTypeSymbol(type());
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
     * {@return a local variable pseudo-instruction}
     *
     * @param slot the local variable slot
     * @param nameEntry the local variable name
     * @param descriptorEntry the local variable descriptor
     * @param startScope the start range of the local variable scope
     * @param endScope the end range of the local variable scope
     * @throws IllegalArgumentException if {@code slot} is out of range
     */
    static LocalVariable of(int slot, Utf8Entry nameEntry, Utf8Entry descriptorEntry, Label startScope, Label endScope) {
        return new AbstractPseudoInstruction.UnboundLocalVariable(slot, nameEntry, descriptorEntry,
                                                                  startScope, endScope);
    }

    /**
     * {@return a local variable pseudo-instruction}
     *
     * @param slot the local variable slot
     * @param name the local variable name
     * @param descriptor the local variable descriptor
     * @param startScope the start range of the local variable scope
     * @param endScope the end range of the local variable scope
     * @throws IllegalArgumentException if {@code slot} is out of range
     */
    static LocalVariable of(int slot, String name, ClassDesc descriptor, Label startScope, Label endScope) {
        return of(slot,
                  TemporaryConstantPool.INSTANCE.utf8Entry(name),
                  TemporaryConstantPool.INSTANCE.utf8Entry(descriptor),
                  startScope, endScope);
    }
}
