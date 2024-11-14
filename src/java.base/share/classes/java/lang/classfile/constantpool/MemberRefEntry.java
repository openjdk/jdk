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
package java.lang.classfile.constantpool;

import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.javac.PreviewFeature;

/**
 * Superinterface modeling symbolic references to a member of a class or interface
 * in the constant pool of a {@code class} file, which include references to
 * {@linkplain FieldRefEntry fields}, {@linkplain MethodRefEntry class methods},
 * and {@linkplain InterfaceMethodRefEntry interface methods}.
 * <p>
 * Conceptually, member reference entries are not treated as a single type.  The
 * subtypes appear in distinct locations and serve distinct purposes.  They
 * resemble each other structurally and share parts of the resolution processes.
 * <p>
 * Physically, a member reference entry is a record:
 * {@snippet lang=text :
 * // @link substring="ClassEntry owner" target="#owner()" :
 * MemberRefEntry(ClassEntry owner, NameAndTypeEntry) // @link substring="NameAndTypeEntry" target="#nameAndType()"
 * }
 *
 * @jvms 4.4.2 The {@code CONSTANT_Fieldref_info}, {@code
 *             CONSTANT_Methodref_info}, and {@code
 *             CONSTANT_InterfaceMethodref_info} Structures
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface MemberRefEntry extends PoolEntry
        permits FieldRefEntry, InterfaceMethodRefEntry, MethodRefEntry, AbstractPoolEntry.AbstractMemberRefEntry {
    /**
     * {@return the class or interface which this member belongs to}
     *
     * @apiNote
     * A symbolic descriptor for the owner is available through {@link
     * ClassEntry#asSymbol() owner().asSymbol()}.
     */
    ClassEntry owner();

    /**
     * {@return the name and descriptor string of the member}
     */
    NameAndTypeEntry nameAndType();

    /**
     * {@return the name of the member}
     *
     * @apiNote
     * A string value for the name is available through {@link
     * Utf8Entry#stringValue() name().stringValue()}.
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
