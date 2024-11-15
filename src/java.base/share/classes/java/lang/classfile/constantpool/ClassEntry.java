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

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;

/**
 * Models a {@code CONSTANT_Class_info} structure, representing a reference
 * type, in the constant pool of a {@code class} file.  This describes a class
 * or interface with the internal form of its binary name (JVMS {@jvms 4.2.1}),
 * or an array type with its descriptor string (JVMS {@jvms 4.3.2}).
 * <p>
 * Conceptually, a class entry is a record:
 * {@snippet lang=text :
 * // @link substring="ClassEntry" target="ConstantPoolBuilder#classEntry(ClassDesc)" :
 * ClassEntry(ClassDesc) // @link substring="ClassDesc" target="#asSymbol()"
 * }
 * where the {@code ClassDesc} must not be primitive.
 * <p>
 * Physically, a class entry is a record:
 * {@snippet lang=text :
 * // @link substring="ClassEntry" target="ConstantPoolBuilder#classEntry(Utf8Entry)" :
 * ClassEntry(Utf8Entry) // @link substring="Utf8Entry" target="Utf8Entry"
 * }
 * where the {@code Utf8Entry} is a valid internal form of binary name or array
 * type descriptor string.
 *
 * @apiNote
 * The internal form of a binary name, where all occurrences of {@code .} in the
 * name are replaced by {@code /}, is informally known as an <dfn>{@index
 * "internal name"}</dfn>.  This concept also applies to package names in
 * addition to class and interface names.
 *
 * @see ConstantPoolBuilder#classEntry ConstantPoolBuilder::classEntry
 * @see ClassDesc
 * @jvms 4.4.1 The {@code CONSTANT_Class_info} Structure
 * @since 24
 */
public sealed interface ClassEntry
        extends LoadableConstantEntry
        permits AbstractPoolEntry.ClassEntryImpl {

    /**
     * {@inheritDoc}
     * <p>
     * This is equivalent to {@link #asSymbol() asSymbol()}.
     */
    @Override
    default ConstantDesc constantValue() {
        return asSymbol();
    }

    /**
     * {@return the {@code Utf8Entry} referred by this class entry}  If the
     * value of the UTF8 starts with a {@code [}, this represents an array type
     * and the value is a descriptor string; otherwise, this represents a class
     * or interface and the value is the {@linkplain ##internal-name internal
     * form} of a binary name.
     *
     * @see ConstantPoolBuilder#classEntry(Utf8Entry)
     *      ConstantPoolBuilder::classEntry(Utf8Entry)
     */
    Utf8Entry name();

    /**
     * {@return the represented reference type, as the {@linkplain
     * ##internal-name internal form} of a binary name or an array descriptor
     * string}  The return value is equivalent to {@link #name()
     * name().stringValue()}.
     */
    String asInternalName();

    /**
     * {@return the represented reference type, as a symbolic descriptor}  The
     * returned descriptor is never {@linkplain ClassDesc#isPrimitive()
     * primitive}.
     *
     * @see ConstantPoolBuilder#classEntry(ClassDesc)
     *      ConstantPoolBuilder::classEntry(ClassDesc)
     */
    ClassDesc asSymbol();
}
