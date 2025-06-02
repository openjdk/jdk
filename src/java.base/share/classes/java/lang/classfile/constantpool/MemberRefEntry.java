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
package java.lang.classfile.constantpool;

import java.lang.classfile.Opcode;

import jdk.internal.classfile.impl.AbstractPoolEntry;

/**
 * Superinterface modeling symbolic references to a member of a class or interface
 * in the constant pool of a {@code class} file, which include references to
 * {@linkplain FieldRefEntry fields}, {@linkplain MethodRefEntry class methods},
 * and {@linkplain InterfaceMethodRefEntry interface methods}.
 * <p>
 * Different types of symbolic references to a member of a class or interface
 * bear structural similarities and share parts of the resolution processes, and
 * they can sometimes appear in the same locations.  For example, both {@link
 * MethodRefEntry} and {@link InterfaceMethodRefEntry} can appear in an {@link
 * Opcode#INVOKESTATIC invokestatic} instruction.
 * <p>
 * A member reference entry is composite:
 * {@snippet lang=text :
 * MemberRefEntry(
 *     ClassEntry owner, // @link substring="owner" target="#owner()"
 *     NameAndTypeEntry nameAndType // @link substring="nameAndType" target="#nameAndType()"
 * )
 * }
 *
 * @jvms 4.4.2 The {@code CONSTANT_Fieldref_info}, {@code
 *             CONSTANT_Methodref_info}, and {@code
 *             CONSTANT_InterfaceMethodref_info} Structures
 * @sealedGraph
 * @since 24
 */
public sealed interface MemberRefEntry extends PoolEntry
        permits FieldRefEntry, InterfaceMethodRefEntry, MethodRefEntry, AbstractPoolEntry.AbstractMemberRefEntry {
    /**
     * {@return the class or interface which this member belongs to}
     */
    ClassEntry owner();

    /**
     * {@return the name and descriptor string of the member}
     */
    NameAndTypeEntry nameAndType();

    /**
     * {@return the name of the member}
     */
    default Utf8Entry name() {
        return nameAndType().name();
    }

    /**
     * {@return the descriptor string of the member}  This is a field descriptor
     * string if this entry is a {@link FieldRefEntry}, or a method descriptor
     * string if this entry is a {@link MethodRefEntry} or {@link
     * InterfaceMethodRefEntry}.
     *
     * @apiNote
     * Each subinterface defines a {@code typeSymbol()} accessor for the
     * symbolic descriptor for the member type.
     */
    default Utf8Entry type() {
        return nameAndType().type();
    }
}
