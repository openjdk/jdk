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

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;

/**
 * Models a {@code CONSTANT_Class_info} structure, representing a reference
 * type, in the constant pool of a {@code class} file.
 * <p>
 * The use of a {@code ClassEntry} is modeled by a {@link ClassDesc} that is not
 * primitive.  Conversions are through {@link ConstantPoolBuilder#classEntry(
 * ClassDesc)} and {@link #asSymbol()}.
 * <p>
 * A {@code ClassEntry} is composite:
 * {@snippet lang=text :
 * // @link substring="ClassEntry" target="ConstantPoolBuilder#classEntry(Utf8Entry)" :
 * ClassEntry(Utf8Entry name) // @link substring="name" target="#name"
 * }
 * where {@code name} represents:
 * <ul>
 * <li>The internal form of a binary name (JVMS {@jvms 4.2.1}), if and only if
 * this {@code ClassEntry} represents a class or interface, such as {@code
 * java/lang/String} for the {@link String} class.
 * <li>A field descriptor string (JVMS {@jvms 4.3.2}) representing an array type,
 * if and only if this {@code ClassEntry} represents an array type, such as
 * {@code [I} for the {@code int[]} type, or {@code [Ljava/lang/String;} for the
 * {@code String[]} type.
 * </ul>
 * A field descriptor string for an array type can be distinguished by its
 * leading {@code '['} character.
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
     * {@return the {@code Utf8Entry} referred by this structure}  If the
     * value of the UTF8 starts with a {@code [}, this represents an array type
     * and the value is a descriptor string; otherwise, this represents a class
     * or interface and the value is the {@linkplain ##internalname internal
     * form} of a binary name.
     *
     * @see ConstantPoolBuilder#classEntry(Utf8Entry)
     *      ConstantPoolBuilder::classEntry(Utf8Entry)
     */
    Utf8Entry name();

    /**
     * {@return the represented reference type, as the {@linkplain
     * ##internalname internal form} of a binary name or an array descriptor
     * string}  This is a shortcut for {@link #name() name().stringValue()}.
     */
    String asInternalName();

    /**
     * {@return the represented reference type, as a symbolic descriptor}  The
     * returned descriptor is never {@linkplain ClassDesc#isPrimitive()
     * primitive}.
     *
     * @apiNote
     * If only symbol equivalence is desired, {@link #matches(ClassDesc)
     * matches} should be used.  It requires reduced parsing and can
     * improve {@code class} file reading performance.
     *
     * @see ConstantPoolBuilder#classEntry(ClassDesc)
     *      ConstantPoolBuilder::classEntry(ClassDesc)
     */
    ClassDesc asSymbol();

    /**
     * {@return whether this entry describes the given reference type}  Returns
     * {@code false} if {@code desc} is primitive.
     *
     * @param desc the reference type
     * @since 25
     */
    boolean matches(ClassDesc desc);
}
