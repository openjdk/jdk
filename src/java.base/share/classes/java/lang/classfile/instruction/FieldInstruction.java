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
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.Util;

/**
 * Models a field access instruction in the {@code code} array of a {@code Code}
 * attribute.  Corresponding opcodes have a {@linkplain Opcode#kind() kind}
 * of {@link Opcode.Kind#FIELD_ACCESS}.  Delivered as a {@link CodeElement} when
 * traversing the elements of a {@link CodeModel}.
 * <p>
 * Conceptually, a field access instruction is a record:
 * {@snippet lang=text :
 * // @link region substring="FieldInstruction" target="#of(Opcode, FieldRefEntry)"
 * // @link substring="Opcode" target="#opcode()" :
 * FieldInstruction(Opcode, FieldRefEntry) // @link substring="FieldRefEntry" target="#field()"
 * // @end
 * // @link region=1 substring="FieldRefEntry" target="ConstantPoolBuilder#fieldRefEntry(ClassDesc, String, ClassDesc)"
 * // @link region=2 substring="ClassDesc owner" target="#owner()"
 * // @link substring="String name" target="#name()" :
 * FieldRefEntry(ClassDesc owner, String name, ClassDesc type) // @link substring="ClassDesc type" target="#typeSymbol()"
 * // @end region=1
 * // @end region=2
 * }
 * where the {@code opcode} is of the field access kind, the {@code owner} is a
 * class or interface, the {@code name} is a simple name, and the {@code type}
 * is not {@link ConstantDescs#CD_void void}.
 * <p>
 * Physically, a field access instruction has the same structure.
 *
 * @see CodeBuilder#fieldAccess CodeBuilder::fieldAccess
 * @since 24
 */
public sealed interface FieldInstruction extends Instruction
        permits AbstractInstruction.BoundFieldInstruction, AbstractInstruction.UnboundFieldInstruction {
    /**
     * {@return the {@link FieldRefEntry} constant described by this instruction}
     */
    FieldRefEntry field();

    /**
     * {@return the class holding the field}
     *
     * @apiNote
     * A symbolic descriptor for the owner is available through {@link
     * ClassEntry#asSymbol() owner().asSymbol()}.
     */
    default ClassEntry owner() {
        return field().owner();
    }

    /**
     * {@return the name of the field}
     *
     * @apiNote
     * A string value for the name is available through {@link
     * Utf8Entry#stringValue() name().stringValue()}.
     */
    default Utf8Entry name() {
        return field().nameAndType().name();
    }

    /**
     * {@return the field descriptor string of the field}
     *
     * @apiNote
     * A symbolic descriptor for the type of the field is available through
     * {@link #typeSymbol() typeSymbol()}.
     */
    default Utf8Entry type() {
        return field().nameAndType().type();
    }

    /**
     * {@return a symbolic descriptor for the type of the field}
     */
    default ClassDesc typeSymbol() {
        return field().typeSymbol();
    }

    /**
     * {@return a field access instruction}
     *
     * @param op the opcode for the specific type of field access instruction,
     *           which must be of kind {@link Opcode.Kind#FIELD_ACCESS}
     * @param field a constant pool entry describing the field
     * @throws IllegalArgumentException if the opcode kind is not
     *         {@link Opcode.Kind#FIELD_ACCESS}.
     */
    static FieldInstruction of(Opcode op, FieldRefEntry field) {
        Util.checkKind(op, Opcode.Kind.FIELD_ACCESS);
        return new AbstractInstruction.UnboundFieldInstruction(op, field);
    }

    /**
     * {@return a field access instruction}
     *
     * @param op the opcode for the specific type of field access instruction,
     *           which must be of kind {@link Opcode.Kind#FIELD_ACCESS}
     * @param owner the class holding the field
     * @param name the name of the field
     * @param type the field descriptor
     */
    static FieldInstruction of(Opcode op,
                               ClassEntry owner,
                               Utf8Entry name,
                               Utf8Entry type) {
        return of(op, owner, TemporaryConstantPool.INSTANCE.nameAndTypeEntry(name, type));
    }

    /**
     * {@return a field access instruction}
     *
     * @param op the opcode for the specific type of field access instruction,
     *           which must be of kind {@link Opcode.Kind#FIELD_ACCESS}
     * @param owner the class holding the field
     * @param nameAndType the name and field descriptor of the field
     */
    static FieldInstruction of(Opcode op,
                               ClassEntry owner,
                               NameAndTypeEntry nameAndType) {
        return of(op, TemporaryConstantPool.INSTANCE.fieldRefEntry(owner, nameAndType));
    }
}
