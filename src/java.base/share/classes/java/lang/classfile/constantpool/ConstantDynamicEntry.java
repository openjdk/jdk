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

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.Util;

/**
 * Models a {@code CONSTANT_Dynamic_info} structure, representing a <dfn>{@index
 * "dynamically-computed constant"}</dfn>, in the constant pool of a {@code
 * class} file.
 * <p>
 * The use of a {@code ConstantDynamicEntry} is modeled by a {@link
 * DynamicConstantDesc}.  Conversions are through {@link #asSymbol()} and {@link
 * ConstantPoolBuilder#constantDynamicEntry(DynamicConstantDesc)}.
 * <p>
 * A dynamic constant entry is composite:
 * {@snippet lang=text :
 * // @link substring="ConstantDynamicEntry" target="ConstantPoolBuilder#constantDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)" :
 * ConstantDynamicEntry(
 *     BootstrapMethodEntry bootstrap, // @link substring="bootstrap" target="#bootstrap()"
 *     NameAndTypeEntry nameAndType // @link substring="nameAndType" target="#nameAndType()"
 * )
 * }
 * where {@link #type() nameAndType.type()} is a {@linkplain #typeSymbol()
 * field descriptor} string.
 *
 * @apiNote
 * A dynamically-computed constant is frequently called a <dfn>{@index "dynamic
 * constant"}</dfn>, or a <dfn>{@index "condy"}</dfn>, from the abbreviation of
 * "constant dynamic".
 *
 * @see ConstantPoolBuilder#constantDynamicEntry
 *      ConstantPoolBuilder::constantDynamicEntry
 * @see DynamicConstantDesc
 * @see java.lang.invoke##condycon Dynamically-computed constants
 * @jvms 4.4.10 The {@code CONSTANT_Dynamic_info} and {@code
 *              CONSTANT_InvokeDynamic_info} Structures
 * @since 24
 */
public sealed interface ConstantDynamicEntry
        extends DynamicConstantPoolEntry, LoadableConstantEntry
        permits AbstractPoolEntry.ConstantDynamicEntryImpl {

    /**
     * {@return a symbolic descriptor for the {@linkplain #type() field type} of
     * this dynamically-computed constant}
     */
    default ClassDesc typeSymbol() {
        return Util.fieldTypeSymbol(type());
    }

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
     * {@return a symbolic descriptor for this dynamically-computed constant}
     *
     * @see ConstantPoolBuilder#constantDynamicEntry(DynamicConstantDesc)
     *      ConstantPoolBuilder::constantDynamicEntry(DynamicConstantDesc)
     */
    default DynamicConstantDesc<?> asSymbol() {
        return DynamicConstantDesc.ofNamed(bootstrap().bootstrapMethod().asSymbol(),
                                           name().stringValue(),
                                           typeSymbol(),
                                           bootstrap().arguments().stream()
                                                      .map(LoadableConstantEntry::constantValue)
                                                      .toArray(ConstantDesc[]::new));
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * The data type of a dynamically-computed constant depends on its
     * {@linkplain #type() descriptor}, while the data type of all other
     * constants can be determined by their {@linkplain #tag() constant type}.
     */
    @Override
    default TypeKind typeKind() {
        return TypeKind.fromDescriptor(type());
    }
}
